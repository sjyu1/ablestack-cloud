#!/usr/bin/env python3

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

import argparse
import base64
import hashlib
import hmac
import os
import subprocess
import sys
from pathlib import Path
from urllib.parse import quote_plus
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError


LOG_FILE = os.environ.get("LOG_FILE", "/var/log/netbackup-mold-restore.log")
MOLD_RESTORE_API_URL = os.environ.get("MOLD_RESTORE_API_URL", "")
MOLD_RESTORE_API_METHOD = os.environ.get("MOLD_RESTORE_API_METHOD", "POST")
MOLD_RESTORE_MODE = os.environ.get("MOLD_RESTORE_MODE", "live")
MOLD_CONFIG_FILE = os.environ.get("MOLD_CONFIG_FILE", "")
MOLD_SECRET_FILE = os.environ.get("MOLD_SECRET_FILE", "")
SECRET_HELPER = os.environ.get("SECRET_HELPER", "")
SECRET_SUBDIR = os.environ.get("SECRET_SUBDIR", "secrets")
CONFIG_ROOT = os.environ.get("CONFIG_ROOT", "/etc/ablestack/netbackup")
MOLD_API_RESPONSE_FORMAT = os.environ.get("MOLD_API_RESPONSE_FORMAT", "json")
SECRET_KEY_FILE = os.environ.get("SECRET_KEY_FILE", "")
SCRIPT_DIR = Path(__file__).resolve().parent
PYTHON_SECRET_HELPER = Path(os.environ.get("PYTHON_SECRET_HELPER", str(SCRIPT_DIR / "netbackup_secret_helper.py")))

ADMIN_APIKEY = os.environ.get("ADMIN_APIKEY", "")
ADMIN_SECRETKEY = os.environ.get("ADMIN_SECRETKEY", "")
MOLD_URL = os.environ.get("MOLD_URL", "")


def log_line(message: str) -> None:
    with open(LOG_FILE, "a", encoding="utf-8") as fh:
        fh.write(f"{__import__('datetime').datetime.now().strftime('%F %T')} {message}\n")


def fail(message: str) -> None:
    log_line(f"RESTORE error: {message}")
    raise SystemExit(1)


def resolve_secret_file_path() -> Path:
    if MOLD_SECRET_FILE:
        return Path(MOLD_SECRET_FILE)
    return Path(CONFIG_ROOT) / SECRET_SUBDIR / "secret.enc"


def load_restore_config() -> None:
    global MOLD_RESTORE_API_URL, ADMIN_APIKEY, MOLD_URL, MOLD_SECRET_FILE, SECRET_KEY_FILE
    if MOLD_CONFIG_FILE:
        config_path = Path(MOLD_CONFIG_FILE)
        if not config_path.is_file():
            fail(f"MOLD_CONFIG_FILE not found: {MOLD_CONFIG_FILE}")
        for line in config_path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            value = value.strip().strip('"').strip("'")
            if key == "MOLD_URL" and not MOLD_URL:
                MOLD_URL = value
            elif key == "ADMIN_APIKEY" and not ADMIN_APIKEY:
                ADMIN_APIKEY = value
            elif key == "MOLD_SECRET_FILE" and not MOLD_SECRET_FILE:
                MOLD_SECRET_FILE = value
            elif key == "SECRET_KEY_FILE" and not SECRET_KEY_FILE:
                SECRET_KEY_FILE = value

    if not MOLD_RESTORE_API_URL:
        MOLD_RESTORE_API_URL = MOLD_URL
    if not MOLD_RESTORE_API_URL:
        fail("MOLD_RESTORE_API_URL or MOLD_URL must be configured")
    if not ADMIN_APIKEY:
        fail("ADMIN_APIKEY must be configured")


def load_restore_secret() -> None:
    global ADMIN_SECRETKEY
    if ADMIN_SECRETKEY:
        return

    secret_file = resolve_secret_file_path()
    if not secret_file.is_file():
        fail(f"Secret file not found: {secret_file}")
    if not PYTHON_SECRET_HELPER.is_file():
        fail(f"Python secret helper not found: {PYTHON_SECRET_HELPER}")

    result = subprocess.run(
        [sys.executable, str(PYTHON_SECRET_HELPER), "decrypt", str(secret_file)],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=False,
        env={**os.environ, **({"SECRET_KEY_FILE": SECRET_KEY_FILE} if SECRET_KEY_FILE else {})},
    )
    if result.returncode != 0:
        fail(f"Failed to decrypt MOLD secret file: {result.stderr.strip()}")
    ADMIN_SECRETKEY = result.stdout.strip()
    if not ADMIN_SECRETKEY:
        fail("Decrypted MOLD secret is empty.")


def build_api_params(command_name: str, params: dict[str, str]) -> str:
    tokens = [f"command={quote_plus(command_name)}"]
    for key, value in params.items():
        tokens.append(f"{key}={quote_plus(str(value))}")
    tokens.append(f"response={quote_plus(MOLD_API_RESPONSE_FORMAT)}")
    return "&".join(tokens)


def build_signed_url(base_url: str, api_params: str) -> str:
    sorted_params = [f"apikey={quote_plus(ADMIN_APIKEY).lower()}"]
    for token in api_params.split("&"):
        key, value = token.split("=", 1)
        sorted_params.append(f"{key.lower()}={value.lower()}")
    sorted_url = "&".join(sorted(sorted_params))
    signature = base64.b64encode(
        hmac.new(ADMIN_SECRETKEY.encode(), sorted_url.encode(), hashlib.sha256).digest()
    ).decode()
    return f"{base_url}?{api_params}&apiKey={quote_plus(ADMIN_APIKEY)}&signature={quote_plus(signature)}"


def invoke_mold_api(method: str, command_name: str, params: dict[str, str], external_id: str, operation: str) -> str:
    log_line(f"RESTORE api-call command={command_name} method={method} externalid={external_id} operation={operation}")
    api_params = build_api_params(command_name, params)
    signed_url = build_signed_url(MOLD_RESTORE_API_URL, api_params)
    request = Request(signed_url, method=method.upper(), headers={
        "Accept": "application/json",
        "Content-type": "application/x-www-form-urlencoded",
    })
    try:
        with urlopen(request, timeout=300) as response:
            return response.read().decode("utf-8")
    except HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        fail(f"Mold API call failed: method={method} command={command_name} code={exc.code} body={body[:240]}")
    except URLError as exc:
        fail(f"Mold API call failed: method={method} command={command_name} error={exc}")
    return ""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="NetBackup restore_notify helper")
    parser.add_argument("program_name")
    parser.add_argument("external_id")
    parser.add_argument("operation", nargs="?", default="")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if not args.program_name or not args.external_id:
        log_line(
            f"RESTORE missing required restore_notify arguments program={args.program_name or ''} "
            f"externalid={args.external_id or ''} operation={args.operation or ''}"
        )
        raise SystemExit(1)

    load_restore_config()
    load_restore_secret()

    if MOLD_RESTORE_MODE == "validate-only":
        log_line(
            f"RESTORE validate-only command=restoreNetBackup externalid={args.external_id} "
            f"operation={args.operation} program={args.program_name}"
        )
        return

    response = invoke_mold_api(
        MOLD_RESTORE_API_METHOD,
        "restoreNetBackup",
        {"externalid": args.external_id},
        args.external_id,
        args.operation,
    )
    log_line(f"RESTORE api-call-success command=restoreNetBackup externalid={args.external_id} response={response}")


if __name__ == "__main__":
    main()
