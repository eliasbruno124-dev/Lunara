#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
project_dir="$(cd -- "${script_dir}/.." && pwd -P)"
android_dir="${project_dir}/apps/android"
artifact_dir="${project_dir}/artifacts"
debug_keystore_dir="${ANDROID_DEBUG_KEYSTORE_DIR:-${project_dir}/.android-builder}"
builder_image="${ANDROID_BUILDER_IMAGE:-mingc/android-build-box:1.29.0}"
gradle_jvm_args="${ANDROID_GRADLE_JVM_ARGS:--Xmx768m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8}"
builder_memory="${ANDROID_BUILDER_MEMORY:-2g}"
builder_memory_swap="${ANDROID_BUILDER_MEMORY_SWAP:-3g}"
builder_cpus="${ANDROID_BUILDER_CPUS:-1}"
minimum_available_kib="${ANDROID_MIN_AVAILABLE_KIB:-4194304}"
upload_keystore="${LUNARA_UPLOAD_KEYSTORE:-}"
upload_store_password="${LUNARA_UPLOAD_KEYSTORE_PASSWORD:-}"
upload_key_alias="${LUNARA_UPLOAD_KEY_ALIAS:-}"
upload_key_password="${LUNARA_UPLOAD_KEY_PASSWORD:-}"
upload_value_count=0
for upload_value in \
  "${upload_keystore}" \
  "${upload_store_password}" \
  "${upload_key_alias}" \
  "${upload_key_password}"; do
  [[ -n "${upload_value}" ]] && upload_value_count=$((upload_value_count + 1))
done
if (( upload_value_count != 0 && upload_value_count != 4 )); then
  echo "Release signing requires all four LUNARA_UPLOAD_* variables." >&2
  exit 2
fi
if (( upload_value_count == 4 )); then
  if [[ "${upload_keystore}" != /* || ! -f "${upload_keystore}" || -L "${upload_keystore}" ]]; then
    echo "LUNARA_UPLOAD_KEYSTORE must be a secure absolute keystore file path." >&2
    exit 2
  fi
fi
unset upload_value

if [[ ! "${minimum_available_kib}" =~ ^[0-9]+$ ]]; then
  echo "ANDROID_MIN_AVAILABLE_KIB must be a nonnegative integer." >&2
  exit 2
fi

available_kib="$(awk '/^MemAvailable:/ { print $2; exit }' /proc/meminfo)"
if [[ -z "${available_kib}" || ! "${available_kib}" =~ ^[0-9]+$ ]]; then
  echo "The available host memory could not be determined." >&2
  exit 1
fi
if (( available_kib < minimum_available_kib )); then
  echo "Android build aborted: only ${available_kib} KiB of host memory available; at least ${minimum_available_kib} KiB required." >&2
  echo "Adjust the limit using ANDROID_MIN_AVAILABLE_KIB only after checking active production services." >&2
  exit 1
fi

mkdir -p -- "${artifact_dir}" "${debug_keystore_dir}"
chmod 0700 -- "${debug_keystore_dir}"

docker run --rm \
  --memory "${builder_memory}" \
  --memory-swap "${builder_memory_swap}" \
  --cpus "${builder_cpus}" \
  --user "$(id -u):$(id -g)" \
  -e ANDROID_SDK_HOME=/root \
  -e ANDROID_USER_HOME=/root/.android \
  -e GRADLE_USER_HOME=/workspace/.gradle-container \
  -e "JAVA_TOOL_OPTIONS=${gradle_jvm_args}" \
  -e "LUNARA_GRADLE_JVM_ARGS=${gradle_jvm_args}" \
  -v "${debug_keystore_dir}:/root/.android" \
  -v "${project_dir}:/workspace" \
  -w /workspace/apps/android \
  "${builder_image}" \
  bash -ceu '
    for task in testLocalDebugUnitTest testProductionDebugUnitTest lintLocalDebug lintProductionDebug compileProductionDebugAndroidTestKotlin assembleProductionDebug; do
      ./gradlew --no-daemon --max-workers=1 \
        "-Dorg.gradle.jvmargs=${LUNARA_GRADLE_JVM_ARGS}" "${task}"
      done
  '

debug_keystore="${debug_keystore_dir}/debug.keystore"
if [[ ! -s "${debug_keystore}" ]]; then
  echo "Persistent local debug key was not generated: ${debug_keystore}" >&2
  exit 1
fi
chmod 0600 -- "${debug_keystore}"

debug_apk="${android_dir}/app/build/outputs/apk/production/debug/app-production-debug.apk"
if [[ ! -s "${debug_apk}" ]]; then
  echo "HTTPS-only production debug APK is missing after the build: ${debug_apk}" >&2
  exit 1
fi
install -m 0644 -- "${debug_apk}" "${artifact_dir}/lunara-debug.apk"
(
  cd -- "${artifact_dir}"
  sha256sum lunara-debug.apk > SHA256SUMS
)

if [[ "${BUILD_ANDROID_RELEASE:-false}" != "true" ]]; then
  echo "HTTPS-only production debug APK verified and copied to ${artifact_dir}."
  echo "Optional unsigned release build: BUILD_ANDROID_RELEASE=true scripts/build-android.sh"
  exit 0
fi

docker run --rm \
  --memory "${builder_memory}" \
  --memory-swap "${builder_memory_swap}" \
  --cpus "${builder_cpus}" \
  --user "$(id -u):$(id -g)" \
  -e ANDROID_SDK_HOME=/root \
  -e ANDROID_USER_HOME=/root/.android \
  -e GRADLE_USER_HOME=/workspace/.gradle-container \
  -e "JAVA_TOOL_OPTIONS=${gradle_jvm_args}" \
  -v "${debug_keystore_dir}:/root/.android" \
  -v "${project_dir}:/workspace" \
  -w /workspace/apps/android \
  "${builder_image}" \
  ./gradlew --no-daemon --max-workers=1 \
    "-Dorg.gradle.jvmargs=${gradle_jvm_args}" \
    assembleProductionRelease bundleProductionRelease

release_apk="${android_dir}/app/build/outputs/apk/production/release/app-production-release-unsigned.apk"
release_bundle="${android_dir}/app/build/outputs/bundle/productionRelease/app-production-release.aab"

if [[ ! -s "${release_apk}" || ! -s "${release_bundle}" ]]; then
  echo "The debug APK was saved, but the unsigned release APK or AAB is missing." >&2
  exit 1
fi

install -m 0644 -- "${release_apk}" "${artifact_dir}/lunara-release-unsigned.apk"
install -m 0644 -- "${release_bundle}" "${artifact_dir}/lunara-release-unsigned.aab"

if (( upload_value_count == 4 )); then
  # Mount the keystore as one read-only file. Passwords are inherited by name,
  # never rendered into the process command line or copied into the workspace.
  export LUNARA_UPLOAD_KEYSTORE_PASSWORD="${upload_store_password}"
  export LUNARA_UPLOAD_KEY_ALIAS="${upload_key_alias}"
  export LUNARA_UPLOAD_KEY_PASSWORD="${upload_key_password}"
  docker run --rm \
    --memory "${builder_memory}" \
    --memory-swap "${builder_memory_swap}" \
    --cpus "${builder_cpus}" \
    --user "$(id -u):$(id -g)" \
    -e ANDROID_SDK_HOME=/root \
    -e ANDROID_USER_HOME=/root/.android \
    -e GRADLE_USER_HOME=/workspace/.gradle-container \
    -e "JAVA_TOOL_OPTIONS=${gradle_jvm_args}" \
    -e LUNARA_UPLOAD_KEYSTORE=/run/lunara-upload.p12 \
    -e LUNARA_UPLOAD_KEYSTORE_PASSWORD \
    -e LUNARA_UPLOAD_KEY_ALIAS \
    -e LUNARA_UPLOAD_KEY_PASSWORD \
    -v "${debug_keystore_dir}:/root/.android" \
    -v "${project_dir}:/workspace" \
    -v "${upload_keystore}:/run/lunara-upload.p12:ro" \
    -w /workspace/apps/android \
    "${builder_image}" \
    ./gradlew --no-daemon --max-workers=1 \
      "-Dorg.gradle.jvmargs=${gradle_jvm_args}" \
      assembleProductionRelease bundleProductionRelease
  unset LUNARA_UPLOAD_KEYSTORE_PASSWORD LUNARA_UPLOAD_KEY_ALIAS LUNARA_UPLOAD_KEY_PASSWORD

  signed_apk="${android_dir}/app/build/outputs/apk/production/release/app-production-release.apk"
  signed_bundle="${android_dir}/app/build/outputs/bundle/productionRelease/app-production-release.aab"
  if [[ ! -s "${signed_apk}" || ! -s "${signed_bundle}" ]]; then
    echo "The signed release build reported success, but the APK or AAB is missing." >&2
    exit 1
  fi
  install -m 0644 -- "${signed_apk}" "${artifact_dir}/lunara-release.apk"
  install -m 0644 -- "${signed_bundle}" "${artifact_dir}/lunara-play-upload.aab"
fi

(
  cd -- "${artifact_dir}"
  checksum_files=(
    lunara-debug.apk
    lunara-release-unsigned.apk
    lunara-release-unsigned.aab
  )
  if (( upload_value_count == 4 )); then
    checksum_files+=(lunara-release.apk lunara-play-upload.aab)
  fi
  sha256sum "${checksum_files[@]}" > SHA256SUMS
)
echo "Android artifacts verified and copied to ${artifact_dir}."
