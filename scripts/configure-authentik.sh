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
owner_marker="lunara-authentik-gateway-v1"

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

run_authentik_model_check() {
  local action="$1"
  local expected_state="$2"
  docker exec -i \
    -e LUNARA_ACTION="${action}" \
    -e LUNARA_EXPECTED_STATE="${expected_state}" \
    -e LUNARA_PUBLIC_HOST="${public_host}" \
    -e LUNARA_INTERNAL_URL="${internal_host}" \
    -e LUNARA_OWNER_MARKER="${owner_marker}" \
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
OWNER = os.environ["LUNARA_OWNER_MARKER"]
OWNER_KEY = "lunara.infrastructure.owner"
OWNER_ATTRIBUTES = {OWNER_KEY: OWNER}
PROVIDER_NAME = "Lunara Proxy"
APPLICATION_NAME = "Lunara"
APPLICATION_SLUG = "lunara"
APPLICATION_GROUP = "Privat"
APPLICATION_DESCRIPTION = "Private selbst gehostete Zyklusbegleitung"
GROUP_NAME = "Lunara Gateway"
USER_PATH = "service-accounts/lunara"
TOKEN_DESCRIPTION = f"Lunara native app header authentication [{OWNER}]"
EXTERNAL_HOST = f"https://{PUBLIC_HOST}"


def fail(message):
    raise RuntimeError(f"Lunara Authentik preflight failed: {message}")


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
        )
        .values_list("pk", flat=True)
    )
    if len(scopes) != 5:
        fail("the five pinned managed proxy scope mappings are not available")
    return flows, outpost, scopes


def lock_configuration_namespace():
    with connection.cursor() as cursor:
        cursor.execute("SELECT pg_advisory_xact_lock(%s)", [781334920250513])


def named_objects_state():
    providers = list(Provider.objects.filter(name=PROVIDER_NAME))
    host_provider_ids = set(
        ProxyProvider.objects.filter(external_host=EXTERNAL_HOST).values_list("pk", flat=True)
    ) | set(
        ProxyProvider.objects.filter(internal_host=INTERNAL_HOST).values_list("pk", flat=True)
    )
    apps_by_slug = list(Application.objects.filter(slug=APPLICATION_SLUG))
    apps_by_name = list(Application.objects.filter(name=APPLICATION_NAME))
    launch_application_ids = set(
        Application.objects.filter(meta_launch_url=EXTERNAL_HOST).values_list("pk", flat=True)
    )
    groups = list(Group.objects.filter(name=GROUP_NAME))
    users = {
        suffix: list(User.objects.filter(username=f"lunara-gateway-{suffix}"))
        for suffix in ("a", "b")
    }
    tokens = {
        suffix: list(
            Token.objects.including_expired().filter(
                identifier=f"lunara-gateway-{suffix}-app-password"
            )
        )
        for suffix in ("a", "b")
    }
    collections = [providers, apps_by_slug, apps_by_name, groups, *users.values(), *tokens.values()]
    if all(len(items) == 0 for items in collections):
        if host_provider_ids or launch_application_ids:
            fail("an differently named provider or application already uses a Lunara host")
        return "create", None
    if any(len(items) != 1 for items in collections):
        fail("partial, duplicate, or colliding named objects exist")
    if apps_by_slug[0].pk != apps_by_name[0].pk:
        fail("application name and slug refer to different objects")
    provider = ProxyProvider.objects.filter(pk=providers[0].pk).first()
    if provider is None:
        fail("the provider name belongs to a different provider type")
    if host_provider_ids != {provider.pk} or launch_application_ids != {apps_by_slug[0].pk}:
        fail("another provider or application collides with a Lunara host")
    return "existing", {
        "provider": provider,
        "application": apps_by_slug[0],
        "group": groups[0],
        "users": {suffix: items[0] for suffix, items in users.items()},
        "tokens": {suffix: items[0] for suffix, items in tokens.items()},
    }


def verify_owned(objects, flows, outpost, expected_scopes):
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
        "skip_path_regex": "",
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
        "meta_publisher": OWNER,
        "meta_hide": False,
        "policy_engine_mode": "any",
    }
    for field, expected in expected_application_values.items():
        if getattr(application, field) != expected:
            fail(f"application field {field} does not match the pinned value or owner marker")
    if str(application.meta_icon) != "":
        fail("application has an unexpected icon override")

    if group.is_superuser or group.attributes != OWNER_ATTRIBUTES:
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

    credentials = {}
    expected_user_ids = {objects["users"][suffix].pk for suffix in ("a", "b")}
    if set(group.users.values_list("pk", flat=True)) != expected_user_ids:
        fail("gateway group contains users outside the two owned service accounts")
    for suffix in ("a", "b"):
        username = f"lunara-gateway-{suffix}"
        user = objects["users"][suffix]
        token = objects["tokens"][suffix]
        expected_name = f"Lunara Gateway {suffix.upper()}"
        if (
            user.username != username
            or user.name != expected_name
            or user.type != UserTypes.SERVICE_ACCOUNT
            or user.path != USER_PATH
            or not user.is_active
            or user.is_staff
            or user.is_superuser
            or user.email != ""
            or user.attributes != OWNER_ATTRIBUTES
            or user.has_usable_password()
        ):
            fail(f"service account {suffix} is not exactly Lunara-owned and locked down")
        if set(user.groups.values_list("pk", flat=True)) != {group.pk}:
            fail(f"service account {suffix} has unexpected direct group memberships")
        if user.roles.exists() or user.sources.exists():
            fail(f"service account {suffix} has unexpected roles or source relationships")
        if (
            token.user_id != user.pk
            or token.intent != TokenIntents.INTENT_APP_PASSWORD
            or token.description != TOKEN_DESCRIPTION
            or token.expiring
            or token.expires is not None
            or not isinstance(token.key, str)
            or len(token.key) < 20
            or len(token.key) > 255
            or not token.key.isalnum()
        ):
            fail(f"app password {suffix} does not match the exact owned token contract")
        credentials[suffix] = {"username": username, "appPassword": token.key}
    if credentials["a"]["appPassword"] == credentials["b"]["appPassword"]:
        fail("the two app passwords are unexpectedly identical")
    return credentials


def create_owned(flows, outpost):
    state, _ = named_objects_state()
    if state != "create":
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
        meta_publisher=OWNER,
        meta_hide=False,
        policy_engine_mode="any",
    )
    group = Group.objects.create(
        name=GROUP_NAME,
        is_superuser=False,
        attributes=OWNER_ATTRIBUTES,
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

    for suffix in ("a", "b"):
        username = f"lunara-gateway-{suffix}"
        user = User(
            username=username,
            name=f"Lunara Gateway {suffix.upper()}",
            type=UserTypes.SERVICE_ACCOUNT,
            path=USER_PATH,
            is_active=True,
            attributes=OWNER_ATTRIBUTES,
        )
        user.set_unusable_password()
        user.save()
        group.users.add(user)
        Token.objects.create(
            identifier=f"{username}-app-password",
            user=user,
            intent=TokenIntents.INTENT_APP_PASSWORD,
            description=TOKEN_DESCRIPTION,
            expiring=False,
            expires=None,
        )


if ACTION not in {"check", "apply"}:
    fail("invalid action")
flows, outpost, expected_scopes = dependencies()
if ACTION == "check":
    with transaction.atomic():
        lock_configuration_namespace()
        state, objects = named_objects_state()
        if state == "existing":
            verify_owned(objects, flows, outpost, expected_scopes)
    print(f"LUNARA_AUTHENTIK_PREFLIGHT={state}")
else:
    if EXPECTED_STATE not in {"create", "existing"}:
        fail("invalid expected state")
    with transaction.atomic():
        lock_configuration_namespace()
        state, objects = named_objects_state()
        if state != EXPECTED_STATE:
            fail("named object state changed after preflight")
        if state == "create":
            create_owned(flows, outpost)
            state, objects = named_objects_state()
        credentials = verify_owned(objects, flows, outpost, expected_scopes)
    print("LUNARA_GATEWAY_JSON=" + json.dumps(credentials, separators=(",", ":")))
PYTHON
}

if ! preflight_output="$(run_authentik_model_check check none)"; then
  echo "Authentik-Preflight fehlgeschlagen; keine Änderung vorgenommen." >&2
  exit 1
fi
preflight_state="$(sed -n 's/^LUNARA_AUTHENTIK_PREFLIGHT=//p' <<< "${preflight_output}" | tail -n 1)"
unset preflight_output
if [[ "${preflight_state}" != "create" && "${preflight_state}" != "existing" ]]; then
  echo "Authentik-Preflight lieferte keinen eindeutigen Zustand; keine Änderung vorgenommen." >&2
  exit 1
fi
if [[ "${check_only}" == "true" ]]; then
  if [[ "${preflight_state}" == "create" ]]; then
    echo "Authentik-Prüfung erfolgreich: alle Lunara-Namen sind frei; eine Neuanlage wäre zulässig."
  else
    echo "Authentik-Prüfung erfolgreich: alle vorhandenen Lunara-Objekte, Marker und Sicherheitswerte stimmen exakt."
  fi
  exit 0
fi

for command_name in jq mktemp stat install; do
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

credentials_file="${runtime_dir}/authentik-gateway-credentials.json"
gateway_a_file="${runtime_dir}/gateway-a.url"
gateway_b_file="${runtime_dir}/gateway-b.url"
config_log="${runtime_dir}/authentik-lunara-config.log"
for output_file in "${credentials_file}" "${gateway_a_file}" "${gateway_b_file}" "${config_log}"; do
  if [[ -e "${output_file}" && ( ! -f "${output_file}" || -L "${output_file}" || ! -O "${output_file}" ) ]]; then
    echo "Unsicheres vorhandenes Ausgabeziel: ${output_file}" >&2
    exit 1
  fi
done

backup_path=""
if [[ "${preflight_state}" == "create" ]]; then
  timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
  backup_path="$(mktemp --tmpdir="${backup_dir}" "authentik-before-lunara-${timestamp}.XXXXXX.dump")"
  if ! docker exec "${authentik_db_container}" sh -ceu \
      'pg_dump --format=custom --no-owner --no-acl --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"' \
      > "${backup_path}" || [[ ! -s "${backup_path}" ]]; then
    echo "Authentik-Backup fehlgeschlagen; keine Änderung vorgenommen." >&2
    exit 1
  fi
  chmod 0600 -- "${backup_path}"
  if ! docker exec -i "${authentik_db_container}" pg_restore --list < "${backup_path}" >/dev/null; then
    echo "Authentik-Backup ist nicht als PostgreSQL-Custom-Dump lesbar; keine Änderung vorgenommen." >&2
    exit 1
  fi
fi

print_authentik_rollback() {
  local output_fd="${1:-2}"
  if [[ -n "${backup_path}" ]]; then
    echo "Vollständiger Datenbank-Rollback; er verwirft alle Authentik-Änderungen seit dem Backup." >&"${output_fd}"
    printf 'Rollback nach kontrolliertem Stop von Authentik: docker exec -i %q sh -ceu '\''pg_restore --clean --if-exists --no-owner --no-acl --single-transaction --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"'\'' < %q\n' \
      "${authentik_db_container}" "${backup_path}" >&"${output_fd}"
  fi
}

work_dir="$(mktemp -d "${runtime_dir}/.authentik-lunara.XXXXXX")"
cleanup() {
  if [[ -n "${work_dir:-}" && "${work_dir}" == "${runtime_dir}"/.authentik-lunara.* ]]; then
    rm -rf -- "${work_dir}"
  fi
}
trap cleanup EXIT

configuration_committed=false
on_postapply_error() {
  local status=$?
  if [[ "${configuration_committed}" == "true" ]]; then
    echo "Fehler nach dem Authentik-Commit; die Zugangsdatenausgabe ist möglicherweise unvollständig." >&2
    print_authentik_rollback 2
  fi
  exit "${status}"
}
trap on_postapply_error ERR

temporary_log="${work_dir}/authentik-config.log"
if ! apply_output="$(run_authentik_model_check apply "${preflight_state}" 2> "${temporary_log}")"; then
  install -m 0600 -- "${temporary_log}" "${config_log}"
  echo "Authentik-Konfiguration fehlgeschlagen; Details stehen in ${config_log}." >&2
  print_authentik_rollback 2
  exit 1
fi
if [[ "${preflight_state}" == "create" ]]; then
  configuration_committed=true
fi
gateway_json="$(sed -n 's/^LUNARA_GATEWAY_JSON=//p' <<< "${apply_output}" | tail -n 1)"
unset apply_output
if ! jq -e '
  .a.username == "lunara-gateway-a" and
  (.a.appPassword | type == "string" and length >= 20) and
  .b.username == "lunara-gateway-b" and
  (.b.appPassword | type == "string" and length >= 20)
' <<< "${gateway_json}" >/dev/null; then
  install -m 0600 -- "${temporary_log}" "${config_log}"
  echo "Authentik lieferte nach interner Validierung keine gültigen Zugangsdaten." >&2
  print_authentik_rollback 2
  exit 1
fi

printf '%s\n' "${gateway_json}" > "${work_dir}/credentials.json"
unset gateway_json
jq -r --arg host "${public_host}" \
  '"https://\(.a.username|@uri):\(.a.appPassword|@uri)@\($host)"' \
  "${work_dir}/credentials.json" > "${work_dir}/gateway-a.url"
jq -r --arg host "${public_host}" \
  '"https://\(.b.username|@uri):\(.b.appPassword|@uri)@\($host)"' \
  "${work_dir}/credentials.json" > "${work_dir}/gateway-b.url"
install -m 0600 -- "${work_dir}/credentials.json" "${credentials_file}"
install -m 0600 -- "${work_dir}/gateway-a.url" "${gateway_a_file}"
install -m 0600 -- "${work_dir}/gateway-b.url" "${gateway_b_file}"
install -m 0600 -- "${temporary_log}" "${config_log}"

configuration_committed=false
trap - ERR

if [[ "${preflight_state}" == "create" ]]; then
  echo "Authentik-Lunara-Objekte wurden neu und eigentumsmarkiert eingerichtet."
  echo "Konsistentes Backup vor der Änderung: ${backup_path}"
  print_authentik_rollback 1
else
  echo "Vorhandene Authentik-Lunara-Objekte stimmen exakt; keine Authentik-Datenbankänderung war nötig."
fi
echo "Zugangsdaten (0600, nicht in Git): ${gateway_a_file} und ${gateway_b_file}"
