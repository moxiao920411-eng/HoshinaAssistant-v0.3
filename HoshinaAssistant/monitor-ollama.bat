@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion

rem Hoshina/Ollama monitor for Windows.
rem Keep this window open while you want Ollama to be monitored.

set "OLLAMA_HOST=http://127.0.0.1:11434"
set "OLLAMA_MODELS=D:\OllamaModels"
set "OLLAMA_LLM_LIBRARY=vulkan"
set "GGML_VK_VISIBLE_DEVICES=1"
set "OLLAMA_FLASH_ATTENTION=0"
set "CHECK_INTERVAL=15"
set "FAIL_LIMIT=2"
set "START_GUI=1"
set "LOG_FILE=%~dp0ollama-monitor.log"

set "OLLAMA_EXE="
if exist "D:\Ollama\ollama.exe" set "OLLAMA_EXE=D:\Ollama\ollama.exe"
if not defined OLLAMA_EXE for /f "delims=" %%P in ('where ollama 2^>nul') do (
    if not defined OLLAMA_EXE set "OLLAMA_EXE=%%P"
)

set "OLLAMA_APP_EXE="
if exist "D:\Ollama\ollama app.exe" set "OLLAMA_APP_EXE=D:\Ollama\ollama app.exe"
if not defined OLLAMA_APP_EXE if exist "%LOCALAPPDATA%\Programs\Ollama\ollama app.exe" set "OLLAMA_APP_EXE=%LOCALAPPDATA%\Programs\Ollama\ollama app.exe"

if not defined OLLAMA_EXE (
    echo [ERROR] 找不到 ollama.exe。
    echo 請確認 Ollama 安裝在 D:\Ollama，或已加入 PATH。
    pause
    exit /b 1
)

call :log "監控啟動：%OLLAMA_EXE%"
call :ensure_gui

set /a FAIL_COUNT=0

:monitor_loop
curl.exe --silent --show-error --fail --max-time 5 "%OLLAMA_HOST%/api/tags" >nul 2>&1
if not errorlevel 1 (
    if !FAIL_COUNT! GTR 0 call :log "Ollama 已恢復，API 正常。"
    set /a FAIL_COUNT=0
    call :ensure_gui
    echo [!time!] Ollama API OK
    if "%MONITOR_ONCE%"=="1" exit /b 0
    timeout /t %CHECK_INTERVAL% /nobreak >nul
    goto monitor_loop
)

set /a FAIL_COUNT+=1
call :log "Ollama API 無回應，失敗次數 !FAIL_COUNT!/%FAIL_LIMIT%。"
echo [!time!] Ollama API 無回應，失敗次數 !FAIL_COUNT!/%FAIL_LIMIT%。

if !FAIL_COUNT! GEQ %FAIL_LIMIT% (
    call :restart_ollama
    set /a FAIL_COUNT=0
)

if "%MONITOR_ONCE%"=="1" exit /b 1
timeout /t 5 /nobreak >nul
goto monitor_loop

:restart_ollama
call :log "正在重啟 Ollama。"
taskkill /IM ollama.exe /F /T >nul 2>&1
timeout /t 2 /nobreak >nul

start "Ollama Server" /min "%OLLAMA_EXE%" serve
call :log "已啟動 Ollama server，等待 API 恢復。"
timeout /t 8 /nobreak >nul
call :ensure_gui
goto :eof

:ensure_gui
if not "%START_GUI%"=="1" goto :eof
if not defined OLLAMA_APP_EXE goto :eof

tasklist /FI "IMAGENAME eq ollama app.exe" | find /I "ollama app.exe" >nul
if errorlevel 1 (
    start "Ollama App" "%OLLAMA_APP_EXE%"
    call :log "已啟動 Ollama 監控視窗。"
)
goto :eof

:log
echo [%date% %time%] %~1>>"%LOG_FILE%"
goto :eof
