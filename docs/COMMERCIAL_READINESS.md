# Commercial readiness and self-hosting

This document describes how Lunara can be delivered as a credible self-hosted
product and Android/web offering. It neither claims complete market readiness
nor provides legal, tax, or medical-device advice. Every release requires the
technical evidence from `FINAL_REPORT.md` and separate business/legal approval.

## Clear product variants

| Variant | Storage location | Account/server | Transfer to a new device | Partner access |
| --- | --- | --- | --- | --- |
| Local-only Android | Encrypted Android database on one device | No account, no server | Manually encrypted Lunara backup file | No |
| Self-hosted couple | Local Android cache plus your own Lunara instance | Separate accounts on your own server | After sign-in and sync; server backups remain the operator's responsibility | Granular shared view |
| Local-only PWA | Passphrase-encrypted browser vault | No account, no server | Portable Lunara backup | No |
| Self-hosted PWA | Browser cache plus your own Lunara instance | The same API and accounts | Server-bound; the browser cache alone is not a backup | Role-dependent, as permitted by the server |

Local-only mode offers the easiest entry. No domain, Docker, or account is
required. Data remains on the Android device. Because Android system backup
is deliberately disabled for Lunara, there is **no automatic Google Cloud
restore**. The intended transfer is an explicit, encrypted export:

1. On the old device, create a portable Lunara backup in Settings and choose
   your own strong passphrase. The Android system file picker can select local
   storage, USB, or an installed provider such as Google Drive/Nextcloud.
   Lunara does not integrate a Google SDK for this.
2. Transfer the file to the new device using a secure method of your choice.
   The transfer service may process data in its own way.
3. On the new device, select **Only on this device**, import the backup, and check
   entries, settings, and the prediction basis.
4. Only then delete the old file or wipe the old device. An additional encrypted
   copy protects against device loss.

The backup file is independent of Keystore and encrypted with a passphrase.
Lunara does not store the passphrase, and it cannot be recovered. Losing the
passphrase means losing access to the backup. Manual export does not provide
continuous cloud sync or protection against a device failing before the next
export. Import validates the complete contents before replacing data and uses
a persistent journal to idempotently complete, at the next startup, a settings
restoration interrupted after the database commit.

In couple mode, customers operate the API, PostgreSQL, domain, TLS, backups,
and updates themselves or commission a service provider. Android and the
installable PWA access the same API with role checks. This allows a partner
to participate using a compatible iPhone/iPad browser without presenting the
PWA as a native iOS app. Browser notifications, background execution, and local
protection can differ from Android and must be documented for each platform.
In 1.1.0, PWA screenshot protection and browser notifications cannot run.
Imported preferences remain encrypted but are shown as not applicable.
The local-only vault locks when the page is hidden, on `pagehide`, or after
15 minutes of inactivity, and then requires the passphrase again.

Authentik remains optional. The portable default is direct Traefik mode with
Lunara accounts. When Authentik is enabled, personal OIDC sign-in with PKCE
runs through the system browser. The production/Play app uses the fixed verified
publisher app link independently of the self-hosting API domain. Custom
distributions build with their own app-link host and signing certificate.
A custom scheme exists exclusively in the Android `local` flavor for a separate
development provider and must not be registered on the production OIDC client.

Direct Bluetooth or local Wi-Fi pairing is currently outside the marketable
feature set and must not be advertised. Peer-to-peer sync would require
independent device authentication, key rotation, conflict resolution,
background limits, and device loss/revocation flows. The current defined scope
is local-only mode with manual transfer or a self-hosted server with sync.

## GitHub as a distribution channel

A private GitHub repository is suitable for development and controlled customer
access, but does not provide anonymously accessible downloads. Private release
links work only for authorized GitHub accounts. A distribution model must
therefore be defined before marketing:

- **Private source repository:** Customers are explicitly assigned to an
  organization/team. Releases and support access depend on authorization.
- **Public distribution repository:** Only approved images, checksums, SBOM,
  and documentation are public. The source code can remain private.
  Usage rights must still be explicitly defined.
- **Public source repository:** Only after reviewing licenses, secrets, history,
  and trademarks. “Source available” and “open source” have different meanings.
- **Your own download/registry channel:** Better suited to paying customers,
  access management, and support contracts. GitHub can continue hosting development.

The repository is published under the [MIT license](../LICENSE) (decision of
the publisher, 2026-11-03). Anyone may use, modify, operate and redistribute
it under those terms. A future commercial offering can still add paid
support, managed hosting or separately licensed extras on top of it.

The publisher decides on free use,
one-time purchases, subscriptions, paid updates, support contracts, or managed
hosting. Pricing, taxes, withdrawal rights, warranties, Play Billing, and local
consumer-protection obligations must be clarified separately before sales.

## Branding and intellectual property

- Product name, publisher name, domain, support address, colors, logo, and text
  must be established as a coherent brand before publication.
- Before commercial use, name and trademark searches are required in the
  target markets. This repository does not establish trademark clearance.
- Use your own logo, screenshots, and text. Do not copy assets, wording, or
  the overall visual impression of commercial period apps.
- App ID `de.lunara.app` and the signing identity become long-term product
  identities after publication. Changing them usually requires a new app
  instead of a normal update.
- Branding tokens should remain centralized. White-label builds need separate
  package IDs, keys, privacy text, OIDC redirects, and support responsibility.

## Releases and supply chain

A commercial release is a traceable unit consisting of a Git commit,
Android AAB/APK, container image, web bundle, database schema, documentation,
and verification results. Minimum requirements:

- SemVer, changelog, upgrade/rollback guidance, and an API/client
  compatibility matrix;
- Signed Android artifacts, immutable container digests, and SHA-256 checksums;
- A server-local upload key, a separate app-signing key managed by Play,
  and a published app-link fingerprint for the certificate actually distributed;
- An SBOM in CycloneDX or SPDX format for Android, Node/web, and containers;
- Automated secret, dependency, license, and container scanning;
- Reproducible builds where practical, a protected release environment,
  and two-person production approval;
- Database migrations tested first on a restored backup, with no automatically
  destructive updates;
- A defined support period for older app/server combinations and a clear
  end-of-life process.

An SBOM is an inventory without guaranteeing security. Each dependency must
also be checked for license compatibility and known vulnerabilities. Debug
keys, `.env`, Authentik credentials, signing keys, database dumps, and real
health data must never appear in GitHub releases.

## Operations, support, and security

For self-hosting, the existing deployment, backup, restore, and update guides
must be reproducible in a fresh Debian-12 environment. Production images are
distributed with version tags rather than `latest`. Every supported version needs:

- Documented minimum requirements, domain/TLS layout, and healthchecks;
- Secure secret generation and an explanation that `APP_MASTER_KEY` is essential
  for restoring encrypted notes;
- An upgrade path with backup, migration, verification, and a rollback decision;
- A support channel, response target, and clear boundaries between Lunara support
  and individual reverse-proxy/DNS/Docker administration;
- A published security contact procedure, severity levels, patch process,
  and supported version line;
- Regular restoration tests. The existence of a dump alone does not establish
  a verified backup.

`SECURITY.md`, `PRIVACY.md`, `DEPLOYMENT.md`, `TESTING.md`, and
`FINAL_REPORT.md` must be consistent for each release. Claims such as “no
telemetry” are permitted only for code controlled by Lunara and only after
an up-to-date SDK/traffic audit. Google Play, the operating system, Authentik,
the reverse proxy, DNS/CDN, and the respective server operator have their own
logging and privacy obligations.

## Privacy responsibilities in self-hosting

The product must distinguish these responsibilities both technically and in its text:

- The **publisher** is responsible for the app binary, store information,
  bundled SDKs, update channel, and support processes.
- The **instance operator** determines the server location, accounts, backups,
  proxy/Authentik logs, retention, and authorized users.
- The **tracking person** determines granular partner sharing permissions.
- A **partner** may receive only the projection shared by the server.
- A chosen file, email, or cloud service may process a manually exported backup
  file under its own terms.

The operator must establish the legal roles arising in a specific country
with qualified advice. Neither self-hosting nor encryption automatically
removes information, deletion, or retention obligations.

## Operator decisions required before sales

| Decision | Must be established before market launch |
| --- | --- |
| Publisher | Verified person/organization, public contacts, D-U-N-S where required |
| License | MIT for the repository; terms of any paid extras or hosting |
| Price | Free, one-time purchase, subscription, support, or managed hosting; billing channel |
| Brand | Final name, domains, logo, package ID, and permitted white labels |
| Support | Languages, hours, response targets, supported versions, and self-hosting boundaries |
| Privacy | Publisher/operator roles, public policy, deletion, retention, subprocessors |
| Security | Disclosure address, patch SLA, SBOM, key management, incident communication |
| Regions | Target countries, age group, and necessary local product/consumer/health review |

## Commercial go/no-go

- [ ] License and pricing model decided in writing and reviewed professionally.
- [ ] Brand, package ID, domains, and signing key strategy finalized.
- [ ] Self-hosting reproducible from a clean system, with upgrade and restore
      tested in practice.
- [ ] Local-only backup exported to a second physical device, imported,
      and checked for content.
- [ ] PWA installed on supported browser/OS versions and tested against
      role/sharing boundaries.
- [ ] Release SBOM, checksums, security scan, and license scan available.
- [ ] No secrets or private data in Git history, images, logs, screenshots,
      or releases.
- [ ] Support, security, privacy, deletion, and EOL procedures accessible.
- [ ] Google Play checklist complete if distributed through Play.
- [ ] Marketing describes only demonstrably built and tested features.
