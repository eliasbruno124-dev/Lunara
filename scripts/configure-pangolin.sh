#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

usage() {
  echo "Usage: $0 [--check]" >&2
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
  echo "LUNARA_CHECK_ONLY accepts only true or false." >&2
  exit 2
fi

pangolin_db="${PANGOLIN_DB:-/docker/pangolin/config/db/db.sqlite}"
backup_dir="${LUNARA_INFRA_BACKUP_DIR:-/var/backups/lunara/infrastructure}"
pangolin_container="${PANGOLIN_CONTAINER:-pangolin}"
public_host="${LUNARA_PUBLIC_HOST:-lunara.3li361.de}"
domain_id="${PANGOLIN_DOMAIN_ID:-domain1}"
site_id="${PANGOLIN_SITE_ID:-1}"
org_id="${PANGOLIN_ORG_ID:-mannheim}"
target_host="${LUNARA_AUTHENTIK_TARGET:-authentik-server}"
target_port="${LUNARA_AUTHENTIK_PORT:-9000}"

expected_pangolin_version="1.21.1"
expected_pangolin_revision="146c287aba43689d28f38b94523969ff71f2d5e1"
expected_user_version="0"
resource_owner_marker="lunara-authentik-proxy"
resource_name="Lunara"

expected_resources_schema="6b9b476bfdbe5f6f672435d3a4dc999aed805dd27449f4d38e568ff65fe3304b"
expected_targets_schema="c17c2ba266d9591307039c91d8ddff266ba3124dc60d6a1088b395806513f338"
expected_domains_schema="53e659ec444658b6a9db943a54674db9676ec10701d566d286994c8cb997a586"
expected_sites_schema="bbd709aade3a2042abefb69357f7bc19f071092608556218feeee60ea12b74bb"
expected_roles_schema="e4314dc91ad520f5aee282e9e1f87501bdaebef4664cc01234c36fd5baa9e7ff"
expected_role_resources_schema="fbdc4cb2a85b3ec6a65f466d33e0d9ffffdc9a72b059ff232ea9a74d17dd6853"

for command_name in docker sqlite3 sha256sum awk stat install mktemp date chmod; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Missing tool: ${command_name}" >&2
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

validate_host_labels() {
  local host="$1"
  local require_dot="$2"
  local label
  local -a labels
  if [[ ! "${host}" =~ ^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$ ]] ||
     [[ "${host}" == *..* ]] || (( ${#host} > 253 )); then
    return 1
  fi
  if [[ "${require_dot}" == "true" && "${host}" != *.* ]]; then
    return 1
  fi
  IFS='.' read -r -a labels <<< "${host}"
  for label in "${labels[@]}"; do
    if (( ${#label} == 0 || ${#label} > 63 )) ||
       [[ "${label}" == -* || "${label}" == *- ]]; then
      return 1
    fi
  done
  return 0
}

if [[ ! "${pangolin_container}" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]]; then
  echo "Invalid Pangolin container name." >&2
  exit 2
fi
if ! validate_host_labels "${public_host}" true ||
   ! validate_host_labels "${target_host}" false ||
   [[ ! "${domain_id}" =~ ^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$ ]] ||
   [[ ! "${org_id}" =~ ^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$ ]] ||
   [[ ! "${site_id}" =~ ^[1-9][0-9]{0,9}$ ]] ||
   [[ ! "${target_port}" =~ ^[1-9][0-9]{0,4}$ ]] ||
   (( 10#${site_id} > 2147483647 )) ||
   (( 10#${target_port} > 65535 )); then
  echo "Invalid Pangolin configuration." >&2
  exit 2
fi
if [[ "${pangolin_db}" != /* || ! "${pangolin_db}" =~ ^/[A-Za-z0-9_./-]+$ ]] ||
   [[ "${pangolin_db}" == */../* || "${pangolin_db}" == */.. ||
      "${pangolin_db}" == */./* || "${pangolin_db}" == */. ]] ||
   path_has_symlink_component "${pangolin_db}" ||
   [[ ! -f "${pangolin_db}" || -L "${pangolin_db}" || ! -r "${pangolin_db}" ]]; then
  echo "The Pangolin database path is unsafe or unreadable: ${pangolin_db}" >&2
  exit 2
fi

pangolin_version="$(docker inspect --type container --format '{{index .Config.Labels "org.opencontainers.image.version"}}' "${pangolin_container}")"
pangolin_revision="$(docker inspect --type container --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "${pangolin_container}")"
if [[ "${pangolin_version}" != "${expected_pangolin_version}" ]] ||
   [[ "${pangolin_revision}" != "${expected_pangolin_revision}" ]]; then
  echo "Unverified Pangolin image (version '${pangolin_version:-unbekannt}', revision '${pangolin_revision:-unbekannt}'); no changes made." >&2
  exit 1
fi
if [[ "$(docker inspect --type container --format '{{.State.Running}}' "${pangolin_container}")" != "true" ]]; then
  echo "The Pangolin container is not running; no changes made." >&2
  exit 1
fi

sql_read() {
  local database="$1"
  local statement="$2"
  sqlite3 -readonly -batch -noheader -cmd '.timeout 5000' "${database}" "${statement}"
}

schema_fingerprint() {
  local database="$1"
  local table="$2"
  sqlite3 -readonly -batch -noheader -separator '|' -cmd '.timeout 5000' \
    "${database}" "PRAGMA table_info(${table});" |
    sha256sum | awk '{print $1}'
}

verify_database_contract() {
  local database="$1"
  local actual
  local expected
  if [[ "$(sql_read "${database}" 'PRAGMA quick_check;')" != "ok" ]]; then
    echo "SQLite quick_check failed for ${database}." >&2
    return 1
  fi
  if [[ -n "$(sql_read "${database}" 'PRAGMA foreign_key_check;')" ]]; then
    echo "SQLite reports foreign key violations in ${database}." >&2
    return 1
  fi
  if [[ "$(sql_read "${database}" 'PRAGMA user_version;')" != "${expected_user_version}" ]]; then
    echo "Unverified Pangolin user_version in ${database}." >&2
    return 1
  fi
  for table in resources targets domains sites roles roleResources; do
    case "${table}" in
      resources) expected="${expected_resources_schema}" ;;
      targets) expected="${expected_targets_schema}" ;;
      domains) expected="${expected_domains_schema}" ;;
      sites) expected="${expected_sites_schema}" ;;
      roles) expected="${expected_roles_schema}" ;;
      roleResources) expected="${expected_role_resources_schema}" ;;
    esac
    actual="$(schema_fingerprint "${database}" "${table}")"
    if [[ "${actual}" != "${expected}" ]]; then
      echo "Unverified Pangolin schema for table ${table} (${actual:-leer}); no changes made." >&2
      return 1
    fi
  done
}

verify_database_contract "${pangolin_db}"

if [[ "$(sql_read "${pangolin_db}" "SELECT count(*) FROM domains WHERE domainId='${domain_id}' AND verified=1;")" != "1" ]] ||
   [[ "$(sql_read "${pangolin_db}" "SELECT count(*) FROM sites WHERE siteId=${site_id} AND orgId='${org_id}' AND online=1;")" != "1" ]]; then
  echo "The specified verified domain or local online site was not found unambiguously." >&2
  exit 1
fi

admin_role_id="$(sql_read "${pangolin_db}" "SELECT roleId FROM roles WHERE orgId='${org_id}' AND name='Admin' AND isAdmin=1;")"
if [[ "$(sql_read "${pangolin_db}" "SELECT count(*) FROM roles WHERE orgId='${org_id}' AND name='Admin' AND isAdmin=1;")" != "1" ]] ||
   [[ ! "${admin_role_id}" =~ ^[1-9][0-9]*$ ]]; then
  echo "The organization's unique Pangolin admin role was not found." >&2
  exit 1
fi

resource_exact_where="
  orgId='${org_id}'
  AND niceId='${resource_owner_marker}'
  AND name='${resource_name}'
  AND subdomain='${public_host%%.*}'
  AND fullDomain='${public_host}'
  AND domainId='${domain_id}'
  AND resourcePolicyId IS NULL
  AND defaultResourcePolicyId IS NULL
  AND ssl=1
  AND blockAccess=0
  AND proxyPort IS NULL
  AND sso=0
  AND emailWhitelistEnabled=0
  AND applyRules=0
  AND enabled=1
  AND stickySession=0
  AND tlsServerName IS NULL
  AND setHostHeader IS NULL
  AND enableProxy=1
  AND skipToIdpId IS NULL
  AND headers IS NULL
  AND proxyProtocol=0
  AND proxyProtocolVersion=1
  AND maintenanceModeEnabled=0
  AND maintenanceModeType='forced'
  AND maintenanceTitle IS NULL
  AND maintenanceMessage IS NULL
  AND maintenanceEstimatedTime IS NULL
  AND postAuthPath IS NULL
  AND wildcard=0
  AND mode='http'
  AND pamMode='passthrough'
  AND authDaemonMode='site'
  AND authDaemonPort=22123
  AND status='approved'"

target_exact_where="
  siteId=${site_id}
  AND ip='${target_host}'
  AND method='http'
  AND port=${target_port}
  AND internalPort IS NULL
  AND enabled=1
  AND path IS NULL
  AND pathMatchType IS NULL
  AND rewritePath IS NULL
  AND rewritePathType IS NULL
  AND priority=100
  AND mode='http'
  AND authToken IS NULL"

determine_resource_state() {
  local collision_count
  local exact_count
  local resource_id
  local resource_guid
  local target_count
  local exact_target_count
  local admin_link_count

  collision_count="$(sql_read "${pangolin_db}" "SELECT count(*) FROM resources WHERE fullDomain='${public_host}' OR niceId='${resource_owner_marker}' OR name='${resource_name}';")"
  if [[ "${collision_count}" == "0" ]]; then
    echo "create"
    return 0
  fi
  if [[ "${collision_count}" != "1" ]]; then
    echo "Ambiguous Pangolin resources collide with the host, ownership marker or name." >&2
    return 1
  fi
  exact_count="$(sql_read "${pangolin_db}" "SELECT count(*) FROM resources WHERE ${resource_exact_where};")"
  if [[ "${exact_count}" != "1" ]]; then
    echo "A colliding Pangolin resource does not have all exact Lunara security values; refusing to adopt it." >&2
    return 1
  fi
  resource_id="$(sql_read "${pangolin_db}" "SELECT resourceId FROM resources WHERE ${resource_exact_where};")"
  if [[ ! "${resource_id}" =~ ^[1-9][0-9]*$ ]]; then
    echo "The existing Lunara resource does not have a unique canonical ID/GUID." >&2
    return 1
  fi
  resource_guid="$(sql_read "${pangolin_db}" "SELECT resourceGuid FROM resources WHERE resourceId=${resource_id};")"
  if [[ ! "${resource_guid}" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$ ]]; then
    echo "The existing Lunara resource does not have a unique canonical ID/GUID." >&2
    return 1
  fi
  target_count="$(sql_read "${pangolin_db}" "SELECT count(*) FROM targets WHERE resourceId=${resource_id};")"
  exact_target_count="$(sql_read "${pangolin_db}" "SELECT count(*) FROM targets WHERE resourceId=${resource_id} AND ${target_exact_where};")"
  if [[ "${target_count}" != "1" || "${exact_target_count}" != "1" ]]; then
    echo "The existing Lunara resource does not have exactly the pinned Authentik target; refusing to adopt it." >&2
    return 1
  fi
  admin_link_count="$(sql_read "${pangolin_db}" "SELECT count(*) FROM roleResources WHERE resourceId=${resource_id} AND roleId=${admin_role_id};")"
  if [[ "${admin_link_count}" == "0" ]]; then
    printf 'repair:%s:%s\n' "${resource_id}" "${resource_guid}"
    return 0
  fi
  if [[ "${admin_link_count}" != "1" ]]; then
    echo "The existing Lunara resource has an ambiguous admin mapping; no changes made." >&2
    return 1
  fi
  printf 'existing:%s:%s\n' "${resource_id}" "${resource_guid}"
}

if ! resource_state="$(determine_resource_state)"; then
  exit 1
fi
if [[ "${resource_state}" != "create" && ! "${resource_state}" =~ ^(existing|repair):[1-9][0-9]*:[0-9a-f-]{36}$ ]]; then
  echo "Pangolin preflight did not return an unambiguous state; no changes made." >&2
  exit 1
fi

if [[ "${check_only}" == "true" ]]; then
  if [[ "${resource_state}" == "create" ]]; then
    echo "Pangolin check passed: the host, ownership marker and name are available; creation would be permitted."
  elif [[ "${resource_state}" == repair:* ]]; then
    echo "Pangolin check failed: the resource routes traffic but cannot be managed in the UI without an admin mapping. Running without --check repairs it safely." >&2
    exit 1
  else
    echo "Pangolin check passed: the ownership marker, resource, target and UI admin mapping match exactly."
  fi
  exit 0
fi

if [[ "${resource_state}" == existing:* ]]; then
  echo "The existing Pangolin Lunara resource matches exactly; no database changes were needed."
  exit 0
fi

if [[ ! -w "${pangolin_db}" ]]; then
  echo "The Pangolin database is not writable: ${pangolin_db}" >&2
  exit 1
fi
if [[ "${backup_dir}" == "/" || "${backup_dir}" != /* || ! "${backup_dir}" =~ ^/[A-Za-z0-9_./-]+$ ]] ||
   [[ "${backup_dir}" == */../* || "${backup_dir}" == */.. ||
      "${backup_dir}" == */./* || "${backup_dir}" == */. ]] ||
   path_has_symlink_component "${backup_dir}"; then
  echo "Unsafe backup path: ${backup_dir}" >&2
  exit 2
fi

install -d -m 0700 -- "${backup_dir}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_path="$(mktemp --tmpdir="${backup_dir}" "pangolin-before-lunara-${timestamp}.XXXXXX.sqlite")"
if ! sqlite3 -batch -cmd '.timeout 5000' "${pangolin_db}" ".backup '${backup_path}'" ||
   [[ ! -s "${backup_path}" ]]; then
  echo "Consistent Pangolin backup failed; no changes made." >&2
  exit 1
fi
chmod 0600 -- "${backup_path}"
verify_database_contract "${backup_path}"

database_uid="$(stat -c '%u' -- "${pangolin_db}")"
database_gid="$(stat -c '%g' -- "${pangolin_db}")"
database_mode="$(stat -c '%a' -- "${pangolin_db}")"
if [[ ! "${database_uid}" =~ ^[0-9]+$ || ! "${database_gid}" =~ ^[0-9]+$ ||
      ! "${database_mode}" =~ ^[0-7]{3,4}$ ]]; then
  echo "The Pangolin database file ownership or mode could not be determined safely." >&2
  exit 1
fi

IFS= read -r resource_guid < /proc/sys/kernel/random/uuid
if [[ "${resource_state}" == repair:* ]]; then
  resource_id="${resource_state#repair:}"
  resource_id="${resource_id%%:*}"
  resource_guid="${resource_state##*:}"
fi
if [[ ! "${resource_guid}" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$ ]] ||
   { [[ "${resource_state}" == repair:* ]] && [[ ! "${resource_id}" =~ ^[1-9][0-9]*$ ]]; }; then
  echo "A canonical random resource GUID could not be generated." >&2
  exit 1
fi

if [[ "${resource_state}" == repair:* ]]; then
  if ! sqlite3 -batch -cmd '.timeout 5000' "${pangolin_db}" <<SQL
.bail on
PRAGMA foreign_keys=ON;
BEGIN IMMEDIATE;
CREATE TEMP TABLE lunara_admin_repair_guard (ok INTEGER NOT NULL CHECK (ok=1));
INSERT INTO lunara_admin_repair_guard VALUES (
  CASE WHEN
    (SELECT count(*) FROM resources WHERE resourceId=${resource_id} AND resourceGuid='${resource_guid}' AND ${resource_exact_where})=1
    AND (SELECT count(*) FROM targets WHERE resourceId=${resource_id} AND ${target_exact_where})=1
    AND (SELECT count(*) FROM roles WHERE roleId=${admin_role_id} AND orgId='${org_id}' AND name='Admin' AND isAdmin=1)=1
    AND (SELECT count(*) FROM roleResources WHERE resourceId=${resource_id} AND roleId=${admin_role_id})=0
  THEN 1 ELSE 0 END
);
INSERT INTO roleResources (roleId, resourceId) VALUES (${admin_role_id}, ${resource_id});
DELETE FROM lunara_admin_repair_guard;
INSERT INTO lunara_admin_repair_guard VALUES (
  CASE WHEN (SELECT count(*) FROM roleResources WHERE resourceId=${resource_id} AND roleId=${admin_role_id})=1
  THEN 1 ELSE 0 END
);
DROP TABLE lunara_admin_repair_guard;
COMMIT;
SQL
  then
    echo "Atomic Pangolin admin repair failed; the transaction was discarded. Backup: ${backup_path}" >&2
    exit 1
  fi
  verify_database_contract "${pangolin_db}"
  if ! resource_state="$(determine_resource_state)" || [[ "${resource_state}" != "existing:${resource_id}:${resource_guid}" ]]; then
    echo "Pangolin verification after admin repair failed. Backup: ${backup_path}" >&2
    exit 1
  fi
  echo "Pangolin resource ${public_host} can now be managed through the admin role in the UI."
  echo "Consistent backup before the change: ${backup_path}"
  exit 0
fi

print_rollback_hints() {
  local output_fd="${1:-2}"
  echo "Targeted rollback (Pangolin must be stopped in a controlled manner first):" >&"${output_fd}"
  printf 'sqlite3 %q %q\n' "${pangolin_db}" \
    "PRAGMA foreign_keys=ON; BEGIN IMMEDIATE; CREATE TEMP TABLE lunara_rollback_guard(ok INTEGER NOT NULL CHECK(ok=1)); INSERT INTO lunara_rollback_guard VALUES(CASE WHEN (SELECT count(*) FROM resources WHERE resourceGuid='${resource_guid}' AND niceId='${resource_owner_marker}' AND fullDomain='${public_host}')=1 THEN 1 ELSE 0 END); DELETE FROM resources WHERE resourceGuid='${resource_guid}' AND niceId='${resource_owner_marker}' AND fullDomain='${public_host}'; DROP TABLE lunara_rollback_guard; COMMIT;" >&"${output_fd}"
  echo "Full restore (discards later Pangolin changes; stop Pangolin in a controlled manner first):" >&"${output_fd}"
  printf 'sqlite3 %q %q && chown %q:%q %q && chmod %q %q\n' \
    "${pangolin_db}" ".restore '${backup_path}'" \
    "${database_uid}" "${database_gid}" "${pangolin_db}" \
    "${database_mode}" "${pangolin_db}" >&"${output_fd}"
}

mutation_committed=false
on_error() {
  local status=$?
  if [[ "${mutation_committed}" == "true" ]]; then
    echo "Error after the Pangolin commit. Backup: ${backup_path}" >&2
    print_rollback_hints
  fi
  exit "${status}"
}
trap on_error ERR

if ! sqlite3 -batch -cmd '.timeout 5000' "${pangolin_db}" <<SQL
.bail on
PRAGMA foreign_keys=ON;
BEGIN IMMEDIATE;
CREATE TEMP TABLE lunara_preflight_guard (ok INTEGER NOT NULL CHECK (ok=1));
INSERT INTO lunara_preflight_guard VALUES (
  CASE WHEN
    (SELECT count(*) FROM domains WHERE domainId='${domain_id}' AND verified=1)=1
    AND (SELECT count(*) FROM sites WHERE siteId=${site_id} AND orgId='${org_id}' AND online=1)=1
    AND (SELECT count(*) FROM roles WHERE roleId=${admin_role_id} AND orgId='${org_id}' AND name='Admin' AND isAdmin=1)=1
    AND (SELECT count(*) FROM resources WHERE fullDomain='${public_host}' OR niceId='${resource_owner_marker}' OR name='${resource_name}')=0
    AND (SELECT user_version FROM pragma_user_version)=${expected_user_version}
  THEN 1 ELSE 0 END
);
INSERT INTO resources (
  resourceGuid, orgId, niceId, name, subdomain, fullDomain, domainId,
  ssl, blockAccess, proxyPort, sso, emailWhitelistEnabled, applyRules,
  enabled, stickySession, tlsServerName, setHostHeader, enableProxy,
  skipToIdpId, headers, proxyProtocol, proxyProtocolVersion,
  maintenanceModeEnabled, maintenanceModeType, maintenanceTitle,
  maintenanceMessage, maintenanceEstimatedTime, postAuthPath, wildcard,
  mode, pamMode, authDaemonMode, authDaemonPort, status
) VALUES (
  '${resource_guid}', '${org_id}', '${resource_owner_marker}', '${resource_name}',
  '${public_host%%.*}', '${public_host}', '${domain_id}',
  1, 0, NULL, 0, 0, 0, 1, 0, NULL, NULL, 1, NULL, NULL, 0, 1,
  0, 'forced', NULL, NULL, NULL, NULL, 0,
  'http', 'passthrough', 'site', 22123, 'approved'
);
INSERT INTO targets (
  resourceId, siteId, ip, method, port, internalPort, enabled, path,
  pathMatchType, rewritePath, rewritePathType, priority, mode, authToken
) VALUES (
  last_insert_rowid(), ${site_id}, '${target_host}', 'http', ${target_port},
  NULL, 1, NULL, NULL, NULL, NULL, 100, 'http', NULL
);
INSERT INTO roleResources (roleId, resourceId)
SELECT ${admin_role_id}, resourceId FROM resources WHERE resourceGuid='${resource_guid}';
DELETE FROM lunara_preflight_guard;
INSERT INTO lunara_preflight_guard VALUES (
  CASE WHEN
    (SELECT count(*) FROM resources WHERE resourceGuid='${resource_guid}' AND ${resource_exact_where})=1
    AND (SELECT count(*) FROM targets WHERE resourceId=(SELECT resourceId FROM resources WHERE resourceGuid='${resource_guid}') AND ${target_exact_where})=1
    AND (SELECT count(*) FROM targets t JOIN resources r ON r.resourceId=t.resourceId WHERE r.resourceGuid='${resource_guid}')=1
    AND (SELECT count(*) FROM roleResources rr JOIN resources r ON r.resourceId=rr.resourceId WHERE r.resourceGuid='${resource_guid}' AND rr.roleId=${admin_role_id})=1
  THEN 1 ELSE 0 END
);
DROP TABLE lunara_preflight_guard;
COMMIT;
SQL
then
  echo "Atomic Pangolin configuration failed; the transaction was discarded. Backup: ${backup_path}" >&2
  exit 1
fi
mutation_committed=true

verify_database_contract "${pangolin_db}"
if ! resource_state="$(determine_resource_state)" || [[ "${resource_state}" != "existing:"*":${resource_guid}" ]]; then
  echo "Pangolin verification after the commit failed. Backup: ${backup_path}" >&2
  print_rollback_hints
  mutation_committed=false
  exit 1
fi

mutation_committed=false
trap - ERR
echo "Pangolin resource ${public_host} -> ${target_host}:${target_port} was configured with an ownership marker."
echo "Consistent backup before the change: ${backup_path}"
print_rollback_hints 1
