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
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/netbackup-host-common.sh"

usage() {
  cat <<'EOF'
Usage:
  netbackup-host-bpend-notify.sh [policy] [schedule] [client] [status]

Environment overrides:
  STATE_ROOT       Default: /var/lib/ablestack/netbackup
  LOG_FILE         Default: /var/log/cloudstack/agent/agent.log
  JOB_STATUS       Client-side bpbkar result code. 0 alone is not treated as full-job success by default.
  NETBACKUP_SUCCESS_CONFIRM_CMD Optional command that returns 0 only when the NetBackup job is fully successful.
  NETBACKUP_JOB_ID Optional NetBackup job id for bpdbjobs validation when bpdbjobs is available.
  NETBACKUP_REQUIRE_JOB_SUCCESS Default: true
  NETBACKUP_COMMIT_ON_CLIENT_STATUS_ZERO Default: false
  POLICY_NAME
  SCHEDULE_NAME
  CLIENT_NAME
EOF
}

cleanup_runtime_backup_paths() {
  local removed=0
  local backup_path=""

  while IFS= read -r backup_path; do
    [[ -z "${backup_path}" ]] && continue
    if [[ -e "${backup_path}" ]]; then
      rm -rf "${backup_path}"
      removed=$((removed + 1))
      log -ne "Removed NetBackup staged backup path ${backup_path}"
    else
      log -ne "NetBackup staged backup path not found, skipping cleanup: ${backup_path}"
    fi
  done < <(list_runtime_success_paths)

  builtin echo "${removed}"
}

update_netbackup_backup_ids() {
  local updated=0
  local vm_id=""
  local backup_path=""
  local response=""
  local member_count=0

  [[ -n "${BACKUP_ID:-}" ]] || fail "NetBackup BACKUP_ID is empty; cannot update backup_details"
  member_count="$(read_runtime_count "success_count" 2>/dev/null || echo 0)"
  [[ "${member_count}" =~ ^[0-9]+$ ]] || member_count=0

  while IFS=$'\t' read -r vm_id backup_path; do
    [[ -z "${vm_id}" || -z "${backup_path}" ]] && continue
    response="$(invoke_mold_api "POST" "${MOLD_CREATE_BACKUP_API_URL}" "updateNetBackup" \
      "virtualmachineid" "${vm_id}" \
      "backupid" "${BACKUP_ID}" \
      "externalid" "${backup_path}" \
      "membercount" "${member_count}" \
      "policyid" "${POLICY_NAME}" \
      "maxbackups" "${MAX_INCREMENTAL_CHAIN}")" || fail "Failed to update NetBackup backup details for vmId=${vm_id} backupPath=${backup_path}"
    updated=$((updated + 1))
    log -ne "Updated NetBackup backup details vmId=${vm_id} backupId=${BACKUP_ID} backupPath=${backup_path} memberCount=${member_count} policyName=${POLICY_NAME} maxChain=${MAX_INCREMENTAL_CHAIN}"
  done < <(list_runtime_success_vm_refs)

  builtin echo "${updated}"
}

resolve_status() {
  local candidate
  if [[ -n "${JOB_STATUS}" ]]; then
    builtin echo "${JOB_STATUS}"
    return
  fi

  for candidate in "$@"; do
    if [[ "${candidate}" =~ ^[0-9]+$ ]]; then
      JOB_STATUS="${candidate}"
    fi
  done

  if [[ -z "${JOB_STATUS}" ]]; then
    JOB_STATUS=0
  fi

  builtin echo "${JOB_STATUS}"
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

ensure_runtime_dirs
cleanup_stale_transient_state
resolve_context "${1:-}" "${2:-}" "${3:-}" ""
cleanup_runtime_history
JOB_STATUS="$(resolve_status "$@")"
acquire_lock

if ! load_state_file "${CONTEXT_FILE}"; then
  fail "NetBackup post-backup cleanup could not find context state: ${CONTEXT_FILE}"
fi

BACKUP_ID="${BACKUPID:-${BACKUP_ID:-}}"
load_policy_schedule_config
SESSION_TIMESTAMP="${SESSION_TIMESTAMP:-$(generate_timestamp)}"
log -ne "NetBackup post-backup finalize start policy=${POLICY_NAME} schedule=${SCHEDULE_NAME} client=${CLIENT_NAME} status=${JOB_STATUS}"

if netbackup_job_success_confirmed; then
  log -ne "NetBackup success confirmed"
  updated_count="$(update_netbackup_backup_ids)"
  log -ne "NetBackup metadata update complete count=${updated_count} backupId=${BACKUP_ID}"
  removed_count="$(cleanup_runtime_backup_paths)"
  update_runtime_status "NBU_CLIENT_SUCCESS_CLEANED"
  log -ne "NetBackup cleanup complete removed=${removed_count}"
else
  log -ne "NetBackup success not confirmed"
  update_runtime_status "NBU_CLIENT_FAILED_PRESERVED"
fi

clear_context_in_progress
rm -f "${CONTEXT_FILE}" "${MOLD_VM_CACHE_FILE:-}"
sync

log -ne "NetBackup post-backup finalize complete status=${JOB_STATUS}"
