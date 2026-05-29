#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOOK_OUTPUT_DIR="${HOOK_OUTPUT_DIR:-/usr/openv/netbackup/bin}"
CONFIG_OUTPUT_DIR="${CONFIG_OUTPUT_DIR:-/etc/ablestack/netbackup}"
SECRET_OUTPUT_DIR="${SECRET_OUTPUT_DIR:-/etc/ablestack/netbackup/secrets}"
BACKUP_STAGING_ROOT="${BACKUP_STAGING_ROOT:-/tmp/mold/netbackup}"
PRE_HELPER_PATH="${PRE_HELPER_PATH:-/usr/share/cloudstack-common/scripts/vm/hypervisor/kvm/ablestack_netbackup_bpstart_notify.sh}"
POST_HELPER_PATH="${POST_HELPER_PATH:-/usr/share/cloudstack-common/scripts/vm/hypervisor/kvm/ablestack_netbackup_bpend_notify.sh}"
HOOK_LOG_PATH="${HOOK_LOG_PATH:-/var/log/netbackup-mold-hook.log}"
SECRET_CIPHER="${SECRET_CIPHER:-aes-256-cbc}"
SECRET_KEY_FILE="${SECRET_KEY_FILE:-/root/.ssh/ablestack.key}"
NETBACKUP_BP_CONF_PATH="${NETBACKUP_BP_CONF_PATH:-/usr/openv/netbackup/bp.conf}"
NETBACKUP_SERVICE_NAME="${NETBACKUP_SERVICE_NAME:-netbackup}"

POLICY_NAME=""
VM_INCLUDE=""
VM_EXCLUDE=""
MAX_INCREMENTAL_CHAIN=""
MOLD_URL=""
ADMIN_APIKEY=""
ADMIN_SECRETKEY=""
NETBACKUP_URL=""
NETBACKUP_APIKEY=""
MOLD_API_RESPONSE_FORMAT="json"
NETBACKUP_PROVIDER_DISPLAY_NAME="netbackup"
NETBACKUP_PROVIDER_CANONICAL_NAME="ablestack-netbackup"
NETBACKUP_OFFERING_NAME="netbackup"
NETBACKUP_OFFERING_DESCRIPTION="netbackup"
NETBACKUP_OFFERING_EXTERNAL_ID="netbackup"
ZONE_ID=""

usage() {
  cat <<EOF
Usage: $(basename "$0")

Interactive generator for NetBackup policy hook files and config files.
This generator applies one NetBackup policy to all schedules under that policy.

Default output targets:
  Hook files   -> /usr/openv/netbackup/bin
  Config files -> /etc/ablestack/netbackup
  Secret files -> /etc/ablestack/netbackup/secrets

Environment overrides:
  HOOK_OUTPUT_DIR
  CONFIG_OUTPUT_DIR
  SECRET_OUTPUT_DIR
  BACKUP_STAGING_ROOT
  PRE_HELPER_PATH
  POST_HELPER_PATH
  HOOK_LOG_PATH
  SECRET_CIPHER
  SECRET_KEY_FILE
  NETBACKUP_BP_CONF_PATH
  NETBACKUP_SERVICE_NAME
EOF
}

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

log_info() {
  printf '%s\n' "$*"
}

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

json_extract() {
  local payload="$1"
  local script="$2"

  python3 - "$payload" "$script" <<'PY'
import json
import sys

payload = sys.argv[1]
script = sys.argv[2]

try:
    data = json.loads(payload)
except Exception as exc:
    preview = payload.strip().replace("\n", "\\n")
    if len(preview) > 240:
        preview = preview[:240] + "..."
    sys.stderr.write(f"Failed to parse Mold API response as JSON: {exc}\n")
    sys.stderr.write(f"Response preview: {preview}\n")
    sys.exit(1)

namespace = {"data": data}
exec(script, {}, namespace)
result = namespace.get("result", "")
if result is None:
    result = ""
if isinstance(result, bool):
    print("true" if result else "false", end="")
elif isinstance(result, (dict, list)):
    print(json.dumps(result), end="")
else:
    print(str(result), end="")
PY
}

file_mode() {
  local target="$1"
  if stat -c '%a' "${target}" >/dev/null 2>&1; then
    stat -c '%a' "${target}"
    return 0
  fi
  stat -f '%OLp' "${target}"
}

set_bp_conf_value() {
  local file_path="$1"
  local key="$2"
  local value="$3"

  if grep -Eq "^[[:space:]]*${key}[[:space:]]*=" "${file_path}"; then
    sed -i'' -E "s|^[[:space:]]*${key}[[:space:]]*=.*$|${key} = ${value}|" "${file_path}"
  else
    printf '%s = %s\n' "${key}" "${value}" >> "${file_path}"
  fi
}

prompt_value() {
  local var_name="$1"
  local prompt_text="$2"
  local default_value="${3:-}"
  local value=""

  if [[ -n "${default_value}" ]]; then
    read -r -p "${prompt_text} [${default_value}]: " value
    value="${value:-${default_value}}"
  else
    read -r -p "${prompt_text}: " value
  fi

  printf -v "${var_name}" '%s' "${value}"
}

prompt_secret_value() {
  local var_name="$1"
  local prompt_text="$2"
  local value=""
  read -r -s -p "${prompt_text}: " value
  printf '\n'
  printf -v "${var_name}" '%s' "${value}"
}

validate_name() {
  local name="$1"
  local label="$2"

  [[ -n "${name}" ]] || fail "${label} is required."
  [[ "${name}" != *"/"* ]] || fail "${label} must not contain '/'."
}

validate_positive_integer() {
  local value="$1"
  local label="$2"

  [[ "${value}" =~ ^[1-9][0-9]*$ ]] || fail "${label} must be a positive integer."
}

validate_secret_key_file() {
  [[ -f "${SECRET_KEY_FILE}" ]] || fail "Secret key file not found: ${SECRET_KEY_FILE}"
  [[ -r "${SECRET_KEY_FILE}" ]] || fail "Secret key file is not readable: ${SECRET_KEY_FILE}"

  local mode
  mode="$(file_mode "${SECRET_KEY_FILE}")"
  [[ "${mode}" == "600" ]] || fail "Secret key file must have permission 600: ${SECRET_KEY_FILE} (current: ${mode})"
}

validate_prerequisites() {
  command_exists python3 || fail "python3 command is required."
  command_exists curl || fail "curl command is required."
  command_exists openssl || fail "openssl command is required."
}

url_encode() {
  local value="$1"
  python3 - "$value" <<'PY'
import sys
from urllib.parse import quote_plus

print(quote_plus(sys.argv[1]), end="")
PY
}

build_mold_api_params() {
  local command_name="$1"
  shift

  local param_string="command=$(url_encode "${command_name}")"
  while [[ $# -gt 1 ]]; do
    local key="$1"
    local value="$2"
    shift 2
    param_string="${param_string}&${key}=$(url_encode "${value}")"
  done

  param_string="${param_string}&response=$(url_encode "${MOLD_API_RESPONSE_FORMAT}")"
  printf '%s' "${param_string}"
}

build_mold_signed_url() {
  local base_url="$1"
  local api_params="$2"

  python3 - "$base_url" "$api_params" "$ADMIN_APIKEY" "$ADMIN_SECRETKEY" <<'PY'
import base64
import hashlib
import hmac
import sys
from urllib.parse import quote_plus

base_url = sys.argv[1]
api_params = sys.argv[2]
api_key = sys.argv[3]
secret_key = sys.argv[4]

sorted_params = [f"apikey={quote_plus(api_key).lower()}"]
for token in api_params.split("&"):
    key, value = token.split("=", 1)
    sorted_params.append(f"{key.lower()}={value.lower()}")

sorted_url = "&".join(sorted(sorted_params))
signature = base64.b64encode(
    hmac.new(secret_key.encode(), sorted_url.encode(), hashlib.sha256).digest()
).decode()
encoded_signature = quote_plus(signature)
final_url = f"{base_url}?{api_params}&apiKey={quote_plus(api_key)}&signature={encoded_signature}"
print(final_url, end="")
PY
}

invoke_mold_api() {
  local method="$1"
  local command_name="$2"
  shift 2

  local api_params
  local signed_url
  local response

  log_info "Calling Mold API: command=${command_name} method=${method}"
  api_params="$(build_mold_api_params "${command_name}" "$@")"
  signed_url="$(build_mold_signed_url "${MOLD_URL}" "${api_params}")" || fail "Failed to build Mold API URL for ${command_name}"

  response="$(curl --silent --show-error --fail \
    -X "${method}" \
    -H "Accept: application/json" \
    -H "Content-type: application/x-www-form-urlencoded" \
    "${signed_url}")" || fail "Mold API call failed: command=${command_name} method=${method} url=${MOLD_URL}"

  [[ -n "${response}" ]] || fail "Mold API returned empty response: command=${command_name} method=${method} url=${MOLD_URL}"

  printf '%s' "${response}"
}

get_configuration_value() {
  local config_name="$1"
  local zone_id="${2:-}"
  local response=""

  if [[ -n "${zone_id}" ]]; then
    response="$(invoke_mold_api GET "listConfigurations" \
      listAll true pagesize 20 page 1 name "${config_name}" zoneid "${zone_id}")"
  else
    response="$(invoke_mold_api GET "listConfigurations" \
      listAll true pagesize 20 page 1 name "${config_name}")"
  fi

  json_extract "${response}" '
configs = data.get("listconfigurationsresponse", {}).get("configuration", [])
if isinstance(configs, dict):
    configs = [configs]
for cfg in configs:
    if str(cfg.get("name", "")).lower() == "'"${config_name,,}"'":
        result = cfg.get("value", "")
        break
else:
    result = ""
'
}

update_configuration_value() {
  local config_name="$1"
  local config_value="$2"
  local zone_id="${3:-}"

  if [[ -n "${zone_id}" ]]; then
    invoke_mold_api GET "updateConfiguration" name "${config_name}" value "${config_value}" zoneid "${zone_id}" >/dev/null
  else
    invoke_mold_api GET "updateConfiguration" name "${config_name}" value "${config_value}" >/dev/null
  fi
}

append_provider_if_missing() {
  local provider_list="$1"
  local provider_name="$2"

  python3 - "$provider_list" "$provider_name" <<'PY'
import sys

provider_list = sys.argv[1]
provider_name = sys.argv[2]

items = [item.strip() for item in provider_list.split(",") if item.strip()]
lower = {item.lower() for item in items}
if provider_name.lower() not in lower:
    items.append(provider_name)
print(",".join(items), end="")
PY
}

resolve_zone_id_from_policy_name() {
  local response=""
  log_info "Resolving zone ID from listHosts using policy/host name=${POLICY_NAME}"
  response="$(invoke_mold_api GET "listHosts" \
    listAll true pagesize 500 page 1 type Routing keyword "${POLICY_NAME}")"

  ZONE_ID="$(json_extract "${response}" '
hosts = data.get("listhostsresponse", {}).get("host", [])
if isinstance(hosts, dict):
    hosts = [hosts]
target = "'"${POLICY_NAME}"'".lower()
exact = None
for host in hosts:
    host_name = str(host.get("name", "")).lower()
    if host_name == target:
        exact = host
        break
if exact is None and hosts:
    exact = hosts[0]
result = exact.get("zoneid", "") if exact else ""
')"

  [[ -n "${ZONE_ID}" ]] || fail "Unable to resolve zone ID from host/policy name '${POLICY_NAME}' via listHosts."
  log_info "Resolved zone ID: ${ZONE_ID}"
}

ensure_backup_framework_configuration() {
  local current_value=""
  local updated_plugins=""
  local current_plugins=""

  log_info "Checking zone configuration: backup.framework.enabled"
  current_value="$(get_configuration_value "backup.framework.enabled" "${ZONE_ID}")"
  if [[ "${current_value,,}" != "true" ]]; then
    log_info "Updating zone configuration: backup.framework.enabled=true"
    update_configuration_value "backup.framework.enabled" "true" "${ZONE_ID}"
    printf 'Updated zone configuration: backup.framework.enabled=true (zoneid=%s)\n' "${ZONE_ID}"
  fi

  log_info "Checking global configuration: backup.enable.attach.detach.of.volumes"
  current_value="$(get_configuration_value "backup.enable.attach.detach.of.volumes")"
  if [[ "${current_value,,}" != "true" ]]; then
    log_info "Updating global configuration: backup.enable.attach.detach.of.volumes=true"
    update_configuration_value "backup.enable.attach.detach.of.volumes" "true"
    printf 'Updated global configuration: backup.enable.attach.detach.of.volumes=true\n'
  fi

  log_info "Checking zone configuration: backup.framework.provider.plugin"
  current_plugins="$(get_configuration_value "backup.framework.provider.plugin" "${ZONE_ID}")"
  updated_plugins="$(append_provider_if_missing "${current_plugins}" "${NETBACKUP_PROVIDER_DISPLAY_NAME}")"
  if [[ "${updated_plugins}" != "${current_plugins}" ]]; then
    log_info "Updating zone configuration: backup.framework.provider.plugin=${updated_plugins}"
    update_configuration_value "backup.framework.provider.plugin" "${updated_plugins}" "${ZONE_ID}"
    printf 'Updated zone configuration: backup.framework.provider.plugin=%s (zoneid=%s)\n' "${updated_plugins}" "${ZONE_ID}"
  fi

  log_info "Checking zone configuration: backup.plugin.netbackup.url"
  current_value="$(get_configuration_value "backup.plugin.netbackup.url" "${ZONE_ID}")"
  if [[ "${current_value}" != "${NETBACKUP_URL}" ]]; then
    log_info "Updating zone configuration: backup.plugin.netbackup.url=${NETBACKUP_URL}"
    update_configuration_value "backup.plugin.netbackup.url" "${NETBACKUP_URL}" "${ZONE_ID}"
    printf 'Updated zone configuration: backup.plugin.netbackup.url=%s (zoneid=%s)\n' "${NETBACKUP_URL}" "${ZONE_ID}"
  fi

  log_info "Checking zone configuration: backup.plugin.netbackup.apikey"
  current_value="$(get_configuration_value "backup.plugin.netbackup.apikey" "${ZONE_ID}")"
  if [[ "${current_value}" != "${NETBACKUP_APIKEY}" ]]; then
    log_info "Updating zone configuration: backup.plugin.netbackup.apikey=<hidden>"
    update_configuration_value "backup.plugin.netbackup.apikey" "${NETBACKUP_APIKEY}" "${ZONE_ID}"
    printf 'Updated zone configuration: backup.plugin.netbackup.apikey=<hidden> (zoneid=%s)\n' "${ZONE_ID}"
  fi
}

provider_matches_netbackup() {
  local provider_name="$1"
  local provider_lc="${provider_name,,}"
  [[ "${provider_lc}" == "${NETBACKUP_PROVIDER_DISPLAY_NAME}" || "${provider_lc}" == "${NETBACKUP_PROVIDER_CANONICAL_NAME}" ]]
}

ensure_netbackup_offering() {
  local response=""
  local offering_exists=""

  log_info "Checking existing NetBackup backup offerings for zoneid=${ZONE_ID}"
  response="$(invoke_mold_api GET "listBackupOfferings" listall true page 1 pagesize 500)"
  offering_exists="$(json_extract "${response}" '
offerings = data.get("listbackupofferingsresponse", {}).get("backupoffering", [])
if isinstance(offerings, dict):
    offerings = [offerings]
result = False
for offering in offerings:
    provider = str(offering.get("provider", "")).lower()
    zoneid = str(offering.get("zoneid", "") or offering.get("zoneId", ""))
    if provider in ("netbackup", "ablestack-netbackup") and zoneid == "'"${ZONE_ID}"'":
        result = True
        break
')"

  if [[ "${offering_exists}" == "true" ]]; then
    printf 'NetBackup backup offering already exists for zoneid=%s\n' "${ZONE_ID}"
    return 0
  fi

  log_info "Importing NetBackup backup offering for zoneid=${ZONE_ID}"
  invoke_mold_api GET "importBackupOffering" \
    name "${NETBACKUP_OFFERING_NAME}" \
    description "${NETBACKUP_OFFERING_DESCRIPTION}" \
    provider "${NETBACKUP_PROVIDER_DISPLAY_NAME}" \
    externalid "${NETBACKUP_OFFERING_EXTERNAL_ID}" \
    allowuserdrivenbackups false \
    zoneid "${ZONE_ID}" >/dev/null

  printf 'Imported NetBackup backup offering for zoneid=%s\n' "${ZONE_ID}"
}

configure_mold_for_netbackup() {
  log_info "Starting Mold API configuration for NetBackup"
  resolve_zone_id_from_policy_name
  ensure_backup_framework_configuration
  ensure_netbackup_offering
  log_info "Completed Mold API configuration for NetBackup"
}

backup_existing_file() {
  local target="$1"
  [[ -f "${target}" ]] || return 0
  local backup_target="${target}.bak.$(date '+%Y%m%d%H%M%S')"
  mv "${target}" "${backup_target}"
  printf 'Backed up existing file: %s -> %s\n' "${target}" "${backup_target}"
}

copy_existing_file_backup() {
  local target="$1"
  [[ -f "${target}" ]] || return 0
  local backup_target="${target}.bak.$(date '+%Y%m%d%H%M%S')"
  cp -p "${target}" "${backup_target}"
  printf 'Backed up existing file: %s -> %s\n' "${target}" "${backup_target}"
}

write_pre_hook() {
  local target="$1"
  local filename_comment="$2"

  backup_existing_file "${target}"
  cat > "${target}" <<EOF
#!/bin/sh

# Generated by netbackup_config.sh: ${filename_comment}

CLIENT="\${1:-}"
POLICY="\${2:-}"
SCHEDULE="\${3:-}"
TYPE="\${4:-}"

LOG="${HOOK_LOG_PATH}"
HELPER="${PRE_HELPER_PATH}"

log_line() {
  printf '%s %s\n' "\$(date '+%F %T')" "\$1" >> "\$LOG" 2>&1
}

if [ \$# -lt 4 ]; then
  log_line "PRE invalid-args argc=\$# client=\$CLIENT policy=\$POLICY schedule=\$SCHEDULE type=\$TYPE"
  exit 2
fi

if [ ! -x "\$HELPER" ]; then
  log_line "PRE helper-missing path=\$HELPER"
  exit 3
fi

log_line "PRE start client=\$CLIENT policy=\$POLICY schedule=\$SCHEDULE type=\$TYPE"
"\$HELPER" "\$POLICY" "\$SCHEDULE" "\$CLIENT" >> "\$LOG" 2>&1
RC=\$?
log_line "PRE end rc=\$RC client=\$CLIENT policy=\$POLICY schedule=\$SCHEDULE type=\$TYPE"
exit \$RC
EOF
  chmod 0755 "${target}"
}

write_post_hook() {
  local target="$1"
  local filename_comment="$2"

  backup_existing_file "${target}"
  cat > "${target}" <<EOF
#!/bin/sh

# Generated by netbackup_config.sh: ${filename_comment}

CLIENT="\${1:-}"
POLICY="\${2:-}"
SCHEDULE="\${3:-}"
TYPE="\${4:-}"
STATUS="\${5:-0}"

LOG="${HOOK_LOG_PATH}"
HELPER="${POST_HELPER_PATH}"

log_line() {
  printf '%s %s\n' "\$(date '+%F %T')" "\$1" >> "\$LOG" 2>&1
}

if [ \$# -lt 4 ]; then
  log_line "POST invalid-args argc=\$# client=\$CLIENT policy=\$POLICY schedule=\$SCHEDULE type=\$TYPE status=\$STATUS"
  exit 2
fi

if [ ! -x "\$HELPER" ]; then
  log_line "POST helper-missing path=\$HELPER"
  exit 3
fi

log_line "POST start client=\$CLIENT policy=\$POLICY schedule=\$SCHEDULE type=\$TYPE status=\$STATUS"
"\$HELPER" "\$POLICY" "\$SCHEDULE" "\$CLIENT" "\$STATUS" >> "\$LOG" 2>&1
RC=\$?
log_line "POST end rc=\$RC client=\$CLIENT policy=\$POLICY schedule=\$SCHEDULE type=\$TYPE status=\$STATUS"
exit \$RC
EOF
  chmod 0755 "${target}"
}

write_config_file() {
  local target="$1"

  backup_existing_file "${target}"
  cat > "${target}" <<EOF
VM_INCLUDE="$(printf '%s' "${VM_INCLUDE}")"
VM_EXCLUDE="$(printf '%s' "${VM_EXCLUDE}")"
MAX_INCREMENTAL_CHAIN=${MAX_INCREMENTAL_CHAIN}
MOLD_URL="$(printf '%s' "${MOLD_URL}")"
ADMIN_APIKEY="$(printf '%s' "${ADMIN_APIKEY}")"
EOF
  chmod 0600 "${target}"
}

write_encrypted_secret_file() {
  local target="$1"

  command_exists openssl || fail "openssl command is required to encrypt ADMIN_SECRETKEY."
  validate_secret_key_file
  backup_existing_file "${target}"
  umask 077
  printf '%s' "${ADMIN_SECRETKEY}" | \
    openssl enc -"${SECRET_CIPHER}" -pbkdf2 -salt -pass file:"${SECRET_KEY_FILE}" -out "${target}"
  chmod 0600 "${target}"
}

apply_permissions() {
  if [[ -d "${CONFIG_OUTPUT_DIR}" ]]; then
    chown root:root "${CONFIG_OUTPUT_DIR}"
    chmod 700 "${CONFIG_OUTPUT_DIR}"
    find "${CONFIG_OUTPUT_DIR}" -maxdepth 1 -type f -name '*.conf' -exec chmod 600 {} \;
  fi
}

ensure_backup_staging_root() {
  mkdir -p "${BACKUP_STAGING_ROOT}"
  chown root:root "${BACKUP_STAGING_ROOT}"
  chmod 755 "${BACKUP_STAGING_ROOT}"
}

apply_netbackup_bp_conf() {
  if [[ ! -f "${NETBACKUP_BP_CONF_PATH}" ]]; then
    printf 'NetBackup bp.conf not found: %s\n' "${NETBACKUP_BP_CONF_PATH}"
    return 1
  fi

  copy_existing_file_backup "${NETBACKUP_BP_CONF_PATH}"

  set_bp_conf_value "${NETBACKUP_BP_CONF_PATH}" "BPSTART_TIMEOUT" "14400"
  set_bp_conf_value "${NETBACKUP_BP_CONF_PATH}" "BPEND_TIMEOUT" "3600"
  set_bp_conf_value "${NETBACKUP_BP_CONF_PATH}" "CLIENT_READ_TIMEOUT" "21600"
  set_bp_conf_value "${NETBACKUP_BP_CONF_PATH}" "CLIENT_CONNECT_TIMEOUT" "1800"
  set_bp_conf_value "${NETBACKUP_BP_CONF_PATH}" "SERVER_CONNECT_TIMEOUT" "1800"

  printf 'Updated NetBackup config: %s\n' "${NETBACKUP_BP_CONF_PATH}"
  return 0
}

restart_netbackup_service() {
  if command_exists systemctl; then
    systemctl restart "${NETBACKUP_SERVICE_NAME}"
    printf 'Restarted NetBackup service via systemctl: %s\n' "${NETBACKUP_SERVICE_NAME}"
    return 0
  fi

  if command_exists service; then
    service "${NETBACKUP_SERVICE_NAME}" restart
    printf 'Restarted NetBackup service via service: %s\n' "${NETBACKUP_SERVICE_NAME}"
    return 0
  fi

  printf 'NetBackup service restart command not found. Please restart manually: %s\n' "${NETBACKUP_SERVICE_NAME}"
}

collect_inputs() {
  prompt_value POLICY_NAME "POLICY_NAME"
  validate_name "${POLICY_NAME}" "POLICY_NAME"

  prompt_value VM_INCLUDE "VM_INCLUDE" "*"
  prompt_value VM_EXCLUDE "VM_EXCLUDE" ""
  prompt_value MAX_INCREMENTAL_CHAIN "MAX_INCREMENTAL_CHAIN" "10"
  validate_positive_integer "${MAX_INCREMENTAL_CHAIN}" "MAX_INCREMENTAL_CHAIN"
  prompt_value MOLD_URL "MOLD_URL"
  prompt_value ADMIN_APIKEY "ADMIN_APIKEY"
  prompt_secret_value ADMIN_SECRETKEY "ADMIN_SECRETKEY"
  [[ -n "${ADMIN_SECRETKEY}" ]] || fail "ADMIN_SECRETKEY is required."
  prompt_value NETBACKUP_URL "NETBACKUP_URL" "https://netbackup:1556/netbackup"
  prompt_value NETBACKUP_APIKEY "NETBACKUP_APIKEY"
  [[ -n "${NETBACKUP_URL}" ]] || fail "NETBACKUP_URL is required."
  [[ -n "${NETBACKUP_APIKEY}" ]] || fail "NETBACKUP_APIKEY is required."
}

generate_outputs() {
  configure_mold_for_netbackup

  log_info "Generating local NetBackup hook/config files"
  mkdir -p "${HOOK_OUTPUT_DIR}" "${CONFIG_OUTPUT_DIR}" "${SECRET_OUTPUT_DIR}"

  local pre_hook_path=""
  local post_hook_path=""
  local config_path=""
  local secret_path=""
  local hook_name_suffix="${POLICY_NAME}"

  pre_hook_path="${HOOK_OUTPUT_DIR}/bpstart_notify.${POLICY_NAME}"
  post_hook_path="${HOOK_OUTPUT_DIR}/bpend_notify.${POLICY_NAME}"
  config_path="${CONFIG_OUTPUT_DIR}/${POLICY_NAME}.conf"

  secret_path="${SECRET_OUTPUT_DIR}/secret.enc"

  write_pre_hook "${pre_hook_path}" "bpstart_notify.${hook_name_suffix}"
  write_post_hook "${post_hook_path}" "bpend_notify.${hook_name_suffix}"
  write_config_file "${config_path}"
  write_encrypted_secret_file "${secret_path}"
  ensure_backup_staging_root
  apply_permissions
  if apply_netbackup_bp_conf; then
    restart_netbackup_service
  else
    printf 'Skipped NetBackup service restart because bp.conf was not found.\n'
  fi

  printf '\nGenerated files:\n'
  printf '  PRE hook   : %s\n' "${pre_hook_path}"
  printf '  POST hook  : %s\n' "${post_hook_path}"
  printf '  Config     : %s\n' "${config_path}"
  printf '  Secret(enc): %s\n' "${secret_path}"
  printf '  Staging dir: %s\n' "${BACKUP_STAGING_ROOT}"
  printf '  Zone ID    : %s\n' "${ZONE_ID}"
}

main() {
  if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
  fi

  validate_prerequisites
  collect_inputs
  generate_outputs
}

main "$@"
