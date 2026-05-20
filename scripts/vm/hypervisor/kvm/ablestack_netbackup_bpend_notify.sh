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
  log -ne "NetBackup success confirmed; removing staged backup directories"
else
  log -ne "NetBackup success not confirmed; preserving staged backup directories for investigation"
fi

if [[ -f "${MANIFEST_FILE}" ]]; then
  while IFS='|' read -r vm_name session_dir; do
    [[ -z "${vm_name}" ]] && continue

    if [[ "${COMMIT_ALLOWED}" == "true" ]]; then
      remove_session_dir "${session_dir}"
    else
      log -ne "Preserving staged directory for VM ${vm_name} because NetBackup success was not confirmed: ${session_dir}"
    fi
  done < "${MANIFEST_FILE}"
fi

clear_context_in_progress
rm -f "${MANIFEST_FILE}" "${CONTEXT_FILE}"
rmdir "${PENDING_DIR}" >/dev/null 2>&1 || true
sync

log -ne "NetBackup post-backup cleanup complete status=${JOB_STATUS}"
