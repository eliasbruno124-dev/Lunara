# Google-Play-Veröffentlichung

Stand dieser Checkliste: 25. August 2026. Google ändert Fristen und Formulare
regelmäßig; deshalb müssen die verlinkten Primärquellen vor **jeder** Einreichung
noch einmal geprüft werden. Dieses Dokument ist eine technische und
produktbezogene Checkliste, keine Rechtsberatung.

## Harte Freigabebedingungen

Lunara darf erst an einen öffentlichen Play-Track übergeben werden, wenn alle
folgenden Punkte belegt sind:

- eine verifizierte Organisation ist Publisher. Google verlangt für Health Apps
  ein Organisationskonto; dafür ist grundsätzlich eine D-U-N-S-Nummer nötig;
- `de.lunara.app` ist als dauerhafte Application-ID bestätigt. Eine in Play
  angelegte Paket-ID kann nicht wiederverwendet werden;
- ein signiertes **Production-Release-AAB** wurde aus genau dem freizugebenden
  Commit gebaut und auf einem internen Track installiert;
- Release-Tests, Datenschutzprüfung und ein Netzwerk-/SDK-Audit sind
  abgeschlossen;
- öffentliche Datenschutz- und Kontolöschungsseiten sowie ein erreichbarer
  Supportkontakt existieren;
- Google kann Solo- und Servermodus mit dauerhaften Review-Zugängen vollständig
  prüfen;
- Health-Apps-Erklärung, Data Safety, App Access, Zielgruppe, Inhaltsbewertung
  und alle weiteren in Play Console angezeigten Erklärungen sind vollständig
  und stimmen mit der ausgelieferten App überein.

Google beschreibt die aktuellen Kontoanforderungen unter
[Play Console Requirements](https://support.google.com/googleplay/android-developer/answer/10788890?hl=en)
und die benötigten Organisationsangaben unter
[Required information to create a Play Console developer account](https://support.google.com/googleplay/android-developer/answer/13628312?hl=en).

## AAB, Versionierung und Signierung

Die Production-Debug/Testinstallation, Release-APK und ein unsigned Bundle aus
`artifacts/` sind **keine** Play-Store-Artefakte. Für Play wird ein mit dem privaten
Upload-Key signiertes Android App Bundle gebaut. Der Keystore liegt nur auf
dem Release-Server; Gradle liest ihn über exakt diese vier Variablen:

```bash
export LUNARA_UPLOAD_KEYSTORE=/sicherer/pfad/lunara-upload.p12
export LUNARA_UPLOAD_KEYSTORE_PASSWORD='<nur im geschützten Release-Prozess>'
export LUNARA_UPLOAD_KEY_ALIAS='<alias>'
export LUNARA_UPLOAD_KEY_PASSWORD='<nur im geschützten Release-Prozess>'
cd apps/android
./gradlew clean bundleProductionRelease
```

Der erwartete Gradle-Ausgabepfad ist
`app/build/outputs/bundle/productionRelease/app-production-release.aab`. Der
Befehl ist erst releasefähig, wenn alle vier Werte gesetzt sind. Eine
Teilkonfiguration bricht bewusst ab; fehlen alle vier Werte, erzeugt Gradle
ein unsigniertes Referenzbundle. Keystore, Alias und Passwörter dürfen weder in
Git noch in CI-Logs oder Build-Artefakten landen. Der lokale 1.1.0-
Releasekandidat `artifacts/lunara-play-upload.aab` wurde tatsächlich mit einem
serverlokalen Upload-Key gebaut und seine Signatur geprüft. Das beweist noch
nicht die Freigabe oder Installation durch Google Play.

Für neue Apps ist Play App Signing verpflichtend. Dabei bleiben zwei Aufgaben
getrennt:

- Der Betreiber schützt den **Upload-Key** und signiert damit das AAB.
- Google Play verwaltet den **App-Signing-Key** und signiert die an Geräte
  ausgelieferten APKs.

Upload- und App-Signing-Key sollen verschieden sein. Lunara verwendet auf dem
beim Build konfigurierten Host für Authentik-OIDC einen Android-verifizierten
HTTPS App Link. Deshalb muss nach der Play-App-Signing-Aktivierung der SHA-256-
Fingerprint des **Play App Signing Certificate** in der Lunara-
`assetlinks.json` stehen; der Upload-Zertifikatsfingerprint allein ist dafür
falsch. Anschließend muss die Domainverifikation mit einem aus dem internen
Play-Track installierten Build geprüft werden.

Die Play-/Production-App verwendet diesen festen Publisher-App-Link unabhängig
von der Self-hosting-API- oder Authentik-Domain. Jeder Instanzbetreiber
registriert exakt denselben HTTPS-Redirect. Der Production-OIDC-Provider darf
keinen Custom-Scheme- oder Debug-Redirect enthalten: PKCE authentifiziert
keinen öffentlichen Client und würde eine fremde App mit eigener
PKCE-Anfrage nicht als Lunara ausweisen.

Ein Betreiber kann für eine eigene Distribution einen Production-Build mit
`LUNARA_APP_LINK_HOST` erstellen, auf diesem kontrollierten Host die passende
`assetlinks.json` veröffentlichen und ausschließlich dessen HTTPS-Redirect
registrieren. Custom Scheme bleibt ausschließlich dem Android-`local`-Flavor
mit getrenntem Entwicklungsprovider und eigener client_id vorbehalten; kein
Production-Manifest enthält diesen Intent-Filter.

Der private 1.1.0-Production-Release wurde für `lunara.3li361.de` mit dem
zugehörigen HTTPS App Link verifiziert. Diese Prüfung überträgt sich nicht
automatisch auf einen später von Google mit einem anderen App-Signing-Key
signierten Build; nach Play-Einschreibung ist die Verifikation mit dem Play-
Zertifikat zu wiederholen.

Offizielle Details zur Schlüsseltrennung stehen in
[Use Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en)
und [Upload your app to the Play Console](https://developer.android.com/studio/publish/upload-bundle).

Vor jedem Upload:

1. `versionCode` strikt erhöhen und `versionName`/Changelog festlegen.
2. Production-Flavor bauen; keine Debug-Signatur, keine unsichere HTTP-Option.
3. AAB-Signatur, Manifest, Berechtigungen, minSdk und targetSdk prüfen.
4. Commit, AAB und SBOM mit SHA-256 dokumentieren; Upload-Key getrennt sichern.
5. Play-App-Signing-Zertifikat in `assetlinks.json` eintragen, öffentliche
   Antwort sowie Android-Domainverifikation prüfen.
6. Das von Play erzeugte Artefakt vom internen Track auf echten Geräten
   installieren und Solo-, Server-, OIDC-, Backup- und Upgradepfad testen.

Die App zielt im aktuellen Projekt auf API 36. Ab 31. August 2026 müssen neue
Apps und Updates für Android-Smartphones API 36 oder höher ansprechen; die
verbindliche, aktuelle Tabelle steht unter
[Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en).

## Play-Console-Ablauf

1. Organisationskonto und Publisher-Daten verifizieren; Support-E-Mail,
   Telefonnummer und Website dauerhaft erreichbar halten.
2. App mit endgültiger Paket-ID anlegen und Play-App-Signing-Bedingungen
   akzeptieren.
3. Store Listing mit den vorbereiteten deutschen und englischen Texten,
   eigenem Icon sowie echten, lokalisierten Screenshots anlegen.
4. App-Inhalte vollständig ausfüllen: Datenschutz, Data Safety, Health Apps,
   App Access, Werbung, Alters-/Zielgruppe und Inhaltsbewertung.
5. Signiertes AAB zuerst intern verteilen, danach kontrolliert geschlossen
   testen und erst nach behobenen Pre-Launch-/Policy-Befunden veröffentlichen.
6. Für Produktion einen gestaffelten Rollout mit beobachteter Server-
   Kompatibilität und dokumentiertem Rollback verwenden.

Google dokumentiert App-Anlage, dauerhafte Paket-ID und Listing-Grenzen unter
[Create and set up your app](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en).

### Testanforderungen

Für **persönliche** Entwicklerkonten, die nach dem 13. November 2023 erstellt
wurden, verlangt Google derzeit einen geschlossenen Test mit mindestens zwölf
durchgehend angemeldeten Testpersonen über mindestens 14 aufeinanderfolgende
Tage, bevor Produktionszugang beantragt werden kann. Lunara ist eine Health
App und soll gemäß der aktuellen Kontorichtlinie über ein Organisationskonto
veröffentlicht werden; diese besondere Personal-Account-Regel ersetzt dennoch
keine sorgfältigen internen und geschlossenen Tests. Primärquellen:
[Testing requirements for new personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
und [Set up a test](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en).

## Health Apps Declaration

Lunara muss mindestens als **Period Tracking** erklärt werden. Diese Google-
Kategorie umfasst ausdrücklich Menstruationszyklus-Tracking, Ovulations-
Tracking/-Vorhersage und Fertility Awareness. Die optionale Schätzung eines
fruchtbaren Fensters darf deshalb nicht weggelassen werden.

Weitere Kategorien werden nur gewählt, wenn der tatsächlich eingereichte Build
sie als eigenständige Funktion anbietet. Ein einzelnes Feld zur Schlafqualität
im Zykluslog ist beispielsweise nicht automatisch als eigenständiges
Schlafmanagement zu vermarkten; andererseits darf eine solche Funktion auch
nicht verborgen werden, falls sie im Produkt zu einem echten Schlaftracking
ausgebaut wird. Maßgeblich ist stets der prüfbare Funktionsumfang, nicht der
gewünschte Marketingtext.

Die Erklärung wird unter **Policy > App content > Health apps** ausgefüllt.
Google erläutert Kategorien und Ablauf unter
[Provide information for the Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en).

Lunara wird nicht als Medizinprodukt positioniert. Die Store-Beschreibung und
relevante In-App-Ansichten müssen daher klar sagen:

> Lunara ist kein Medizinprodukt und diagnostiziert, behandelt, heilt oder
> verhindert keine Erkrankung. Wende dich für medizinische Beratung, Diagnose
> oder Behandlung an medizinisches Fachpersonal.

Zusätzlich gilt: Zyklusphase, nächster Periodenzeitraum und fruchtbares Fenster
sind statistische Schätzungen. Das fruchtbare Fenster bestätigt keine
Ovulation und darf nicht als Verhütungsmethode verwendet werden. Google fordert
für nicht regulierte Health Apps einen eindeutigen Nicht-Medizinprodukt-
Disclaimer und den Verweis auf Fachpersonal; siehe
[Health Content and Services](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en).

Die beabsichtigte Produktklassifizierung muss vor Vermarktung in allen
Zielländern von einer dafür qualifizierten Stelle geprüft werden. Der
Disclaimer allein entscheidet keine gesetzliche Medizinprodukteinstufung.

## Data Safety

Google definiert „collected“ grundsätzlich als Übertragung aus der App vom
Gerät. Der Solo-Modus verarbeitet seine Daten nur lokal und fällt insoweit
nicht unter diese Übertragung. Sobald der freiwillige Self-hosted-Modus Daten
an einen ausgewählten Server sendet, findet jedoch eine Off-Device-Übertragung
statt. Deshalb wäre die pauschale Antwort „keine Daten erhoben“ für das gesamte
Play-Paket nicht belastbar.

Vor dem Ausfüllen muss ein finaler Traffic- und SDK-Audit genau des signierten
AAB erfolgen. Als Arbeitsgrundlage sind mindestens diese Datentypen zu prüfen:

| Play-Datentyp | Lunara-Beispiele im Servermodus | Typischer Zweck |
| --- | --- | --- |
| Name | frei gewählter Anzeigename | App-Funktion, Kontoverwaltung |
| User IDs | Benutzername, interne Konto-/Sitzungskennung, OIDC-Subjekt | Anmeldung, Kontoverwaltung, Sicherheit |
| Health info | Perioden, Symptome, Schmerzen, Temperatur, Schlafqualität, abgeleitete Zeiträume | App-Funktion |
| Other user-generated content | Notizen, eigene Symptome, Care Preferences, eigene Tipps | App-Funktion, Personalisierung |
| Other in-app messages | Check-in-Fragen und Antworten | App-Funktion |
| Device or other IDs | nur wählen, falls die finale Implementierung eine nach Googles Definition personen-/gerätebeziehbare Gerätekennung überträgt | Sitzung/Sicherheit |

Die Tabelle ist **keine vorab ausgefüllte Erklärung**. Für jeden Datentyp sind
Collection, Sharing, Optionalität, Zweck, Aufbewahrung und Löschung anhand des
Release-Builds zu beantworten. Weil jede Person den Solo-Modus ohne Server
wählen kann, kann Off-Device-Collection voraussichtlich als optional gelten;
auch das ist unmittelbar vor Einreichung gegen den echten Ablauf zu prüfen.

Partnerfreigaben sind bewusste, nutzergesteuerte Übertragungen. Ob sie in der
Play-Taxonomie unter die Ausnahme für nutzerinitiierte Weitergabe fallen und
wie der unabhängige Self-host-Betreiber einzuordnen ist, muss konsistent mit
Datenschutztext und realem Betriebsmodell entschieden werden. Nicht
automatisch „no sharing“ auswählen.

Lunara selbst verwendet keine Werbe- oder Analytics-SDKs. Das bedeutet nicht,
dass Google Play, Android, der gewählte Reverse Proxy, Authentik oder der
selbst betriebene Server keinerlei technische Daten verarbeiten. Diese
Verarbeitung ist getrennt zu erheben und ehrlich zu beschreiben. Die App darf
„data encrypted in transit“ nur angeben, solange der Production-Build HTTPS
erzwingt und alle produktiven Ziele korrektes TLS verwenden.

Offizielle Definitionen, Ausnahmen, globale Betrachtung aller veröffentlichten
Versionen und Datentypen stehen unter
[Provide information for the Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469)
und [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en).

## Datenschutz- und Löschseiten

Die Repository-Datei `PRIVACY.md` ist eine gute technische Grundlage, aber ein
privates GitHub-Repository und eine durch Authentik gesperrte Domain erfüllen
die Play-Vorgabe nicht. Vor Veröffentlichung werden benötigt:

- eine aktive, weltweit öffentlich erreichbare, nicht geogesperrte HTTPS-URL;
- eine normale, nicht editierbare Webseite, ausdrücklich **kein PDF**;
- derselbe Link in Play Console und gut auffindbar in der App;
- Name von App und Publisher, Datenschutzkontakt, alle Datenarten,
  Empfänger/Rollen, Zwecke, Schutz, Aufbewahrung, Export und Löschung;
- eine zweite öffentliche URL, über die eine Kontolöschung angefordert werden
  kann, falls die App serverseitige Konten erstellen lässt;
- eine klar auffindbare Löschfunktion in der App selbst. Deaktivieren oder
  Sperren eines Kontos reicht nicht als Löschung.

Beim Self-hosting muss die öffentliche Seite erklären, welche Pflichten beim
Publisher und welche beim jeweiligen Instanzbetreiber liegen. Sie muss auch
Solo-Modus, manuellen Backuptransfer, Self-hosted-Servermodus, PWA, optionales
Authentik und mögliche Infrastruktur-Logs abdecken. Konkrete
Aufbewahrungsfristen dürfen erst eingetragen werden, wenn der Betreiber sie
tatsächlich durchsetzt.

## Reviewer-Zugang für eine Self-hosted App

Für den Review wird eine eigene, dauerhaft erreichbare Instanz mit rein
synthetischen Beispieldaten bereitgestellt. Sie ist vom Produktivsystem
getrennt und enthält keine realen Gesundheitsdaten. In **App content > App
access** werden bis zu zwei wiederverwendbare Konten bereitgestellt:

- ein `TRACKER` mit vollständigem Zugriff auf Eintrag, Prognose, Freigaben und
  Partner-Einladung;
- ein `PARTNER`, um die serverseitig gefilterte Partneransicht zu prüfen.

Der Zugang muss rund um die Uhr, unabhängig vom Standort und ohne ablaufendes
OTP, manuelle Freischaltung oder nicht dokumentierte QR-Abhängigkeit
funktionieren. Falls Authentik aktiviert ist, benötigt der Reviewer entweder
einen dedizierten wiederverwendbaren Authentik-Testzugang ohne erzwungenes 2FA
oder eine ausschließlich für die Review-Instanz dokumentierte, sichere
Alternative. Zugangsdaten werden **nur** in Play Console hinterlegt, niemals in
dieser Datei, Git, Screenshots oder Store-Texten.

Vorlage für die englischen App-Access-Anweisungen:

```text
1. Start Lunara and choose "Use own server".
2. Enter server URL: <REVIEW_SERVER_HTTPS_URL>.
3. Tap "Test connection".
4. Sign in with the reusable TRACKER credentials below.
5. To review the partner view, sign out and use the reusable PARTNER account.
6. No OTP, invitation, payment or location-specific step is required.

TRACKER username: <PLAY_CONSOLE_ONLY>
TRACKER password: <PLAY_CONSOLE_ONLY>
PARTNER username: <PLAY_CONSOLE_ONLY>
PARTNER password: <PLAY_CONSOLE_ONLY>
```

Vor jeder Einreichung die Anleitung auf einem frischen Gerät exakt aus dem
Play-Formular nachspielen. Google fordert aktive Demo-Zugänge und alle
Hilfsmittel für die vollständige Prüfung ausdrücklich in den
[Play Console Requirements](https://support.google.com/googleplay/android-developer/answer/10788890?hl=en).

## Letzte Go/No-Go-Prüfung

- [ ] Organisationskonto, D-U-N-S und veröffentlichte Kontaktdaten stimmen.
- [ ] Application-ID, App-Name und Branding sind langfristig entschieden.
- [ ] Signiertes Production-AAB aus sauberem Commit; Upload-Key extern
      gesichert und Play App Signing aktiviert.
- [ ] Play-App-Signing-Fingerprint in öffentlicher `assetlinks.json`; App Link
      mit einem aus dem internen Track installierten Build verifiziert.
- [ ] API 36 oder die dann neuere verpflichtende Stufe ist erfüllt.
- [ ] Internes Play-Artefakt auf mindestens zwei Android-Versionen und einem
      kleinen sowie großen Bildschirm geprüft.
- [ ] Solo-Daten, manueller verschlüsselter Restore und Self-hosted Sync geprüft.
- [ ] Tracker-/Partnerrollen, Freigabeentzug und OIDC-Login geprüft.
- [ ] Health Apps Declaration und Disclaimer stimmen mit allen Funktionen überein.
- [ ] Data Safety basiert auf finalem Traffic-/SDK-Audit, nicht auf Annahmen.
- [ ] Öffentliche Privacy- und Account-Deletion-URLs sind anonym erreichbar.
- [ ] Review-Instanz und beide Konten funktionieren ohne zeitabhängige Schritte.
- [ ] Store-Texte, Screenshots und Supportseite enthalten keine fremden Assets,
      medizinischen Versprechen oder vertraulichen Daten.
- [ ] Bekannte Einschränkungen, Support- und Updatezeitraum sind veröffentlicht.
