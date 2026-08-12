#!/usr/bin/env bash
set -Eeuo pipefail
umask 022

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
package_dir="$(cd -- "${script_dir}/.." && pwd -P)"
spec="${package_dir}/openapi/lunara-v1.openapi.json"
output="${package_dir}/generated/kotlin"
generator_image="openapitools/openapi-generator-cli:v7.24.0@sha256:5bf3dc75f764c584da8e3344c51b2f3f1e74703461d46a035b5ac1d31515cc88"

if [[ ! -f "${spec}" ]]; then
  echo "OpenAPI-Spezifikation fehlt: ${spec}. Zuerst npm run export ausführen." >&2
  exit 1
fi
if ! command -v docker >/dev/null 2>&1; then
  echo "Docker wird für die gepinnte OpenAPI-Generator-Version 7.24.0 benötigt." >&2
  exit 1
fi

case "${output}" in
  "${package_dir}/generated/kotlin") ;;
  *)
    echo "Unerwartetes Generator-Ziel; Abbruch: ${output}" >&2
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
  --user "$(id -u):$(id -g)" \
  --mount "type=bind,src=${package_dir},dst=/work" \
  "${generator_image}" generate \
  --input-spec /work/openapi/lunara-v1.openapi.json \
  --generator-name kotlin \
  --config /work/generator-config.json \
  --output /work/generated/kotlin \
  --global-property apiDocs=false,modelDocs=false,apiTests=false,modelTests=false \
  --skip-operation-example

chmod 0755 -- "${output}/gradlew"

docker run --rm \
  --user "$(id -u):$(id -g)" \
  --mount "type=bind,src=${package_dir},dst=/work" \
  --workdir /work \
  node:24.18.0-bookworm-slim@sha256:6f7b03f7c2c8e2e784dcf9295400527b9b1270fd37b7e9a7285cf83b6951452d \
  node scripts/write-generation-metadata.mjs
echo "Kotlin-Client reproduzierbar mit OpenAPI Generator 7.24.0 erzeugt: ${output}"
