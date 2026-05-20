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
NETBACKUP_SECRET_PASSPHRASE="${NETBACKUP_SECRET_PASSPHRASE:-}"

usage() {
  cat <<EOF
Usage:
  $(basename "$0") decrypt <secret_file>

Environment:
  NETBACKUP_SECRET_PASSPHRASE   Passphrase used to decrypt the secret file
  SECRET_CIPHER                 OpenSSL cipher, default: aes-256-cbc
EOF
}

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

decrypt_secret() {
  local secret_file="$1"
  [[ -f "${secret_file}" ]] || fail "Secret file not found: ${secret_file}"
  [[ -n "${NETBACKUP_SECRET_PASSPHRASE}" ]] || fail "NETBACKUP_SECRET_PASSPHRASE is required."

  openssl enc -"${SECRET_CIPHER}" -d -pbkdf2 -pass env:NETBACKUP_SECRET_PASSPHRASE -in "${secret_file}"
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
