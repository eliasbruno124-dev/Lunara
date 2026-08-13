# Android-Release signieren

Das Repository enthält absichtlich keinen privaten Signierschlüssel. Der
Standardlauf `scripts/build-android.sh` erzeugt die installierbare,
HTTPS-erzwingende Production-Debug-APK. Ein unsigned Release-Build wird nur mit
dem expliziten Schalter versucht:

```bash
BUILD_ANDROID_RELEASE=true ./scripts/build-android.sh
```

Für wiederholbare lokale Test-Updates legt das Skript seinen Debug-Key unter
`.android-builder/debug.keystore` an. Das Verzeichnis ist Git-ignoriert und auf
dem Buildhost nur für den Besitzer zugänglich. Der Key muss wie ein Secret
gesichert werden; geht er verloren, kann eine damit installierte Debug-App nur
nach einer Deinstallation mit einem neuen Schlüssel installiert werden. Mit
`ANDROID_DEBUG_KEYSTORE_DIR="/sicherer/lokaler/pfad"` lässt sich der Speicherort
außerhalb des Arbeitsbaums festlegen. Dieser Debug-Key ersetzt keinen privaten
Release-Key.

Wenn `artifacts/lunara-release-unsigned.apk` danach nicht existiert, wurde auf
diesem Host kein erfolgreicher Release-Build erzeugt. Niemals die Debug-APK als
Release-Artefakt umbenennen.

Erzeuge den Release-Key offline und außerhalb des Repositorys:

```bash
keytool -genkeypair -v \
  -keystore /sicherer/pfad/lunara-release.jks \
  -alias lunara \
  -keyalg RSA -keysize 4096 -validity 10000
```

Signiere mit dem `apksigner` des Android-SDK und prüfe anschließend:

```bash
apksigner sign \
  --ks /sicherer/pfad/lunara-release.jks \
  --out artifacts/lunara-release.apk \
  artifacts/lunara-release-unsigned.apk
apksigner verify --verbose --print-certs artifacts/lunara-release.apk
```

Keystore, Passwörter und Signing-Properties niemals in Git, CI-Logs oder
OneDrive ablegen. Sichere den Schlüssel doppelt verschlüsselt; ohne denselben
Schlüssel sind spätere Updates einer bereits installierten Release-App nicht
möglich.
