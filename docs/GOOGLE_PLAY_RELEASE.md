# Google Play release

Checklist as of August 25, 2026. Google regularly changes deadlines and forms,
so the linked primary sources must be checked again before **every** submission.
This document is a technical and product checklist, not legal advice.

## Mandatory release conditions

Lunara may only be submitted to a public Play track once all of the following
points have been demonstrated:

- a verified organization is the publisher. Google requires an organization
  account for health apps; this generally requires a D-U-N-S number;
- `de.lunara.app` is confirmed as the permanent application ID. A package ID
  created in Play cannot be reused;
- a signed **production release AAB** was built from exactly the commit being
  released and installed from an internal track;
- release testing, a privacy review and a network/SDK audit are complete;
- public privacy and account deletion pages and a reachable support contact exist;
- Google can fully review local-only and server modes using persistent review access;
- the Health apps declaration, Data safety, App access, target audience, content
  rating and all other declarations shown in Play Console are complete and
  match the app being shipped.

Google describes the current account requirements in
[Play Console Requirements](https://support.google.com/googleplay/android-developer/answer/10788890?hl=en)
and the required organization information in
[Required information to create a Play Console developer account](https://support.google.com/googleplay/android-developer/answer/13628312?hl=en).

## AAB, versioning and signing

The production debug/test installation, release APK and an unsigned bundle
from `artifacts/` are **not** Play Store artifacts. For Play, build an Android
App Bundle signed with the private upload key. The keystore exists only on
the release server; Gradle reads it through exactly these four variables:

```bash
export LUNARA_UPLOAD_KEYSTORE=/sicherer/pfad/lunara-upload.p12
export LUNARA_UPLOAD_KEYSTORE_PASSWORD='<nur im geschützten Release-Prozess>'
export LUNARA_UPLOAD_KEY_ALIAS='<alias>'
export LUNARA_UPLOAD_KEY_PASSWORD='<nur im geschützten Release-Prozess>'
cd apps/android
./gradlew clean bundleProductionRelease
```

The expected Gradle output path is
`app/build/outputs/bundle/productionRelease/app-production-release.aab`.
The command is only suitable for release when all four values are set.
A partial configuration deliberately aborts; if all four values are absent,
Gradle produces an unsigned reference bundle. The keystore, alias and passwords
must never appear in Git, CI logs or build artifacts. The local 1.1.0 release
candidate `artifacts/lunara-play-upload.aab` was actually built using an upload
key stored only on the server, and its signature was verified. This does not
yet demonstrate approval or installation through Google Play.

Play App Signing is mandatory for new apps. It separates two responsibilities:

- The operator protects the **upload key** and uses it to sign the AAB.
- Google Play manages the **app signing key** and signs the APKs delivered to devices.

The upload and app signing keys should be different. For Authentik OIDC,
Lunara uses an Android-verified HTTPS App Link on the host configured at
build time. After enabling Play App Signing, the SHA-256 fingerprint of the
**Play App Signing Certificate** must therefore be present in Lunara's
`assetlinks.json`; the upload certificate fingerprint alone is incorrect for
this purpose. Then verify the domain association using a build installed
from the internal Play track.

The Play/production app uses this fixed publisher App Link independently of
the self-hosted API or Authentik domain. Every instance operator registers
exactly the same HTTPS redirect. The production OIDC provider must not contain
a custom-scheme or debug redirect: PKCE does not authenticate a public client
and would not identify a third-party app making its own PKCE request as Lunara.

For their own distribution, an operator can create a production build with
`LUNARA_APP_LINK_HOST`, publish the matching `assetlinks.json` on that controlled
host and register only its HTTPS redirect. A custom scheme remains reserved
for the Android `local` flavor with a separate development provider and its
own client_id; no production manifest contains that intent filter.

The private 1.1.0 production release was verified for `lunara.3li361.de`
with its corresponding HTTPS App Link. This verification does not automatically
apply to a later build signed by Google with a different app signing key;
after enrolling in Play, repeat the verification with the Play certificate.

Official details about key separation are available in
[Use Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en)
and [Upload your app to the Play Console](https://developer.android.com/studio/publish/upload-bundle).

Before every upload:

1. Strictly increase `versionCode` and set `versionName` and the changelog.
2. Build the production flavor; use no debug signature or insecure HTTP option.
3. Check the AAB signature, manifest, permissions, minSdk and targetSdk.
4. Record the commit, AAB and SBOM with SHA-256; back up the upload key separately.
5. Add the Play App Signing certificate to `assetlinks.json`, then check the
   public response and Android domain verification.
6. Install the artifact generated by Play from the internal track on real
   devices and test local-only, server, OIDC, backup and upgrade flows.

The current project targets API 36. From August 31, 2026, new apps and updates
for Android phones must target API 36 or higher; the binding, current table
is available in
[Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en).

## Play Console process

1. Verify the organization account and publisher information; keep the support
   email address, phone number and website permanently reachable.
2. Create the app with its final package ID and accept the Play App Signing terms.
3. Create the store listing using the prepared German and English text,
   your own icon and real, localized screenshots.
4. Complete all app content sections: privacy, Data safety, Health apps,
   App access, ads, age/target audience and content rating.
5. Distribute the signed AAB internally first, then conduct a controlled closed
   test, and publish only after resolving pre-launch and policy findings.
6. For production, use a staged rollout with monitored server compatibility
   and a documented rollback procedure.

Google documents app creation, permanent package IDs and listing limits in
[Create and set up your app](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en).

### Testing requirements

For **personal** developer accounts created after November 13, 2023,
Google currently requires a closed test with at least twelve continuously
opted-in testers over at least 14 consecutive days before production access
can be requested. Lunara is a health app and should be published through an
organization account under the current account policy; this special personal
account rule still does not replace careful internal and closed testing.
Primary sources:
[Testing requirements for new personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
and [Set up a test](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en).

## Health Apps Declaration

Lunara must be declared as at least **Period Tracking**. This Google category
explicitly includes menstrual cycle tracking, ovulation tracking/prediction
and fertility awareness. The optional fertile window estimate must therefore
not be omitted.

Select additional categories only if the build actually being submitted
offers them as a standalone feature. For example, a single sleep quality
field in the cycle log should not automatically be marketed as standalone
sleep management; conversely, such a feature must not be hidden if the product
is expanded to provide actual sleep tracking. The verifiable feature set
always determines the declaration, rather than the desired marketing copy.

Complete the declaration under **Policy > App content > Health apps**.
Google explains the categories and process in
[Provide information for the Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en).

Lunara is not positioned as a medical device. The store description and
relevant in-app screens must therefore clearly state:

> Lunara is not a medical device and does not diagnose, treat, cure or
> prevent any disease. Consult a healthcare professional for medical advice,
> diagnosis or treatment.

In addition, cycle phase, the next period window and the fertile window
are statistical estimates. The fertile window does not confirm ovulation
and must not be used as a contraceptive method. Google requires a clear
non-medical-device disclaimer and a reference to healthcare professionals
for unregulated health apps; see
[Health Content and Services](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en).

Before marketing, the intended product classification must be reviewed by
a qualified body in every target country. The disclaimer alone does not
determine the legal classification as a medical device.

## Data Safety

Google generally defines "collected" as transmission from the app off the
device. Local-only mode processes its data solely on the device and therefore
does not involve such transmission. However, once the optional self-hosted
mode sends data to a selected server, an off-device transmission takes place.
A blanket "no data collected" answer for the entire Play package would
therefore be unsupported.

Before completing the form, conduct a final traffic and SDK audit of the
exact signed AAB. At minimum, assess these data types as a starting point:

| Play data type | Lunara examples in server mode | Typical purpose |
| --- | --- | --- |
| Name | freely chosen display name | App functionality, account management |
| User IDs | username, internal account/session ID, OIDC subject | Sign-in, account management, security |
| Health info | periods, symptoms, pain, temperature, sleep quality, derived windows | App functionality |
| Other user-generated content | notes, custom symptoms, care preferences, personal tips | App functionality, personalization |
| Other in-app messages | check-in questions and answers | App functionality |
| Device or other IDs | select only if the final implementation transmits a device ID that can identify a person/device under Google's definition | Session/security |

This table is **not a pre-completed declaration**. For every data type,
answer the collection, sharing, optionality, purpose, retention and deletion
questions on the basis of the release build. Because anyone can choose
local-only mode without a server, off-device collection can probably be
classified as optional; this also needs to be checked against the actual
flow immediately before submission.

Partner sharing consists of deliberate, user-controlled transmissions.
Whether these fall under the Play taxonomy exception for user-initiated
sharing, and how the independent self-hosting operator should be classified,
must be decided consistently with the privacy text and actual operating
model. Do not automatically select "no sharing".

Lunara itself uses no advertising or analytics SDKs. This does not mean
that Google Play, Android, the selected reverse proxy, Authentik or the
self-hosted server process no technical data. Identify and describe this
processing separately and honestly. The app may only declare "data encrypted
in transit" while the production build enforces HTTPS and all production
destinations use correct TLS.

Official definitions, exceptions, the global assessment of all published
versions and data types are available in
[Provide information for the Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469)
and [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en).

## Privacy and deletion pages

The repository file `PRIVACY.md` is a useful technical starting point, but
a private GitHub repository and a domain protected by Authentik do not meet
the Play requirement. Before publication, you need:

- an active HTTPS URL accessible publicly worldwide, without geographic restrictions;
- a regular, non-editable webpage, explicitly **not a PDF**;
- the same link in Play Console and an easily discoverable link in the app;
- the app and publisher names, a privacy contact, all data types,
  recipients/roles, purposes, protection, retention, export and deletion;
- a second public URL through which account deletion can be requested
  if the app allows server-side accounts to be created;
- a clearly discoverable deletion feature within the app itself. Disabling
  or suspending an account is insufficient for deletion.

For self-hosting, the public page must explain which obligations belong to
the publisher and which belong to each instance operator. It must also cover
local-only mode, manual backup transfers, self-hosted server mode, the PWA,
optional Authentik and possible infrastructure logs. Only specify concrete
retention periods once the operator actually enforces them.

## Reviewer access for a self-hosted app

Provide a dedicated, permanently reachable instance containing exclusively
synthetic example data for review. It is isolated from production and
contains no real health data. Under **App content > App access**, provide
up to two reusable accounts:

- a `TRACKER` with full access to logging, predictions, sharing and partner invitations;
- a `PARTNER` for reviewing the partner view filtered by the server.

Access must work around the clock, regardless of location, without an expiring
OTP, manual activation or an undocumented QR dependency. If Authentik is enabled,
the reviewer needs either a dedicated, reusable Authentik test account without
mandatory 2FA or a secure alternative documented exclusively for the review
instance. Store credentials **only** in Play Console, never in this file,
Git, screenshots or store text.

Template for the English App access instructions:

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

Before every submission, follow the instructions exactly as written in the
Play form on a fresh device. Google explicitly requires active demo accounts
and all resources needed for a complete review in
[Play Console Requirements](https://support.google.com/googleplay/android-developer/answer/10788890?hl=en).

## Final go/no-go check

- [ ] Organization account, D-U-N-S and published contact details are correct.
- [ ] Application ID, app name and branding are decided for the long term.
- [ ] Signed production AAB from a clean commit; upload key backed up externally
      and Play App Signing enabled.
- [ ] Play App Signing fingerprint in public `assetlinks.json`; App Link
      verified with a build installed from the internal track.
- [ ] API 36 or the newer level required at that time is met.
- [ ] Internal Play artifact tested on at least two Android versions and on
      a small and a large screen.
- [ ] Local-only data, manual encrypted restore and self-hosted sync tested.
- [ ] Tracker/partner roles, sharing revocation and OIDC sign-in tested.
- [ ] Health Apps Declaration and disclaimer match all features.
- [ ] Data Safety is based on the final traffic/SDK audit, rather than assumptions.
- [ ] Public privacy and account deletion URLs are accessible anonymously.
- [ ] Review instance and both accounts work without time-dependent steps.
- [ ] Store text, screenshots and support page contain no third-party assets,
      medical promises or confidential data.
- [ ] Known limitations and support/update periods are published.
