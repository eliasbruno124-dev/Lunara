#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
project_dir="$(cd -- "${script_dir}/.." && pwd -P)"
compose_file="${project_dir}/infra/docker/docker-compose.test.yml"
project_name="lunara-e2e-$RANDOM-$$"
db_port="${LUNARA_E2E_DB_PORT:-55432}"

if [[ ! "${db_port}" =~ ^[0-9]+$ ]] || (( db_port < 1024 || db_port > 65535 )); then
  echo "LUNARA_E2E_DB_PORT must be an available port between 1024 and 65535." >&2
  exit 1
fi
export LUNARA_E2E_DB_PORT="${db_port}"

cleanup() {
  # Tests create only this uniquely named ephemeral project. Removal is explicit
  # and cannot resolve to a production stack.
  docker compose --project-name "${project_name}" -f "${compose_file}" down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker compose --project-name "${project_name}" -f "${compose_file}" up -d postgres-test
for attempt in {1..30}; do
  if docker compose --project-name "${project_name}" -f "${compose_file}" exec -T postgres-test \
    pg_isready -U lunara_test -d lunara_test >/dev/null; then
    break
  fi
  sleep 1
done

export NODE_ENV=test
export DATABASE_URL="postgresql://lunara_test:integration-only-password@127.0.0.1:${db_port}/lunara_test?schema=public"
export PUBLIC_BASE_URL="http://api-test:3000"
export APP_MASTER_KEY="AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
export AUTH_SIGNING_KEY="integration-only-signing-key-at-least-32-bytes"
export REFRESH_TOKEN_PEPPER="integration-only-refresh-pepper-at-least-32-bytes"
export BOOTSTRAP_TOKEN="integration-only-bootstrap-token"

cd "${project_dir}"
npm run prisma:generate --workspace=@lunara/api
npm run prisma:migrate:deploy --workspace=@lunara/api
npm run build --workspace=@lunara/api
npm run test:e2e --workspace=@lunara/api

echo "Isolated PostgreSQL 18 E2E flow succeeded."
