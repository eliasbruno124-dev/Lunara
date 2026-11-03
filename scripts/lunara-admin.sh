#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
project_dir="$(cd -- "${script_dir}/.." && pwd -P)"
env_file="${LUNARA_ENV_FILE:-${project_dir}/.env}"
lock_file="${LUNARA_OPERATION_LOCK:-/run/lock/lunara-maintenance.lock}"

lunara_compose() {
  LUNARA_ENV_FILE="${env_file}" "${script_dir}/compose.sh" "$@"
}

usage() {
  cat >&2 <<'USAGE'
Usage:
  ./scripts/lunara-admin.sh users list
  ./scripts/lunara-admin.sh bootstrap status
  ./scripts/lunara-admin.sh invitations list
  ./scripts/lunara-admin.sh users disable --user-id UUID --confirm DISABLE:UUID
  ./scripts/lunara-admin.sh sessions revoke-all --user-id UUID --confirm REVOKE-SESSIONS:UUID
  ./scripts/lunara-admin.sh invitations revoke --invitation-id UUID --confirm REVOKE-INVITATION:UUID
  ./scripts/lunara-admin.sh backup
  ./scripts/lunara-admin.sh migrate --confirm MIGRATE

LUNARA_ENV_FILE can point to a specifically named alternative environment file.
USAGE
}

if [[ ! -f "${env_file}" ]]; then
  echo "Configuration file missing: ${env_file}" >&2
  exit 1
fi
if [[ $# -lt 1 ]]; then
  usage
  exit 2
fi

case "$1" in
  backup)
    if [[ $# -ne 1 ]]; then usage; exit 2; fi
    LUNARA_ENV_FILE="${env_file}" "${script_dir}/backup.sh"
    ;;
  migrate)
    if [[ $# -ne 3 || "$2" != "--confirm" || "$3" != "MIGRATE" ]]; then
      echo "Migration rejected: --confirm MIGRATE is missing." >&2
      exit 2
    fi
    exec 9>"${lock_file}"
    if ! flock -n 9; then
      echo "Another Lunara backup, update, or restore operation is already running." >&2
      exit 1
    fi
    # Keep the maintenance lock for backup plus migration. The backup gets a
    # narrowly named nested lock because descriptor 9 already owns the global one.
    LUNARA_ENV_FILE="${env_file}" \
      LUNARA_OPERATION_LOCK="${lock_file}.nested.$$" \
      "${script_dir}/backup.sh"
    lunara_compose run --rm migrate
    ;;
  users|sessions|bootstrap|invitations)
    lunara_compose exec -T api node services/api/dist/admin/admin-cli.js "$@"
    ;;
  *)
    usage
    exit 2
    ;;
esac
