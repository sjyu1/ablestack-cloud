#!/usr/bin/env python3

import json
import os
import shlex
import subprocess
import sys
import time
from pathlib import Path
from typing import Any, Optional
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


CONFIG_FILE = Path(os.environ.get("NETBACKUP_WATCHER_CONFIG", "/etc/ablestack/netbackup/restore-watcher.conf"))
DEFAULT_STATE_FILE = Path("/var/lib/ablestack/netbackup/restore-watcher-state.json")
DEFAULT_LOG_FILE = Path("/var/log/netbackup-mold-restore-watcher.log")
NETBACKUP_ACCEPT = "application/vnd.netbackup+json;version=12.0"


def log(message: str) -> None:
    log_file = Path(CONFIG.get("LOG_FILE", str(DEFAULT_LOG_FILE)))
    log_file.parent.mkdir(parents=True, exist_ok=True)
    line = f"{time.strftime('%Y-%m-%d %H:%M:%S')} {message}"
    with log_file.open("a", encoding="utf-8") as handle:
        handle.write(line + "\n")


def load_key_value_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.is_file():
        raise SystemExit(f"Config file not found: {path}")
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = shlex.split(value.strip())[0] if value.strip() else ""
    return values


CONFIG = load_key_value_file(CONFIG_FILE)


def require_config(name: str) -> str:
    value = CONFIG.get(name, "")
    if not value:
        raise SystemExit(f"{name} must be configured in {CONFIG_FILE}")
    return value


def load_state() -> dict[str, Any]:
    state_file = Path(CONFIG.get("STATE_FILE", str(DEFAULT_STATE_FILE)))
    if not state_file.is_file():
        return {"processed": {}}
    try:
        return json.loads(state_file.read_text(encoding="utf-8"))
    except Exception:
        return {"processed": {}}


def save_state(state: dict[str, Any]) -> None:
    state_file = Path(CONFIG.get("STATE_FILE", str(DEFAULT_STATE_FILE)))
    state_file.parent.mkdir(parents=True, exist_ok=True)
    tmp_file = state_file.with_suffix(".tmp")
    tmp_file.write_text(json.dumps(state, sort_keys=True, indent=2), encoding="utf-8")
    tmp_file.replace(state_file)


def prune_state(state: dict[str, Any]) -> None:
    ttl = int(CONFIG.get("PROCESSED_JOB_TTL_SECONDS", "604800"))
    cutoff = int(time.time()) - ttl
    processed = state.setdefault("processed", {})
    for job_id in list(processed.keys()):
        if int(processed[job_id].get("timestamp", 0)) < cutoff:
            processed.pop(job_id, None)


def netbackup_get(path: str, params: Optional[dict[str, str]] = None) -> Any:
    base_url = require_config("NETBACKUP_URL").rstrip("/")
    api_key = require_config("NETBACKUP_APIKEY")
    query = f"?{urlencode(params)}" if params else ""
    request = Request(f"{base_url}{path}{query}", headers={
        "Accept": NETBACKUP_ACCEPT,
        "Authorization": f"Bearer {api_key}",
        "X-API-Key": api_key,
    })
    try:
        with urlopen(request, timeout=int(CONFIG.get("NETBACKUP_REQUEST_TIMEOUT", "60"))) as response:
            payload = response.read().decode("utf-8")
    except HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"NetBackup API returned HTTP {exc.code}: {body[:240]}")
    except URLError as exc:
        raise RuntimeError(f"NetBackup API failed: {exc}")
    return json.loads(payload) if payload.strip() else {}


def find_strings(node: Any) -> list[str]:
    found: list[str] = []
    if isinstance(node, dict):
        for value in node.values():
            found.extend(find_strings(value))
    elif isinstance(node, list):
        for item in node:
            found.extend(find_strings(item))
    elif isinstance(node, str):
        found.append(node)
    return found


def extract_jobs(payload: Any) -> list[dict[str, Any]]:
    if isinstance(payload, dict):
        data = payload.get("data")
        if isinstance(data, list):
            return [job for job in data if isinstance(job, dict)]
        if isinstance(data, dict):
            return [data]
        for key in ("jobs", "job", "items"):
            value = payload.get(key)
            if isinstance(value, list):
                return [job for job in value if isinstance(job, dict)]
    if isinstance(payload, list):
        return [job for job in payload if isinstance(job, dict)]
    return []


def get_nested(node: dict[str, Any], *paths: str) -> str:
    for path in paths:
        current: Any = node
        for part in path.split("."):
            if not isinstance(current, dict) or part not in current:
                current = None
                break
            current = current[part]
        if current is not None:
            return str(current)
    return ""


def job_id(job: dict[str, Any]) -> str:
    return get_nested(job, "id", "jobId", "jobid", "data.id", "attributes.jobId")


def job_state(job: dict[str, Any]) -> str:
    return get_nested(job, "state", "status", "attributes.state", "attributes.status", "attributes.jobState").lower()


def job_status_code(job: dict[str, Any]) -> str:
    return get_nested(job, "statusCode", "statuscode", "attributes.statusCode", "attributes.statuscode")


def is_successful_restore_job(job: dict[str, Any]) -> bool:
    state = job_state(job)
    status_code = job_status_code(job)
    if state in {"done", "successful", "success", "completed"}:
        return status_code in {"", "0", "1"} or "success" in state
    return status_code == "0" and state not in {"active", "running", "queued", "incomplete", "failed"}


def candidate_external_ids(job: dict[str, Any]) -> list[str]:
    staging_root = CONFIG.get("NETBACKUP_STAGING_ROOT", "/tmp/mold/netbackup").rstrip("/")
    values = []
    for value in find_strings(job):
        normalized = value.rstrip("/")
        if normalized.startswith(staging_root + "/"):
            suffix = normalized[len(staging_root) + 1:]
            parts = [part for part in suffix.split("/") if part]
            if len(parts) >= 2:
                values.append(f"{staging_root}/{parts[0]}/{parts[1]}")
    return sorted(set(values), key=len, reverse=True)


def invoke_restore_notify(external_id: str, job_id_value: str) -> bool:
    notify_script = require_config("RESTORE_NOTIFY_SCRIPT")
    env = os.environ.copy()
    if CONFIG.get("MOLD_CONFIG_FILE"):
        env["MOLD_CONFIG_FILE"] = CONFIG["MOLD_CONFIG_FILE"]
    if CONFIG.get("MOLD_RESTORE_API_METHOD"):
        env["MOLD_RESTORE_API_METHOD"] = CONFIG["MOLD_RESTORE_API_METHOD"]
    proc = subprocess.run(
        [notify_script, "netbackup-host-restore-watcher", external_id, "restore"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        env=env,
        check=False,
    )
    if proc.returncode != 0:
        log(f"FAILED notify jobId=[{job_id_value}] externalId=[{external_id}] rc=[{proc.returncode}] stderr=[{proc.stderr.strip()}]")
        return False
    log(f"Submitted Mold restore from NetBackup watcher jobId=[{job_id_value}] externalId=[{external_id}]")
    return True


def poll_once(state: dict[str, Any]) -> None:
    params = {}
    job_filter = CONFIG.get("NETBACKUP_JOBS_FILTER", "")
    if job_filter:
        params["filter"] = job_filter
    page_limit = CONFIG.get("NETBACKUP_JOBS_LIMIT", "")
    if page_limit:
        params["page[limit]"] = page_limit

    payload = netbackup_get(CONFIG.get("NETBACKUP_JOBS_PATH", "/admin/jobs"), params)
    jobs = extract_jobs(payload)
    processed = state.setdefault("processed", {})
    for job in jobs:
        current_job_id = job_id(job)
        if not current_job_id or current_job_id in processed:
            continue
        if not is_successful_restore_job(job):
            continue
        external_ids = candidate_external_ids(job)
        if not external_ids:
            continue
        external_id = external_ids[0]
        if invoke_restore_notify(external_id, current_job_id):
            processed[current_job_id] = {
                "externalId": external_id,
                "timestamp": int(time.time()),
            }


def main() -> None:
    interval = int(CONFIG.get("POLL_INTERVAL_SECONDS", "60"))
    once = "--once" in sys.argv
    log(f"Starting NetBackup restore watcher config=[{CONFIG_FILE}] once=[{once}]")
    state = load_state()
    while True:
        try:
            prune_state(state)
            poll_once(state)
            save_state(state)
        except Exception as exc:
            log(f"FAILED watcher poll error=[{exc}]")
        if once:
            break
        time.sleep(interval)


if __name__ == "__main__":
    main()
