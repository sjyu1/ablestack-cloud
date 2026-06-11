@echo off
REM Licensed to the Apache Software Foundation (ASF) under one
REM or more contributor license agreements.  See the NOTICE file
REM distributed with this work for additional information
REM regarding copyright ownership.  The ASF licenses this file
REM to you under the Apache License, Version 2.0 (the
REM "License"); you may not use this file except in compliance
REM with the License.  You may obtain a copy of the License at
REM
REM   http://www.apache.org/licenses/LICENSE-2.0
REM
REM Unless required by applicable law or agreed to in writing,
REM software distributed under the License is distributed on an
REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
REM KIND, either express or implied.  See the License for the
REM specific language governing permissions and limitations
REM under the License.

setlocal ENABLEEXTENSIONS ENABLEDELAYEDEXPANSION

set "SCRIPT_DIR=%~dp0"
set "LOG_FILE=C:\ProgramData\AbleStack\NetBackup\netbackup-mold-restore.log"
set "PS1_SCRIPT=%SCRIPT_DIR%netbackup-server-restore-notify.ps1"

if not exist "C:\ProgramData\AbleStack\NetBackup" mkdir "C:\ProgramData\AbleStack\NetBackup" >nul 2>&1

if not exist "%PS1_SCRIPT%" (
  >> "%LOG_FILE%" echo [%DATE% %TIME%] ERROR netbackup-server-restore-notify.ps1 not found: %PS1_SCRIPT%
  echo netbackup-server-restore-notify.ps1 not found: %PS1_SCRIPT% 1>&2
  exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS1_SCRIPT%" %* >> "%LOG_FILE%" 2>&1
exit /b %ERRORLEVEL%
