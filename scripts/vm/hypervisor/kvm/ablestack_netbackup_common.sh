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

CONFIG_ROOT="${CONFIG_ROOT:-$CONFIG_ROOT_DEFAULT}"
VM_INCLUDE="${VM_INCLUDE:-*}"
VM_EXCLUDE="${VM_EXCLUDE:-}"
MAX_INCREMENTAL_CHAIN="${MAX_INCREMENTAL_CHAIN:-10}"

BACKUP_ENGINE_QCOW2="QCOW2"
BACKUP_ENGINE_RBD_DIFF="RBD_DIFF"

verb="${verb:-0}"
BACKUP_ROOT="${BACKUP_ROOT:-$BACKUP_ROOT_DEFAULT}"
STATE_ROOT="${STATE_ROOT:-$STATE_ROOT_DEFAULT}"
LOG_FILE="${LOG_FILE:-$LOG_FILE_DEFAULT}"
LIBVIRT_URI="${LIBVIRT_URI:-$LIBVIRT_URI_DEFAULT}"
QUIESCE="${QUIESCE:-false}"
POLICY_NAME="${POLICY_NAME:-}"
SCHEDULE_NAME="${SCHEDULE_NAME:-}"
CLIENT_NAME="${CLIENT_NAME:-}"
SESSION_TIMESTAMP="${SESSION_TIMESTAMP:-}"
JOB_STATUS="${JOB_STATUS:-}"
NETBACKUP_REQUIRE_JOB_SUCCESS="${NETBACKUP_REQUIRE_JOB_SUCCESS:-true}"
NETBACKUP_COMMIT_ON_CLIENT_STATUS_ZERO="${NETBACKUP_COMMIT_ON_CLIENT_STATUS_ZERO:-false}"
NETBACKUP_JOB_ID="${NETBACKUP_JOB_ID:-${NB_JOBID:-${JOB_ID:-}}}"
NETBACKUP_SUCCESS_CONFIRM_CMD="${NETBACKUP_SUCCESS_CONFIRM_CMD:-}"

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
           "${STATE_ROOT}/pending" \
           "${STATE_ROOT}/sessions" \
           "${STATE_ROOT}/vms"
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
  PENDING_DIR="${STATE_ROOT}/pending/${CONTEXT_KEY}"
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

load_policy_schedule_config() {
  local schedule_config_file="${CONFIG_ROOT}/${POLICY_SAFE}.${SCHEDULE_SAFE}.conf"
  local policy_config_file="${CONFIG_ROOT}/${POLICY_SAFE}.conf"

  VM_INCLUDE="*"
  VM_EXCLUDE=""

  if [[ -f "${schedule_config_file}" ]]; then
    # shellcheck disable=SC1090
    source "${schedule_config_file}"
    log -ne "Loaded NetBackup VM selection config: ${schedule_config_file}"
  elif [[ -f "${policy_config_file}" ]]; then
    # shellcheck disable=SC1090
    source "${policy_config_file}"
    log -ne "Loaded NetBackup VM selection config: ${policy_config_file}"
  else
    log -ne "No NetBackup VM selection config found. Using default VM_INCLUDE=* VM_EXCLUDE="
  fi

  VM_INCLUDE="${VM_INCLUDE:-*}"
  VM_EXCLUDE="${VM_EXCLUDE:-}"
  MAX_INCREMENTAL_CHAIN="${MAX_INCREMENTAL_CHAIN:-10}"

  log -ne "VM selection include=${VM_INCLUDE} exclude=${VM_EXCLUDE} max_incremental_chain=${MAX_INCREMENTAL_CHAIN}"

  if ! [[ "${MAX_INCREMENTAL_CHAIN}" =~ ^[1-9][0-9]*$ ]]; then
    fail "Invalid MAX_INCREMENTAL_CHAIN=${MAX_INCREMENTAL_CHAIN}. It must be a positive integer."
  fi
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

netbackup_job_success_confirmed() {
  # Return 0 only when it is safe to commit the new incremental base.
  # JOB_STATUS is the client-side bpbkar status passed by bpend_notify.
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
    log -ne "NetBackup overall job success was not independently confirmed. Set NETBACKUP_SUCCESS_CONFIRM_CMD, set NETBACKUP_JOB_ID with bpdbjobs access, or set NETBACKUP_COMMIT_ON_CLIENT_STATUS_ZERO=true to allow client-status-only commit."
    return 1
  fi

  [[ "${NETBACKUP_COMMIT_ON_CLIENT_STATUS_ZERO}" == "true" ]]
}

acquire_lock() {
  exec 9>"${LOCK_FILE}"
  if ! flock -n 9; then
    fail "Another NetBackup staging operation is already running for context ${CONTEXT_KEY}"
  fi
}

vercomp() {
  local IFS=.
  local i ver1=($1) ver2=($3)
  for ((i=0; i<${#ver1[@]}; i++)); do
    if [[ -z "${ver2[i]:-}" ]]; then
      ver2[i]=0
    fi
    if ((10#${ver1[i]} > 10#${ver2[i]})); then
      return 0
    elif ((10#${ver1[i]} < 10#${ver2[i]})); then
      return 2
    fi
  done
  return 0
}

sanity_checks() {
  local hv_version
  local libvirt_version
  local api_version

  command -v virsh >/dev/null 2>&1 || fail "virsh command not found"
  command -v qemu-img >/dev/null 2>&1 || fail "qemu-img command not found"

  hv_version=$(virsh -c "${LIBVIRT_URI}" version | awk '/hypervisor/ {print $NF}')
  libvirt_version=$(virsh -c "${LIBVIRT_URI}" version | awk '/libvirt/ {print $NF}' | tail -n 1)
  api_version=$(virsh -c "${LIBVIRT_URI}" version | awk '/API/ {print $NF}')

  vercomp "${hv_version}" ">=" "4.2.0"
  local hv_status=$?
  vercomp "${libvirt_version}" ">=" "7.2.0"
  local libvirt_status=$?

  if [[ ${hv_status} -ne 0 || ${libvirt_status} -ne 0 ]]; then
    fail "Unsupported QEMU/libvirt version. QEMU=${hv_version}, libvirt=${libvirt_version}"
  fi

  log -ne "NetBackup sanity checks passed [QEMU=${hv_version} libvirt=${libvirt_version} API=${api_version}]"
}

list_running_vms() {
  virsh -c "${LIBVIRT_URI}" list --name --state-running | sed '/^$/d'
}

collect_disk_map() {
  local vm_name="$1"
  virsh -c "${LIBVIRT_URI}" domblklist "${vm_name}" --details 2>/dev/null | awk '$2 == "disk" {print $3 "|" $4}'
}

split_csv() {
  tr ',' '\n' <<< "$1"
}

is_positive_integer() {
  local value="$1"
  [[ "${value}" =~ ^[1-9][0-9]*$ ]]
}

is_rbd_disk_path() {
  local disk_path="$1"
  [[ "${disk_path}" == rbd:* || "${disk_path}" == rbd/* ]]
}

build_disk_layout_signature() {
  local vm_name="$1"
  collect_disk_map "${vm_name}" | sort | tr '\n' ';'
}

detect_backup_engine() {
  local vm_name="$1"
  local disk_path
  local has_rbd=0
  local has_file=0

  while IFS='|' read -r _target disk_path; do
    [[ -z "${disk_path}" ]] && continue
    if is_rbd_disk_path "${disk_path}"; then
      has_rbd=1
    else
      has_file=1
    fi
  done < <(collect_disk_map "${vm_name}")

  if [[ ${has_rbd} -eq 1 && ${has_file} -eq 1 ]]; then
    fail "VM ${vm_name} has mixed RBD/file disks. This script does not support mixed backup engines in one VM."
  fi
  if [[ ${has_rbd} -eq 1 ]]; then
    builtin echo "${BACKUP_ENGINE_RBD_DIFF}"
  else
    builtin echo "${BACKUP_ENGINE_QCOW2}"
  fi
}

parse_rbd_uri() {
  local uri="$1"

  RBD_IMAGE=""
  RBD_MON_HOST=""
  RBD_USER=""
  RBD_KEY=""

  if [[ "${uri}" == rbd:* ]]; then
    local payload="${uri#rbd:}"
    RBD_IMAGE="${payload%%:*}"
    if [[ "${uri}" =~ :mon_host=([^:]*) ]]; then
      RBD_MON_HOST="${BASH_REMATCH[1]}"
      RBD_MON_HOST="${RBD_MON_HOST//\\;/,}"
      RBD_MON_HOST="${RBD_MON_HOST//\\:/:}"
    fi
    if [[ "${uri}" =~ :id=([^:]*) ]]; then
      RBD_USER="${BASH_REMATCH[1]}"
    fi
    if [[ "${uri}" =~ :key=([^:]*) ]]; then
      RBD_KEY="${BASH_REMATCH[1]}"
    fi
  elif [[ "${uri}" == rbd/* ]]; then
    RBD_IMAGE="${uri}"
  else
    fail "Invalid RBD disk path: ${uri}"
  fi

  [[ -n "${RBD_IMAGE}" ]] || fail "Failed to parse RBD image from uri: ${uri}"
}

build_rbd_cmd() {
  RBD_CMD=(rbd)
  if [[ -n "${RBD_MON_HOST}" ]]; then
    RBD_CMD+=(-m "${RBD_MON_HOST}")
  fi
  if [[ -n "${RBD_USER}" ]]; then
    RBD_CMD+=(--id "${RBD_USER}")
  fi
  if [[ -n "${RBD_KEY}" ]]; then
    RBD_CMD+=(--key "${RBD_KEY}")
  fi
}

vm_state_file() {
  local vm_name="$1"
  builtin echo "${STATE_ROOT}/vms/$(sanitize_name "${vm_name}").env"
}

pending_vm_state_file() {
  local vm_name="$1"
  builtin echo "${PENDING_DIR}/$(sanitize_name "${vm_name}").env"
}

write_manifest_header() {
  : > "${MANIFEST_FILE}"
}

append_manifest_line() {
  local vm_name="$1"
  local session_dir="$2"
  local engine="$3"
  local checkpoint_name="$4"
  local parent_checkpoint_name="$5"
  printf '%s|%s|%s|%s|%s\n' \
    "${vm_name}" "${session_dir}" "${engine}" "${checkpoint_name}" "${parent_checkpoint_name}" >> "${MANIFEST_FILE}"
}

backup_domain_information() {
  local vm_name="$1"
  local dest="$2"
  local backup_type="$3"
  local checkpoint_name="$4"
  local parent_checkpoint_name="$5"
  local engine="$6"
  local disk_paths="$7"

  mkdir -p "${dest}/checkpoints"

  virsh -c "${LIBVIRT_URI}" dumpxml "${vm_name}" > "${dest}/domain-config.xml" 2>/dev/null || true
  virsh -c "${LIBVIRT_URI}" dominfo "${vm_name}" > "${dest}/dominfo.xml" 2>/dev/null || true
  virsh -c "${LIBVIRT_URI}" domiflist "${vm_name}" > "${dest}/domiflist.xml" 2>/dev/null || true
  virsh -c "${LIBVIRT_URI}" domblklist "${vm_name}" > "${dest}/domblklist.xml" 2>/dev/null || true

  write_state_file "${dest}/backup.meta" \
    BACKUP_FRAMEWORK "ABLESTACK_NETBACKUP" \
    VM_NAME "${vm_name}" \
    BACKUP_TYPE "${backup_type}" \
    CHECKPOINT_NAME "${checkpoint_name}" \
    PARENT_CHECKPOINT_NAME "${parent_checkpoint_name}" \
    BACKUP_ENGINE "${engine}" \
    DISK_PATHS "${disk_paths}" \
    POLICY_NAME "${POLICY_NAME}" \
    SCHEDULE_NAME "${SCHEDULE_NAME}" \
    CLIENT_NAME "${CLIENT_NAME}" \
    SESSION_TIMESTAMP "${SESSION_TIMESTAMP}"
}

dump_checkpoint_xml() {
  local vm_name="$1"
  local checkpoint_name="$2"
  local dest="$3"
  [[ -n "${checkpoint_name}" ]] || return 0
  virsh -c "${LIBVIRT_URI}" checkpoint-dumpxml --domain "${vm_name}" --checkpointname "${checkpoint_name}" --no-domain > "${dest}/checkpoints/${checkpoint_name}.xml" 2>/dev/null || true
}

virsh_checkpoint_exists() {
  local vm_name="$1"
  local checkpoint_name="$2"
  [[ -n "${checkpoint_name}" ]] || return 1
  virsh -c "${LIBVIRT_URI}" checkpoint-info --domain "${vm_name}" --checkpointname "${checkpoint_name}" >/dev/null 2>&1
}

rbd_checkpoint_exists_for_all_disks() {
  local disk_paths_csv="$1"
  local checkpoint_name="$2"
  local disk_path

  [[ -n "${checkpoint_name}" ]] || return 1

  while IFS= read -r disk_path; do
    [[ -z "${disk_path}" ]] && continue
    parse_rbd_uri "${disk_path}"
    build_rbd_cmd
    if ! timeout 30s "${RBD_CMD[@]}" snap ls "${RBD_IMAGE}" 2>/dev/null | awk 'NR>1 {print $2}' | grep -Fxq "${checkpoint_name}"; then
      return 1
    fi
  done < <(split_csv "${disk_paths_csv}")

  return 0
}

create_qcow2_incremental_backup() {
  local vm_name="$1"
  local dest="$2"
  local checkpoint_name="$3"
  local parent_checkpoint_name="$4"
  local backup_type="$5"
  local disk_paths_csv="$6"

  mkdir -p "${dest}/checkpoints"

  {
    echo "<domainbackup mode='push'>"
    if [[ "${backup_type}" == "INCREMENTAL" ]]; then
      echo "<incremental>${parent_checkpoint_name}</incremental>"
    fi
    echo "<disks>"
    while IFS='|' read -r target _source; do
      [[ -z "${target}" ]] && continue
      echo "<disk name='${target}' backup='yes' type='file'><target file='${dest}/${target}.qcow2' /><driver type='qcow2'/></disk>"
    done < <(collect_disk_map "${vm_name}")
    echo "</disks>"
    echo "</domainbackup>"
  } > "${dest}/backup.xml"

  {
    echo "<domaincheckpoint><name>${checkpoint_name}</name><disks>"
    while IFS='|' read -r target _source; do
      [[ -z "${target}" ]] && continue
      echo "<disk name='${target}' checkpoint='bitmap'/>"
    done < <(collect_disk_map "${vm_name}")
    echo "</disks></domaincheckpoint>"
  } > "${dest}/checkpoint.xml"

  local thaw=0
  if [[ "${QUIESCE}" == "true" ]]; then
    if virsh -c "${LIBVIRT_URI}" qemu-agent-command "${vm_name}" '{"execute":"guest-fsfreeze-freeze"}' >/dev/null 2>/dev/null; then
      thaw=1
    fi
  fi

  local backup_started=0
  if virsh -c "${LIBVIRT_URI}" backup-begin --domain "${vm_name}" --backupxml "${dest}/backup.xml" --checkpointxml "${dest}/checkpoint.xml" >/dev/null 2>&1; then
    backup_started=1
  fi

  if [[ ${thaw} -eq 1 ]]; then
    virsh -c "${LIBVIRT_URI}" qemu-agent-command "${vm_name}" '{"execute":"guest-fsfreeze-thaw"}' >/dev/null 2>&1 || true
  fi

  [[ ${backup_started} -eq 1 ]] || fail "Failed to start libvirt incremental backup for VM ${vm_name}"

  while true; do
    local status
    status=$(virsh -c "${LIBVIRT_URI}" domjobinfo "${vm_name}" --completed --keep-completed 2>/dev/null | awk '/Job type:/ {print $3}')
    case "${status}" in
      Completed)
        break
        ;;
      Failed)
        fail "Libvirt incremental backup job failed for VM ${vm_name}"
        ;;
    esac
    sleep 5
  done

  dump_checkpoint_xml "${vm_name}" "${checkpoint_name}" "${dest}"
  rm -f "${dest}/backup.xml" "${dest}/checkpoint.xml"

  write_state_file "${dest}/engine.meta" \
    BACKUP_ENGINE "${BACKUP_ENGINE_QCOW2}" \
    DISK_PATHS "${disk_paths_csv}"
}

create_rbd_incremental_backup() {
  local vm_name="$1"
  local dest="$2"
  local checkpoint_name="$3"
  local parent_checkpoint_name="$4"
  local backup_type="$5"
  local disk_paths_csv="$6"
  local disk_path
  local index=0

  mkdir -p "${dest}/checkpoints"

  while IFS= read -r disk_path; do
    [[ -z "${disk_path}" ]] && continue
    parse_rbd_uri "${disk_path}"
    build_rbd_cmd

    timeout 30s "${RBD_CMD[@]}" info "${RBD_IMAGE}" >/dev/null 2>&1 || fail "Failed to access RBD image ${RBD_IMAGE}"
    timeout 30s "${RBD_CMD[@]}" snap create "${RBD_IMAGE}@${checkpoint_name}" >/dev/null 2>&1 || fail "Failed to create RBD snapshot ${RBD_IMAGE}@${checkpoint_name}"

    local output_file="${dest}/disk${index}.raw"
    if [[ "${backup_type}" == "INCREMENTAL" ]]; then
      timeout 6h "${RBD_CMD[@]}" export-diff --from-snap "${parent_checkpoint_name}" "${RBD_IMAGE}@${checkpoint_name}" "${output_file}" >/dev/null 2>&1 || fail "Failed to export incremental RBD diff for ${RBD_IMAGE}"
    else
      timeout 6h "${RBD_CMD[@]}" export "${RBD_IMAGE}@${checkpoint_name}" "${output_file}" >/dev/null 2>&1 || fail "Failed to export full RBD snapshot ${RBD_IMAGE}"
    fi
    index=$((index + 1))
  done < <(split_csv "${disk_paths_csv}")

  write_state_file "${dest}/engine.meta" \
    BACKUP_ENGINE "${BACKUP_ENGINE_RBD_DIFF}" \
    DISK_PATHS "${disk_paths_csv}"
}

stage_vm_backup() {
  local vm_name="$1"
  local engine="$2"
  local vm_dir="${BACKUP_ROOT}/${vm_name}"
  local dest="${vm_dir}/${SESSION_TIMESTAMP}"
  local disk_paths_csv=""
  local parent_checkpoint_name=""
  local previous_checkpoint_name=""
  local backup_type="FULL"
  local checkpoint_name="nbu_${SESSION_TIMESTAMP}"
  local disk_layout_signature=""
  checkpoint_name="${checkpoint_name//./_}"

  mkdir -p "${vm_dir}"
  rm -rf "${dest}"
  mkdir -p "${dest}"

  while IFS='|' read -r _target disk_path; do
    [[ -z "${disk_path}" ]] && continue
    if [[ -n "${disk_paths_csv}" ]]; then
      disk_paths_csv+=","
    fi
    disk_paths_csv+="${disk_path}"
  done < <(collect_disk_map "${vm_name}")

  disk_layout_signature="$(build_disk_layout_signature "${vm_name}")"

  local state_file
  state_file="$(vm_state_file "${vm_name}")"
  local chain_depth=0
  if load_state_file "${state_file}"; then
    previous_checkpoint_name="${LAST_CHECKPOINT_NAME:-}"
    chain_depth="${CHAIN_DEPTH:-0}"
    if [[ -n "${previous_checkpoint_name}" ]] && ! is_positive_integer "${chain_depth}"; then
      log -ne "Invalid CHAIN_DEPTH for VM ${vm_name}: ${chain_depth}. Forcing FULL backup."
      previous_checkpoint_name=""
      chain_depth=0
    fi

    if [[ -z "${previous_checkpoint_name}" && "${chain_depth}" != "0" ]]; then
      log -ne "Missing LAST_CHECKPOINT_NAME with CHAIN_DEPTH=${chain_depth} for VM ${vm_name}. Forcing FULL backup."
      chain_depth=0
    fi

    if [[ -n "${LAST_DISK_LAYOUT_SIGNATURE:-}" ]] &&
      [[ "${LAST_DISK_LAYOUT_SIGNATURE}" != "${disk_layout_signature}" ]]; then
      log -ne "Disk layout changed for VM ${vm_name}. Previous signature differs from current layout. Forcing FULL backup."
      previous_checkpoint_name=""
      chain_depth=0
    fi

    if [[ "${LAST_BACKUP_ENGINE:-}" == "${engine}" ]]; then
      parent_checkpoint_name="${LAST_CHECKPOINT_NAME:-}"
    fi
  fi

  if [[ "${engine}" == "${BACKUP_ENGINE_QCOW2}" ]]; then
    if ! virsh_checkpoint_exists "${vm_name}" "${parent_checkpoint_name}"; then
      parent_checkpoint_name=""
    fi
  else
    if ! rbd_checkpoint_exists_for_all_disks "${disk_paths_csv}" "${parent_checkpoint_name}"; then
      parent_checkpoint_name=""
    fi
  fi

  local max_chain="${MAX_INCREMENTAL_CHAIN:-10}"

  if [[ -n "${parent_checkpoint_name}" ]] &&
    [[ "${chain_depth}" -lt "${max_chain}" ]]; then

    backup_type="INCREMENTAL"
    chain_depth=$((chain_depth + 1))
  else
    backup_type="FULL"
    parent_checkpoint_name=""
    chain_depth=1
  fi

  log -ne "Staging VM ${vm_name} engine=${engine} type=${backup_type} checkpoint=${checkpoint_name} parent=${parent_checkpoint_name}"

  if [[ "${engine}" == "${BACKUP_ENGINE_QCOW2}" ]]; then
    create_qcow2_incremental_backup "${vm_name}" "${dest}" "${checkpoint_name}" "${parent_checkpoint_name}" "${backup_type}" "${disk_paths_csv}"
  else
    create_rbd_incremental_backup "${vm_name}" "${dest}" "${checkpoint_name}" "${parent_checkpoint_name}" "${backup_type}" "${disk_paths_csv}"
  fi

  backup_domain_information "${vm_name}" "${dest}" "${backup_type}" "${checkpoint_name}" "${parent_checkpoint_name}" "${engine}" "${disk_paths_csv}"

  mkdir -p "${PENDING_DIR}"
  write_state_file "$(pending_vm_state_file "${vm_name}")" \
    VM_NAME "${vm_name}" \
    SESSION_DIR "${dest}" \
    LAST_BACKUP_ENGINE "${engine}" \
    NEXT_CHECKPOINT_NAME "${checkpoint_name}" \
    PREVIOUS_COMMITTED_CHECKPOINT_NAME "${previous_checkpoint_name}" \
    PARENT_CHECKPOINT_NAME "${parent_checkpoint_name}" \
    DISK_PATHS "${disk_paths_csv}" \
    CHAIN_DEPTH "${chain_depth}" \
    DISK_LAYOUT_SIGNATURE "${disk_layout_signature}"

  append_manifest_line "${vm_name}" "${dest}" "${engine}" "${checkpoint_name}" "${parent_checkpoint_name}"
}

cleanup_rbd_snapshot() {
  local disk_paths_csv="$1"
  local checkpoint_name="$2"
  local disk_path

  [[ -n "${checkpoint_name}" ]] || return 0

  while IFS= read -r disk_path; do
    [[ -z "${disk_path}" ]] && continue
    parse_rbd_uri "${disk_path}"
    build_rbd_cmd
    "${RBD_CMD[@]}" snap rm "${RBD_IMAGE}@${checkpoint_name}" >/dev/null 2>&1 || true
  done < <(split_csv "${disk_paths_csv}")
}

cleanup_virsh_checkpoint() {
  local vm_name="$1"
  local checkpoint_name="$2"
  [[ -n "${checkpoint_name}" ]] || return 0
  virsh -c "${LIBVIRT_URI}" checkpoint-delete --domain "${vm_name}" --checkpointname "${checkpoint_name}" --metadata >/dev/null 2>&1 || true
}

commit_vm_state() {
  local vm_name="$1"
  local engine="$2"
  local checkpoint_name="$3"
  local chain_depth="$4"
  local disk_layout_signature="$5"
  write_state_file "$(vm_state_file "${vm_name}")" \
    LAST_CHECKPOINT_NAME "${checkpoint_name}" \
    LAST_BACKUP_ENGINE "${engine}" \
    LAST_SESSION_TIMESTAMP "${SESSION_TIMESTAMP}" \
    CHAIN_DEPTH "${chain_depth}" \
    LAST_DISK_LAYOUT_SIGNATURE "${disk_layout_signature}"
}

remove_session_dir() {
  local session_dir="$1"
  [[ -d "${session_dir}" ]] || return 0
  rm -rf "${session_dir}"
}
