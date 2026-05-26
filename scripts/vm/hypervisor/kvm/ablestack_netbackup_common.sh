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

BACKUP_ROOT_DEFAULT="/var/lib/ablestack/netbackup/staging"
STATE_ROOT_DEFAULT="/var/lib/ablestack/netbackup"
LOG_FILE_DEFAULT="/var/log/cloudstack/agent/agent.log"
LIBVIRT_URI_DEFAULT="qemu:///system"
CONFIG_ROOT_DEFAULT="/etc/ablestack/netbackup"
SECRET_HELPER_DEFAULT="/usr/share/cloudstack-common/scripts/vm/hypervisor/kvm/netbackup_secret_helper.sh"
SECRET_SUBDIR_DEFAULT="secrets"

CONFIG_ROOT="${CONFIG_ROOT:-$CONFIG_ROOT_DEFAULT}"
BACKUP_ROOT="${BACKUP_ROOT:-$BACKUP_ROOT_DEFAULT}"
STATE_ROOT="${STATE_ROOT:-$STATE_ROOT_DEFAULT}"
LOG_FILE="${LOG_FILE:-$LOG_FILE_DEFAULT}"
LIBVIRT_URI="${LIBVIRT_URI:-$LIBVIRT_URI_DEFAULT}"
SECRET_HELPER="${SECRET_HELPER:-$SECRET_HELPER_DEFAULT}"
SECRET_SUBDIR="${SECRET_SUBDIR:-$SECRET_SUBDIR_DEFAULT}"

VM_INCLUDE="${VM_INCLUDE:-*}"
VM_EXCLUDE="${VM_EXCLUDE:-}"
MAX_INCREMENTAL_CHAIN="${MAX_INCREMENTAL_CHAIN:-10}"
MOLD_URL="${MOLD_URL:-}"
ADMIN_APIKEY="${ADMIN_APIKEY:-}"
ADMIN_SECRETKEY="${ADMIN_SECRETKEY:-}"
MOLD_CREATE_BACKUP_API_URL="${MOLD_CREATE_BACKUP_API_URL:-}"
MOLD_CREATE_BACKUP_API_METHOD="${MOLD_CREATE_BACKUP_API_METHOD:-POST}"
MOLD_LIST_VMS_API_URL="${MOLD_LIST_VMS_API_URL:-}"
MOLD_LIST_VMS_API_METHOD="${MOLD_LIST_VMS_API_METHOD:-GET}"
MOLD_API_RESPONSE_FORMAT="${MOLD_API_RESPONSE_FORMAT:-json}"
MOLD_API_SKIP_TLS_VERIFY="${MOLD_API_SKIP_TLS_VERIFY:-false}"

verb="${verb:-0}"
POLICY_NAME="${POLICY_NAME:-}"
SCHEDULE_NAME="${SCHEDULE_NAME:-}"
CLIENT_NAME="${CLIENT_NAME:-}"
SESSION_TIMESTAMP="${SESSION_TIMESTAMP:-}"
JOB_STATUS="${JOB_STATUS:-}"
NETBACKUP_REQUIRE_JOB_SUCCESS="${NETBACKUP_REQUIRE_JOB_SUCCESS:-true}"
NETBACKUP_COMMIT_ON_CLIENT_STATUS_ZERO="${NETBACKUP_COMMIT_ON_CLIENT_STATUS_ZERO:-false}"
NETBACKUP_JOB_ID="${NETBACKUP_JOB_ID:-${NB_JOBID:-${JOB_ID:-}}}"
NETBACKUP_SUCCESS_CONFIRM_CMD="${NETBACKUP_SUCCESS_CONFIRM_CMD:-}"
MOLD_VM_CACHE_FILE=""

log() {
  local ts
  ts="$(date '+%Y-%m-%d %H-%M-%S>')"
  [[ "${verb}" -eq 1 ]] && builtin echo "$@"
  if [[ "${1:-}" == "-ne" || "${1:-}" == "-e" || "${1:-}" == "-n" ]]; then
    builtin echo -e "${ts}" "${@:2}" >> "${LOG_FILE}"
  else
    builtin echo "${ts}" "$@" >> "${LOG_FILE}"
  fi
}

fail() {
  builtin echo "$*" >&2
  log -ne "$*"
  exit 1
}

ensure_runtime_dirs() {
  mkdir -p "${BACKUP_ROOT}" \
           "${STATE_ROOT}/contexts" \
           "${STATE_ROOT}/locks" \
           "${STATE_ROOT}/sessions"
}

sanitize_name() {
  local value="$1"
  value="${value//\//_}"
  value="${value// /_}"
  value="${value//:/_}"
  value="${value//;/_}"
  builtin echo "${value}"
}

generate_timestamp() {
  date '+%Y.%m.%d.%H.%M.%S.%3N'
}

write_state_file() {
  local path="$1"
  shift
  : > "${path}"
  while [[ $# -gt 1 ]]; do
    local key="$1"
    local value="$2"
    shift 2
    printf '%s=%q\n' "${key}" "${value}" >> "${path}"
  done
}

load_state_file() {
  local path="$1"
  [[ -f "${path}" ]] || return 1
  # shellcheck disable=SC1090
  source "${path}"
}

resolve_context() {
  local arg_policy="${1:-}"
  local arg_schedule="${2:-}"
  local arg_client="${3:-}"
  local arg_timestamp="${4:-}"

  if [[ -n "${arg_policy}" && -z "${POLICY_NAME}" ]]; then
    POLICY_NAME="${arg_policy}"
  fi
  if [[ -n "${arg_schedule}" && -z "${SCHEDULE_NAME}" ]]; then
    SCHEDULE_NAME="${arg_schedule}"
  fi
  if [[ -n "${arg_client}" && -z "${CLIENT_NAME}" ]]; then
    CLIENT_NAME="${arg_client}"
  fi
  if [[ -n "${arg_timestamp}" && -z "${SESSION_TIMESTAMP}" ]]; then
    SESSION_TIMESTAMP="${arg_timestamp}"
  fi

  POLICY_NAME="${POLICY_NAME:-${NB_ORA_POLICY:-manual}}"
  SCHEDULE_NAME="${SCHEDULE_NAME:-${NB_ORA_SCHEDULE:-manual}}"
  CLIENT_NAME="${CLIENT_NAME:-$(hostname -s)}"
  SESSION_TIMESTAMP="${SESSION_TIMESTAMP:-$(generate_timestamp)}"

  POLICY_SAFE="$(sanitize_name "${POLICY_NAME}")"
  SCHEDULE_SAFE="$(sanitize_name "${SCHEDULE_NAME}")"
  CLIENT_SAFE="$(sanitize_name "${CLIENT_NAME}")"
  CONTEXT_KEY="${POLICY_SAFE}__${SCHEDULE_SAFE}__${CLIENT_SAFE}"
  LOCK_FILE="${STATE_ROOT}/locks/${CONTEXT_KEY}.lock"
  CONTEXT_FILE="${STATE_ROOT}/contexts/${CONTEXT_KEY}.env"
  MANIFEST_FILE="${STATE_ROOT}/sessions/${CONTEXT_KEY}.manifest"
}

context_in_progress_file() {
  builtin echo "${STATE_ROOT}/contexts/${CONTEXT_KEY}.inprogress"
}

mark_context_in_progress() {
  local marker
  marker="$(context_in_progress_file)"
  if [[ -e "${marker}" ]]; then
    fail "NetBackup staging context is already initialized for ${CONTEXT_KEY}. Disable multistreaming for this policy/schedule or clean stale state after verification: ${marker}"
  fi
  write_state_file "${marker}" \
    POLICY_NAME "${POLICY_NAME}" \
    SCHEDULE_NAME "${SCHEDULE_NAME}" \
    CLIENT_NAME "${CLIENT_NAME}" \
    SESSION_TIMESTAMP "${SESSION_TIMESTAMP}"
}

clear_context_in_progress() {
  rm -f "$(context_in_progress_file)"
}

schedule_config_file_path() {
  builtin echo "${CONFIG_ROOT}/${POLICY_SAFE}.${SCHEDULE_SAFE}.conf"
}

policy_config_file_path() {
  builtin echo "${CONFIG_ROOT}/${POLICY_SAFE}.conf"
}

resolve_config_file_path() {
  local schedule_config_file
  local policy_config_file

  schedule_config_file="$(schedule_config_file_path)"
  policy_config_file="$(policy_config_file_path)"

  if [[ -n "${SCHEDULE_NAME}" && "${SCHEDULE_NAME}" != "manual" && -f "${schedule_config_file}" ]]; then
    builtin echo "${schedule_config_file}"
    return 0
  fi
  builtin echo "${policy_config_file}"
}

resolve_secret_file_path() {
  builtin echo "${CONFIG_ROOT}/${SECRET_SUBDIR}/secret.enc"
}

load_admin_secretkey() {
  local secret_file
  secret_file="$(resolve_secret_file_path)"

  if [[ -n "${ADMIN_SECRETKEY}" ]]; then
    return 0
  fi
  if [[ ! -f "${secret_file}" ]]; then
    fail "ADMIN_SECRETKEY is not set and encrypted secret file was not found: ${secret_file}"
  fi
  if [[ ! -x "${SECRET_HELPER}" ]]; then
    fail "Secret helper not found or not executable: ${SECRET_HELPER}"
  fi

  ADMIN_SECRETKEY="$("${SECRET_HELPER}" decrypt "${secret_file}")" || fail "Failed to decrypt NetBackup secret file ${secret_file}"
  [[ -n "${ADMIN_SECRETKEY}" ]] || fail "Decrypted ADMIN_SECRETKEY is empty."
}

load_policy_schedule_config() {
  local config_file
  config_file="$(resolve_config_file_path)"

  VM_INCLUDE="*"
  VM_EXCLUDE=""
  MAX_INCREMENTAL_CHAIN="10"
  MOLD_URL=""
  ADMIN_APIKEY=""

  if [[ -f "${config_file}" ]]; then
    # shellcheck disable=SC1090
    source "${config_file}"
    log -ne "Loaded NetBackup config: ${config_file}"
  else
    log -ne "No NetBackup config found for policy=${POLICY_NAME} schedule=${SCHEDULE_NAME}"
  fi

  VM_INCLUDE="${VM_INCLUDE:-*}"
  VM_EXCLUDE="${VM_EXCLUDE:-}"
  MAX_INCREMENTAL_CHAIN="${MAX_INCREMENTAL_CHAIN:-10}"
  MOLD_CREATE_BACKUP_API_URL="${MOLD_CREATE_BACKUP_API_URL:-${MOLD_URL}}"
  MOLD_LIST_VMS_API_URL="${MOLD_LIST_VMS_API_URL:-${MOLD_URL}}"

  [[ "${MAX_INCREMENTAL_CHAIN}" =~ ^[1-9][0-9]*$ ]] || fail "Invalid MAX_INCREMENTAL_CHAIN=${MAX_INCREMENTAL_CHAIN}. It must be a positive integer."
  [[ -n "${MOLD_CREATE_BACKUP_API_URL}" ]] || fail "MOLD_URL or MOLD_CREATE_BACKUP_API_URL must be configured."
  [[ -n "${MOLD_LIST_VMS_API_URL}" ]] || fail "MOLD_URL or MOLD_LIST_VMS_API_URL must be configured."
  [[ -n "${ADMIN_APIKEY}" ]] || fail "ADMIN_APIKEY must be configured."

  load_admin_secretkey

  log -ne "VM selection include=${VM_INCLUDE} exclude=${VM_EXCLUDE} max_incremental_chain=${MAX_INCREMENTAL_CHAIN}"
}

match_csv_glob() {
  local value="$1"
  local patterns="$2"
  local pattern

  [[ -z "${patterns}" ]] && return 1
  while IFS= read -r pattern; do
    pattern="${pattern#"${pattern%%[![:space:]]*}"}"
    pattern="${pattern%"${pattern##*[![:space:]]}"}"
    [[ -z "${pattern}" ]] && continue
    if [[ "${value}" == ${pattern} ]]; then
      return 0
    fi
  done < <(tr ',' '\n' <<< "${patterns}")
  return 1
}

vm_is_selected() {
  local vm_name="$1"
  if match_csv_glob "${vm_name}" "${VM_EXCLUDE}"; then
    return 1
  fi
  if match_csv_glob "${vm_name}" "${VM_INCLUDE}"; then
    return 0
  fi
  return 1
}

list_running_vms() {
  virsh -c "${LIBVIRT_URI}" list --name --state-running | sed '/^$/d'
}

list_target_vms() {
  local vm_name
  while IFS= read -r vm_name; do
    [[ -z "${vm_name}" ]] && continue
    if vm_is_selected "${vm_name}"; then
      builtin echo "${vm_name}"
    else
      log -ne "Skipping VM ${vm_name}: not selected by VM_INCLUDE/VM_EXCLUDE"
    fi
  done < <(list_running_vms)
}

acquire_lock() {
  exec 9>"${LOCK_FILE}"
  if ! flock -n 9; then
    fail "Another NetBackup staging operation is already running for context ${CONTEXT_KEY}"
  fi
}

sanity_checks() {
  command -v virsh >/dev/null 2>&1 || fail "virsh command not found"
  command -v curl >/dev/null 2>&1 || fail "curl command not found"
  command -v python3 >/dev/null 2>&1 || fail "python3 command not found"
  command -v openssl >/dev/null 2>&1 || fail "openssl command not found"
}

netbackup_job_success_confirmed() {
  [[ "${JOB_STATUS}" == "0" ]] || return 1

  if [[ -n "${NETBACKUP_SUCCESS_CONFIRM_CMD}" ]]; then
    export POLICY_NAME SCHEDULE_NAME CLIENT_NAME SESSION_TIMESTAMP JOB_STATUS NETBACKUP_JOB_ID
    bash -c "${NETBACKUP_SUCCESS_CONFIRM_CMD}" >/dev/null 2>&1 && return 0
    return 1
  fi

  if [[ -n "${NETBACKUP_JOB_ID}" ]] && command -v bpdbjobs >/dev/null 2>&1; then
    local job_status
    job_status=$(bpdbjobs -most_columns -jobid "${NETBACKUP_JOB_ID}" 2>/dev/null | awk -F, 'NR==1 {print $4}')
    [[ "${job_status}" == "0" ]] && return 0
    return 1
  fi

  if [[ "${NETBACKUP_REQUIRE_JOB_SUCCESS}" == "true" ]]; then
    log -ne "NetBackup overall job success was not independently confirmed. Set NETBACKUP_SUCCESS_CONFIRM_CMD, set NETBACKUP_JOB_ID with bpdbjobs access, or set NETBACKUP_COMMIT_ON_CLIENT_STATUS_ZERO=true to allow client-status-only completion."
    return 1
  fi

  [[ "${NETBACKUP_COMMIT_ON_CLIENT_STATUS_ZERO}" == "true" ]]
}

write_manifest_header() {
  : > "${MANIFEST_FILE}"
  MOLD_VM_CACHE_FILE="${STATE_ROOT}/sessions/${CONTEXT_KEY}.listVirtualMachines.json"
}

append_manifest_line() {
  local vm_name="$1"
  local session_dir="$2"
  printf '%s|%s\n' "${vm_name}" "${session_dir}" >> "${MANIFEST_FILE}"
}

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\t'/\\t}"
  printf '%s' "${value}"
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

sign_mold_request() {
  local request="$1"
  local signature

  signature="$(printf '%s' "${request}" | openssl dgst -sha256 -hmac "${ADMIN_SECRETKEY}" -binary | openssl base64 -A)" || fail "Failed to sign Mold API request"
  url_encode "${signature}"
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
  local url="$2"
  local command_name="$3"
  shift 3
  local api_params
  local signed_url
  local response=""

  api_params="$(build_mold_api_params "${command_name}" "$@")"
  signed_url="$(build_mold_signed_url "${url}" "${api_params}")" || fail "Failed to build signed Mold API URL for command=${command_name}"

  local curl_args=(
    --silent
    --show-error
    --fail
    -X "${method}"
    -H "Accept: application/json"
    -H "Content-type: application/x-www-form-urlencoded"
  )

  if [[ "${MOLD_API_SKIP_TLS_VERIFY}" == "true" && "${signed_url}" == https://* ]]; then
    curl_args+=(-k)
  fi

  response="$(curl "${curl_args[@]}" "${signed_url}")" || fail "Mold API call failed: method=${method} command=${command_name} url=${url}"
  printf '%s' "${response}"
}

cache_mold_virtual_machines() {
  [[ -n "${MOLD_VM_CACHE_FILE}" ]] || fail "MOLD_VM_CACHE_FILE is not initialized."
  if [[ -f "${MOLD_VM_CACHE_FILE}" ]]; then
    return 0
  fi

  log -ne "Calling Mold listVirtualMachines API url=${MOLD_LIST_VMS_API_URL}"
  invoke_mold_api "${MOLD_LIST_VMS_API_METHOD}" "${MOLD_LIST_VMS_API_URL}" "listVirtualMachines" > "${MOLD_VM_CACHE_FILE}"
}

lookup_mold_vm_id() {
  local vm_name="$1"
  [[ -f "${MOLD_VM_CACHE_FILE}" ]] || fail "Mold VM cache file not found: ${MOLD_VM_CACHE_FILE}"

  python3 - "$MOLD_VM_CACHE_FILE" "$vm_name" <<'PY'
import json
import sys

cache_path = sys.argv[1]
vm_name = sys.argv[2]

with open(cache_path, "r", encoding="utf-8") as fh:
    data = json.load(fh)

matches = []

def walk(node):
    if isinstance(node, dict):
        if str(node.get("instancename", "")) == vm_name and "id" in node:
            matches.append(str(node["id"]))
        for value in node.values():
            walk(value)
    elif isinstance(node, list):
        for item in node:
            walk(item)

walk(data)

if matches:
    print(matches[0], end="")
PY
}

write_backup_metadata() {
  local vm_name="$1"
  local dest="$2"

  mkdir -p "${dest}"
  virsh -c "${LIBVIRT_URI}" dumpxml "${vm_name}" > "${dest}/domain-config.xml" 2>/dev/null || true
  virsh -c "${LIBVIRT_URI}" dominfo "${vm_name}" > "${dest}/dominfo.xml" 2>/dev/null || true
  virsh -c "${LIBVIRT_URI}" domiflist "${vm_name}" > "${dest}/domiflist.xml" 2>/dev/null || true
  virsh -c "${LIBVIRT_URI}" domblklist "${vm_name}" > "${dest}/domblklist.xml" 2>/dev/null || true

  write_state_file "${dest}/backup.meta" \
    BACKUP_FRAMEWORK "ABLESTACK_NETBACKUP" \
    BACKUP_MODE "MOLD_API" \
    VM_NAME "${vm_name}" \
    POLICY_NAME "${POLICY_NAME}" \
    SCHEDULE_NAME "${SCHEDULE_NAME}" \
    CLIENT_NAME "${CLIENT_NAME}" \
    SESSION_TIMESTAMP "${SESSION_TIMESTAMP}" \
    MAX_INCREMENTAL_CHAIN "${MAX_INCREMENTAL_CHAIN}"
}

invoke_mold_create_backup() {
  local vm_name="$1"
  local vm_id="$2"
  local dest="$3"

  log -ne "Calling Mold createBackup API for vm=${vm_name} vmId=${vm_id} url=${MOLD_CREATE_BACKUP_API_URL}"

  local response
  response="$(invoke_mold_api \
    "${MOLD_CREATE_BACKUP_API_METHOD}" \
    "${MOLD_CREATE_BACKUP_API_URL}" \
    "createBackup" \
    "vmName" "${vm_name}" \
    "vmId" "${vm_id}" \
    "policyName" "${POLICY_NAME}" \
    "scheduleName" "${SCHEDULE_NAME}" \
    "clientName" "${CLIENT_NAME}" \
    "sessionTimestamp" "${SESSION_TIMESTAMP}" \
    "backupRoot" "${dest}" \
    "maxIncrementalChain" "${MAX_INCREMENTAL_CHAIN}")" || fail "Mold createBackup API call failed for vm=${vm_name}"

  printf '%s\n' "${response}" > "${dest}/create-backup.response.json"
}

stage_vm_backup() {
  local vm_name="$1"
  local dest="${BACKUP_ROOT}/${vm_name}/${SESSION_TIMESTAMP}"
  local vm_id=""

  rm -rf "${dest}"
  mkdir -p "${dest}"

  vm_id="$(lookup_mold_vm_id "${vm_name}")"
  [[ -n "${vm_id}" ]] || fail "Unable to resolve Mold VM id for instance name ${vm_name}"

  write_backup_metadata "${vm_name}" "${dest}"
  invoke_mold_create_backup "${vm_name}" "${vm_id}" "${dest}"
  append_manifest_line "${vm_name}" "${dest}"
}

remove_session_dir() {
  local session_dir="$1"
  [[ -d "${session_dir}" ]] || return 0
  rm -rf "${session_dir}"
}
