#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
project_dir="$(cd -- "${script_dir}/.." && pwd -P)"
android_dir="${project_dir}/apps/android"
artifact_dir="${project_dir}/artifacts"
builder_image="${ANDROID_BUILDER_IMAGE:-mingc/android-build-box:1.29.0}"

mkdir -p -- "${artifact_dir}"

docker run --rm \
  --user "$(id -u):$(id -g)" \
  -e GRADLE_USER_HOME=/workspace/.gradle-container \
  -v "${project_dir}:/workspace" \
  -w /workspace/apps/android \
  "${builder_image}" \
  ./gradlew --no-daemon --max-workers=1 \
    testLocalDebugUnitTest \
    lintLocalDebug \
    lintProductionDebug \
    assembleProductionDebug

debug_apk="${android_dir}/app/build/outputs/apk/production/debug/app-production-debug.apk"
if [[ ! -s "${debug_apk}" ]]; then
  echo "HTTPS-only Production-Debug-APK fehlt trotz Build: ${debug_apk}" >&2
  exit 1
fi
install -m 0644 -- "${debug_apk}" "${artifact_dir}/lunara-debug.apk"
(
  cd -- "${artifact_dir}"
  sha256sum lunara-debug.apk > SHA256SUMS
)

if [[ "${BUILD_ANDROID_RELEASE:-false}" != "true" ]]; then
  echo "HTTPS-only Production-Debug-APK geprüft und nach ${artifact_dir} kopiert."
  echo "Optionaler unsigned Release-Build: BUILD_ANDROID_RELEASE=true scripts/build-android.sh"
  exit 0
fi

docker run --rm \
  --user "$(id -u):$(id -g)" \
  -e GRADLE_USER_HOME=/workspace/.gradle-container \
  -v "${project_dir}:/workspace" \
  -w /workspace/apps/android \
  "${builder_image}" \
  ./gradlew --no-daemon --max-workers=1 assembleProductionRelease

release_apk="${android_dir}/app/build/outputs/apk/production/release/app-production-release-unsigned.apk"

if [[ ! -s "${release_apk}" ]]; then
  echo "Debug-APK wurde gesichert, aber unsigned Release-APK fehlt: ${release_apk}" >&2
  exit 1
fi

install -m 0644 -- "${release_apk}" "${artifact_dir}/lunara-release-unsigned.apk"
(
  cd -- "${artifact_dir}"
  sha256sum lunara-debug.apk lunara-release-unsigned.apk > SHA256SUMS
)
echo "Android-Artefakte geprüft und nach ${artifact_dir} kopiert."
