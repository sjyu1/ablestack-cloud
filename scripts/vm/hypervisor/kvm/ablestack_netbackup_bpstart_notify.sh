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
  ablestack_netbackup_bpstart_notify.sh [policy] [schedule] [client] [timestamp]

Environment overrides:
  BACKUP_ROOT      Default: /var/lib/ablestack/netbackup/staging
  STATE_ROOT       Default: /var/lib/ablestack/netbackup
  LOG_FILE         Default: /var/log/cloudstack/agent/agent.log
  QUIESCE          Default: false
  NOTE             Increase NetBackup BPSTART_TIMEOUT/CLIENT_READ_TIMEOUT for large VM staging.
  POLICY_NAME
  SCHEDULE_NAME
  CLIENT_NAME
  SESSION_TIMESTAMP
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

ensure_runtime_dirs
resolve_context "${1:-}" "${2:-}" "${3:-}" "${4:-}"
load_policy_schedule_config
acquire_lock
sanity_checks
mark_context_in_progress
trap 'clear_context_in_progress' ERR INT TERM
write_manifest_header

write_state_file "${CONTEXT_FILE}" \
  POLICY_NAME "${POLICY_NAME}" \
  SCHEDULE_NAME "${SCHEDULE_NAME}" \
  CLIENT_NAME "${CLIENT_NAME}" \
  SESSION_TIMESTAMP "${SESSION_TIMESTAMP}"

log -ne "NetBackup pre-backup staging start policy=${POLICY_NAME} schedule=${SCHEDULE_NAME} client=${CLIENT_NAME} timestamp=${SESSION_TIMESTAMP}"

vm_count=0
while IFS= read -r vm_name; do
  [[ -z "${vm_name}" ]] && continue
  vm_count=$((vm_count + 1))
  stage_vm_backup "${vm_name}" "$(detect_backup_engine "${vm_name}")"
done < <(list_target_vms)

if [[ ${vm_count} -eq 0 ]]; then
  log -ne "No running VMs found on host for NetBackup staging"
fi

sync
log -ne "NetBackup pre-backup staging complete count=${vm_count} manifest=${MANIFEST_FILE}"
