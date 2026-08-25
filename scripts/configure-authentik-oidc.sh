#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

usage() {
  echo "Verwendung: $0 [--check]" >&2
}

check_only="${LUNARA_CHECK_ONLY:-false}"
if [[ $# -gt 1 ]]; then
  usage
  exit 2
fi
if [[ $# -eq 1 ]]; then
  [[ "$1" == "--check" ]] || { usage; exit 2; }
  check_only=true
fi
[[ "${check_only}" == "true" || "${check_only}" == "false" ]] || {
  echo "LUNARA_CHECK_ONLY akzeptiert nur true oder false." >&2
  exit 2
}

runtime_dir="${LUNARA_RUNTIME_DIR:-/docker/lunara}"
backup_dir="${LUNARA_INFRA_BACKUP_DIR:-/var/backups/lunara/infrastructure}"
authentik_container="${AUTHENTIK_CONTAINER:-authentik-server}"
authentik_db_container="${AUTHENTIK_DB_CONTAINER:-postgresql}"
expected_authentik_version="${AUTHENTIK_EXPECTED_VERSION:-2026.5.3}"
public_host="${LUNARA_PUBLIC_HOST:-lunara.3li361.de}"
authentik_public_url="${AUTHENTIK_PUBLIC_URL:-https://authentik.3li361.de}"
allowed_users="${LUNARA_AUTHENTIK_USERS:-Dune,Elias}"
signing_key_name="${AUTHENTIK_SIGNING_KEY_NAME:-authentik Self-signed Certificate}"
android_app_link_host="${LUNARA_ANDROID_APP_LINK_HOST:-lunara.3li361.de}"
android_app_package="${LUNARA_ANDROID_APP_PACKAGE:-de.lunara.app}"
android_cert_fingerprints="${LUNARA_ANDROID_CERT_SHA256:-}"

for command_name in docker install mktemp python3 sed tail; do
  command -v "${command_name}" >/dev/null 2>&1 || {
    echo "Fehlendes Werkzeug: ${command_name}" >&2
    exit 1
  }
done

if [[ ! "${authentik_container}" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] ||
   [[ ! "${authentik_db_container}" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]]; then
  echo "Ungültiger Authentik-Containername." >&2
  exit 2
fi
if [[ ! "${public_host}" =~ ^[a-z0-9][a-z0-9.-]*[a-z0-9]$ ]] ||
   [[ "${public_host}" != *.* ]] || [[ "${public_host}" == *..* ]] ||
   (( ${#public_host} > 253 )); then
  echo "Ungültiger öffentlicher Lunara-Hostname." >&2
  exit 2
fi
if [[ ! "${android_app_link_host}" =~ ^[a-z0-9][a-z0-9.-]*[a-z0-9]$ ]] ||
   [[ "${android_app_link_host}" != *.* ]] || [[ "${android_app_link_host}" == *..* ]] ||
   (( ${#android_app_link_host} > 253 )); then
  echo "Ungültiger Host für den Android-App-Link." >&2
  exit 2
fi
if [[ ! "${android_app_package}" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ ]]; then
  echo "Ungültige Android-Application-ID." >&2
  exit 2
fi
if [[ ! "${allowed_users}" =~ ^[A-Za-z0-9_.@+-]+(,[A-Za-z0-9_.@+-]+)*$ ]] ||
   (( ${#allowed_users} > 512 )); then
  echo "LUNARA_AUTHENTIK_USERS muss eine eindeutige, kommaseparierte Benutzernamenliste sein." >&2
  exit 2
fi
if [[ -z "${signing_key_name}" || "${signing_key_name}" == *$'\n'* ||
      "${signing_key_name}" == *$'\r'* ]] || (( ${#signing_key_name} > 255 )); then
  echo "Ungültiger Name des Authentik-Signaturschlüssels." >&2
  exit 2
fi

python3 - "${authentik_public_url}" <<'PY'
import sys
from urllib.parse import urlsplit

url = urlsplit(sys.argv[1])
if (
    url.scheme != "https"
    or not url.hostname
    or url.username is not None
    or url.password is not None
    or url.query
    or url.fragment
    or url.path not in ("", "/")
):
    raise SystemExit("AUTHENTIK_PUBLIC_URL muss eine credentialfreie HTTPS-Origin sein.")
PY
authentik_public_url="${authentik_public_url%/}"

if [[ "${check_only}" == "false" ]]; then
  python3 - "${android_cert_fingerprints}" <<'PY'
import re
import sys

values = [value.strip().upper() for value in sys.argv[1].split(",") if value.strip()]
pattern = re.compile(r"^(?:[0-9A-F]{2}:){31}[0-9A-F]{2}$")
if not 1 <= len(values) <= 8 or len(values) != len(set(values)) or any(
    not pattern.fullmatch(value) for value in values
):
    raise SystemExit(
        "LUNARA_ANDROID_CERT_SHA256 muss 1-8 eindeutige SHA-256-Zertifikat-Fingerprints enthalten."
    )
PY
fi

authentik_version="$(docker inspect --type container --format '{{index .Config.Labels "org.opencontainers.image.version"}}' "${authentik_container}")"
if [[ "${authentik_version}" != "${expected_authentik_version}" &&
      "${authentik_version}" != "refs/tags/version/${expected_authentik_version}" ]]; then
  echo "Ungeprüfte Authentik-Version '${authentik_version:-unbekannt}'; erwartet ${expected_authentik_version}." >&2
  exit 1
fi
if [[ "$(docker inspect --type container --format '{{.State.Running}}' "${authentik_container}")" != "true" ]] ||
   [[ "$(docker inspect --type container --format '{{.State.Running}}' "${authentik_db_container}")" != "true" ]]; then
  echo "Authentik- oder Datenbankcontainer läuft nicht; keine Änderung vorgenommen." >&2
  exit 1
fi

run_model() {
  local action="$1"
  local expected_state="$2"
  docker exec -i \
    -e LUNARA_ACTION="${action}" \
    -e LUNARA_EXPECTED_STATE="${expected_state}" \
    -e LUNARA_PUBLIC_HOST="${public_host}" \
    -e LUNARA_AUTHENTIK_URL="${authentik_public_url}" \
    -e LUNARA_ALLOWED_USERS="${allowed_users}" \
    -e LUNARA_SIGNING_KEY_NAME="${signing_key_name}" \
    -e LUNARA_ANDROID_APP_LINK_HOST="${android_app_link_host}" \
    -e LUNARA_ANDROID_APP_PACKAGE="${android_app_package}" \
    "${authentik_container}" ak shell --no-imports -v 0 <<'PYTHON'
import os
from dataclasses import asdict

from django.db import connection, transaction

from authentik.core.models import Application, Group, Provider, User, UserTypes
from authentik.crypto.models import CertificateKeyPair
from authentik.flows.models import Flow
from authentik.policies.models import PolicyBinding
from authentik.providers.oauth2.models import (
    ClientType,
    GrantType,
    IssuerMode,
    OAuth2Provider,
    RedirectURI,
    RedirectURIMatchingMode,
    ScopeMapping,
)
from authentik.providers.proxy.models import ProxyMode, ProxyProvider

ACTION = os.environ["LUNARA_ACTION"]
EXPECTED_STATE = os.environ["LUNARA_EXPECTED_STATE"]
PUBLIC_HOST = os.environ["LUNARA_PUBLIC_HOST"]
AUTHENTIK_URL = os.environ["LUNARA_AUTHENTIK_URL"]
ALLOWED_USERNAMES = os.environ["LUNARA_ALLOWED_USERS"].split(",")
SIGNING_KEY_NAME = os.environ["LUNARA_SIGNING_KEY_NAME"]
ANDROID_APP_LINK_HOST = os.environ["LUNARA_ANDROID_APP_LINK_HOST"]
ANDROID_APP_PACKAGE = os.environ["LUNARA_ANDROID_APP_PACKAGE"]

OWNER_KEY = "lunara.infrastructure.owner"
OWNER = "lunara-authentik-oidc-v1"
PROXY_PROVIDER_NAME = "Lunara Proxy"
PROXY_APPLICATION_SLUG = "lunara"
GATEWAY_GROUP_NAME = "Lunara Gateway"
SERVICE_USERNAME = "lunara-native-app"
INTERNAL_HOST = "http://lunara-api:3000"
EXTERNAL_HOST = f"https://{PUBLIC_HOST}"
PUBLIC_BYPASS = r"^/(?:api/v1/meta|\.well-known/assetlinks\.json)$"

OIDC_PROVIDER_NAME = "Lunara Native and Web"
OIDC_APPLICATION_NAME = "Lunara Login"
OIDC_APPLICATION_SLUG = "lunara-android"
OIDC_GROUP_NAME = "Lunara Users"
OIDC_CLIENT_ID = "lunara-android"
OIDC_REDIRECTS = [
    f"https://{ANDROID_APP_LINK_HOST}/auth/android-callback",
    f"https://{PUBLIC_HOST}/auth/callback",
]

if len(ALLOWED_USERNAMES) != len(set(ALLOWED_USERNAMES)):
    raise RuntimeError("Lunara Authentik OIDC preflight failed: duplicate allowed usernames")


def fail(message):
    raise RuntimeError(f"Lunara Authentik OIDC preflight failed: {message}")


def one(queryset, description):
    values = list(queryset)
    if len(values) != 1:
        fail(f"{description} is missing or ambiguous")
    return values[0]


def none_or_one(queryset, description):
    values = list(queryset)
    if len(values) > 1:
        fail(f"duplicate {description} objects exist")
    return values[0] if values else None


def lock_namespace():
    with connection.cursor() as cursor:
        cursor.execute("SELECT pg_advisory_xact_lock(%s)", [781334920250514])


def dependencies():
    try:
        flows = {
            "authentication": Flow.objects.get(slug="default-authentication-flow"),
            "authorization": Flow.objects.get(
                slug="default-provider-authorization-explicit-consent"
            ),
            "invalidation": Flow.objects.get(slug="default-provider-invalidation-flow"),
        }
        signing_key = CertificateKeyPair.objects.get(name=SIGNING_KEY_NAME)
    except Exception as exc:
        fail(f"managed flow or signing key is missing/ambiguous ({exc.__class__.__name__})")
    if not signing_key.key_data or not signing_key.certificate_data:
        fail("the selected signing key is not an asymmetric key pair")
    scope_ids = set(
        ScopeMapping.objects.filter(
            managed__in={
                "goauthentik.io/providers/oauth2/scope-openid",
                "goauthentik.io/providers/oauth2/scope-offline_access",
            }
        ).values_list("pk", flat=True)
    )
    if len(scope_ids) != 2:
        fail("the managed openid and offline_access mappings are required")
    return flows, signing_key, scope_ids


def inspect_base():
    proxy = one(Provider.objects.filter(name=PROXY_PROVIDER_NAME), "Lunara proxy provider")
    proxy = ProxyProvider.objects.filter(pk=proxy.pk).first()
    if proxy is None:
        fail("Lunara Proxy has an unexpected provider type")
    application = one(
        Application.objects.filter(slug=PROXY_APPLICATION_SLUG),
        "Lunara proxy application",
    )
    gateway_group = one(Group.objects.filter(name=GATEWAY_GROUP_NAME), "gateway group")
    service_user = one(User.objects.filter(username=SERVICE_USERNAME), "native service user")
    if application.provider_id != proxy.pk:
        fail("Lunara application does not use Lunara Proxy")
    if (
        proxy.internal_host != INTERNAL_HOST
        or proxy.external_host != EXTERNAL_HOST
        or proxy.mode != ProxyMode.PROXY
        or not proxy.intercept_header_auth
        or proxy.basic_auth_enabled
    ):
        fail("existing Lunara proxy target or security mode differs")
    if (
        service_user.type != UserTypes.SERVICE_ACCOUNT
        or not service_user.is_active
        or set(gateway_group.users.values_list("pk", flat=True)) != {service_user.pk}
    ):
        fail("gateway group must contain only the active native service user")
    return proxy, application, gateway_group


def inspect_oidc_named_objects():
    provider = none_or_one(Provider.objects.filter(name=OIDC_PROVIDER_NAME), "OIDC provider")
    app_by_slug = none_or_one(
        Application.objects.filter(slug=OIDC_APPLICATION_SLUG), "OIDC application slug"
    )
    app_by_name = none_or_one(
        Application.objects.filter(name=OIDC_APPLICATION_NAME), "OIDC application name"
    )
    group = none_or_one(Group.objects.filter(name=OIDC_GROUP_NAME), "OIDC group")
    values = [provider, app_by_slug, app_by_name, group]
    if all(value is None for value in values):
        return None
    if any(value is None for value in values) or app_by_slug.pk != app_by_name.pk:
        fail("partial or colliding OIDC objects exist")
    typed_provider = OAuth2Provider.objects.filter(pk=provider.pk).first()
    if typed_provider is None:
        fail("OIDC provider name belongs to another provider type")
    return typed_provider, app_by_slug, group


def expected_redirects():
    return [
        asdict(RedirectURI(RedirectURIMatchingMode.STRICT, value))
        for value in OIDC_REDIRECTS
    ]


def verify_target(proxy, proxy_app, gateway_group, oidc, signing_key, scope_ids):
    provider, application, group = oidc
    allowed_users = list(User.objects.filter(username__in=ALLOWED_USERNAMES))
    if len(allowed_users) != len(set(ALLOWED_USERNAMES)):
        fail("an allowed human user is missing or duplicated")
    if any(not user.is_active or user.type != UserTypes.INTERNAL for user in allowed_users):
        fail("all allowed Lunara users must be active internal Authentik users")
    if set(user.username for user in allowed_users) != set(ALLOWED_USERNAMES):
        fail("allowed Authentik user lookup is incomplete")

    expected_provider_values = {
        "name": OIDC_PROVIDER_NAME,
        "client_type": ClientType.PUBLIC,
        "client_id": OIDC_CLIENT_ID,
        "authentication_flow_id": one(Flow.objects.filter(slug="default-authentication-flow"), "authentication flow").pk,
        "authorization_flow_id": one(Flow.objects.filter(slug="default-provider-authorization-explicit-consent"), "authorization flow").pk,
        "invalidation_flow_id": one(Flow.objects.filter(slug="default-provider-invalidation-flow"), "invalidation flow").pk,
        "signing_key_id": signing_key.pk,
        "encryption_key_id": None,
        "issuer_mode": IssuerMode.PER_PROVIDER,
        "sub_mode": "hashed_user_id",
        "access_code_validity": "minutes=1",
        "access_token_validity": "minutes=15",
        "refresh_token_validity": "days=30",
        "refresh_token_threshold": "seconds=0",
        "include_claims_in_id_token": True,
    }
    for field, expected in expected_provider_values.items():
        if getattr(provider, field) != expected:
            fail(f"OIDC provider field {field} differs from the pinned value")
    if list(provider.grant_types) != [GrantType.AUTHORIZATION_CODE, GrantType.REFRESH_TOKEN]:
        fail("OIDC provider grant types must be authorization_code + refresh_token only")
    if provider._redirect_uris != expected_redirects():
        fail("OIDC redirect URIs are not the exact strict Android/PWA set")
    if set(provider.property_mappings.values_list("pk", flat=True)) != scope_ids:
        fail("OIDC provider scopes must be openid + offline_access only")
    if provider.jwt_federation_sources.exists() or set(
        provider.jwt_federation_providers.values_list("pk", flat=True)
    ) != {proxy.pk}:
        fail("OIDC token federation is not restricted to the Lunara proxy")

    if (
        application.provider_id != provider.pk
        or application.slug != OIDC_APPLICATION_SLUG
        or application.name != OIDC_APPLICATION_NAME
        or application.meta_launch_url != EXTERNAL_HOST
        or application.policy_engine_mode != "any"
    ):
        fail("OIDC application differs from the pinned contract")
    if group.is_superuser or group.attributes != {OWNER_KEY: OWNER}:
        fail("Lunara Users group ownership or privilege differs")
    if set(group.users.values_list("pk", flat=True)) != {user.pk for user in allowed_users}:
        fail("Lunara Users group does not contain exactly the configured people")
    if group.roles.exists() or group.parents.exists() or group.children.exists():
        fail("Lunara Users has unexpected roles or hierarchy")

    oidc_bindings = list(PolicyBinding.objects.filter(target=application))
    if len(oidc_bindings) != 1:
        fail("OIDC application must have exactly one group binding")
    binding = oidc_bindings[0]
    if (
        binding.group_id != group.pk
        or binding.user_id is not None
        or binding.policy_id is not None
        or not binding.enabled
        or binding.negate
        or binding.order != 0
    ):
        fail("OIDC application binding differs from the pinned group binding")

    proxy_bindings = list(PolicyBinding.objects.filter(target=proxy_app).order_by("order"))
    if len(proxy_bindings) != 2:
        fail("proxy application must allow exactly gateway and human groups")
    expected_groups = [(gateway_group.pk, 0), (group.pk, 1)]
    if [
        (binding.group_id, binding.order)
        for binding in proxy_bindings
        if binding.enabled and not binding.negate and binding.user_id is None and binding.policy_id is None
    ] != expected_groups:
        fail("proxy application group bindings differ from the pinned any-policy")
    if proxy.skip_path_regex != PUBLIC_BYPASS:
        fail("only meta and Android App Links may bypass proxy authentication")
    if proxy.jwt_federation_sources.exists() or proxy.jwt_federation_providers.exists():
        fail("proxy provider has unexpected outbound federation relationships")


def classify(proxy, proxy_app, gateway_group, signing_key, scope_ids):
    oidc = inspect_oidc_named_objects()
    proxy_bindings = list(PolicyBinding.objects.filter(target=proxy_app))
    if oidc is None:
        if proxy.skip_path_regex != "":
            fail("base proxy already has an unexpected path bypass")
        if len(proxy_bindings) != 1 or proxy_bindings[0].group_id != gateway_group.pk:
            fail("base proxy application does not have exactly the gateway binding")
        if proxy.jwt_federation_sources.exists() or proxy.jwt_federation_providers.exists():
            fail("base proxy has unexpected federation")
        return "base", None
    verify_target(proxy, proxy_app, gateway_group, oidc, signing_key, scope_ids)
    return "target", oidc


def create_target(proxy, proxy_app, gateway_group, flows, signing_key, scope_ids):
    # All colliding names were checked while this transaction holds the namespace lock.
    users = list(User.objects.filter(username__in=ALLOWED_USERNAMES))
    if len(users) != len(set(ALLOWED_USERNAMES)) or set(user.username for user in users) != set(ALLOWED_USERNAMES):
        fail("configured human Authentik users are missing")
    if any(not user.is_active or user.type != UserTypes.INTERNAL for user in users):
        fail("configured human Authentik users are not active internal accounts")

    provider = OAuth2Provider(
        name=OIDC_PROVIDER_NAME,
        authentication_flow=flows["authentication"],
        authorization_flow=flows["authorization"],
        invalidation_flow=flows["invalidation"],
        client_type=ClientType.PUBLIC,
        grant_types=[GrantType.AUTHORIZATION_CODE, GrantType.REFRESH_TOKEN],
        client_id=OIDC_CLIENT_ID,
        redirect_uris=[
            RedirectURI(RedirectURIMatchingMode.STRICT, value)
            for value in OIDC_REDIRECTS
        ],
        signing_key=signing_key,
        encryption_key=None,
        issuer_mode=IssuerMode.PER_PROVIDER,
        sub_mode="hashed_user_id",
        access_code_validity="minutes=1",
        access_token_validity="minutes=15",
        refresh_token_validity="days=30",
        refresh_token_threshold="seconds=0",
        include_claims_in_id_token=True,
    )
    provider.save()
    provider.property_mappings.set(scope_ids)
    provider.jwt_federation_providers.add(proxy)

    application = Application.objects.create(
        name=OIDC_APPLICATION_NAME,
        slug=OIDC_APPLICATION_SLUG,
        group="Privat",
        provider=provider,
        meta_launch_url=EXTERNAL_HOST,
        open_in_new_tab=True,
        meta_description="Sichere Lunara-Anmeldung für Android und Web",
        meta_publisher=OWNER,
        meta_hide=False,
        policy_engine_mode="any",
    )
    group = Group.objects.create(
        name=OIDC_GROUP_NAME,
        is_superuser=False,
        attributes={OWNER_KEY: OWNER},
    )
    group.users.set(users)
    PolicyBinding.objects.create(
        target=application,
        group=group,
        enabled=True,
        order=0,
        negate=False,
        timeout=30,
        failure_result=False,
    )
    PolicyBinding.objects.create(
        target=proxy_app,
        group=group,
        enabled=True,
        order=1,
        negate=False,
        timeout=30,
        failure_result=False,
    )
    proxy.skip_path_regex = PUBLIC_BYPASS
    proxy.save(update_fields=["skip_path_regex"])


if ACTION not in {"check", "apply"}:
    fail("invalid action")
flows, signing_key, scope_ids = dependencies()
with transaction.atomic():
    lock_namespace()
    proxy, proxy_app, gateway_group = inspect_base()
    state, _ = classify(proxy, proxy_app, gateway_group, signing_key, scope_ids)
    if ACTION == "apply":
        if state != EXPECTED_STATE:
            fail("configuration changed after preflight")
        if state == "base":
            create_target(proxy, proxy_app, gateway_group, flows, signing_key, scope_ids)
        state, _ = classify(proxy, proxy_app, gateway_group, signing_key, scope_ids)
        if state != "target":
            fail("target OIDC contract could not be verified after apply")
print(f"LUNARA_AUTHENTIK_OIDC_STATE={state}")
PYTHON
}

if ! preflight_output="$(run_model check none)"; then
  echo "Authentik-OIDC-Preflight fehlgeschlagen; keine Änderung vorgenommen." >&2
  exit 1
fi
preflight_state="$(sed -n 's/^LUNARA_AUTHENTIK_OIDC_STATE=//p' <<< "${preflight_output}" | tail -n 1)"
unset preflight_output
[[ "${preflight_state}" == "base" || "${preflight_state}" == "target" ]] || {
  echo "Authentik-OIDC-Preflight lieferte keinen eindeutigen Zustand." >&2
  exit 1
}
if [[ "${check_only}" == "true" ]]; then
  if [[ "${preflight_state}" == "base" ]]; then
    echo "Authentik-Prüfung erfolgreich: der bestehende Lunara-Vollschutz kann sicher um optionales OIDC erweitert werden."
  else
    echo "Authentik-Prüfung erfolgreich: OIDC, PKCE-Weiterleitungen, Gruppen, Federation und einzige Meta-Ausnahme entsprechen dem Sollzustand."
  fi
  exit 0
fi

if [[ "${runtime_dir}" == "/" || "${backup_dir}" == "/" ||
      "${runtime_dir}" != /* || "${backup_dir}" != /* ||
      "${runtime_dir}" == *".."* || "${backup_dir}" == *".."* ]]; then
  echo "Runtime- und Backup-Verzeichnis müssen enge absolute Pfade sein." >&2
  exit 2
fi
install -d -m 0700 -- "${runtime_dir}" "${backup_dir}"

# Prepare the exact public API configuration before touching Authentik. The
# pending file is harmless until it is atomically promoted after verification;
# this prevents a successful provider mutation paired with an unwritable API
# configuration target.
oidc_environment_file="${runtime_dir}/authentik-oidc.env"
pending_oidc_environment_file="${runtime_dir}/.authentik-oidc.env.pending"
for candidate in "${oidc_environment_file}" "${pending_oidc_environment_file}"; do
  if [[ -e "${candidate}" &&
        ( ! -f "${candidate}" || -L "${candidate}" || ! -O "${candidate}" ) ]]; then
    echo "Unsicheres vorhandenes OIDC-Umgebungsziel: ${candidate}" >&2
    exit 1
  fi
done
python3 - "${pending_oidc_environment_file}" "${authentik_public_url}" "${android_app_package}" "${android_cert_fingerprints}" <<'PY'
import os
import sys

destination, authentik_url, android_package, raw_fingerprints = sys.argv[1:]
issuer = f"{authentik_url}/application/o/lunara-android/"
fingerprints = ",".join(
    value.strip().upper() for value in raw_fingerprints.split(",") if value.strip()
)
lines = [
    "OIDC_ENABLED=true",
    "OIDC_PROVIDER_NAME=Authentik",
    f"OIDC_ISSUER={issuer}",
    "OIDC_AUDIENCE=lunara-android",
    "OIDC_CLIENT_ID=lunara-android",
    "OIDC_SCOPES=openid offline_access",
    f"OIDC_DISCOVERY_URL={issuer}.well-known/openid-configuration",
    f"OIDC_JWKS_URL={issuer}jwks/",
    "OIDC_ALLOWED_ALGORITHMS=RS256",
    "OIDC_MAX_TOKEN_AGE_SECONDS=3600",
    "OIDC_CLOCK_TOLERANCE_SECONDS=30",
    "ANDROID_APP_LINK_ENABLED=true",
    f"ANDROID_APP_LINK_PACKAGE={android_package}",
    f"ANDROID_APP_LINK_CERT_SHA256={fingerprints}",
]
descriptor = os.open(
    destination,
    os.O_WRONLY | os.O_CREAT | os.O_TRUNC | os.O_NOFOLLOW,
    0o600,
)
try:
    os.fchmod(descriptor, 0o600)
    payload = ("\n".join(lines) + "\n").encode("utf-8")
    while payload:
        payload = payload[os.write(descriptor, payload):]
    os.fsync(descriptor)
finally:
    os.close(descriptor)
PY

backup_path=""
if [[ "${preflight_state}" == "base" ]]; then
  timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
  backup_path="$(mktemp --tmpdir="${backup_dir}" "authentik-before-lunara-oidc-${timestamp}.XXXXXX.dump")"
  chmod 0600 -- "${backup_path}"
  if ! docker exec "${authentik_db_container}" sh -ceu \
      'pg_dump --format=custom --no-owner --no-acl --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"' \
      > "${backup_path}" || [[ ! -s "${backup_path}" ]]; then
    echo "Authentik-Backup fehlgeschlagen; keine Änderung vorgenommen. Datei: ${backup_path}" >&2
    exit 1
  fi
  if ! docker exec -i "${authentik_db_container}" pg_restore --list < "${backup_path}" >/dev/null; then
    echo "Authentik-Backup ist nicht lesbar; keine Änderung vorgenommen. Datei: ${backup_path}" >&2
    exit 1
  fi
fi

if ! run_model apply "${preflight_state}" >/dev/null; then
  echo "Authentik-OIDC-Konfiguration fehlgeschlagen." >&2
  if [[ -n "${backup_path}" ]]; then
    echo "Das validierte Vorab-Backup liegt unter: ${backup_path}" >&2
  fi
  exit 1
fi
if ! final_output="$(run_model check none)" ||
   [[ "$(sed -n 's/^LUNARA_AUTHENTIK_OIDC_STATE=//p' <<< "${final_output}" | tail -n 1)" != "target" ]]; then
  echo "Authentik-OIDC-Nachprüfung fehlgeschlagen." >&2
  [[ -n "${backup_path}" ]] && echo "Vorab-Backup: ${backup_path}" >&2
  exit 1
fi
unset final_output

python3 - "${pending_oidc_environment_file}" "${oidc_environment_file}" <<'PY'
import os
import sys

pending, destination = sys.argv[1:]
os.replace(pending, destination)
directory = os.open(os.path.dirname(destination), os.O_RDONLY | os.O_DIRECTORY)
try:
    os.fsync(directory)
finally:
    os.close(directory)
PY

echo "Authentik-OIDC ist sicher eingerichtet und nachgeprüft."
echo "Nicht geheime Compose-Werte wurden als 0600-Datei geschrieben: ${oidc_environment_file}"
if [[ -n "${backup_path}" ]]; then
  echo "Konsistentes Authentik-Vorab-Backup: ${backup_path}"
fi
