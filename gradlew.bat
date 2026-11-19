@echo off
setlocal
set "GRADLE_VERSION=8.11.1"
if defined GRADLE_USER_HOME (
  set "GRADLE_CACHE=%GRADLE_USER_HOME%\tasbeeh\gradle-%GRADLE_VERSION%"
) else (
  set "GRADLE_CACHE=%USERPROFILE%\.gradle\tasbeeh\gradle-%GRADLE_VERSION%"
)
set "GRADLE_BIN=%GRADLE_CACHE%\bin\gradle.bat"
if not exist "%GRADLE_BIN%" (
  set "TMP_DIR=%TEMP%\tasbeeh-gradle-%RANDOM%-%RANDOM%"
  mkdir "%TMP_DIR%"
  set "ARCHIVE=%TMP_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ARCHIVE%'"
  if errorlevel 1 exit /b 1
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ARCHIVE%' -DestinationPath '%TMP_DIR%' -Force"
  if errorlevel 1 exit /b 1
  if not exist "%GRADLE_CACHE%" mkdir "%GRADLE_CACHE%"
  xcopy "%TMP_DIR%\gradle-%GRADLE_VERSION%\*" "%GRADLE_CACHE%\" /E /I /Y >nul
  if errorlevel 1 exit /b 1
  rmdir /S /Q "%TMP_DIR%"
)
call "%GRADLE_BIN%" %*
exit /b %ERRORLEVEL%
