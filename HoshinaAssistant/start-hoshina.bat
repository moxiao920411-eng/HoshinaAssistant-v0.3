@echo off
chcp 65001 >nul
setlocal EnableExtensions

set "PROJECT_DIR=%~dp0"
set "BACKEND_DIR=%PROJECT_DIR%backend"
set "PYTHON_EXE="
set "OLLAMA_EXE="

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

rem Find Ollama. The default path also covers the current local installation.
for /f "delims=" %%P in ('where ollama 2^>nul') do (
  if not defined OLLAMA_EXE set "OLLAMA_EXE=%%P"
)
if not defined OLLAMA_EXE if exist "D:\Ollama\ollama.exe" set "OLLAMA_EXE=D:\Ollama\ollama.exe"

if not defined OLLAMA_EXE (
  echo [ERROR] Ollama was not found.
  echo Install Ollama or add ollama.exe to PATH.
  pause
  exit /b 1
)

curl.exe --silent --max-time 2 http://localhost:11434/api/tags >nul 2>&1
if errorlevel 1 (
  echo [1/2] Starting Ollama...
  start "Hoshina Ollama" /min "%OLLAMA_EXE%" serve
  timeout /t 3 /nobreak >nul
) else (
  echo [1/2] Ollama is already running.
)

echo [2/2] Starting FastAPI backend...
start "Hoshina Backend" /d "%BACKEND_DIR%" cmd.exe /k ""%PYTHON_EXE%" -m uvicorn main:app --reload --host 0.0.0.0 --port 8000"

set "BACKEND_READY=0"
for /l %%N in (1,1,10) do (
  curl.exe --silent --max-time 1 http://localhost:8000/agents >nul 2>&1
  if not errorlevel 1 set "BACKEND_READY=1"
  if "%BACKEND_READY%"=="1" goto backend_ready
  timeout /t 1 /nobreak >nul
)

:backend_ready
if "%BACKEND_READY%"=="0" (
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
