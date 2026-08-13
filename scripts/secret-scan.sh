#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
project_dir="$(cd -- "${script_dir}/.." && pwd -P)"

cd -- "${project_dir}"

if git ls-files -co --exclude-standard -z \
  | grep -zE '(^|/)(\.env($|\.)|.*\.(pem|key|p12|jks|keystore)$)' \
  | grep -zvE '(^|/)\.env\.example$' >/dev/null; then
  echo "Abbruch: Eine Secret-/Key-Datei wäre Teil des Commits." >&2
  exit 1
fi

patterns='(BEGIN (RSA |EC |OPENSSH |PGP )?PRIVATE KEY|gh[pousr]_[A-Za-z0-9_]{30,}|github_pat_[A-Za-z0-9_]{40,}|AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9]{32,}|https://[^[:space:]/:]+:[^[:space:]@]+@|AUTHENTIK_TOKEN[[:space:]]*=[[:space:]]*[^$<{[:space:]]|GATEWAY_(USER|PASSWORD)[[:space:]]*=[[:space:]]*[^$<{[:space:]]|(POSTGRES_PASSWORD|APP_MASTER_KEY|AUTH_SIGNING_KEY|REFRESH_TOKEN_PEPPER|BOOTSTRAP_TOKEN|OPENAPI_PASSWORD)[[:space:]]*=[[:space:]]*[^r$<{[:space:]])'

pattern_report="$(mktemp)"
entropy_report="$(mktemp)"
trap 'rm -f -- "${pattern_report}" "${entropy_report}"' EXIT

git ls-files -co --exclude-standard -z \
  | xargs -0 -r grep -IEn "${patterns}" \
  | grep -vE '^(scripts/(generate-secrets|secret-scan|configure-authentik|verify-gateway|e2e)\.sh:|DEPLOYMENT\.md:|apps/android/app/src/(main/res/values(-en)?/strings\.xml|main/java/de/lunara/app/data/repository/SessionSecrets\.kt|test/java/de/lunara/app/core/network/ServerAddressParserTest\.kt):)' \
  | grep -vE '^README\.md:[0-9]+:https://\{BENUTZERNAME\}:\{AUTHENTIK-APP-PASSWORT\}@lunara\.3li361\.de$' \
  > "${pattern_report}" || true

if [[ -s "${pattern_report}" ]]; then
  match_count="$(wc -l < "${pattern_report}")"
  echo "Abbruch: ${match_count} mögliche Zugangsschlüssel oder unredigierte Secrets gefunden (Werte werden nicht ausgegeben)." >&2
  exit 1
fi

# High-entropy candidates are counted without echoing their values.
git ls-files -co --exclude-standard -z \
  | xargs -0 -r grep -IEno '[A-Za-z0-9+/=_-]{48,}' \
  | grep -Ev '(package-lock\.json|gradle-wrapper\.properties|sha256|integrity|distributionUrl|docs/partner-tips\.seed\.json|@sha256:|^[^:]*Dockerfile:[0-9]+:[a-f0-9]{64}$|^docker-compose\.yml:[0-9]+:[a-f0-9]{64}$)' \
  | grep -Ev '(^\.env\.example:|^infra/docker/docker-compose\.test\.yml:|^scripts/(build-android|secret-scan|e2e)\.sh:|^packages/openapi-client/scripts/(export-openapi\.mjs|generate-kotlin\.sh):|^(DEPLOYMENT|IMPLEMENTATION_PLAN|PROGRESS)\.md:)' \
  | grep -Ev '(^FINAL_REPORT\.md:[0-9]+:(9868fb2f45fa2f3ed76e9f9acfa726b16591872f7e16b77a42444c6a760bfb7d|8ec7dab3fee58a65452393887c60e82c61ac27a2d49e0c7966d810d832d19533|10c34b75a1833894ecae89613b9ad25ef4d038d02b25364901ad719ca2f9a5c8)$|^packages/openapi-client/openapi/lunara-v1\.openapi\.json:1009:.*/components/schemas/PredefinedSymptomResponseDto$|^scripts/configure-pangolin\.sh:(43|44|45|46):[a-f0-9]{64}$)' \
  | grep -Ev '(^\.github/workflows/|^apps/android/gradlew:|^artifacts/SHA256SUMS:|^services/api/prisma/migrations/[^/]+/migration\.sql:|^packages/openapi-client/generated/kotlin/(gradlew|\.lunara-generation\.json|\.openapi-generator/FILES):)' \
  > "${entropy_report}" || true

if [[ -s "${entropy_report}" ]]; then
  candidate_count="$(wc -l < "${entropy_report}")"
  echo "Abbruch: ${candidate_count} nicht freigegebene High-Entropy-Kandidaten gefunden (Werte werden nicht ausgegeben)." >&2
  exit 1
fi

if command -v gitleaks >/dev/null; then
  gitleaks git --redact --no-banner --exit-code 1 .
else
  echo "Hinweis: gitleaks ist lokal nicht installiert; CI führt den redigierten History-Scan aus." >&2
fi

echo "Gezielter und High-Entropy-Secret-Scan ohne Treffer abgeschlossen."
