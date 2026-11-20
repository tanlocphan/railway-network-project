@echo off
rem =====================================================================
rem  Railway Network & Timetable - database setup (MySQL 8.0)
rem  Runs 01_schema_and_seed.sql, 02_payment_table.sql and
rem  03_business_routines.sql with the MySQL command-line client.
rem  WARNING: 01_schema_and_seed.sql starts with DROP DATABASE, so all
rem  existing data in RailwayManagement is replaced by the sample data.
rem =====================================================================
setlocal
set "MYSQL=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if not exist "%MYSQL%" (
    echo MySQL client not found at "%MYSQL%".
    echo Edit MYSQL in this file to point to your mysql.exe.
    pause
    exit /b 1
)
set "DB_DIR=%~dp0database"
set /p "DB_USER=MySQL admin user [root]: "
if "%DB_USER%"=="" set "DB_USER=root"
echo.
echo This will DROP and recreate the RailwayManagement database.
set /p "CONFIRM=Type YES to continue: "
if /i not "%CONFIRM%"=="YES" (
    echo Cancelled.
    exit /b 0
)
echo You will be asked for the password of %DB_USER% once.
(
    type "%DB_DIR%\01_schema_and_seed.sql"
    echo.
    type "%DB_DIR%\02_payment_table.sql"
    echo.
    type "%DB_DIR%\03_business_routines.sql"
) | "%MYSQL%" -u "%DB_USER%" -p --default-character-set=utf8mb4 > nul
if errorlevel 1 (
    echo.
    echo Setup FAILED. See the message above.
    pause
    exit /b 1
)
echo.
echo Database RailwayManagement is ready.
echo Optional: run database\04_app_account.sql to create a restricted app account.
pause
