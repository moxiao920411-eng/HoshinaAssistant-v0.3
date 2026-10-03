@echo off
chcp 65001 >nul
setlocal EnableExtensions

set "PROJECT_DIR=%~dp0"
set "BACKEND_DIR=%PROJECT_DIR%backend"
set "PYTHON_EXE="
set "VENV_PYTHON=%BACKEND_DIR%\.venv\Scripts\python.exe"

echo ============================================================
echo  Hoshina Assistant first-time setup
echo ============================================================
echo.

if exist "%VENV_PYTHON%" set "PYTHON_EXE=%VENV_PYTHON%"
if not defined PYTHON_EXE (
  for /f "delims=" %%P in ('where python 2^>nul') do (
    if /I not "%%~P"=="%LOCALAPPDATA%\Microsoft\WindowsApps\python.exe" if not defined PYTHON_EXE set "PYTHON_EXE=%%P"
  )
)

if not defined PYTHON_EXE (
  echo [ERROR] Python was not found.
  echo Install Python 3.10+ first, then run this file again.
  pause
  exit /b 1
)

if not exist "%VENV_PYTHON%" (
  echo [1/2] Creating backend virtual environment...
  "%PYTHON_EXE%" -m venv "%BACKEND_DIR%\.venv"
  if errorlevel 1 goto failed
)

echo [2/2] Installing backend dependencies...
"%VENV_PYTHON%" -m pip install -r "%BACKEND_DIR%\requirements.txt"
if errorlevel 1 goto failed

echo.
echo Setup complete. You can now double-click 快速啟動.bat
pause
exit /b 0

:failed
echo.
echo [ERROR] Setup failed. Check the message above.
pause
exit /b 1
