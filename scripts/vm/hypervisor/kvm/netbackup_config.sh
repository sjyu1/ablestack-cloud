#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_HELPER="${SCRIPT_DIR}/netbackup_config.py"

SCOPE="host"
TARGET_OS="linux"
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

Scopes:
  host              Configure host-side backup hooks/config/bp.conf and Mold settings
  netbackup-server  Configure restore_notify files and restore.conf/secret.enc only

Target OS:
  linux             Use Linux NetBackup server default paths
  windows           Use Windows NetBackup server default paths
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
  prompt_value SCOPE "SCOPE (host|netbackup-server)" "${SCOPE}"
  [[ "${SCOPE}" == "host" || "${SCOPE}" == "netbackup-server" ]] || fail "SCOPE must be one of: host, netbackup-server."

  if [[ "${SCOPE}" == "host" ]]; then
    prompt_value POLICY_NAME "POLICY_NAME"
    validate_name "${POLICY_NAME}" "POLICY_NAME"

    prompt_value VM_INCLUDE "VM_INCLUDE" "*"
    prompt_value VM_EXCLUDE "VM_EXCLUDE" ""
    prompt_value MAX_INCREMENTAL_CHAIN "MAX_INCREMENTAL_CHAIN" "10"
    validate_positive_integer "${MAX_INCREMENTAL_CHAIN}" "MAX_INCREMENTAL_CHAIN"
  fi

  prompt_value MOLD_URL "MOLD_URL"
  prompt_value ADMIN_APIKEY "ADMIN_APIKEY"
  prompt_secret_value ADMIN_SECRETKEY "ADMIN_SECRETKEY"
  [[ -n "${ADMIN_SECRETKEY}" ]] || fail "ADMIN_SECRETKEY is required."

  if [[ "${SCOPE}" == "host" ]]; then
    prompt_value NETBACKUP_URL "NETBACKUP_URL" "https://netbackup:1556/netbackup"
    prompt_value NETBACKUP_APIKEY "NETBACKUP_APIKEY"
    [[ -n "${NETBACKUP_URL}" ]] || fail "NETBACKUP_URL is required."
    [[ -n "${NETBACKUP_APIKEY}" ]] || fail "NETBACKUP_APIKEY is required."
  else
    prompt_value TARGET_OS "TARGET_OS (linux|windows)" "${TARGET_OS}"
    [[ "${TARGET_OS}" == "linux" || "${TARGET_OS}" == "windows" ]] || fail "TARGET_OS must be one of: linux, windows."
  fi
}

main() {
  if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
  fi

  [[ -f "${PYTHON_HELPER}" ]] || fail "Python helper not found: ${PYTHON_HELPER}"
  collect_inputs

  local -a cmd=(
    python3 "${PYTHON_HELPER}"
    --scope "${SCOPE}"
    --target-os "${TARGET_OS}"
    --mold-url "${MOLD_URL}"
    --admin-apikey "${ADMIN_APIKEY}"
    --admin-secretkey "${ADMIN_SECRETKEY}"
  )

  if [[ -n "${POLICY_NAME}" ]]; then
    cmd+=(--policy-name "${POLICY_NAME}")
  fi
  if [[ -n "${VM_INCLUDE}" ]]; then
    cmd+=(--vm-include "${VM_INCLUDE}")
  fi
  if [[ -n "${VM_EXCLUDE}" ]]; then
    cmd+=(--vm-exclude "${VM_EXCLUDE}")
  fi
  if [[ -n "${MAX_INCREMENTAL_CHAIN}" ]]; then
    cmd+=(--max-incremental-chain "${MAX_INCREMENTAL_CHAIN}")
  fi
  if [[ -n "${NETBACKUP_URL}" ]]; then
    cmd+=(--netbackup-url "${NETBACKUP_URL}")
  fi
  if [[ -n "${NETBACKUP_APIKEY}" ]]; then
    cmd+=(--netbackup-apikey "${NETBACKUP_APIKEY}")
  fi

  "${cmd[@]}"
}

main "$@"
