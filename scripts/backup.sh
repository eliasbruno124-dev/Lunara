#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
project_dir="$(cd -- "${script_dir}/.." && pwd -P)"
env_file="${LUNARA_ENV_FILE:-${project_dir}/.env}"
backup_root="${LUNARA_BACKUP_DIR:-/var/backups/lunara}"
lock_file="${LUNARA_OPERATION_LOCK:-/run/lock/lunara-maintenance.lock}"

exec 9>"${lock_file}"
if ! flock -n 9; then
  echo "Eine andere Lunara-Backup-/Update-/Restore-Operation läuft bereits." >&2
  exit 1
fi

if [[ ! -f "${env_file}" ]]; then
  echo "Fehlt: ${env_file}" >&2
  exit 1
fi

install -d -m 0700 -- "${backup_root}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
work_dir="$(mktemp -d --tmpdir="${backup_root}" ".lunara-${timestamp}.XXXXXX")"
archive="${backup_root}/lunara-${timestamp}-$(openssl rand -hex 4).tar.gz"

cleanup() {
  if [[ -n "${work_dir:-}" && "${work_dir}" == "${backup_root}"/.lunara-* && -d "${work_dir}" ]]; then
    rm -rf -- "${work_dir}"
  fi
}
trap cleanup EXIT

docker compose --project-directory "${project_dir}" --env-file "${env_file}" \
  exec -T postgres sh -ceu \
  'pg_dump --format=custom --no-owner --no-acl --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"' \
  > "${work_dir}/database.dump"

cp -- "${project_dir}/docker-compose.yml" "${work_dir}/docker-compose.yml"
awk -F= '
  /^(APP_NAME|APP_VERSION|PUBLIC_BASE_URL|PUBLIC_HOST|POSTGRES_DB|POSTGRES_USER|REGISTRATION_ENABLED|NTPY_ENABLED|NTPY_BASE_URL|OPENAPI_ENABLED|OPENAPI_TRUST_AUTHENTIK|CORS_ORIGINS|LOG_LEVEL|ACCESS_TOKEN_TTL_SECONDS|REFRESH_TOKEN_TTL_DAYS|INVITATION_TTL_MINUTES|TZ|PROXY_NETWORK)=/ { print }
' "${env_file}" > "${work_dir}/configuration.public.env"

cat > "${work_dir}/RESTORE-NOTE.txt" <<'NOTE'
This archive intentionally does not contain APP_MASTER_KEY, AUTH_SIGNING_KEY,
REFRESH_TOKEN_PEPPER, BOOTSTRAP_TOKEN, or the PostgreSQL password. Restore it
with the original APP_MASTER_KEY; encrypted health notes cannot be decrypted
without that exact key. Keep the key in a separate encrypted offline backup.
NOTE

(
  cd -- "${work_dir}"
  sha256sum database.dump docker-compose.yml configuration.public.env RESTORE-NOTE.txt > SHA256SUMS
  tar -czf "${archive}" database.dump docker-compose.yml configuration.public.env RESTORE-NOTE.txt SHA256SUMS
)
chmod 0600 -- "${archive}"

echo "Backup erstellt: ${archive}"
echo "APP_MASTER_KEY ist absichtlich nicht enthalten und muss separat gesichert sein."
