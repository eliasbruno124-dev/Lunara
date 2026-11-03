#!/usr/bin/env bash
set -Eeuo pipefail
set +x

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
project_dir="$(cd -- "${script_dir}/.." && pwd -P)"
environment_file="${LUNARA_ENV_FILE:-${project_dir}/.env}"
oidc_environment_file="${LUNARA_OIDC_ENV_FILE:-${project_dir}/authentik-oidc.env}"

if [[ ! -f "${environment_file}" || -L "${environment_file}" ]]; then
  echo "The Lunara environment file is missing or unsafe: ${environment_file}" >&2
  exit 1
fi

gateway_mode="${GATEWAY_MODE:-}"
if [[ -z "${gateway_mode}" ]]; then
  gateway_mode="$(sed -n 's/^[[:space:]]*GATEWAY_MODE[[:space:]]*=[[:space:]]*\([^#[:space:]]\+\).*$/\1/p' "${environment_file}" | tail -n 1)"
fi
case "${gateway_mode}" in
  direct|authentik) ;;
  *)
    echo "GATEWAY_MODE must be exactly direct or authentik." >&2
    exit 2
    ;;
esac

compose_args=(
  --project-directory "${project_dir}"
  --env-file "${environment_file}"
  --file "${project_dir}/docker-compose.yml"
)

if [[ "${gateway_mode}" == "direct" ]]; then
  compose_args+=(--file "${project_dir}/infra/docker/docker-compose.direct.yml")
else
  if [[ -e "${oidc_environment_file}" ]]; then
    if [[ ! -f "${oidc_environment_file}" || -L "${oidc_environment_file}" ]]; then
      echo "Unsafe Authentik OIDC environment file: ${oidc_environment_file}" >&2
      exit 1
    fi
    compose_args+=(--env-file "${oidc_environment_file}")
  fi
fi

if [[ $# -eq 0 ]]; then
  echo "Usage: $0 <docker-compose-command> [arguments...]" >&2
  echo "Example: $0 config --quiet" >&2
  exit 2
fi

exec docker compose "${compose_args[@]}" "$@"
