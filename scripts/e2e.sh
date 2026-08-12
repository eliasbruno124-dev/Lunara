#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
project_dir="$(cd -- "${script_dir}/.." && pwd -P)"
compose_file="${project_dir}/infra/docker/docker-compose.test.yml"
project_name="lunara-e2e-$RANDOM-$$"
test_network="${project_name}_test_internal"
db_container="${project_name}-postgres-test-1"

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

docker run --rm --network "${test_network}" \
  -e NODE_ENV=test \
  -e DATABASE_URL="postgresql://lunara_test:integration-only-password@postgres-test:5432/lunara_test?schema=public" \
  -e PUBLIC_BASE_URL="http://api-test:3000" \
  -e APP_MASTER_KEY="AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" \
  -e AUTH_SIGNING_KEY="integration-only-signing-key-at-least-32-bytes" \
  -e REFRESH_TOKEN_PEPPER="integration-only-refresh-pepper-at-least-32-bytes" \
  -e BOOTSTRAP_TOKEN="integration-only-bootstrap-token" \
  -v "${project_dir}:/workspace" \
  -w /workspace \
  node:24.18.0-bookworm-slim@sha256:6f7b03f7c2c8e2e784dcf9295400527b9b1270fd37b7e9a7285cf83b6951452d \
  sh -ceu 'npm run prisma:migrate:deploy --workspace=@lunara/api && npm run test:e2e --workspace=@lunara/api'

echo "Isolierter PostgreSQL-18-E2E-Ablauf erfolgreich."
