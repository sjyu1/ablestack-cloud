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

if not defined MOLD_CONFIG_FILE set "MOLD_CONFIG_FILE=C:\ProgramData\AbleStack\NetBackup\restore.conf"
call :resolve_python
if errorlevel 1 exit /b 1
call :log "CONFIG helper=%PYTHON_HELPER% mold_config=%MOLD_CONFIG_FILE% python=%PYTHON_EXE% %PYTHON_VERSION_ARG%"

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
  "%PYTHON_EXE%" %PYTHON_VERSION_ARG% "%PYTHON_HELPER%" "%~1" "%~2" "%~3" >> "%RESTORE_NOTIFY_LOG%" 2>&1
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

:resolve_python
set "PYTHON_VERSION_ARG="

if defined PYTHON_EXE (
  echo "%PYTHON_EXE%" | findstr /R "[\\:]" >nul
  if not errorlevel 1 (
    if exist "%PYTHON_EXE%" (
      call :log "PYTHON source=env path=%PYTHON_EXE%"
      exit /b 0
    )
    call :log "PYTHON env-path-not-found path=%PYTHON_EXE%"
  ) else (
    where "%PYTHON_EXE%" >nul 2>&1
    if not errorlevel 1 (
      call :log "PYTHON source=env-command exe=%PYTHON_EXE%"
      exit /b 0
    )
    call :log "PYTHON env-command-not-found exe=%PYTHON_EXE%"
  )
)

call :find_python_launcher
if not errorlevel 1 exit /b 0

call :find_python_command
if not errorlevel 1 exit /b 0

call :find_python_common_paths
if not errorlevel 1 exit /b 0

call :log "ERROR python-not-found"
echo Python executable not found. Set PYTHON_EXE to an absolute path. 1>&2
exit /b 1

:find_python_launcher
where py >nul 2>&1
if errorlevel 1 exit /b 1
set "PYTHON_EXE=py"
set "PYTHON_VERSION_ARG=-3"
call :log "PYTHON source=launcher exe=py version=-3"
exit /b 0

:find_python_command
where python >nul 2>&1
if errorlevel 1 exit /b 1
set "PYTHON_EXE=python"
set "PYTHON_VERSION_ARG="
call :log "PYTHON source=path exe=python"
exit /b 0

:find_python_common_paths
for %%P in (
  "C:\Python313\python.exe"
  "C:\Python312\python.exe"
  "C:\Python311\python.exe"
  "C:\Python310\python.exe"
  "C:\Python39\python.exe"
  "C:\Program Files\Python313\python.exe"
  "C:\Program Files\Python312\python.exe"
  "C:\Program Files\Python311\python.exe"
  "C:\Program Files\Python310\python.exe"
  "C:\Program Files\Python39\python.exe"
  "C:\Program Files (x86)\Python313\python.exe"
  "C:\Program Files (x86)\Python312\python.exe"
  "C:\Program Files (x86)\Python311\python.exe"
  "C:\Program Files (x86)\Python310\python.exe"
  "C:\Program Files (x86)\Python39\python.exe"
) do (
  if exist "%%~P" (
    set "PYTHON_EXE=%%~P"
    set "PYTHON_VERSION_ARG="
    call :log "PYTHON source=common-path path=%%~P"
    exit /b 0
  )
)
exit /b 1

:log
>> "%RESTORE_NOTIFY_LOG%" echo [%DATE% %TIME%] %*
exit /b 0
