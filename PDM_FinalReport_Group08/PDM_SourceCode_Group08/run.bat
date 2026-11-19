@echo off
rem Starts the Railway Network & Timetable application.
cd /d "%~dp0"
if not exist "target\railway-manager.jar" (
    echo The application has not been built yet. Run: mvn package
    pause
    exit /b 1
)
start "" javaw -jar "target\railway-manager.jar"
