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
set "PYTHON_HELPER=%SCRIPT_DIR%netbackup_restore_notify.py"
set "FINAL_RC=0"
if not defined LOG_FILE set "LOG_FILE=C:\ProgramData\AbleStack\NetBackup\netbackup-mold-restore.log"
if not defined RESTORE_NOTIFY_LOG set "RESTORE_NOTIFY_LOG=%LOG_FILE%"

call :log "START script=%~nx0 args=%*"

if not exist "%PYTHON_HELPER%" (
  call :log "ERROR helper-missing path=%PYTHON_HELPER%"
  echo netbackup_restore_notify.py not found: %PYTHON_HELPER% 1>&2
  exit /b 1
)

if not defined PYTHON_EXE set "PYTHON_EXE=python"
if not defined MOLD_CONFIG_FILE set "MOLD_CONFIG_FILE=C:\ProgramData\AbleStack\NetBackup\restore.conf"
call :log "CONFIG helper=%PYTHON_HELPER% mold_config=%MOLD_CONFIG_FILE% python=%PYTHON_EXE%"

if "%~3"=="" (
  call :log "ERROR invalid-args args=%*"
  echo restore_notify.cmd expects parameter triplets: programname pathname operation 1>&2
  exit /b 1
)

:MainLoop
if "%~3"=="" goto EndMain

call :log "TRIPLET program=%~1 path=%~2 operation=%~3"
if /I "%~3"=="restore" (
  call :log "CALL python-helper program=%~1 path=%~2 operation=%~3"
  "%PYTHON_EXE%" "%PYTHON_HELPER%" "%~1" "%~2" "%~3" >> "%RESTORE_NOTIFY_LOG%" 2>&1
  set "PY_RC=%ERRORLEVEL%"
  call :log "PYTHON_EXIT rc=%PY_RC% program=%~1 path=%~2 operation=%~3"
  if not "%PY_RC%"=="0" set "FINAL_RC=1"
)

shift /1
shift /1
shift /1
goto MainLoop

:EndMain
call :log "END final_rc=%FINAL_RC%"
exit /b %FINAL_RC%

:log
>> "%RESTORE_NOTIFY_LOG%" echo [%DATE% %TIME%] %*
exit /b 0
