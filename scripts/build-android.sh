#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
project_dir="$(cd -- "${script_dir}/.." && pwd -P)"
android_dir="${project_dir}/apps/android"
artifact_dir="${project_dir}/artifacts"
builder_image="${ANDROID_BUILDER_IMAGE:-mingc/android-build-box:1.29.0}"
gradle_jvm_args="${ANDROID_GRADLE_JVM_ARGS:--Xmx768m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8}"

mkdir -p -- "${artifact_dir}"

docker run --rm \
  --user "$(id -u):$(id -g)" \
  -e GRADLE_USER_HOME=/workspace/.gradle-container \
  -e "JAVA_TOOL_OPTIONS=${gradle_jvm_args}" \
  -e "LUNARA_GRADLE_JVM_ARGS=${gradle_jvm_args}" \
  -v "${project_dir}:/workspace" \
  -w /workspace/apps/android \
  "${builder_image}" \
  bash -ceu '
    for task in testLocalDebugUnitTest lintLocalDebug lintProductionDebug assembleProductionDebug; do
      ./gradlew --no-daemon --max-workers=1 \
        "-Dorg.gradle.jvmargs=${LUNARA_GRADLE_JVM_ARGS}" "${task}"
    done
  '

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
  -e "JAVA_TOOL_OPTIONS=${gradle_jvm_args}" \
  -v "${project_dir}:/workspace" \
  -w /workspace/apps/android \
  "${builder_image}" \
  ./gradlew --no-daemon --max-workers=1 \
    "-Dorg.gradle.jvmargs=${gradle_jvm_args}" assembleProductionRelease

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
