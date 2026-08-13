#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

credential_file="${1:-${LUNARA_GATEWAY_URL_FILE:-/docker/lunara/lunara-app-headerauth.url}}"
public_host="${LUNARA_PUBLIC_HOST:-lunara.3li361.de}"
resolve_ip="${LUNARA_GATEWAY_RESOLVE_IP:-}"
allow_untrusted_tls="${LUNARA_GATEWAY_ALLOW_UNTRUSTED_TLS:-false}"

if ! command -v python3 >/dev/null 2>&1; then
  echo "Fehlendes Werkzeug: python3" >&2
  exit 1
fi

case "${allow_untrusted_tls}" in
  false)
    ;;
  true)
    echo "WARNUNG: TLS-Zertifikatsprüfung ist nur für diesen Gateway-Test deaktiviert." >&2
    ;;
  *)
    echo "LUNARA_GATEWAY_ALLOW_UNTRUSTED_TLS akzeptiert nur true oder false." >&2
    exit 2
    ;;
esac

if ! python3 - "${credential_file}" "${public_host}" "${resolve_ip}" "${allow_untrusted_tls}" <<'PYTHON'
import base64
import http.client
import ipaddress
import json
import os
import socket
import ssl
import stat
import sys
from urllib.parse import unquote, urlsplit


class VerificationError(Exception):
    pass


def safe_excepthook(exception_type, exception, traceback):
    if issubclass(exception_type, VerificationError):
        print(f"Gateway-Prüfung: {exception}", file=sys.stderr)
    else:
        print("Gateway-Prüfung: interner Prüfablauffehler.", file=sys.stderr)


sys.excepthook = safe_excepthook


def fail(message):
    raise VerificationError(message)


credential_path, expected_host, resolve_ip, allow_untrusted_tls = sys.argv[1:]
if not os.path.isabs(credential_path):
    fail("Die Gateway-Datei muss ein absoluter Pfad sein.")
try:
    file_stat = os.lstat(credential_path)
except OSError:
    fail("Die Gateway-Datei ist nicht lesbar.")
if (
    not stat.S_ISREG(file_stat.st_mode)
    or stat.S_ISLNK(file_stat.st_mode)
    or file_stat.st_uid != os.geteuid()
    or stat.S_IMODE(file_stat.st_mode) != 0o600
):
    fail("Die Gateway-Datei muss eine eigene reguläre 0600-Datei sein.")
try:
    with open(credential_path, "r", encoding="ascii", newline="") as credential_stream:
        raw_url = credential_stream.read(2049)
except (OSError, UnicodeError):
    fail("Die Gateway-Datei konnte nicht sicher gelesen werden.")
if len(raw_url) > 2048 or "\r" in raw_url or raw_url.count("\n") != 1 or not raw_url.endswith("\n"):
    fail("Die Gateway-Datei muss genau eine kurze ASCII-URL enthalten.")
gateway_url = raw_url[:-1]
del raw_url
try:
    parsed = urlsplit(gateway_url)
except ValueError:
    fail("Die Gateway-Datei enthält keine unterstützte URL.")
del gateway_url
if (
    parsed.scheme != "https"
    or parsed.hostname != expected_host
    or parsed.port not in (None, 443)
    or parsed.path not in ("", "/")
    or parsed.query
    or parsed.fragment
    or parsed.username is None
    or parsed.password is None
):
    fail("Die Gateway-Datei enthält keine kanonische Lunara-HTTPS-URL.")
username = unquote(parsed.username)
password = unquote(parsed.password)
if (
    username != "lunara-native-app"
    or not (20 <= len(password) <= 255)
    or not password.isascii()
    or not password.isalnum()
):
    fail("Die Gateway-Datei enthält nicht den erwarteten technischen App-Zugang.")
del parsed

if resolve_ip:
    try:
        canonical_ip = str(ipaddress.ip_address(resolve_ip))
    except ValueError:
        fail("LUNARA_GATEWAY_RESOLVE_IP ist keine kanonische IP-Adresse.")
    if canonical_ip != resolve_ip or "%" in resolve_ip:
        fail("LUNARA_GATEWAY_RESOLVE_IP ist keine kanonische IP-Adresse.")

tls_context = ssl.create_default_context()
tls_context.minimum_version = ssl.TLSVersion.TLSv1_2
if allow_untrusted_tls == "true":
    tls_context.check_hostname = False
    tls_context.verify_mode = ssl.CERT_NONE


class ResolvedHTTPSConnection(http.client.HTTPSConnection):
    def connect(self):
        target = resolve_ip or self.host
        plain_socket = socket.create_connection((target, self.port), self.timeout)
        self.sock = self._context.wrap_socket(plain_socket, server_hostname=self.host)


def request(path, authenticated):
    connection = ResolvedHTTPSConnection(
        expected_host,
        443,
        timeout=15,
        context=tls_context,
    )
    headers = {
        "Accept": "application/json",
        "Host": expected_host,
        "User-Agent": "Lunara-Gateway-Verifier/1",
    }
    if authenticated:
        basic_value = base64.b64encode(f"{username}:{password}".encode("ascii")).decode("ascii")
        headers["Authorization"] = f"Basic {basic_value}"
    try:
        connection.request("GET", path, headers=headers)
        response = connection.getresponse()
        body = response.read(2_097_153)
        if len(body) > 2_097_152:
            fail("Eine Gateway-Antwort überschreitet die sichere Prüfgröße.")
        return response.status, response.getheader("Location"), body
    except (OSError, ssl.SSLError, http.client.HTTPException):
        fail("TLS-, DNS- oder Verbindungsfehler bei der Gateway-Prüfung.")
    finally:
        connection.close()


meta_status, _, meta_body = request("/api/v1/meta", authenticated=True)
try:
    meta = json.loads(meta_body)
except (UnicodeError, json.JSONDecodeError):
    meta = None
if (
    meta_status != 200
    or not isinstance(meta, dict)
    or meta.get("apiVersion") != "v1"
    or not isinstance(meta.get("name"), str)
    or not isinstance(meta.get("setupRequired"), bool)
    or not isinstance(meta.get("registrationEnabled"), bool)
):
    fail("Header-Auth lieferte keine gültige Lunara-Metaantwort.")
del meta_body, meta

docs_status, _, docs_body = request("/api-docs-json", authenticated=True)
try:
    docs = json.loads(docs_body)
except (UnicodeError, json.JSONDecodeError):
    docs = None
if (
    docs_status != 200
    or not isinstance(docs, dict)
    or not isinstance(docs.get("openapi"), str)
    or not isinstance(docs.get("info"), dict)
    or not isinstance(docs["info"].get("title"), str)
):
    fail("Header-Auth lieferte keine gültige geschützte Lunara-OpenAPI-Antwort.")
del docs_body, docs

for protected_path in (
    "/",
    "/health",
    "/api-docs-json",
    "/api/v1/meta",
    "/api/v1/auth/login",
):
    status_code, redirect_location, body = request(protected_path, authenticated=False)
    if status_code not in (302, 303, 307, 308) or not redirect_location:
        fail("Mindestens ein Lunara-Pfad ist ohne Authentik-Zugang erreichbar.")
    try:
        redirect = urlsplit(redirect_location)
    except ValueError:
        fail("Authentik lieferte ein ungültiges Weiterleitungsziel.")
    if redirect.username is not None or redirect.password is not None:
        fail("Authentik lieferte ein unsicheres Weiterleitungsziel.")
    if redirect.scheme and redirect.scheme != "https":
        fail("Authentik leitete nicht auf HTTPS weiter.")
    del body, redirect

# Discard credentials before emitting the non-sensitive result line.
username = ""
password = ""
print(
    "Gateway-Prüfung erfolgreich: Ein technischer App-Zugang funktioniert; "
    "alle geprüften Lunara-Pfade verlangen Authentik."
)
PYTHON
then
  echo "Gateway-Prüfung fehlgeschlagen; Zugangsdaten werden nicht ausgegeben." >&2
  exit 1
fi
