#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

if [[ $# -ne 2 || "$1" != "--confirm-replace-lunara-database" ]]; then
  echo "Verwendung: $0 --confirm-replace-lunara-database /absoluter/pfad/lunara-....tar.gz" >&2
  echo "Achtung: Der aktuelle Inhalt der Lunara-Datenbank wird ersetzt." >&2
  exit 2
fi

archive="$2"
if [[ "${archive}" != /* || ! -f "${archive}" ]]; then
  echo "Das Backup muss als existierende absolute Datei angegeben werden." >&2
  exit 2
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
project_dir="$(cd -- "${script_dir}/.." && pwd -P)"
env_file="${LUNARA_ENV_FILE:-${project_dir}/.env}"
restore_parent="${LUNARA_RESTORE_TMP_DIR:-/var/backups/lunara}"
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

install -d -m 0700 -- "${restore_parent}"
work_dir="$(mktemp -d --tmpdir="${restore_parent}" .lunara-restore.XXXXXX)"
cleanup() {
  if [[ -n "${work_dir:-}" && "${work_dir}" == "${restore_parent}"/.lunara-restore.* && -d "${work_dir}" ]]; then
    rm -rf -- "${work_dir}"
  fi
  if [[ "${api_stopped:-false}" == "true" && "${restore_committed:-false}" != "true" ]]; then
    docker compose --project-directory "${project_dir}" --env-file "${env_file}" up -d --no-deps api >/dev/null || true
  fi
}
trap cleanup EXIT

tar -xzf "${archive}" -C "${work_dir}" \
  database.dump docker-compose.yml configuration.public.env RESTORE-NOTE.txt SHA256SUMS
(
  cd -- "${work_dir}"
  sha256sum --check SHA256SUMS
)

# Always make a narrow pre-restore backup before the destructive database step.
LUNARA_ENV_FILE="${env_file}" LUNARA_OPERATION_LOCK="${lock_file}.nested.$$" "${script_dir}/backup.sh"

# The explicit --confirm flag authorizes this short maintenance window. Stop
# only Lunara's API; PostgreSQL and every unrelated host service remain online.
api_stopped=true
restore_committed=false
docker compose --project-directory "${project_dir}" --env-file "${env_file}" stop api

docker compose --project-directory "${project_dir}" --env-file "${env_file}" \
  exec -T postgres sh -ceu \
  'psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --set=ON_ERROR_STOP=1 \
    --command="SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname=current_database() AND pid <> pg_backend_pid();"'

docker compose --project-directory "${project_dir}" --env-file "${env_file}" \
  exec -T postgres sh -ceu \
  'pg_restore --clean --if-exists --no-owner --no-acl --single-transaction --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"' \
  < "${work_dir}/database.dump"
restore_committed=true

docker compose --project-directory "${project_dir}" --env-file "${env_file}" run --rm migrate
docker compose --project-directory "${project_dir}" --env-file "${env_file}" up -d --no-deps api

for attempt in {1..20}; do
  if docker compose --project-directory "${project_dir}" --env-file "${env_file}" \
    exec -T api node -e "fetch('http://127.0.0.1:3000/ready').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"; then
    api_stopped=false
    break
  fi
  sleep 3
done
if [[ "${api_stopped}" == "true" ]]; then
  echo "Restore wurde eingespielt, aber die API ist nicht bereit; sie wird vorsichtshalber gestoppt gelassen." >&2
  docker compose --project-directory "${project_dir}" --env-file "${env_file}" stop api
  exit 1
fi

docker compose --project-directory "${project_dir}" --env-file "${env_file}" \
  exec -T postgres sh -ceu 'pg_isready --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"'

echo "Datenbank wiederhergestellt. API-Health und Entschlüsselung einer Testnotiz jetzt prüfen."
