@echo off
rem =====================================================================
rem  Setup.bat - backup for Setup.exe
rem  Use it only if Setup.exe is blocked (antivirus, company policy).
rem  Starts Railway Setup (SetupApp.jar) with the Java found in PATH or
rem  JAVA_HOME. Java 17 or newer (a JDK) is required.
rem =====================================================================
setlocal
cd /d "%~dp0"

if not exist "%~dp0SetupApp.jar" (
    echo.
    echo   SetupApp.jar is missing next to Setup.bat.
    echo   Rebuild it with:  powershell -ExecutionPolicy Bypass -File scripts\build-setup.ps1
    echo.
    pause
    exit /b 1
)

if not "%JAVA_HOME%"=="" if exist "%JAVA_HOME%\bin\javaw.exe" (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
    start "" "%JAVA_HOME%\bin\javaw.exe" -Dfile.encoding=UTF-8 -jar "%~dp0SetupApp.jar"
    exit /b 0
)

where javaw >nul 2>&1
if %errorlevel% equ 0 (
    start "" javaw -Dfile.encoding=UTF-8 -jar "%~dp0SetupApp.jar"
    exit /b 0
)

echo.
echo   Java was not found on this computer.
echo.
echo   Install JDK 21 with this command in PowerShell, then open Setup.bat again:
echo       winget install EclipseAdoptium.Temurin.21.JDK
echo.
echo   (Setup.exe does this for you and does not need a new window afterwards.)
echo.
pause
exit /b 1
