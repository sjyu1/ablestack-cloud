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
set "STAGING_ROOT=%NETBACKUP_STAGING_ROOT%"

if not exist "%PYTHON_HELPER%" (
  echo netbackup_restore_notify.py not found: %PYTHON_HELPER% 1>&2
  exit /b 1
)

if not defined PYTHON_EXE set "PYTHON_EXE=python"
if not defined STAGING_ROOT set "STAGING_ROOT=/tmp/mold/netbackup"

if "%~3"=="" (
  echo restore_notify.cmd expects parameter triplets: programname pathname operation 1>&2
  exit /b 1
)

:MainLoop
if "%~3"=="" goto EndMain

if /I "%~3"=="restore" (
  echo(%~2 | findstr /B /C:"%STAGING_ROOT%" >nul
  if not errorlevel 1 (
    "%PYTHON_EXE%" "%PYTHON_HELPER%" "%~1" "%~2" "%~3"
    if errorlevel 1 set "FINAL_RC=1"
  )
)

shift /1
shift /1
shift /1
goto MainLoop

:EndMain
exit /b %FINAL_RC%
