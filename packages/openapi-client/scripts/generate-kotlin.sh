#!/usr/bin/env bash
set -Eeuo pipefail
umask 022

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
package_dir="$(cd -- "${script_dir}/.." && pwd -P)"
spec="${package_dir}/openapi/lunara-v1.openapi.json"
output="${package_dir}/generated/kotlin"
generator_image="openapitools/openapi-generator-cli:v7.24.0@sha256:5bf3dc75f764c584da8e3344c51b2f3f1e74703461d46a035b5ac1d31515cc88"
generator_cpus="${OPENAPI_GENERATOR_CPUS:-0.5}"
generator_memory="${OPENAPI_GENERATOR_MEMORY:-1g}"
generator_memory_swap="${OPENAPI_GENERATOR_MEMORY_SWAP:-1536m}"

if [[ ! -f "${spec}" ]]; then
  echo "OpenAPI specification missing: ${spec}. Run npm run export first." >&2
  exit 1
fi
if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required for the pinned OpenAPI Generator version 7.24.0." >&2
  exit 1
fi

case "${output}" in
  "${package_dir}/generated/kotlin") ;;
  *)
    echo "Unexpected generator destination; aborting: ${output}" >&2
    exit 1
    ;;
esac

# Always generate from a clean, narrowly validated directory so removed API
# operations cannot survive as stale source files.
if [[ -d "${output}" ]]; then
  find "${output}" -mindepth 1 -depth -delete
fi
mkdir -p -- "${output}"
docker run --rm \
  --cpus "${generator_cpus}" \
  --memory "${generator_memory}" \
  --memory-swap "${generator_memory_swap}" \
  --user "$(id -u):$(id -g)" \
  --mount "type=bind,src=${package_dir},dst=/work" \
  "${generator_image}" generate \
  --input-spec /work/openapi/lunara-v1.openapi.json \
  --generator-name kotlin \
  --config /work/generator-config.json \
  --output /work/generated/kotlin \
  --global-property apiDocs=false,modelDocs=false,apiTests=false,modelTests=false \
  --skip-operation-example

# OpenAPI Generator 7.24.0 emits spaces on otherwise empty KDoc lines.
# Normalize generated text before hashing so `git diff --check` remains clean
# and regeneration stays reproducible across developer machines and CI.
find "${output}" -type f \( -name '*.kt' -o -name '*.md' -o -name '*.gradle' -o -name '*.properties' \) \
  -exec sed -i 's/[[:space:]]\+$//' {} +
find "${output}" -type f \( -name '*.kt' -o -name '*.md' -o -name '*.gradle' -o -name '*.properties' \) \
  -exec perl -0pi -e 's/\n(?:[ \t]*\n)+\z/\n/' {} +

chmod 0755 -- "${output}/gradlew"

docker run --rm \
  --cpus 0.5 \
  --memory 256m \
  --memory-swap 384m \
  --user "$(id -u):$(id -g)" \
  --mount "type=bind,src=${package_dir},dst=/work" \
  --workdir /work \
  node:24.18.0-bookworm-slim@sha256:6f7b03f7c2c8e2e784dcf9295400527b9b1270fd37b7e9a7285cf83b6951452d \
  node scripts/write-generation-metadata.mjs
echo "Kotlin client generated reproducibly with OpenAPI Generator 7.24.0: ${output}"
