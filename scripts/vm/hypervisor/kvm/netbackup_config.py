#!/usr/bin/env python3

import argparse
import base64
import hashlib
import hmac
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import time
from pathlib import Path
from typing import Optional
from urllib.parse import quote_plus
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError


HOOK_OUTPUT_DIR = Path(os.environ.get("HOOK_OUTPUT_DIR", "/usr/openv/netbackup/bin"))
CONFIG_OUTPUT_DIR = Path(os.environ.get("CONFIG_OUTPUT_DIR", "/etc/ablestack/netbackup"))
SECRET_OUTPUT_DIR = Path(os.environ.get("SECRET_OUTPUT_DIR", "/etc/ablestack/netbackup/secrets"))
BACKUP_STAGING_ROOT = Path(os.environ.get("BACKUP_STAGING_ROOT", "/tmp/mold/netbackup"))
PRE_HELPER_PATH = os.environ.get("PRE_HELPER_PATH", "/usr/share/cloudstack-common/scripts/vm/hypervisor/kvm/ablestack_netbackup_bpstart_notify.sh")
POST_HELPER_PATH = os.environ.get("POST_HELPER_PATH", "/usr/share/cloudstack-common/scripts/vm/hypervisor/kvm/ablestack_netbackup_bpend_notify.sh")
HOOK_LOG_PATH = os.environ.get("HOOK_LOG_PATH", "/var/log/netbackup-mold-hook.log")
SECRET_CIPHER = os.environ.get("SECRET_CIPHER", "aes-256-cbc")
SECRET_KEY_FILE = Path(os.environ.get("SECRET_KEY_FILE", "/root/.ssh/ablestack.key"))
NETBACKUP_BP_CONF_PATH = Path(os.environ.get("NETBACKUP_BP_CONF_PATH", "/usr/openv/netbackup/bp.conf"))
NETBACKUP_SERVICE_NAME = os.environ.get("NETBACKUP_SERVICE_NAME", "netbackup")
MOLD_API_RESPONSE_FORMAT = "json"
NETBACKUP_PROVIDER_DISPLAY_NAME = "netbackup"
NETBACKUP_PROVIDER_CANONICAL_NAME = "ablestack-netbackup"
NETBACKUP_OFFERING_NAME = "netbackup"
NETBACKUP_OFFERING_DESCRIPTION = "netbackup"
NETBACKUP_OFFERING_EXTERNAL_ID = "netbackup"


def log(message: str) -> None:
    print(message, file=sys.stderr)


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def backup_existing_file(target: Path) -> None:
    if not target.exists():
        return
    stamp = time.strftime("%Y%m%d%H%M%S")
    backup = target.with_name(f"{target.name}.bak.{stamp}")
    shutil.move(str(target), str(backup))
    print(f"Backed up existing file: {target} -> {backup}")


def copy_existing_file_backup(target: Path) -> None:
    if not target.exists():
        return
    stamp = time.strftime("%Y%m%d%H%M%S")
    backup = target.with_name(f"{target.name}.bak.{stamp}")
    shutil.copy2(str(target), str(backup))
    print(f"Backed up existing file: {target} -> {backup}")


def validate_secret_key_file() -> None:
    if not SECRET_KEY_FILE.is_file():
        fail(f"Secret key file not found: {SECRET_KEY_FILE}")
    mode = stat.S_IMODE(SECRET_KEY_FILE.stat().st_mode)
    if mode != 0o600:
        fail(f"Secret key file must have permission 600: {SECRET_KEY_FILE} (current: {oct(mode)[2:]})")


def url_encode(value: str) -> str:
    return quote_plus(value)


def build_api_params(command_name: str, params: dict[str, str]) -> str:
    tokens = [f"command={url_encode(command_name)}"]
    for key, value in params.items():
        tokens.append(f"{key}={url_encode(str(value))}")
    tokens.append(f"response={url_encode(MOLD_API_RESPONSE_FORMAT)}")
    return "&".join(tokens)


def build_signed_url(base_url: str, api_params: str, api_key: str, secret_key: str) -> str:
    sorted_params = [f"apikey={quote_plus(api_key).lower()}"]
    for token in api_params.split("&"):
        key, value = token.split("=", 1)
        sorted_params.append(f"{key.lower()}={value.lower()}")
    sorted_url = "&".join(sorted(sorted_params))
    signature = base64.b64encode(hmac.new(secret_key.encode(), sorted_url.encode(), hashlib.sha256).digest()).decode()
    encoded_signature = quote_plus(signature)
    return f"{base_url}?{api_params}&apiKey={quote_plus(api_key)}&signature={encoded_signature}"


def invoke_mold_api(method: str, command_name: str, params: dict[str, str], mold_url: str, api_key: str, secret_key: str) -> dict:
    log(f"Calling Mold API: command={command_name} method={method}")
    api_params = build_api_params(command_name, params)
    signed_url = build_signed_url(mold_url, api_params, api_key, secret_key)
    request = Request(signed_url, method=method.upper(), headers={
        "Accept": "application/json",
        "Content-type": "application/x-www-form-urlencoded",
    })
    try:
        with urlopen(request, timeout=300) as response:
            raw = response.read().decode("utf-8")
    except HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        fail(f"Mold API call failed: command={command_name} method={method} code={exc.code} body={body[:240]}")
    except URLError as exc:
        fail(f"Mold API call failed: command={command_name} method={method} error={exc}")

    if not raw.strip():
        fail(f"Mold API returned empty response: command={command_name} method={method}")
    try:
        return json.loads(raw)
    except Exception as exc:
        preview = raw.strip().replace("\n", "\\n")
        if len(preview) > 240:
            preview = preview[:240] + "..."
        fail(f"Failed to parse Mold API response as JSON: {exc}\nResponse preview: {preview}")
    return {}


def get_configuration_value(config_name: str, mold_url: str, api_key: str, secret_key: str, zone_id: Optional[str] = None) -> str:
    params = {"listAll": "true", "pagesize": "20", "page": "1", "name": config_name}
    if zone_id:
        params["zoneid"] = zone_id
    data = invoke_mold_api("GET", "listConfigurations", params, mold_url, api_key, secret_key)
    configs = data.get("listconfigurationsresponse", {}).get("configuration", [])
    if isinstance(configs, dict):
        configs = [configs]
    for cfg in configs:
        if str(cfg.get("name", "")).lower() == config_name.lower():
            return str(cfg.get("value", ""))
    return ""


def update_configuration_value(config_name: str, config_value: str, mold_url: str, api_key: str, secret_key: str, zone_id: Optional[str] = None) -> None:
    params = {"name": config_name, "value": config_value}
    if zone_id:
        params["zoneid"] = zone_id
    invoke_mold_api("POST", "updateConfiguration", params, mold_url, api_key, secret_key)


def append_provider_if_missing(provider_list: str, provider_name: str) -> str:
    items = [item.strip() for item in provider_list.split(",") if item.strip()]
    lowered = {item.lower() for item in items}
    if provider_name.lower() not in lowered:
        items.append(provider_name)
    return ",".join(items)


def resolve_zone_id(policy_name: str, mold_url: str, api_key: str, secret_key: str) -> str:
    log(f"Resolving zone ID from listHosts using policy/host name={policy_name}")
    data = invoke_mold_api("GET", "listHosts", {
        "listAll": "true",
        "pagesize": "500",
        "page": "1",
        "type": "Routing",
        "keyword": policy_name,
    }, mold_url, api_key, secret_key)
    hosts = data.get("listhostsresponse", {}).get("host", [])
    if isinstance(hosts, dict):
        hosts = [hosts]
    exact = next((host for host in hosts if str(host.get("name", "")).lower() == policy_name.lower()), None)
    if exact is None and hosts:
        exact = hosts[0]
    if not exact or not exact.get("zoneid"):
        fail(f"Unable to resolve zone ID from host/policy name '{policy_name}' via listHosts.")
    zone_id = str(exact["zoneid"])
    log(f"Resolved zone ID: {zone_id}")
    return zone_id


def ensure_backup_framework_configuration(zone_id: str, args: argparse.Namespace) -> None:
    log("Checking zone configuration: backup.framework.enabled")
    current = get_configuration_value("backup.framework.enabled", args.mold_url, args.admin_apikey, args.admin_secretkey, zone_id)
    if current.lower() != "true":
        log("Updating zone configuration: backup.framework.enabled=true")
        update_configuration_value("backup.framework.enabled", "true", args.mold_url, args.admin_apikey, args.admin_secretkey, zone_id)
        print(f"Updated zone configuration: backup.framework.enabled=true (zoneid={zone_id})")

    log("Checking global configuration: backup.enable.attach.detach.of.volumes")
    current = get_configuration_value("backup.enable.attach.detach.of.volumes", args.mold_url, args.admin_apikey, args.admin_secretkey)
    if current.lower() != "true":
        log("Updating global configuration: backup.enable.attach.detach.of.volumes=true")
        update_configuration_value("backup.enable.attach.detach.of.volumes", "true", args.mold_url, args.admin_apikey, args.admin_secretkey)
        print("Updated global configuration: backup.enable.attach.detach.of.volumes=true")

    log("Checking zone configuration: backup.framework.provider.plugin")
    current = get_configuration_value("backup.framework.provider.plugin", args.mold_url, args.admin_apikey, args.admin_secretkey, zone_id)
    updated = append_provider_if_missing(current, NETBACKUP_PROVIDER_DISPLAY_NAME)
    if updated != current:
        log(f"Updating zone configuration: backup.framework.provider.plugin={updated}")
        update_configuration_value("backup.framework.provider.plugin", updated, args.mold_url, args.admin_apikey, args.admin_secretkey, zone_id)
        print(f"Updated zone configuration: backup.framework.provider.plugin={updated} (zoneid={zone_id})")

    log("Checking zone configuration: backup.plugin.netbackup.url")
    current = get_configuration_value("backup.plugin.netbackup.url", args.mold_url, args.admin_apikey, args.admin_secretkey, zone_id)
    if current != args.netbackup_url:
        log(f"Updating zone configuration: backup.plugin.netbackup.url={args.netbackup_url}")
        update_configuration_value("backup.plugin.netbackup.url", args.netbackup_url, args.mold_url, args.admin_apikey, args.admin_secretkey, zone_id)
        print(f"Updated zone configuration: backup.plugin.netbackup.url={args.netbackup_url} (zoneid={zone_id})")

    log("Checking zone configuration: backup.plugin.netbackup.apikey")
    current = get_configuration_value("backup.plugin.netbackup.apikey", args.mold_url, args.admin_apikey, args.admin_secretkey, zone_id)
    if current != args.netbackup_apikey:
        log("Updating zone configuration: backup.plugin.netbackup.apikey=<hidden>")
        update_configuration_value("backup.plugin.netbackup.apikey", args.netbackup_apikey, args.mold_url, args.admin_apikey, args.admin_secretkey, zone_id)
        print(f"Updated zone configuration: backup.plugin.netbackup.apikey=<hidden> (zoneid={zone_id})")


def ensure_netbackup_offering(zone_id: str, args: argparse.Namespace) -> None:
    log(f"Checking existing NetBackup backup offerings for zoneid={zone_id}")
    data = invoke_mold_api("GET", "listBackupOfferings", {
        "listall": "true",
        "page": "1",
        "pagesize": "500",
    }, args.mold_url, args.admin_apikey, args.admin_secretkey)
    offerings = data.get("listbackupofferingsresponse", {}).get("backupoffering", [])
    if isinstance(offerings, dict):
        offerings = [offerings]
    for offering in offerings:
        provider = str(offering.get("provider", "")).lower()
        offering_zone = str(offering.get("zoneid", "") or offering.get("zoneId", ""))
        if provider in {NETBACKUP_PROVIDER_DISPLAY_NAME, NETBACKUP_PROVIDER_CANONICAL_NAME} and offering_zone == zone_id:
            print(f"NetBackup backup offering already exists for zoneid={zone_id}")
            return

    log(f"Importing NetBackup backup offering for zoneid={zone_id}")
    invoke_mold_api("POST", "importBackupOffering", {
        "name": NETBACKUP_OFFERING_NAME,
        "description": NETBACKUP_OFFERING_DESCRIPTION,
        "provider": NETBACKUP_PROVIDER_DISPLAY_NAME,
        "externalid": NETBACKUP_OFFERING_EXTERNAL_ID,
        "allowuserdrivenbackups": "false",
        "zoneid": zone_id,
    }, args.mold_url, args.admin_apikey, args.admin_secretkey)
    print(f"Imported NetBackup backup offering for zoneid={zone_id}")


def write_hook(path: Path, comment_name: str, helper_path: str, is_post: bool) -> None:
    backup_existing_file(path)
    if is_post:
        content = f"""#!/bin/sh

# Generated by netbackup_config.py: {comment_name}

CLIENT="${{1:-}}"
POLICY="${{2:-}}"
SCHEDULE="${{3:-}}"
TYPE="${{4:-}}"
STATUS="${{5:-0}}"

LOG="{HOOK_LOG_PATH}"
HELPER="{helper_path}"

log_line() {{
  printf '%s %s\\n' "$(date '+%F %T')" "$1" >> "$LOG" 2>&1
}}

if [ $# -lt 4 ]; then
  log_line "POST invalid-args argc=$# client=$CLIENT policy=$POLICY schedule=$SCHEDULE type=$TYPE status=$STATUS"
  exit 2
fi

if [ ! -x "$HELPER" ]; then
  log_line "POST helper-missing path=$HELPER"
  exit 3
fi

log_line "POST start client=$CLIENT policy=$POLICY schedule=$SCHEDULE type=$TYPE status=$STATUS"
"$HELPER" "$POLICY" "$SCHEDULE" "$CLIENT" "$STATUS" >> "$LOG" 2>&1
RC=$?
log_line "POST end rc=$RC client=$CLIENT policy=$POLICY schedule=$SCHEDULE type=$TYPE status=$STATUS"
exit $RC
"""
    else:
        content = f"""#!/bin/sh

# Generated by netbackup_config.py: {comment_name}

CLIENT="${{1:-}}"
POLICY="${{2:-}}"
SCHEDULE="${{3:-}}"
TYPE="${{4:-}}"

LOG="{HOOK_LOG_PATH}"
HELPER="{helper_path}"

log_line() {{
  printf '%s %s\\n' "$(date '+%F %T')" "$1" >> "$LOG" 2>&1
}}

if [ $# -lt 4 ]; then
  log_line "PRE invalid-args argc=$# client=$CLIENT policy=$POLICY schedule=$SCHEDULE type=$TYPE"
  exit 2
fi

if [ ! -x "$HELPER" ]; then
  log_line "PRE helper-missing path=$HELPER"
  exit 3
fi

log_line "PRE start client=$CLIENT policy=$POLICY schedule=$SCHEDULE type=$TYPE"
"$HELPER" "$POLICY" "$SCHEDULE" "$CLIENT" >> "$LOG" 2>&1
RC=$?
log_line "PRE end rc=$RC client=$CLIENT policy=$POLICY schedule=$SCHEDULE type=$TYPE"
exit $RC
"""
    path.write_text(content, encoding="utf-8")
    path.chmod(0o755)


def write_config_file(path: Path, args: argparse.Namespace) -> None:
    backup_existing_file(path)
    content = (
        f'VM_INCLUDE="{args.vm_include}"\n'
        f'VM_EXCLUDE="{args.vm_exclude}"\n'
        f'MAX_INCREMENTAL_CHAIN={args.max_incremental_chain}\n'
        f'MOLD_URL="{args.mold_url}"\n'
        f'ADMIN_APIKEY="{args.admin_apikey}"\n'
    )
    path.write_text(content, encoding="utf-8")
    path.chmod(0o600)


def write_encrypted_secret_file(path: Path, secret: str) -> None:
    validate_secret_key_file()
    backup_existing_file(path)
    env = os.environ.copy()
    proc = subprocess.run(
        [
            "openssl", "enc", f"-{SECRET_CIPHER}", "-pbkdf2", "-salt",
            "-pass", f"file:{SECRET_KEY_FILE}", "-out", str(path)
        ],
        input=secret.encode(),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
        env=env,
    )
    if proc.returncode != 0:
        fail(f"Failed to encrypt ADMIN_SECRETKEY: {proc.stderr.decode(errors='replace')}")
    path.chmod(0o600)


def ensure_backup_staging_root() -> None:
    BACKUP_STAGING_ROOT.mkdir(parents=True, exist_ok=True)
    os.chown(BACKUP_STAGING_ROOT, 0, 0)
    BACKUP_STAGING_ROOT.chmod(0o755)


def apply_permissions() -> None:
    if CONFIG_OUTPUT_DIR.is_dir():
        os.chown(CONFIG_OUTPUT_DIR, 0, 0)
        CONFIG_OUTPUT_DIR.chmod(0o700)
        for conf in CONFIG_OUTPUT_DIR.glob("*.conf"):
            conf.chmod(0o600)


def set_bp_conf_value(path: Path, key: str, value: str) -> None:
    lines = path.read_text(encoding="utf-8").splitlines()
    pattern = re.compile(rf"^\s*{re.escape(key)}\s*=")
    replaced = False
    new_lines = []
    for line in lines:
        if pattern.match(line):
            new_lines.append(f"{key} = {value}")
            replaced = True
        else:
            new_lines.append(line)
    if not replaced:
        new_lines.append(f"{key} = {value}")
    path.write_text("\n".join(new_lines) + "\n", encoding="utf-8")


def apply_netbackup_bp_conf() -> bool:
    if not NETBACKUP_BP_CONF_PATH.is_file():
        print(f"NetBackup bp.conf not found: {NETBACKUP_BP_CONF_PATH}")
        return False
    copy_existing_file_backup(NETBACKUP_BP_CONF_PATH)
    set_bp_conf_value(NETBACKUP_BP_CONF_PATH, "BPSTART_TIMEOUT", "14400")
    set_bp_conf_value(NETBACKUP_BP_CONF_PATH, "BPEND_TIMEOUT", "3600")
    set_bp_conf_value(NETBACKUP_BP_CONF_PATH, "CLIENT_READ_TIMEOUT", "21600")
    set_bp_conf_value(NETBACKUP_BP_CONF_PATH, "CLIENT_CONNECT_TIMEOUT", "1800")
    set_bp_conf_value(NETBACKUP_BP_CONF_PATH, "SERVER_CONNECT_TIMEOUT", "1800")
    print(f"Updated NetBackup config: {NETBACKUP_BP_CONF_PATH}")
    return True


def restart_netbackup_service() -> None:
    if shutil.which("systemctl"):
        subprocess.run(["systemctl", "restart", NETBACKUP_SERVICE_NAME], check=True)
        print(f"Restarted NetBackup service via systemctl: {NETBACKUP_SERVICE_NAME}")
        return
    if shutil.which("service"):
        subprocess.run(["service", NETBACKUP_SERVICE_NAME, "restart"], check=True)
        print(f"Restarted NetBackup service via service: {NETBACKUP_SERVICE_NAME}")
        return
    print(f"NetBackup service restart command not found. Please restart manually: {NETBACKUP_SERVICE_NAME}")


def generate_outputs(zone_id: str, args: argparse.Namespace) -> None:
    log("Generating local NetBackup hook/config files")
    HOOK_OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    CONFIG_OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    SECRET_OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    pre_hook = HOOK_OUTPUT_DIR / f"bpstart_notify.{args.policy_name}"
    post_hook = HOOK_OUTPUT_DIR / f"bpend_notify.{args.policy_name}"
    config_path = CONFIG_OUTPUT_DIR / f"{args.policy_name}.conf"
    secret_path = SECRET_OUTPUT_DIR / "secret.enc"

    write_hook(pre_hook, f"bpstart_notify.{args.policy_name}", PRE_HELPER_PATH, False)
    write_hook(post_hook, f"bpend_notify.{args.policy_name}", POST_HELPER_PATH, True)
    write_config_file(config_path, args)
    write_encrypted_secret_file(secret_path, args.admin_secretkey)
    ensure_backup_staging_root()
    apply_permissions()
    if apply_netbackup_bp_conf():
        restart_netbackup_service()
    else:
        print("Skipped NetBackup service restart because bp.conf was not found.")

    print("\nGenerated files:")
    print(f"  PRE hook   : {pre_hook}")
    print(f"  POST hook  : {post_hook}")
    print(f"  Config     : {config_path}")
    print(f"  Secret(enc): {secret_path}")
    print(f"  Staging dir: {BACKUP_STAGING_ROOT}")
    print(f"  Zone ID    : {zone_id}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="NetBackup configuration helper")
    parser.add_argument("--policy-name", required=True)
    parser.add_argument("--vm-include", required=True)
    parser.add_argument("--vm-exclude", required=True)
    parser.add_argument("--max-incremental-chain", required=True, type=int)
    parser.add_argument("--mold-url", required=True)
    parser.add_argument("--admin-apikey", required=True)
    parser.add_argument("--admin-secretkey", required=True)
    parser.add_argument("--netbackup-url", required=True)
    parser.add_argument("--netbackup-apikey", required=True)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    log("Starting Mold API configuration for NetBackup")
    zone_id = resolve_zone_id(args.policy_name, args.mold_url, args.admin_apikey, args.admin_secretkey)
    ensure_backup_framework_configuration(zone_id, args)
    ensure_netbackup_offering(zone_id, args)
    log("Completed Mold API configuration for NetBackup")
    generate_outputs(zone_id, args)


if __name__ == "__main__":
    main()
