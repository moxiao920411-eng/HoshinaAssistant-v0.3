@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion

set "PROJECT_DIR=%~dp0"
set "BACKEND_DIR=%PROJECT_DIR%backend"
set "PYTHON_EXE="
set "MONITOR_BAT=%PROJECT_DIR%monitor-ollama.bat"

echo ============================================================
echo  Hoshina Assistant launcher
echo ============================================================
echo.

rem Prefer the project's virtual environment, then use Python on PATH.
if exist "%BACKEND_DIR%\.venv\Scripts\python.exe" set "PYTHON_EXE=%BACKEND_DIR%\.venv\Scripts\python.exe"
if not defined PYTHON_EXE (
  for /f "delims=" %%P in ('where python 2^>nul') do (
    if /I not "%%~P"=="%LOCALAPPDATA%\Microsoft\WindowsApps\python.exe" if not defined PYTHON_EXE set "PYTHON_EXE=%%P"
  )
)

if not exist "%PYTHON_EXE%" (
  echo [ERROR] Python was not found.
  echo Install Python 3.10+ or create backend\.venv first.
  pause
  exit /b 1
)

if not exist "%MONITOR_BAT%" (
  echo [ERROR] Ollama monitor was not found.
  echo Missing: %MONITOR_BAT%
  pause
  exit /b 1
)

echo [1/2] Starting Ollama monitor...
start "Hoshina Ollama Monitor" cmd.exe /d /c call "%MONITOR_BAT%"

set "OLLAMA_READY=0"
for /l %%N in (1,1,20) do (
  curl.exe --silent --max-time 1 http://localhost:11434/api/tags >nul 2>&1
  if not errorlevel 1 set "OLLAMA_READY=1"
  if "!OLLAMA_READY!"=="1" goto ollama_ready
  timeout /t 1 /nobreak >nul
)

:ollama_ready
if "!OLLAMA_READY!"=="0" (
  echo [ERROR] Ollama API did not become ready on port 11434.
  echo Check the Hoshina Ollama Monitor window and ollama-monitor.log.
  pause
  exit /b 1
)
echo Ollama API is ready.

echo [2/2] Starting FastAPI backend...
set "BACKEND_READY=0"
curl.exe --silent --max-time 2 http://localhost:8000/agents >nul 2>&1
if not errorlevel 1 (
  set "BACKEND_READY=1"
  echo FastAPI backend is already running.
)

if "!BACKEND_READY!"=="0" start "Hoshina Backend" /d "%BACKEND_DIR%" cmd.exe /k ""%PYTHON_EXE%" -m uvicorn main:app --host 0.0.0.0 --port 8000"

if "!BACKEND_READY!"=="0" for /l %%N in (1,1,15) do (
  curl.exe --silent --max-time 1 http://localhost:8000/agents >nul 2>&1
  if not errorlevel 1 set "BACKEND_READY=1"
  if "!BACKEND_READY!"=="1" goto backend_ready
  timeout /t 1 /nobreak >nul
)

:backend_ready
if "!BACKEND_READY!"=="0" (
  echo [ERROR] Backend did not start on port 8000.
  echo Check the Hoshina Backend window for the actual error.
  pause
  exit /b 1
)

echo.
echo Startup complete.
echo API:  http://localhost:8000
echo Docs: http://localhost:8000/docs
echo Android Emulator URL: http://10.0.2.2:8000/
echo.
