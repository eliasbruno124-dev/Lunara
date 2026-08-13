#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

credential_file="${1:-${LUNARA_GATEWAY_URL_FILE:-/docker/lunara/gateway-a.url}}"
resolve_ip="${LUNARA_GATEWAY_RESOLVE_IP:-}"
allow_untrusted_tls="${LUNARA_GATEWAY_ALLOW_UNTRUSTED_TLS:-false}"

for command_name in curl jq stat mktemp; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Fehlendes Werkzeug: ${command_name}" >&2
    exit 1
  fi
done

if [[ "${credential_file}" != /* || ! -f "${credential_file}" || -L "${credential_file}" || ! -O "${credential_file}" ]]; then
  echo "Die Gateway-Datei muss eine eigene, reguläre absolute Datei sein und darf kein Symlink sein." >&2
  exit 2
fi

file_mode="$(stat -c '%a' -- "${credential_file}")"
if (( (8#${file_mode} & 077) != 0 )); then
  echo "Die Gateway-Datei darf keine Gruppen- oder Weltrechte besitzen." >&2
  exit 1
fi

mapfile -t credential_lines < "${credential_file}"
if [[ ${#credential_lines[@]} -ne 1 ]]; then
  echo "Die Gateway-Datei muss genau eine URL enthalten." >&2
  exit 1
fi
gateway_url="${credential_lines[0]}"
unset credential_lines

# configure-authentik.sh percent-encodes both user-info fields. Requiring this
# narrow shape prevents redirects, paths or non-TLS endpoints from being tested.
gateway_pattern='^https://([A-Za-z0-9._~-]|%[[:xdigit:]]{2})+:([A-Za-z0-9._~-]|%[[:xdigit:]]{2})+@[A-Za-z0-9.-]+(:[0-9]{1,5})?/?$'
if [[ ! "${gateway_url}" =~ ${gateway_pattern} ]]; then
  echo "Die Gateway-Datei enthält keine unterstützte HTTPS-URL." >&2
  exit 1
fi

without_scheme="${gateway_url#https://}"
public_authority="${without_scheme#*@}"
public_authority="${public_authority%/}"
public_host="${public_authority%%:*}"
authenticated_endpoint="${gateway_url%/}/api-docs-json"
cookie_endpoint="https://${public_authority}/api-docs-json"
public_endpoint="https://${public_authority}/api/v1/meta"
unset without_scheme gateway_pattern

curl_network_options=()
if [[ -n "${resolve_ip}" ]]; then
  if [[ "${public_authority}" == *:* && "${public_authority}" != *:443 ]]; then
    echo "LUNARA_GATEWAY_RESOLVE_IP unterstützt ausschließlich den HTTPS-Standardport 443." >&2
    exit 2
  fi
  if ! command -v python3 >/dev/null 2>&1; then
    echo "Für die kanonische IP-Prüfung von LUNARA_GATEWAY_RESOLVE_IP wird python3 benötigt." >&2
    exit 1
  fi
  if ! canonical_ip="$(python3 - "${resolve_ip}" <<'PY'
import ipaddress
import sys

candidate = sys.argv[1]
if "%" in candidate:
    raise SystemExit(1)
try:
    parsed = ipaddress.ip_address(candidate)
except ValueError:
    raise SystemExit(1) from None
if str(parsed) != candidate:
    raise SystemExit(1)
print(parsed)
PY
  )"; then
    echo "LUNARA_GATEWAY_RESOLVE_IP muss eine kanonische IPv4- oder IPv6-Adresse sein." >&2
    exit 2
  fi
  resolve_target="${canonical_ip}"
  if [[ "${canonical_ip}" == *:* ]]; then
    resolve_target="[${canonical_ip}]"
  fi
  curl_network_options+=(--resolve "${public_host}:443:${resolve_target}")
  unset canonical_ip resolve_target resolve_ip
fi

case "${allow_untrusted_tls}" in
  false)
    ;;
  true)
    echo "WARNUNG: TLS-Zertifikatsprüfung ist nur für diesen Gateway-Test deaktiviert." >&2
    curl_network_options+=(--insecure)
    ;;
  *)
    echo "LUNARA_GATEWAY_ALLOW_UNTRUSTED_TLS akzeptiert nur true oder false." >&2
    exit 2
    ;;
esac
unset allow_untrusted_tls

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/lunara-gateway-check.XXXXXX")"
cleanup() {
  unset gateway_url authenticated_endpoint
  if [[ -n "${work_dir:-}" && "${work_dir}" == "${TMPDIR:-/tmp}"/lunara-gateway-check.* ]]; then
    rm -rf -- "${work_dir}"
  fi
}
trap cleanup EXIT

curl_config="${work_dir}/curl.conf"
cookie_jar="${work_dir}/cookies.txt"
response_file="${work_dir}/meta.json"

# Keeping the credential-bearing URL in a 0600 temporary config avoids exposing
# it through curl's process arguments. Neither response bodies nor curl errors are
# printed, because either can contain gateway-controlled content.
printf '%s\n' \
  'silent' \
  'request = "GET"' \
  'connect-timeout = 10' \
  'max-time = 30' \
  "output = \"${response_file}\"" \
  "cookie-jar = \"${cookie_jar}\"" \
  'write-out = "%{http_code}"' \
  "url = \"${authenticated_endpoint}\"" \
  > "${curl_config}"

if ! first_status="$(curl --disable --proto '=https' --tlsv1.2 \
  "${curl_network_options[@]}" --config "${curl_config}" 2>/dev/null)"; then
  echo "Gateway-Prüfung fehlgeschlagen: TLS-, DNS- oder Verbindungsfehler." >&2
  exit 1
fi
unset gateway_url authenticated_endpoint

if [[ "${first_status}" != "200" ]] ||
   ! jq -e '
     (.openapi | type == "string" and length > 0) and
     (.info.title | type == "string" and length > 0) and
     (.info.version | type == "string" and length > 0)
   ' "${response_file}" >/dev/null 2>&1; then
  echo "Gateway-Prüfung fehlgeschlagen: Authentik lieferte keine gültige geschützte Lunara-OpenAPI-Antwort (HTTP ${first_status})." >&2
  exit 1
fi

if ! awk '
  /^#HttpOnly_/ { found = 1 }
  !/^#/ && NF >= 7 { found = 1 }
  END { exit(found ? 0 : 1) }
' "${cookie_jar}"; then
  echo "Gateway-Prüfung fehlgeschlagen: Authentik hat keine Sitzung gesetzt." >&2
  exit 1
fi

: > "${response_file}"
if ! cookie_status="$(curl --disable --silent --proto '=https' --tlsv1.2 \
  "${curl_network_options[@]}" \
  --connect-timeout 10 --max-time 30 \
  --output "${response_file}" --cookie "${cookie_jar}" \
  --write-out '%{http_code}' "${cookie_endpoint}" 2>/dev/null)"; then
  echo "Gateway-Prüfung fehlgeschlagen: Die Authentik-Sitzung konnte nicht erneut verwendet werden." >&2
  exit 1
fi

if [[ "${cookie_status}" != "200" ]] ||
   ! jq -e '(.openapi | type == "string" and length > 0) and (.info.title | type == "string" and length > 0)' \
   "${response_file}" >/dev/null 2>&1; then
  echo "Gateway-Prüfung fehlgeschlagen: Die Authentik-Sitzung öffnete die geschützte OpenAPI nicht (HTTP ${cookie_status})." >&2
  exit 1
fi

: > "${response_file}"
if ! public_status="$(curl --disable --silent --proto '=https' --tlsv1.2 \
  "${curl_network_options[@]}" \
  --connect-timeout 10 --max-time 30 \
  --output "${response_file}" --write-out '%{http_code}' \
  "${public_endpoint}" 2>/dev/null)"; then
  echo "Gateway-Prüfung fehlgeschlagen: Der native Lunara-Metaendpoint ist nicht erreichbar." >&2
  exit 1
fi
if [[ "${public_status}" != "200" ]] ||
   ! jq -e '
     .apiVersion == "v1" and
     (.name | type == "string" and length > 0) and
     (.setupRequired | type == "boolean") and
     (.registrationEnabled | type == "boolean")
   ' "${response_file}" >/dev/null 2>&1; then
  echo "Gateway-Prüfung fehlgeschlagen: Der native Lunara-Metaendpoint lieferte keine gültige Antwort (HTTP ${public_status})." >&2
  exit 1
fi

echo "Gateway-Prüfung erfolgreich: Authentik schützt OpenAPI; Sitzung und native Lunara-API funktionieren."
