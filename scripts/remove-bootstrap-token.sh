#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
project_dir="$(cd -- "${script_dir}/.." && pwd -P)"
env_file="${LUNARA_ENV_FILE:-${project_dir}/.env}"

if [[ ! -f "${env_file}" ]]; then
  echo "Missing: ${env_file}" >&2
  exit 1
fi

if ! grep -q '^BOOTSTRAP_TOKEN=' "${env_file}"; then
  echo "BOOTSTRAP_TOKEN has already been removed."
  exit 0
fi

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_file="${env_file}.pre-bootstrap-removal-${timestamp}"
temp_file="$(mktemp --tmpdir="$(dirname -- "${env_file}")" .lunara-env.XXXXXX)"

cp --preserve=mode,ownership,timestamps -- "${env_file}" "${backup_file}"
chmod 0600 -- "${backup_file}"

awk '!/^BOOTSTRAP_TOKEN=/' "${env_file}" > "${temp_file}"
chmod --reference="${env_file}" "${temp_file}"
chown --reference="${env_file}" "${temp_file}"
mv -- "${temp_file}" "${env_file}"

echo "BOOTSTRAP_TOKEN removed; server-local backup: ${backup_file}"
