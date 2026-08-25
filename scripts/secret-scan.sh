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

pattern_report="$(git ls-files -co --exclude-standard -z \
  | xargs -0 -r grep -IEn "${patterns}" \
  | grep -vE '^(scripts/(generate-secrets|secret-scan|configure-authentik|verify-gateway|e2e|remove-bootstrap-token)\.sh:|apps/android/app/src/(main/java/de/lunara/app/data/repository/SessionSecrets\.kt|test/java/de/lunara/app/core/network/ServerAddressParserTest\.kt):)' \
  | grep -vE '^README\.md:[0-9]+:https://\{BENUTZERNAME\}:\{AUTHENTIK-APP-PASSWORT\}@lunara\.3li361\.de$' \
  | grep -vE '^FINAL_REPORT\.md:[0-9]+:https://lunara-native-app:APP-PASSWORT@lunara\.3li361\.de$' \
  | grep -vE '^DEPLOYMENT\.md:[0-9]+:.*https://gateway-user:gateway-pass@lunara\.3li361\.de.*$' \
  | grep -vE '^apps/android/app/src/main/res/values/strings\.xml:[0-9]+:    <string name="server_url_example">https://user:pass@lunara\.example\.de</string>$' \
  | grep -vE '^apps/android/app/src/main/res/values-en/strings\.xml:[0-9]+:    <string name="server_url_example">https://user:pass@lunara\.example\.com</string>$' \
  | grep -vE '^apps/android/app/src/main/res/values/strings\.xml:[0-9]+:    <string name="gateway_hint">Trage die normale HTTPS-Adresse ein\. Falls der Server eine eigene Header-Auth-Adresse bereitstellt, kannst du einmalig https://benutzer:app-passwort@dein-server\.example einfügen\. Lunara trennt und verschlüsselt diese Zugangsdaten anschließend\.</string>$' \
  | grep -vE '^apps/android/app/src/main/res/values-en/strings\.xml:[0-9]+:    <string name="gateway_hint">Enter the regular HTTPS address\. If the server provides a dedicated header-auth address, you may enter https://user:app-password@your-server\.example once\. Lunara then separates and encrypts those credentials\.</string>$' \
  | grep -vE '^apps/android/app/src/main/res/values/strings\.xml:[0-9]+:    <string name="error_bootstrap_token">Der Bootstrap-Token muss 20–256 Zeichen lang sein\. Kopiere nur den Wert, ohne „BOOTSTRAP_TOKEN=“ oder Anführungszeichen\.</string>$' \
  | grep -vE '^apps/android/app/src/main/res/values/strings\.xml:[0-9]+:    <string name="error_bootstrap_rejected">Der Server hat den Bootstrap-Token nicht akzeptiert\. Kopiere nur den Wert aus der lokalen Serverdatei, ohne „BOOTSTRAP_TOKEN=“ oder Anführungszeichen\.</string>$' \
  | grep -vE '^apps/android/app/src/main/res/values-en/strings\.xml:[0-9]+:    <string name="error_bootstrap_token">The bootstrap token must be 20–256 characters long\. Copy only its value, without “BOOTSTRAP_TOKEN=” or quotation marks\.</string>$' \
  | grep -vE '^apps/android/app/src/main/res/values-en/strings\.xml:[0-9]+:    <string name="error_bootstrap_rejected">The server did not accept the bootstrap token\. Copy only its value from the local server file, without “BOOTSTRAP_TOKEN=” or quotation marks\.</string>$' \
  || true)"

if [[ -n "${pattern_report}" ]]; then
  match_count="$(printf '%s\n' "${pattern_report}" | wc -l)"
  echo "Abbruch: ${match_count} mögliche Zugangsschlüssel oder unredigierte Secrets gefunden (Werte werden nicht ausgegeben)." >&2
  printf '%s\n' "${pattern_report}" | cut -d: -f1-2 | sort -u \
    | sed 's/^/Sichere Fundstelle: /' >&2
  exit 1
fi
unset pattern_report

# High-entropy candidates are counted without echoing their values.
entropy_report="$(git ls-files -co --exclude-standard -z \
  | xargs -0 -r grep -IEn '[A-Za-z0-9+/=_-]{48,}' \
  | grep -Ev '(package-lock\.json|gradle-wrapper\.properties|sha256|integrity|distributionUrl|docs/partner-tips\.seed\.json|@sha256:|^[^:]*Dockerfile:[0-9]+:[a-f0-9]{64}$|^docker-compose\.yml:[0-9]+:[a-f0-9]{64}$)' \
  | grep -Ev '(^\.env\.example:|^infra/docker/docker-compose\.test\.yml:|^scripts/(build-android|secret-scan|e2e)\.sh:|^packages/openapi-client/scripts/(export-openapi\.mjs|generate-kotlin\.sh):)' \
  | grep -Ev '(^DEPLOYMENT\.md:[0-9]+:.*(/var/backups/lunara/lunara-YYYYMMDDTHHMMSSZ-XXXXXXXX\.tar\.gz|LUNARA_UPLOAD_KEYSTORE=/sicherer/pfad/lunara-upload\.p12).*$|^docs/GOOGLE_PLAY_RELEASE\.md:[0-9]+:.*https://support\.google\.com/googleplay/android-developer/answer/[0-9]+.*$)' \
  | grep -Ev '^docs/GOOGLE_PLAY_RELEASE\.md:[0-9]+:.*(LUNARA_UPLOAD_KEYSTORE=/sicherer/pfad/lunara-upload\.p12|app/build/outputs/bundle/productionRelease/app-production-release\.aab).*$' \
  | grep -Ev '(^apps/android/app/src/test/java/de/lunara/app/data/backup/PortableBackupCryptoTest\.kt:[0-9]+:[[:space:]]*val webEnvelope = .*lunara-portable-backup.*$|^apps/web/src/__tests__/backup\.test\.ts:[0-9]+:[[:space:]]*const vector = .*lunara-portable-backup.*$)' \
  | grep -Ev '(^FINAL_REPORT\.md:[0-9]+:(OpenAPI-Spezifikation SHA-256: `|- SHA-256: `|  Zertifikat SHA-256: `)[a-f0-9]{64}`[.;]?$|^PROGRESS\.md:[0-9]+:  SHA-256 \(v1\.0\.[123]\): `[a-f0-9]{64}`\.$|^TESTING\.md:[0-9]+:`[a-f0-9]{64}`\.$)' \
  | grep -Ev '^(CHANGELOG|FINAL_REPORT|PROGRESS|README|TESTING)\.md:[0-9]+:[[:space:]]*`?(sha256:)?[a-f0-9]{64}`?[.;)]{0,2}$' \
  | grep -Ev '^(FINAL_REPORT|PROGRESS|TESTING)\.md:[0-9]+:.*lunara-pre-server-state-[0-9]{8}T[0-9]{6}Z\.dump.*$' \
  | grep -Ev '^FINAL_REPORT\.md:[0-9]+:.*authentik-before-lunara-single-app-[0-9]{8}T[0-9]{6}Z\.[A-Za-z0-9]+\.dump.*$' \
  | grep -Ev '^(FINAL_REPORT|README)\.md:[0-9]+:.*https://github\.com/eliasbruno124-dev/Lunara/(tree/agent/authentik-human-access|raw/refs/heads/agent/authentik-human-access/artifacts/lunara-(debug|release-unsigned)\.apk|commit/[a-f0-9]{40}).*$' \
  | grep -Ev '(^README\.md:[0-9]+:\[direkter APK-Download\]\(https://github\.com/eliasbruno124-dev/Lunara/releases/download/v[0-9]+\.[0-9]+\.[0-9]+/lunara-debug\.apk\)$|^DEPLOYMENT\.md:[0-9]+:https://docs\.goauthentik\.io/add-secure-apps/providers/proxy/header_authentication$|^IMPLEMENTATION_PLAN\.md:[0-9]+:- Android: JVM-Tests für URL/Auth/Prediction-Sichtmodelle/Queue/Navigation und$|^PROGRESS\.md:[0-9]+:- \[x\] Docker-/CI-/Backup-/Restore-/Update-/Gateway-Skripte und Dokumentation$)' \
  | grep -Ev '^FINAL_REPORT\.md:[0-9]+:https://github\.com/eliasbruno124-dev/Lunara/releases/(tag/v[0-9]+\.[0-9]+\.[0-9]+|download/v[0-9]+\.[0-9]+\.[0-9]+/lunara-release\.apk)$' \
  | grep -Ev '(^packages/openapi-client/openapi/lunara-v1\.openapi\.json:[0-9]+:.*?/components/schemas/PredefinedSymptomResponseDto.*$|^scripts/configure-pangolin\.sh:[0-9]+:expected_(resources|targets|domains|sites|roles|role_resources)_schema="[a-f0-9]{64}"$)' \
  | grep -Ev '^apps/android/app/src/(androidTest|test)/.*\.kt:[0-9]+:[[:space:]]*fun [A-Za-z][A-Za-z0-9]*\(' \
  | grep -Ev '(^\.github/workflows/|^apps/android/gradlew:|^artifacts/SHA256SUMS:|^services/api/prisma/migrations/[^/]+/migration\.sql:|^packages/openapi-client/generated/kotlin/(gradlew|\.lunara-generation\.json|\.openapi-generator/FILES):)' \
  || true)"

if [[ -n "${entropy_report}" ]]; then
  candidate_count="$(printf '%s\n' "${entropy_report}" | wc -l)"
  echo "Abbruch: ${candidate_count} nicht freigegebene High-Entropy-Kandidaten gefunden (Werte werden nicht ausgegeben)." >&2
  printf '%s\n' "${entropy_report}" | cut -d: -f1-2 | sort -u \
    | sed 's/^/Sichere Fundstelle: /' >&2
  exit 1
fi
unset entropy_report

if command -v gitleaks >/dev/null; then
  gitleaks git --redact --no-banner --exit-code 1 .
else
  echo "Hinweis: gitleaks ist lokal nicht installiert; CI führt den redigierten History-Scan aus." >&2
fi

echo "Gezielter und High-Entropy-Secret-Scan ohne Treffer abgeschlossen."
