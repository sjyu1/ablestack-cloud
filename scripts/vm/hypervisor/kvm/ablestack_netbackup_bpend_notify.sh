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
source "${SCRIPT_DIR}/ablestack_netbackup_common.sh"

usage() {
  cat <<'EOF'
Usage:
  ablestack_netbackup_bpend_notify.sh [policy] [schedule] [client] [status]

Environment overrides:
  BACKUP_ROOT      Default: /var/lib/ablestack/netbackup/staging
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
resolve_context "${1:-}" "${2:-}" "${3:-}" ""
JOB_STATUS="$(resolve_status "$@")"
acquire_lock

if ! load_state_file "${CONTEXT_FILE}"; then
  fail "NetBackup post-backup cleanup could not find context state: ${CONTEXT_FILE}"
fi

SESSION_TIMESTAMP="${SESSION_TIMESTAMP:-$(generate_timestamp)}"
log -ne "NetBackup post-backup cleanup start policy=${POLICY_NAME} schedule=${SCHEDULE_NAME} client=${CLIENT_NAME} status=${JOB_STATUS}"

COMMIT_ALLOWED=false
if netbackup_job_success_confirmed; then
  COMMIT_ALLOWED=true
  log -ne "NetBackup success confirmed; committing new incremental checkpoints"
else
  log -ne "NetBackup success not confirmed; preserving previous incremental checkpoints and rolling back new checkpoints"
fi

if [[ -f "${MANIFEST_FILE}" ]]; then
  while IFS='|' read -r vm_name session_dir engine checkpoint_name _parent_checkpoint_name; do
    [[ -z "${vm_name}" ]] && continue
    pending_file="$(pending_vm_state_file "${vm_name}")"

    if load_state_file "${pending_file}"; then
      if [[ "${COMMIT_ALLOWED}" == "true" ]]; then
        commit_vm_state "${VM_NAME}" "${LAST_BACKUP_ENGINE}" "${NEXT_CHECKPOINT_NAME}" "${CHAIN_DEPTH}" "${DISK_LAYOUT_SIGNATURE:-}"

        if [[ "${LAST_BACKUP_ENGINE}" == "${BACKUP_ENGINE_RBD_DIFF}" ]]; then
          cleanup_rbd_snapshot "${DISK_PATHS}" "${PREVIOUS_COMMITTED_CHECKPOINT_NAME}"
        else
          cleanup_virsh_checkpoint "${VM_NAME}" "${PREVIOUS_COMMITTED_CHECKPOINT_NAME}"
        fi
      else
        if [[ "${LAST_BACKUP_ENGINE}" == "${BACKUP_ENGINE_RBD_DIFF}" ]]; then
          cleanup_rbd_snapshot "${DISK_PATHS}" "${NEXT_CHECKPOINT_NAME}"
        else
          cleanup_virsh_checkpoint "${VM_NAME}" "${NEXT_CHECKPOINT_NAME}"
        fi
      fi
      rm -f "${pending_file}"
    else
      log -ne "Pending state file missing for VM ${vm_name}: ${pending_file}"
    fi

    remove_session_dir "${session_dir}"
  done < "${MANIFEST_FILE}"
fi

clear_context_in_progress
rm -f "${MANIFEST_FILE}" "${CONTEXT_FILE}"
rmdir "${PENDING_DIR}" >/dev/null 2>&1 || true
sync

log -ne "NetBackup post-backup cleanup complete status=${JOB_STATUS}"
