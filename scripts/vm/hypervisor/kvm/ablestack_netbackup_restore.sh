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

RESTORE_DIR="${1:-}"
LOG_FILE="${LOG_FILE:-/var/log/netbackup-cloudstack-restore.log}"
CLOUDSTACK_API_ENDPOINT="${CLOUDSTACK_API_ENDPOINT:-}"
CLOUDSTACK_API_KEY="${CLOUDSTACK_API_KEY:-}"
CLOUDSTACK_SECRET_KEY="${CLOUDSTACK_SECRET_KEY:-}"
CLOUDSTACK_RESTORE_MODE="${CLOUDSTACK_RESTORE_MODE:-validate-only}"

log_line() {
  printf '%s %s\n' "$(date '+%F %T')" "$1" >> "${LOG_FILE}" 2>&1
}

fail() {
  log_line "RESTORE helper error: $*"
  exit 1
}

load_meta() {
  local meta_file="$1"
  [[ -f "${meta_file}" ]] || fail "missing metadata file ${meta_file}"
  # shellcheck disable=SC1090
  source "${meta_file}"
}

require_meta_value() {
  local key="$1"
  local value="$2"
  [[ -n "${value}" ]] || fail "missing required metadata ${key}"
}

build_restore_payload() {
  cat <<EOF
framework=${BACKUP_FRAMEWORK}
vm_name=${VM_NAME}
backup_type=${BACKUP_TYPE}
checkpoint_name=${CHECKPOINT_NAME}
parent_checkpoint_name=${PARENT_CHECKPOINT_NAME}
backup_engine=${BACKUP_ENGINE}
policy_name=${POLICY_NAME}
schedule_name=${SCHEDULE_NAME}
session_timestamp=${SESSION_TIMESTAMP}
restore_dir=${RESTORE_DIR}
restore_mode=${CLOUDSTACK_RESTORE_MODE}
EOF
}

validate_restore_metadata() {
  require_meta_value "BACKUP_FRAMEWORK" "${BACKUP_FRAMEWORK:-}"
  require_meta_value "VM_NAME" "${VM_NAME:-}"
  require_meta_value "CHECKPOINT_NAME" "${CHECKPOINT_NAME:-}"
  require_meta_value "BACKUP_ENGINE" "${BACKUP_ENGINE:-}"
  require_meta_value "POLICY_NAME" "${POLICY_NAME:-}"
  require_meta_value "SESSION_TIMESTAMP" "${SESSION_TIMESTAMP:-}"

  if [[ "${BACKUP_FRAMEWORK}" != "ABLESTACK_NETBACKUP" ]]; then
    fail "unsupported backup framework ${BACKUP_FRAMEWORK}"
  fi
}

validate_restore_artifacts() {
  [[ -f "${RESTORE_DIR}/domain-config.xml" ]] || fail "missing restored domain-config.xml in ${RESTORE_DIR}"
  [[ -f "${RESTORE_DIR}/engine.meta" ]] || fail "missing restored engine.meta in ${RESTORE_DIR}"
}

invoke_cloudstack_restore() {
  if [[ "${CLOUDSTACK_RESTORE_MODE}" == "validate-only" ]]; then
    log_line "RESTORE validate-only vm=${VM_NAME} checkpoint=${CHECKPOINT_NAME} engine=${BACKUP_ENGINE} dir=${RESTORE_DIR}"
    build_restore_payload >> "${LOG_FILE}" 2>&1
    return 0
  fi

  [[ -n "${CLOUDSTACK_API_ENDPOINT}" ]] || fail "CLOUDSTACK_API_ENDPOINT is required for live restore"
  [[ -n "${CLOUDSTACK_API_KEY}" ]] || fail "CLOUDSTACK_API_KEY is required for live restore"
  [[ -n "${CLOUDSTACK_SECRET_KEY}" ]] || fail "CLOUDSTACK_SECRET_KEY is required for live restore"

  fail "live CloudStack restore API invocation is not implemented yet; validated metadata only"
}

[[ -n "${RESTORE_DIR}" ]] || fail "usage: ablestack_netbackup_restore.sh <restore_dir>"
[[ -d "${RESTORE_DIR}" ]] || fail "restore directory not found: ${RESTORE_DIR}"

load_meta "${RESTORE_DIR}/backup.meta"
validate_restore_metadata
validate_restore_artifacts
invoke_cloudstack_restore
