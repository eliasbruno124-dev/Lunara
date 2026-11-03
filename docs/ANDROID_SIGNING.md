# Signing an Android release

The repository deliberately contains no private signing key. The default
`scripts/build-android.sh` run produces the installable production debug APK,
which enforces HTTPS. An unsigned release build is only attempted with the
explicit switch:

```bash
BUILD_ANDROID_RELEASE=true ./scripts/build-android.sh
```

For repeatable local test updates, the script creates its debug key at
`.android-builder/debug.keystore`. The directory is ignored by Git and
accessible only to its owner on the build host. Back up this key as a secret;
if it is lost, a debug app installed with it must be uninstalled before it can
be installed with a new key. Use
`ANDROID_DEBUG_KEYSTORE_DIR="/sicherer/lokaler/pfad"` to set a location outside
the working tree. This debug key does not replace a private release key.

If `artifacts/lunara-release-unsigned.apk` does not exist afterwards, no
successful release build was produced on this host. Never rename the debug APK
as a release artifact.

Generate the release key offline and outside the repository:

```bash
keytool -genkeypair -v \
  -keystore /sicherer/pfad/lunara-release.jks \
  -alias lunara \
  -keyalg RSA -keysize 4096 -validity 10000
```

Sign using `apksigner` from the Android SDK, then verify:

```bash
apksigner sign \
  --ks /sicherer/pfad/lunara-release.jks \
  --out artifacts/lunara-release.apk \
  artifacts/lunara-release-unsigned.apk
apksigner verify --verbose --print-certs artifacts/lunara-release.apk
```

Never store the keystore, passwords or signing properties in Git, CI logs or
OneDrive. Back up the key with two layers of encryption; without the same key, an
already installed release app cannot receive subsequent updates.
