@echo off
setlocal EnableExtensions
cd /d "%~dp0"
set "HOSHINA_PYTHON=python"
if exist "backend\.venv\Scripts\python.exe" set "HOSHINA_PYTHON=backend\.venv\Scripts\python.exe"
"%HOSHINA_PYTHON%" backend\remote_launcher.py
if errorlevel 1 echo Remote startup failed. Read the message above.
pause
