#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_HELPER="${SCRIPT_DIR}/netbackup_config.py"

POLICY_NAME=""
VM_INCLUDE=""
VM_EXCLUDE=""
MAX_INCREMENTAL_CHAIN=""
MOLD_URL=""
ADMIN_APIKEY=""
ADMIN_SECRETKEY=""
NETBACKUP_URL=""
NETBACKUP_APIKEY=""

usage() {
  cat <<EOF
Usage: $(basename "$0")

Interactive NetBackup configuration launcher.
This script collects inputs and delegates configuration work to netbackup_config.py.
EOF
}

fail() {
  printf '%s\n' "$*" >&2
  exit 1
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

main() {
  if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
  fi

  [[ -f "${PYTHON_HELPER}" ]] || fail "Python helper not found: ${PYTHON_HELPER}"
  collect_inputs

  python3 "${PYTHON_HELPER}" \
    --policy-name "${POLICY_NAME}" \
    --vm-include "${VM_INCLUDE}" \
    --vm-exclude "${VM_EXCLUDE}" \
    --max-incremental-chain "${MAX_INCREMENTAL_CHAIN}" \
    --mold-url "${MOLD_URL}" \
    --admin-apikey "${ADMIN_APIKEY}" \
    --admin-secretkey "${ADMIN_SECRETKEY}" \
    --netbackup-url "${NETBACKUP_URL}" \
    --netbackup-apikey "${NETBACKUP_APIKEY}"
}

main "$@"
