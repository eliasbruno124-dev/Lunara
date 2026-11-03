#!/usr/bin/env bash
set -Eeuo pipefail
if [[ "${GITHUB_ACTIONS:-}" != true || "${RUNNER_ENVIRONMENT:-}" != github-hosted ]]; then
  echo 'This device runner is restricted to disposable GitHub-hosted machines.' >&2
  exit 1
fi
mkdir -p artifacts/device-validation
app_apks=apps/android/app/build/outputs/apk
test_apks="$app_apks/androidTest/production/debug"
adb install -r "$app_apks/production/debug/app-production-debug.apk"
adb install -r "$test_apks/app-production-debug-androidTest.apk"
adb shell wm size 1080x2400
adb shell wm density 420
adb shell cmd locale set-app-locales de.lunara.app.debug --user 0 --locales de-DE
adb shell am instrument -w -r de.lunara.app.debug.test/androidx.test.runner.AndroidJUnitRunner > artifacts/device-validation/instrumentation.log 2>&1
result=0
node scripts/verify-android-instrumentation.mjs artifacts/device-validation/instrumentation.log || result=$?
if (( result == 0 )); then
  adb shell cmd locale set-app-locales de.lunara.app.debug --user 0 --locales en-US
  adb shell am instrument -w -r -e class de.lunara.app.ui.RedesignComposeTest,de.lunara.app.ui.SetupScreenshotsTest de.lunara.app.debug.test/androidx.test.runner.AndroidJUnitRunner > artifacts/device-validation/instrumentation-en.log 2>&1
  node scripts/verify-android-instrumentation.mjs artifacts/device-validation/instrumentation-en.log || result=$?
fi
adb pull /sdcard/Android/data/de.lunara.app.debug/files artifacts/device-validation/screenshots || result=1
if (( result == 0 )); then
  for language in de en; do
    screenshot_count=$(find artifacts/device-validation/screenshots -type f -name "redesign120-*-${language}.png" | wc -l)
    if (( screenshot_count < 9 )); then
      echo "Missing visual evidence for ${language}." >&2
      result=1
    fi
  done
fi
exit "$result"
