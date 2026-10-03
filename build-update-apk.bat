@echo off
chcp 65001 >nul
setlocal EnableExtensions

set "PROJECT_DIR=%~dp0"
set "GRADLE_BAT=%PROJECT_DIR%gradlew.bat"
set "APK_PATH=%PROJECT_DIR%app\build\outputs\apk\debug\app-debug.apk"
set "APK_LIST_DIR=%PROJECT_DIR%artifacts\apk"

echo ============================================================
echo  Hoshina Assistant Update APK Builder
echo ============================================================
echo.
echo Before building, make sure app\build.gradle.kts has:
echo   versionCode higher than the APK already installed on the phone.
echo   same applicationId: com.hoshina.assistant
echo.

if not exist "%GRADLE_BAT%" (
  echo Gradle wrapper was not found:
  echo %GRADLE_BAT%
  echo Open the project in Android Studio or add the Gradle wrapper files first.
  pause
  exit /b 1
)

cd /d "%PROJECT_DIR%"
call "%GRADLE_BAT%" :app:assembleDebug --no-daemon --console=plain
if errorlevel 1 (
  echo.
  echo Build failed.
  pause
  exit /b 1
)

if not exist "%APK_PATH%" (
  echo.
  echo APK was not found:
  echo %APK_PATH%
  pause
  exit /b 1
)

if not exist "%APK_LIST_DIR%" (
  mkdir "%APK_LIST_DIR%"
)

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "BUILD_TIMESTAMP=%%i"
set "ARCHIVED_APK=%APK_LIST_DIR%\APK_%BUILD_TIMESTAMP%.apk"
copy /y "%APK_PATH%" "%ARCHIVED_APK%" >nul
if errorlevel 1 (
  echo.
  echo Failed to copy APK to APKlist.
  pause
  exit /b 1
)

echo.
echo Update APK created:
echo %APK_PATH%
echo.
echo Archived APK:
echo %ARCHIVED_APK%
echo.
echo Copy this APK to the phone and install it directly over the old app.
echo Do not uninstall first, or local chat/settings/memory data will be removed.
pause
