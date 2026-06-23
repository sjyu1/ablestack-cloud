#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

MOLD_URL="${MOLD_URL:-}"
ADMIN_APIKEY="${ADMIN_APIKEY:-}"
ADMIN_SECRETKEY="${ADMIN_SECRETKEY:-}"
RESTORE_SCRIPT_OUTPUT_DIR="${RESTORE_SCRIPT_OUTPUT_DIR:-/usr/openv/netbackup/bin}"
RESTORE_CONFIG_OUTPUT_DIR="${RESTORE_CONFIG_OUTPUT_DIR:-/etc/ablestack/netbackup}"
RESTORE_SECRET_OUTPUT_DIR="${RESTORE_SECRET_OUTPUT_DIR:-${RESTORE_CONFIG_OUTPUT_DIR}/secrets}"
NETBACKUP_SERVER_SECRET_KEY_FILE="${NETBACKUP_SERVER_SECRET_KEY_FILE:-${RESTORE_CONFIG_OUTPUT_DIR}/ablestack.key}"
LOG_FILE="${LOG_FILE:-/var/log/netbackup-mold-restore.log}"
NETBACKUP_STAGING_ROOT="${NETBACKUP_STAGING_ROOT:-/tmp/mold/netbackup}"
MOLD_RESTORE_API_METHOD="${MOLD_RESTORE_API_METHOD:-POST}"
MOLD_RESTORE_MODE="${MOLD_RESTORE_MODE:-live}"
MOLD_API_RESPONSE_FORMAT="${MOLD_API_RESPONSE_FORMAT:-json}"
CONFIG_FILE="${RESTORE_CONFIG_OUTPUT_DIR}/restore.conf"
SECRET_FILE="${RESTORE_SECRET_OUTPUT_DIR}/secret.enc"
SECRET_KEY_CONTENT="QWJsZWNsb3VkMSE="

usage() {
  cat <<EOF
Usage: $(basename "$0")

Interactive NetBackup configuration launcher for Linux NetBackup servers.
This script generates restore.conf, secret.enc, and netbackup-server-restore-notify shell files without Python.
EOF
}

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

require_commands() {
  local missing=()
  local command_name
  for command_name in "$@"; do
    command -v "${command_name}" >/dev/null 2>&1 || missing+=("${command_name}")
  done
  [[ ${#missing[@]} -eq 0 ]] || fail "Required command(s) not found: ${missing[*]}"
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

join_bytes_hex() {
  local left_hex="$1"
  local right_hex="$2"
  printf '%s%s' "${left_hex}" "${right_hex}"
}

raw_to_hex() {
  od -An -tx1 -v | tr -d ' \n'
}

hex_to_raw() {
  local hex
  hex="$(printf '%s' "$1" | tr -d '[:space:]')"
  [[ $(( ${#hex} % 2 )) -eq 0 ]] || fail "Invalid hex string length."
  printf '%b' "$(printf '%s' "${hex}" | sed 's/../\\x&/g')"
}

hex_to_b64() {
  hex_to_raw "$1" | base64 | tr -d '\n'
}

base64_to_hex() {
  printf '%s' "$1" | base64 -d | raw_to_hex
}

encrypt_secret() {
  local secret_key_file="$1"
  local plaintext="$2"
  local iterations=200000
  local salt_hex key_iv_lines key_hex iv_hex ciphertext_hex ciphertext_b64 mac_key_hex mac_hex

  salt_hex="$(openssl rand -hex 8)"
  key_iv_lines="$(openssl enc -aes-256-cbc -pbkdf2 -iter "${iterations}" -md sha256 -S "${salt_hex}" -pass file:"${secret_key_file}" -P)"
  key_hex="$(printf '%s\n' "${key_iv_lines}" | sed -n 's/^key=//p' | head -n1 | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')"
  iv_hex="$(printf '%s\n' "${key_iv_lines}" | sed -n 's/^iv *=//p' | head -n1 | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')"
  ciphertext_b64="$(printf '%s' "${plaintext}" | openssl enc -aes-256-cbc -K "${key_hex}" -iv "${iv_hex}" -nosalt | base64 | tr -d '\n')"
  ciphertext_hex="$(base64_to_hex "${ciphertext_b64}")"
  mac_key_hex="$(hex_to_raw "${key_hex}${salt_hex}" | openssl dgst -sha256 -binary | raw_to_hex)"
  mac_hex="$(hex_to_raw "$(join_bytes_hex "${iv_hex}" "${ciphertext_hex}")" | openssl dgst -sha256 -mac HMAC -macopt "hexkey:${mac_key_hex}" -binary | raw_to_hex)"

  printf '{'
  printf '"version":1,'
  printf '"iterations":%s,' "${iterations}"
  printf '"salt":"%s",' "$(hex_to_b64 "${salt_hex}")"
  printf '"iv":"%s",' "$(hex_to_b64 "${iv_hex}")"
  printf '"ciphertext":"%s",' "${ciphertext_b64}"
  printf '"mac":"%s"' "$(hex_to_b64 "${mac_hex}")"
  printf '}'
}

write_restore_config() {
  mkdir -p "${RESTORE_CONFIG_OUTPUT_DIR}" "${RESTORE_SECRET_OUTPUT_DIR}" "$(dirname "${LOG_FILE}")"
  cat > "${CONFIG_FILE}" <<EOF
MOLD_URL="${MOLD_URL}"
ADMIN_APIKEY="${ADMIN_APIKEY}"
MOLD_SECRET_FILE="${SECRET_FILE}"
SECRET_KEY_FILE="${NETBACKUP_SERVER_SECRET_KEY_FILE}"
LOG_FILE="${LOG_FILE}"
NETBACKUP_STAGING_ROOT="${NETBACKUP_STAGING_ROOT}"
EOF
  chmod 600 "${CONFIG_FILE}"
}

write_secret_key_file() {
  mkdir -p "$(dirname "${NETBACKUP_SERVER_SECRET_KEY_FILE}")"
  printf '%s\n' "${SECRET_KEY_CONTENT}" > "${NETBACKUP_SERVER_SECRET_KEY_FILE}"
  chmod 600 "${NETBACKUP_SERVER_SECRET_KEY_FILE}"
}

write_secret_file() {
  local encrypted
  encrypted="$(encrypt_secret "${NETBACKUP_SERVER_SECRET_KEY_FILE}" "${ADMIN_SECRETKEY}")"
  printf '%s\n' "${encrypted}" > "${SECRET_FILE}"
  chmod 600 "${SECRET_FILE}"
}

copy_restore_notify_files() {
  local sources=(
    "${SCRIPT_DIR}/netbackup-server-restore-notify"
  )
  mkdir -p "${RESTORE_SCRIPT_OUTPUT_DIR}"
  for source in "${sources[@]}"; do
    [[ -f "${source}" ]] || fail "Required restore notify source file not found: ${source}"
    cp -f "${source}" "${RESTORE_SCRIPT_OUTPUT_DIR}/restore_notify"
  done
  chmod 755 "${RESTORE_SCRIPT_OUTPUT_DIR}/restore_notify"
}

main() {
  if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
  fi
  require_commands openssl base64 od sed tr cp chmod mkdir

  prompt_value MOLD_URL "MOLD_URL" "${MOLD_URL}"
  prompt_value ADMIN_APIKEY "ADMIN_APIKEY" "${ADMIN_APIKEY}"
  prompt_secret_value ADMIN_SECRETKEY "ADMIN_SECRETKEY"

  [[ -n "${MOLD_URL}" ]] || fail "MOLD_URL is required."
  [[ -n "${ADMIN_APIKEY}" ]] || fail "ADMIN_APIKEY is required."
  [[ -n "${ADMIN_SECRETKEY}" ]] || fail "ADMIN_SECRETKEY is required."

  write_secret_key_file
  write_restore_config
  write_secret_file
  copy_restore_notify_files

  printf '\nGenerated NetBackup server files:\n'
  printf '  Restore cfg: %s\n' "${CONFIG_FILE}"
  printf '  Secret key : %s\n' "${NETBACKUP_SERVER_SECRET_KEY_FILE}"
  printf '  Secret(enc): %s\n' "${SECRET_FILE}"
  printf '  Scripts dir: %s\n' "${RESTORE_SCRIPT_OUTPUT_DIR}"
}

main "$@"
