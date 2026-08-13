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
  if [[ "$1" != "--check" ]]; then
    usage
    exit 2
  fi
  check_only=true
fi
if [[ "${check_only}" != "true" && "${check_only}" != "false" ]]; then
  echo "LUNARA_CHECK_ONLY akzeptiert nur true oder false." >&2
  exit 2
fi

runtime_dir="${LUNARA_RUNTIME_DIR:-/docker/lunara}"
backup_dir="${LUNARA_INFRA_BACKUP_DIR:-/var/backups/lunara/infrastructure}"
authentik_container="${AUTHENTIK_CONTAINER:-authentik-server}"
authentik_db_container="${AUTHENTIK_DB_CONTAINER:-postgresql}"
public_host="${LUNARA_PUBLIC_HOST:-lunara.3li361.de}"
internal_host="${LUNARA_INTERNAL_URL:-http://lunara-api:3000}"
expected_authentik_version="2026.5.3"
credential_pipe_container="/media/.lunara-authentik-credentials.pipe"

for command_name in docker sed tail; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Fehlendes Werkzeug: ${command_name}" >&2
    exit 1
  fi
done

if [[ ! "${authentik_container}" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] ||
   [[ ! "${authentik_db_container}" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]]; then
  echo "Ungültiger Authentik-Containername." >&2
  exit 2
fi
if [[ ! "${public_host}" =~ ^[a-z0-9][a-z0-9.-]*[a-z0-9]$ ]] ||
   [[ "${public_host}" != *.* ]] || [[ "${public_host}" == *..* ]] ||
   (( ${#public_host} > 253 )); then
  echo "Ungültiger kanonischer öffentlicher Hostname." >&2
  exit 2
fi
IFS='.' read -r -a host_labels <<< "${public_host}"
for host_label in "${host_labels[@]}"; do
  if (( ${#host_label} > 63 )) || [[ "${host_label}" == -* ]] || [[ "${host_label}" == *- ]]; then
    echo "Ungültiger kanonischer öffentlicher Hostname." >&2
    exit 2
  fi
done
unset host_labels host_label
if [[ "${internal_host}" != "http://lunara-api:3000" ]]; then
  echo "Aus Sicherheitsgründen ist nur der geprüfte interne Lunara-Endpunkt erlaubt." >&2
  exit 2
fi

authentik_version="$(docker inspect --type container --format '{{index .Config.Labels "org.opencontainers.image.version"}}' "${authentik_container}")"
if [[ "${authentik_version}" != "${expected_authentik_version}" &&
      "${authentik_version}" != "refs/tags/version/${expected_authentik_version}" ]]; then
  echo "Ungeprüfte Authentik-Version '${authentik_version:-unbekannt}'; erwartet ${expected_authentik_version}. Keine Änderung vorgenommen." >&2
  exit 1
fi
if [[ "$(docker inspect --type container --format '{{.State.Running}}' "${authentik_container}")" != "true" ]] ||
   [[ "$(docker inspect --type container --format '{{.State.Running}}' "${authentik_db_container}")" != "true" ]]; then
  echo "Authentik- oder Datenbankcontainer läuft nicht; keine Änderung vorgenommen." >&2
  exit 1
fi

run_authentik_model() {
  local action="$1"
  local expected_state="$2"
  docker exec -i \
    -e LUNARA_ACTION="${action}" \
    -e LUNARA_EXPECTED_STATE="${expected_state}" \
    -e LUNARA_PUBLIC_HOST="${public_host}" \
    -e LUNARA_INTERNAL_URL="${internal_host}" \
    -e LUNARA_CREDENTIAL_PIPE="${credential_pipe_container}" \
    "${authentik_container}" \
    ak shell --no-imports -v 0 <<'PYTHON'
import json
import os
from dataclasses import asdict

from django.db import connection, transaction

from authentik.core.models import (
    Application,
    Group,
    Provider,
    Token,
    TokenIntents,
    User,
    UserTypes,
)
from authentik.flows.models import Flow
from authentik.outposts.models import Outpost
from authentik.policies.models import PolicyBinding
from authentik.providers.oauth2.models import ClientType, GrantType, ScopeMapping
from authentik.providers.proxy.models import ProxyMode, ProxyProvider, _get_callback_url

ACTION = os.environ["LUNARA_ACTION"]
EXPECTED_STATE = os.environ["LUNARA_EXPECTED_STATE"]
PUBLIC_HOST = os.environ["LUNARA_PUBLIC_HOST"]
INTERNAL_HOST = os.environ["LUNARA_INTERNAL_URL"]
CREDENTIAL_PIPE = os.environ.get("LUNARA_CREDENTIAL_PIPE", "")

OWNER_KEY = "lunara.infrastructure.owner"
LEGACY_OWNER = "lunara-authentik-gateway-v1"
TARGET_OWNER = "lunara-authentik-gateway-v2"
PROVIDER_NAME = "Lunara Proxy"
APPLICATION_NAME = "Lunara"
APPLICATION_SLUG = "lunara"
APPLICATION_GROUP = "Privat"
APPLICATION_DESCRIPTION = "Private selbst gehostete Zyklusbegleitung"
GROUP_NAME = "Lunara Gateway"
USER_PATH = "service-accounts/lunara"
EXTERNAL_HOST = f"https://{PUBLIC_HOST}"
API_SKIP_PATH_REGEX = r"^/api/v1(?:/|$)"

LEGACY_HUMAN_USERNAMES = ("Dune", "Elias")
LEGACY_IDENTITIES = {
    suffix: {
        "username": f"lunara-gateway-{suffix}",
        "name": f"Lunara Gateway {suffix.upper()}",
        "identifier": f"lunara-gateway-{suffix}-app-password",
    }
    for suffix in ("a", "b")
}
TARGET_IDENTITY = {
    "username": "lunara-native-app",
    "name": "Lunara Native App",
    "identifier": "lunara-native-app-app-password",
}


def fail(message):
    raise RuntimeError(f"Lunara Authentik preflight failed: {message}")


def owner_attributes(owner):
    return {OWNER_KEY: owner}


def token_description(owner):
    return f"Lunara native app header authentication [{owner}]"


def dependencies():
    try:
        flows = {
            "authentication": Flow.objects.get(slug="default-authentication-flow"),
            "authorization": Flow.objects.get(
                slug="default-provider-authorization-implicit-consent"
            ),
            "invalidation": Flow.objects.get(slug="default-provider-invalidation-flow"),
        }
        outpost = Outpost.objects.get(managed="goauthentik.io/outposts/embedded")
    except Exception as exc:
        fail(f"required managed dependency is missing or ambiguous ({exc.__class__.__name__})")
    scopes = set(
        ScopeMapping.objects.filter(
            managed__in={
                "goauthentik.io/providers/oauth2/scope-openid",
                "goauthentik.io/providers/oauth2/scope-profile",
                "goauthentik.io/providers/oauth2/scope-email",
                "goauthentik.io/providers/oauth2/scope-entitlements",
                "goauthentik.io/providers/proxy/scope-proxy",
            }
        ).values_list("pk", flat=True)
    )
    if len(scopes) != 5:
        fail("the five pinned managed proxy scope mappings are not available")
    return flows, outpost, scopes


def lock_configuration_namespace():
    with connection.cursor() as cursor:
        cursor.execute("SELECT pg_advisory_xact_lock(%s)", [781334920250513])


def one_or_none(queryset, description):
    matches = list(queryset)
    if len(matches) > 1:
        fail(f"duplicate {description} objects exist")
    return matches


def inspect_named_objects():
    providers = one_or_none(Provider.objects.filter(name=PROVIDER_NAME), "provider")
    applications_by_slug = one_or_none(
        Application.objects.filter(slug=APPLICATION_SLUG), "application slug"
    )
    applications_by_name = one_or_none(
        Application.objects.filter(name=APPLICATION_NAME), "application name"
    )
    groups = one_or_none(Group.objects.filter(name=GROUP_NAME), "gateway group")
    target_users = one_or_none(
        User.objects.filter(username=TARGET_IDENTITY["username"]), "target user"
    )
    target_tokens = one_or_none(
        Token.objects.including_expired().filter(identifier=TARGET_IDENTITY["identifier"]),
        "target token",
    )
    legacy_users = {
        suffix: one_or_none(
            User.objects.filter(username=identity["username"]),
            f"legacy user {suffix}",
        )
        for suffix, identity in LEGACY_IDENTITIES.items()
    }
    legacy_tokens = {
        suffix: one_or_none(
            Token.objects.including_expired().filter(identifier=identity["identifier"]),
            f"legacy token {suffix}",
        )
        for suffix, identity in LEGACY_IDENTITIES.items()
    }

    host_provider_ids = set(
        ProxyProvider.objects.filter(external_host=EXTERNAL_HOST).values_list("pk", flat=True)
    ) | set(
        ProxyProvider.objects.filter(internal_host=INTERNAL_HOST).values_list("pk", flat=True)
    )
    launch_application_ids = set(
        Application.objects.filter(meta_launch_url=EXTERNAL_HOST).values_list("pk", flat=True)
    )
    path_user_ids = set(User.objects.filter(path=USER_PATH).values_list("pk", flat=True))

    core_collections = [providers, applications_by_slug, applications_by_name, groups]
    identity_collections = [
        target_users,
        target_tokens,
        *legacy_users.values(),
        *legacy_tokens.values(),
    ]
    if all(len(items) == 0 for items in [*core_collections, *identity_collections]):
        if host_provider_ids or launch_application_ids or path_user_ids:
            fail("a differently named object already uses a Lunara-owned host or user path")
        return "create", None
    if any(len(items) != 1 for items in core_collections):
        fail("partial or colliding Lunara provider, application, or group objects exist")
    if applications_by_slug[0].pk != applications_by_name[0].pk:
        fail("application name and slug refer to different objects")
    provider = ProxyProvider.objects.filter(pk=providers[0].pk).first()
    if provider is None:
        fail("the provider name belongs to a different provider type")
    if host_provider_ids != {provider.pk} or launch_application_ids != {applications_by_slug[0].pk}:
        fail("another provider or application collides with a Lunara host")

    target_complete = len(target_users) == 1 and len(target_tokens) == 1
    target_absent = len(target_users) == 0 and len(target_tokens) == 0
    legacy_complete = all(
        len(items) == 1 for items in [*legacy_users.values(), *legacy_tokens.values()]
    )
    legacy_absent = all(
        len(items) == 0 for items in [*legacy_users.values(), *legacy_tokens.values()]
    )
    common = {
        "provider": provider,
        "application": applications_by_slug[0],
        "group": groups[0],
    }
    if target_complete and legacy_absent:
        if path_user_ids != {target_users[0].pk}:
            fail("the Lunara service-account path contains an unexpected user")
        return "target_layout", {
            **common,
            "user": target_users[0],
            "token": target_tokens[0],
        }
    if target_absent and legacy_complete:
        if path_user_ids != {users[0].pk for users in legacy_users.values()}:
            fail("the legacy Lunara service-account path contains an unexpected user")
        return "legacy_layout", {
            **common,
            "users": {suffix: users[0] for suffix, users in legacy_users.items()},
            "tokens": {suffix: tokens[0] for suffix, tokens in legacy_tokens.items()},
        }
    fail("partial, mixed, or colliding Lunara gateway identities exist")


def verify_common(objects, flows, outpost, expected_scopes, owner, skip_path_regex):
    provider = objects["provider"]
    application = objects["application"]
    group = objects["group"]
    expected_redirects = [asdict(item) for item in _get_callback_url(EXTERNAL_HOST)]
    expected_provider_values = {
        "name": PROVIDER_NAME,
        "authentication_flow_id": flows["authentication"].pk,
        "authorization_flow_id": flows["authorization"].pk,
        "invalidation_flow_id": flows["invalidation"].pk,
        "internal_host": INTERNAL_HOST,
        "external_host": EXTERNAL_HOST,
        "internal_host_ssl_validation": True,
        "mode": ProxyMode.PROXY,
        "skip_path_regex": skip_path_regex,
        "intercept_header_auth": True,
        "basic_auth_enabled": False,
        "basic_auth_user_attribute": "",
        "basic_auth_password_attribute": "",
        "cookie_domain": PUBLIC_HOST,
        "certificate_id": None,
        "signing_key_id": None,
        "encryption_key_id": None,
        "backchannel_application_id": None,
        "is_backchannel": False,
        "client_type": ClientType.CONFIDENTIAL,
        "include_claims_in_id_token": True,
        "logout_uri": "",
        "logout_method": "backchannel",
        "access_code_validity": "minutes=1",
        "access_token_validity": "hours=1",
        "refresh_token_validity": "days=30",
        "refresh_token_threshold": "seconds=0",
        "sub_mode": "hashed_user_id",
        "issuer_mode": "per_provider",
    }
    for field, expected in expected_provider_values.items():
        if getattr(provider, field) != expected:
            fail(f"provider field {field} does not match the pinned value")
    if list(provider.grant_types) != [
        GrantType.AUTHORIZATION_CODE,
        GrantType.CLIENT_CREDENTIALS,
        GrantType.PASSWORD,
    ]:
        fail("provider grant types do not match the pinned proxy defaults")
    if provider._redirect_uris != expected_redirects:
        fail("provider redirect URIs do not match the pinned external host")
    if set(provider.property_mappings.values_list("pk", flat=True)) != expected_scopes:
        fail("provider property mappings differ from the five pinned managed scopes")
    if (
        not isinstance(provider.cookie_secret, str)
        or len(provider.cookie_secret) != 32
        or not all(character.isupper() or character.isdigit() for character in provider.cookie_secret)
    ):
        fail("provider cookie secret does not match the pinned generated contract")
    if (
        not isinstance(provider.client_id, str)
        or len(provider.client_id) != 40
        or not provider.client_id.isalnum()
        or not isinstance(provider.client_secret, str)
        or len(provider.client_secret) != 128
        or "\n" in provider.client_secret
        or "\r" in provider.client_secret
    ):
        fail("provider OAuth credentials do not match the pinned generated contract")
    if provider.jwt_federation_sources.exists() or provider.jwt_federation_providers.exists():
        fail("provider has unexpected JWT federation relationships")

    expected_application_values = {
        "name": APPLICATION_NAME,
        "slug": APPLICATION_SLUG,
        "group": APPLICATION_GROUP,
        "provider_id": provider.pk,
        "meta_launch_url": EXTERNAL_HOST,
        "open_in_new_tab": True,
        "meta_description": APPLICATION_DESCRIPTION,
        "meta_publisher": owner,
        "meta_hide": False,
        "policy_engine_mode": "any",
    }
    for field, expected in expected_application_values.items():
        if getattr(application, field) != expected:
            fail(f"application field {field} does not match the pinned value or owner marker")
    if str(application.meta_icon) != "":
        fail("application has an unexpected icon override")

    if group.is_superuser or group.attributes != owner_attributes(owner):
        fail("gateway group is not exactly marked as Lunara-owned and non-superuser")
    if group.roles.exists() or group.parents.exists() or group.children.exists():
        fail("gateway group has unexpected roles or hierarchy relationships")
    bindings = list(PolicyBinding.objects.filter(target=application))
    if len(bindings) != 1:
        fail("application must have exactly one policy binding")
    binding = bindings[0]
    if (
        binding.group_id != group.pk
        or binding.user_id is not None
        or binding.policy_id is not None
        or not binding.enabled
        or binding.negate
        or binding.order != 0
        or binding.timeout != 30
        or binding.failure_result
    ):
        fail("application policy binding is not the exact Lunara gateway group binding")
    linked_outposts = set(Outpost.objects.filter(providers=provider).values_list("pk", flat=True))
    if linked_outposts != {outpost.pk}:
        fail("provider is not linked exclusively to the embedded managed outpost")


def verify_service_account(user, token, group, identity, owner):
    if (
        user.username != identity["username"]
        or user.name != identity["name"]
        or user.type != UserTypes.SERVICE_ACCOUNT
        or user.path != USER_PATH
        or not user.is_active
        or user.is_staff
        or user.is_superuser
        or user.email != ""
        or user.attributes != owner_attributes(owner)
        or user.has_usable_password()
    ):
        fail(f"service account {identity['username']} does not match the exact owned contract")
    if set(user.groups.values_list("pk", flat=True)) != {group.pk}:
        fail(f"service account {identity['username']} has unexpected group memberships")
    if user.roles.exists() or user.sources.exists():
        fail(f"service account {identity['username']} has unexpected relationships")
    if set(
        Token.objects.including_expired().filter(user_id=user.pk).values_list("pk", flat=True)
    ) != {token.pk}:
        fail(f"service account {identity['username']} must own exactly one token")
    if (
        token.identifier != identity["identifier"]
        or token.user_id != user.pk
        or token.intent != TokenIntents.INTENT_APP_PASSWORD
        or token.description != token_description(owner)
        or token.expiring
        or token.expires is not None
        or not isinstance(token.key, str)
        or len(token.key) < 20
        or len(token.key) > 255
        or not token.key.isalnum()
    ):
        fail(f"app password for {identity['username']} does not match the exact contract")


def legacy_human_users():
    users = []
    for username in LEGACY_HUMAN_USERNAMES:
        matches = list(User.objects.filter(username=username))
        if len(matches) != 1:
            fail(f"legacy human user {username} is missing or ambiguous")
        user = matches[0]
        if not user.is_active or user.type != UserTypes.INTERNAL:
            fail(f"legacy human user {username} is not an active internal account")
        users.append(user)
    return users


def classify_and_verify(flows, outpost, expected_scopes):
    layout, objects = inspect_named_objects()
    if layout == "create":
        return "create", None, None
    if layout == "target_layout":
        verify_common(objects, flows, outpost, expected_scopes, TARGET_OWNER, "")
        verify_service_account(
            objects["user"], objects["token"], objects["group"], TARGET_IDENTITY, TARGET_OWNER
        )
        if set(objects["group"].users.values_list("pk", flat=True)) != {objects["user"].pk}:
            fail("target gateway group must contain only lunara-native-app")
        return "target", objects, {
            "username": objects["user"].username,
            "appPassword": objects["token"].key,
        }

    skip_path_regex = objects["provider"].skip_path_regex
    if skip_path_regex == "":
        state = "legacy_v102"
    elif skip_path_regex == API_SKIP_PATH_REGEX:
        state = "legacy_v103"
    else:
        fail("legacy provider skip_path_regex is neither blank nor the exact API bypass")
    verify_common(objects, flows, outpost, expected_scopes, LEGACY_OWNER, skip_path_regex)
    humans = legacy_human_users()
    expected_group_users = {user.pk for user in humans}
    for suffix, identity in LEGACY_IDENTITIES.items():
        user = objects["users"][suffix]
        token = objects["tokens"][suffix]
        verify_service_account(user, token, objects["group"], identity, LEGACY_OWNER)
        expected_group_users.add(user.pk)
    if set(objects["group"].users.values_list("pk", flat=True)) != expected_group_users:
        fail("legacy gateway group does not exactly contain A, B, Dune, and Elias")
    return state, objects, None


def create_service_account(group):
    user = User(
        username=TARGET_IDENTITY["username"],
        name=TARGET_IDENTITY["name"],
        type=UserTypes.SERVICE_ACCOUNT,
        path=USER_PATH,
        is_active=True,
        attributes=owner_attributes(TARGET_OWNER),
    )
    user.set_unusable_password()
    user.save()
    group.users.add(user)
    Token.objects.create(
        identifier=TARGET_IDENTITY["identifier"],
        user=user,
        intent=TokenIntents.INTENT_APP_PASSWORD,
        description=token_description(TARGET_OWNER),
        expiring=False,
        expires=None,
    )


def create_target(flows, outpost):
    layout, _ = inspect_named_objects()
    if layout != "create":
        fail("named objects appeared after preflight; refusing to adopt or overwrite them")
    provider = ProxyProvider(
        name=PROVIDER_NAME,
        authentication_flow=flows["authentication"],
        authorization_flow=flows["authorization"],
        invalidation_flow=flows["invalidation"],
        internal_host=INTERNAL_HOST,
        external_host=EXTERNAL_HOST,
        internal_host_ssl_validation=True,
        mode=ProxyMode.PROXY,
        skip_path_regex="",
        intercept_header_auth=True,
        basic_auth_enabled=False,
        basic_auth_user_attribute="",
        basic_auth_password_attribute="",
        cookie_domain=PUBLIC_HOST,
        certificate=None,
        signing_key=None,
        encryption_key=None,
        backchannel_application=None,
        is_backchannel=False,
    )
    provider.save()
    provider.set_oauth_defaults()
    provider.save()
    application = Application.objects.create(
        name=APPLICATION_NAME,
        slug=APPLICATION_SLUG,
        group=APPLICATION_GROUP,
        provider=provider,
        meta_launch_url=EXTERNAL_HOST,
        open_in_new_tab=True,
        meta_description=APPLICATION_DESCRIPTION,
        meta_publisher=TARGET_OWNER,
        meta_hide=False,
        policy_engine_mode="any",
    )
    group = Group.objects.create(
        name=GROUP_NAME,
        is_superuser=False,
        attributes=owner_attributes(TARGET_OWNER),
    )
    PolicyBinding.objects.create(
        target=application,
        group=group,
        policy=None,
        user=None,
        enabled=True,
        order=0,
        negate=False,
        timeout=30,
        failure_result=False,
    )
    outpost.providers.add(provider)
    create_service_account(group)


def migrate_legacy(objects):
    provider = objects["provider"]
    application = objects["application"]
    group = objects["group"]

    # The complete source contract was verified while this same transaction
    # holds the namespace advisory lock. No partial or user-managed state is
    # adopted. Human accounts themselves remain untouched.
    group.users.clear()
    create_service_account(group)
    for suffix in ("a", "b"):
        objects["tokens"][suffix].delete()
        objects["users"][suffix].delete()
    provider.skip_path_regex = ""
    provider.save(update_fields=["skip_path_regex"])
    application.meta_publisher = TARGET_OWNER
    application.save(update_fields=["meta_publisher"])
    group.attributes = owner_attributes(TARGET_OWNER)
    group.save(update_fields=["attributes"])


if ACTION not in {"check", "apply"}:
    fail("invalid action")
flows, outpost, expected_scopes = dependencies()
if ACTION == "check":
    with transaction.atomic():
        lock_configuration_namespace()
        state, _, _ = classify_and_verify(flows, outpost, expected_scopes)
    print(f"LUNARA_AUTHENTIK_PREFLIGHT={state}")
else:
    if EXPECTED_STATE not in {"create", "target", "legacy_v102", "legacy_v103"}:
        fail("invalid expected state")
    with transaction.atomic():
        lock_configuration_namespace()
        state, objects, credentials = classify_and_verify(flows, outpost, expected_scopes)
        if state != EXPECTED_STATE:
            fail("named object state changed after preflight")
        if state == "create":
            create_target(flows, outpost)
        elif state in {"legacy_v102", "legacy_v103"}:
            migrate_legacy(objects)
        state, _, credentials = classify_and_verify(flows, outpost, expected_scopes)
        if state != "target" or credentials is None:
            fail("target gateway contract could not be verified after apply")
        if CREDENTIAL_PIPE != "/media/.lunara-authentik-credentials.pipe":
            fail("credential pipe path does not match the pinned bind-mounted FIFO")
        pipe_fd = os.open(CREDENTIAL_PIPE, os.O_WRONLY | os.O_NOFOLLOW)
        try:
            pipe_stat = os.fstat(pipe_fd)
            stat_module = __import__("stat")
            if (
                not stat_module.S_ISFIFO(pipe_stat.st_mode)
                or stat_module.S_IMODE(pipe_stat.st_mode) != 0o600
                or pipe_stat.st_uid != os.geteuid()
            ):
                fail("credential transport is not the exact owner-only FIFO")
            payload = json.dumps(credentials, separators=(",", ":")).encode("utf-8") + b"\n"
            while payload:
                written = os.write(pipe_fd, payload)
                payload = payload[written:]
        finally:
            os.close(pipe_fd)
PYTHON
}

if ! preflight_output="$(run_authentik_model check none)"; then
  echo "Authentik-Preflight fehlgeschlagen; keine Änderung vorgenommen." >&2
  exit 1
fi
preflight_state="$(sed -n 's/^LUNARA_AUTHENTIK_PREFLIGHT=//p' <<< "${preflight_output}" | tail -n 1)"
unset preflight_output
case "${preflight_state}" in
  create|target|legacy_v102|legacy_v103)
    ;;
  *)
    echo "Authentik-Preflight lieferte keinen eindeutigen Zustand; keine Änderung vorgenommen." >&2
    exit 1
    ;;
esac
if [[ "${check_only}" == "true" ]]; then
  case "${preflight_state}" in
    create)
      echo "Authentik-Prüfung erfolgreich: alle Lunara-Namen sind frei; die geschützte Einzel-App-Konfiguration könnte angelegt werden."
      ;;
    legacy_v102)
      echo "Authentik-Prüfung erfolgreich: exakter alter A/B+Dune/Elias-Stand mit Vollschutz erkannt; Migration auf einen App-Zugang ausstehend (keine Änderung vorgenommen)."
      ;;
    legacy_v103)
      echo "Authentik-Prüfung erfolgreich: exakter A/B+Dune/Elias-Stand mit API-Ausnahme erkannt; Migration auf einen vollständig geschützten App-Zugang ausstehend (keine Änderung vorgenommen)."
      ;;
    target)
      echo "Authentik-Prüfung erfolgreich: genau ein technischer App-Zugang schützt sämtliche Lunara-Pfade; keine menschlichen Gruppenmitglieder und keine API-Ausnahme."
      ;;
  esac
  exit 0
fi

for command_name in basename chown flock install jq mkfifo mktemp mv python3 stat; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Fehlendes Werkzeug: ${command_name}" >&2
    exit 1
  fi
done

path_has_symlink_component() {
  local path="$1"
  local current=""
  local component
  local -a components
  IFS='/' read -r -a components <<< "${path}"
  for component in "${components[@]}"; do
    [[ -z "${component}" ]] && continue
    current="${current}/${component}"
    if [[ -L "${current}" ]]; then
      return 0
    fi
  done
  return 1
}

for directory in "${runtime_dir}" "${backup_dir}"; do
  if [[ "${directory}" == "/" || "${directory}" != /* || ! "${directory}" =~ ^/[A-Za-z0-9_./-]+$ ]] ||
     [[ "${directory}" == */../* || "${directory}" == */.. ||
        "${directory}" == */./* || "${directory}" == */. ]] ||
     path_has_symlink_component "${directory}"; then
    echo "Runtime- und Backup-Verzeichnis müssen sichere absolute Pfade ohne symbolische Komponenten sein." >&2
    exit 2
  fi
done
install -d -m 0700 -- "${runtime_dir}" "${backup_dir}"

configuration_lock_file="${runtime_dir}/.configure-authentik.lock"
if [[ -e "${configuration_lock_file}" &&
      ( ! -f "${configuration_lock_file}" || -L "${configuration_lock_file}" ||
        ! -O "${configuration_lock_file}" ) ]]; then
  echo "Unsichere Authentik-Konfigurationssperre." >&2
  exit 1
fi
exec {configuration_lock_fd}> "${configuration_lock_file}"
chmod 0600 -- "${configuration_lock_file}"
if ! flock -n "${configuration_lock_fd}"; then
  echo "Eine andere Lunara-Authentik-Konfiguration läuft bereits." >&2
  exit 1
fi

credentials_file="${runtime_dir}/authentik-gateway-credentials.json"
gateway_file="${runtime_dir}/lunara-app-headerauth.url"
for output_file in "${credentials_file}" "${gateway_file}"; do
  if [[ -e "${output_file}" && ( ! -f "${output_file}" || -L "${output_file}" || ! -O "${output_file}" ) ]]; then
    echo "Unsicheres vorhandenes Ausgabeziel: ${output_file}" >&2
    exit 1
  fi
done

backup_path=""
legacy_files_archive=""
archived_legacy_names=()
restore_archived_legacy_files() {
  local archived_name
  local failed_output
  if [[ -z "${legacy_files_archive}" ]]; then
    return 0
  fi
  for failed_output in "${credentials_file}" "${gateway_file}"; do
    if [[ -f "${failed_output}" && ! -L "${failed_output}" && -O "${failed_output}" ]]; then
      mv -T -- "${failed_output}" \
        "${legacy_files_archive}/failed-new-$(basename -- "${failed_output}")"
      chmod 0600 -- "${legacy_files_archive}/failed-new-$(basename -- "${failed_output}")"
    fi
  done
  for archived_name in "${archived_legacy_names[@]}"; do
    if [[ -e "${legacy_files_archive}/${archived_name}" &&
          ! -e "${runtime_dir}/${archived_name}" ]]; then
      mv -T -- "${legacy_files_archive}/${archived_name}" "${runtime_dir}/${archived_name}"
      chmod 0600 -- "${runtime_dir}/${archived_name}"
    fi
  done
}
if [[ "${preflight_state}" != "target" ]]; then
  timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
  backup_path="$(mktemp --tmpdir="${backup_dir}" "authentik-before-lunara-single-app-${timestamp}.XXXXXX.dump")"
  chmod 0600 -- "${backup_path}"
  if ! docker exec "${authentik_db_container}" sh -ceu \
      'pg_dump --format=custom --no-owner --no-acl --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"' \
      > "${backup_path}" || [[ ! -s "${backup_path}" ]]; then
    echo "Authentik-Backup fehlgeschlagen; keine Änderung vorgenommen. Die unvollständige Datei bleibt geschützt unter ${backup_path}." >&2
    exit 1
  fi
  if ! docker exec -i "${authentik_db_container}" pg_restore --list < "${backup_path}" >/dev/null; then
    echo "Authentik-Backup ist nicht als PostgreSQL-Custom-Dump lesbar; keine Änderung vorgenommen. Die Datei bleibt geschützt unter ${backup_path}." >&2
    exit 1
  fi
fi

if [[ "${preflight_state}" == "legacy_v102" || "${preflight_state}" == "legacy_v103" ]]; then
  legacy_files_archive="$(mktemp -d --tmpdir="${backup_dir}" "authentik-legacy-headerauth-${timestamp}.XXXXXX")"
  chmod 0700 -- "${legacy_files_archive}"
  legacy_file_names=(
    authentik-gateway-credentials.json
    authentik-lunara-config.log
    gateway-a.url
    gateway-b.url
    dunja-headerauth.url
    elias-headerauth.url
    lunara-app-headerauth.url
  )
  for legacy_name in "${legacy_file_names[@]}"; do
    legacy_path="${runtime_dir}/${legacy_name}"
    if [[ ! -e "${legacy_path}" ]]; then
      continue
    fi
    if [[ ! -f "${legacy_path}" || -L "${legacy_path}" || ! -O "${legacy_path}" ]]; then
      echo "Unsichere Altdatei; keine Authentik-Änderung vorgenommen: ${legacy_path}" >&2
      exit 1
    fi
    legacy_mode="$(stat -c '%a' -- "${legacy_path}")"
    if (( (8#${legacy_mode} & 077) != 0 )); then
      echo "Altdatei besitzt Gruppen- oder Weltrechte; keine Authentik-Änderung vorgenommen: ${legacy_path}" >&2
      exit 1
    fi
  done
  for legacy_name in "${legacy_file_names[@]}"; do
    legacy_path="${runtime_dir}/${legacy_name}"
    if [[ -e "${legacy_path}" ]]; then
      if mv -T -- "${legacy_path}" "${legacy_files_archive}/${legacy_name}"; then
        archived_legacy_names+=("${legacy_name}")
        if ! chmod 0600 -- "${legacy_files_archive}/${legacy_name}"; then
          set +e
          restore_archived_legacy_files
          echo "Altdateirechte konnten nicht gesichert werden; keine Authentik-Änderung vorgenommen." >&2
          exit 1
        fi
      else
        set +e
        restore_archived_legacy_files
        echo "Altdateien konnten nicht vollständig gesichert werden; keine Authentik-Änderung vorgenommen." >&2
        exit 1
      fi
    fi
  done
  unset legacy_file_names legacy_name legacy_path legacy_mode
fi

print_authentik_rollback() {
  local output_fd="${1:-2}"
  if [[ -n "${backup_path}" ]]; then
    echo "Vollständiger Datenbank-Rollback; er verwirft alle Authentik-Änderungen seit dem Backup." >&"${output_fd}"
    printf 'Rollback nach kontrolliertem Stop von Authentik: docker exec -i %q sh -ceu '\''pg_restore --clean --if-exists --no-owner --no-acl --single-transaction --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"'\'' < %q\n' \
      "${authentik_db_container}" "${backup_path}" >&"${output_fd}"
  fi
}

configuration_may_have_committed=false
on_postapply_error() {
  local status=$?
  trap - EXIT
  set +e
  if [[ "${configuration_may_have_committed}" == "false" ]]; then
    restore_archived_legacy_files
  else
    echo "Fehler während oder nach dem Authentik-Commit; die Datenbank zuerst mit --check prüfen." >&2
    print_authentik_rollback 2
  fi
  exit "${status}"
}
trap on_postapply_error EXIT

# A permanent, root-selected FIFO in Authentik's media bind mount transports
# the credential payload without command arguments, stdout, or a secret temp
# file. The FIFO itself contains no data at rest and is safe to reuse.
mapfile -t authentik_media_mounts < <(
  docker inspect --type container --format \
    '{{range .Mounts}}{{if eq .Destination "/media"}}{{printf "%s\t%t\t%s\n" .Type .RW .Source}}{{end}}{{end}}' \
    "${authentik_container}" | sed -n '/./p'
)
if [[ ${#authentik_media_mounts[@]} -ne 1 ]]; then
  echo "Der eindeutige Authentik-/media-Mount wurde nicht gefunden." >&2
  exit 1
fi
IFS=$'\t' read -r authentik_media_type authentik_media_rw authentik_media_source authentik_media_extra \
  <<< "${authentik_media_mounts[0]}"
if [[ "${authentik_media_type}" != "bind" || "${authentik_media_rw}" != "true" ||
      -n "${authentik_media_extra}" ]]; then
  echo "Authentik-/media ist nicht der erwartete schreibbare Bind-Mount." >&2
  exit 1
fi
if [[ "${authentik_media_source}" == "/" || "${authentik_media_source}" != /* ||
      "${authentik_media_source}" == *$'\n'* || "${authentik_media_source}" == *$'\r'* ||
      "${authentik_media_source}" == *$'\t'* ]] ||
   path_has_symlink_component "${authentik_media_source}"; then
  echo "Der Authentik-/media-Bind-Mount ist kein sicherer absoluter Pfad." >&2
  exit 1
fi
credential_pipe_host="${authentik_media_source}/.lunara-authentik-credentials.pipe"
authentik_uid="$(docker exec "${authentik_container}" id -u)"
authentik_gid="$(docker exec "${authentik_container}" id -g)"
if [[ ! "${authentik_uid}" =~ ^[0-9]+$ || ! "${authentik_gid}" =~ ^[0-9]+$ ]]; then
  echo "Die numerische Authentik-Benutzeridentität konnte nicht bestimmt werden." >&2
  exit 1
fi
if [[ -e "${credential_pipe_host}" || -L "${credential_pipe_host}" ]]; then
  if [[ ! -p "${credential_pipe_host}" || -L "${credential_pipe_host}" ]]; then
    echo "Der feste Authentik-Zugangsdatenkanal ist kein sicherer FIFO." >&2
    exit 1
  fi
else
  mkfifo -m 0600 -- "${credential_pipe_host}"
  chown "${authentik_uid}:${authentik_gid}" -- "${credential_pipe_host}"
fi
credential_pipe_signature="$(stat -c '%u:%g:%a' -- "${credential_pipe_host}")"
if [[ "${credential_pipe_signature}" != "${authentik_uid}:${authentik_gid}:600" ]]; then
  echo "Der Authentik-Zugangsdatenkanal hat unerwartete Eigentümer oder Rechte." >&2
  exit 1
fi

python3 -c '
import os
import sys

destination = sys.argv[1]
descriptor = os.open(
    destination,
    os.O_WRONLY | os.O_CREAT | os.O_TRUNC | os.O_NOFOLLOW,
    0o600,
)
try:
    os.fchmod(descriptor, 0o600)
    while True:
        chunk = sys.stdin.buffer.read(65_536)
        if not chunk:
            break
        while chunk:
            written = os.write(descriptor, chunk)
            chunk = chunk[written:]
    os.fsync(descriptor)
finally:
    os.close(descriptor)
' "${credentials_file}" < "${credential_pipe_host}" &
credential_reader_pid=$!
if ! run_authentik_model apply "${preflight_state}"; then
  kill "${credential_reader_pid}" >/dev/null 2>&1 || true
  wait "${credential_reader_pid}" 2>/dev/null || true
  echo "Authentik-Konfiguration oder sichere Zugangsdatenausgabe fehlgeschlagen." >&2
  exit 1
fi
configuration_may_have_committed=true
if ! wait "${credential_reader_pid}"; then
  echo "Die Zugangsdaten konnten nicht sicher in die 0600-Zieldatei geschrieben werden." >&2
  exit 1
fi
if ! jq -e -s '
  length == 1 and
  .[0].username == "lunara-native-app" and
  (.[0].appPassword | type == "string" and length >= 20 and length <= 255 and test("^[A-Za-z0-9]+$")) and
  (.[0] | keys | sort) == ["appPassword", "username"]
' "${credentials_file}" >/dev/null; then
  echo "Authentik lieferte nach interner Validierung keine gültigen Einzel-Zugangsdaten." >&2
  print_authentik_rollback 2
  exit 1
fi
if ! python3 - "${credentials_file}" "${gateway_file}" "${public_host}" <<'PYTHON'
import json
import os
import sys
from urllib.parse import quote

credentials_path, gateway_path, public_host = sys.argv[1:]
with open(credentials_path, "r", encoding="utf-8") as credentials_file:
    credentials = json.load(credentials_file)
gateway_url = "https://{}:{}@{}\n".format(
    quote(credentials["username"], safe="-._~"),
    quote(credentials["appPassword"], safe="-._~"),
    public_host,
)
descriptor = os.open(
    gateway_path,
    os.O_WRONLY | os.O_CREAT | os.O_TRUNC | os.O_NOFOLLOW,
    0o600,
)
try:
    os.fchmod(descriptor, 0o600)
    payload = gateway_url.encode("ascii")
    while payload:
        written = os.write(descriptor, payload)
        payload = payload[written:]
    os.fsync(descriptor)
finally:
    os.close(descriptor)
PYTHON
then
  echo "Die geschützte Header-Auth-URL konnte nicht geschrieben werden." >&2
  print_authentik_rollback 2
  exit 1
fi
if [[ "$(stat -c '%a' -- "${credentials_file}")" != "600" ||
      "$(stat -c '%a' -- "${gateway_file}")" != "600" ]]; then
  echo "Die Zugangsdaten-Dateirechte sind nicht exakt 0600." >&2
  print_authentik_rollback 2
  exit 1
fi

configuration_may_have_committed=false
trap - EXIT

case "${preflight_state}" in
  create)
    echo "Authentik-Lunara wurde mit genau einem technischen App-Zugang und Vollschutz neu eingerichtet."
    ;;
  legacy_v102|legacy_v103)
    echo "Der exakt validierte Altstand wurde transaktional auf genau einen technischen App-Zugang und Vollschutz migriert."
    echo "Menschliche Authentik-Konten blieben unangetastet; sie sind nicht mehr Mitglied der Lunara-Gateway-Gruppe."
    echo "Die alten 0600-Zugangsdateien wurden in das datierte 0700-Archiv verschoben: ${legacy_files_archive}"
    echo "Die früheren A/B-Zugangsdaten sind serverseitig ungültig."
    ;;
  target)
    echo "Die vorhandene Einzel-App-Konfiguration stimmt exakt; keine Authentik-Datenbankänderung war nötig."
    ;;
esac
if [[ -n "${backup_path}" ]]; then
  echo "Konsistentes Backup vor der Änderung: ${backup_path}"
  print_authentik_rollback 1
fi
echo "Einzige aktive Header-Auth-URL (0600, nicht in Git): ${gateway_file}"
echo "Zugangsdaten-JSON (0600, nicht in Git): ${credentials_file}"
