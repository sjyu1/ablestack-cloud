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

SECRET_CIPHER="${SECRET_CIPHER:-aes-256-cbc}"
SECRET_KEY_FILE="${SECRET_KEY_FILE:-/root/.ssh/ablestack.key}"

usage() {
  cat <<EOF
Usage:
  $(basename "$0") decrypt <secret_file>

Environment:
  SECRET_KEY_FILE               Key file used to decrypt the secret file
  SECRET_CIPHER                 OpenSSL cipher, default: aes-256-cbc
EOF
}

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

file_mode() {
  local target="$1"
  if stat -c '%a' "${target}" >/dev/null 2>&1; then
    stat -c '%a' "${target}"
    return 0
  fi
  stat -f '%OLp' "${target}"
}

validate_secret_key_file() {
  [[ -f "${SECRET_KEY_FILE}" ]] || fail "Secret key file not found: ${SECRET_KEY_FILE}"
  [[ -r "${SECRET_KEY_FILE}" ]] || fail "Secret key file is not readable: ${SECRET_KEY_FILE}"

  local mode
  mode="$(file_mode "${SECRET_KEY_FILE}")"
  [[ "${mode}" == "600" ]] || fail "Secret key file must have permission 600: ${SECRET_KEY_FILE} (current: ${mode})"
}

decrypt_secret() {
  local secret_file="$1"
  [[ -f "${secret_file}" ]] || fail "Secret file not found: ${secret_file}"
  validate_secret_key_file

  openssl enc -"${SECRET_CIPHER}" -d -pbkdf2 -pass file:"${SECRET_KEY_FILE}" -in "${secret_file}"
}

ACTION="${1:-}"

case "${ACTION}" in
  decrypt)
    shift
    decrypt_secret "${1:-}"
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    usage
    exit 1
    ;;
esac
