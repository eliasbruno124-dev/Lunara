#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
project_dir="$(cd -- "${script_dir}/.." && pwd -P)"
env_file="${LUNARA_ENV_FILE:-${project_dir}/.env}"
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

# File descriptor 9 already owns the maintenance lock; use a distinct lock for
# the nested backup while preserving mutual exclusion for external operations.
LUNARA_ENV_FILE="${env_file}" LUNARA_OPERATION_LOCK="${lock_file}.nested.$$" "${script_dir}/backup.sh"
docker compose --project-directory "${project_dir}" --env-file "${env_file}" build --pull api
docker compose --project-directory "${project_dir}" --env-file "${env_file}" run --rm migrate
docker compose --project-directory "${project_dir}" --env-file "${env_file}" up -d --no-deps api

for attempt in {1..20}; do
  if docker compose --project-directory "${project_dir}" --env-file "${env_file}" \
    exec -T api node -e "fetch('http://127.0.0.1:3000/ready').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"; then
    echo "Lunara wurde aktualisiert und ist bereit."
    exit 0
  fi
  sleep 3
done

echo "Update abgeschlossen, Readiness blieb jedoch fehlerhaft. Logs prüfen; Backup liegt bereit." >&2
exit 1
