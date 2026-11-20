# =====================================================================
#  build-setup.ps1 - rebuilds SetupApp.jar and Setup.exe (maintainers only)
# ---------------------------------------------------------------------
#  Team members never need this: both files are committed. Run it after
#  changing tools/setup-app (the Java GUI) or tools/launcher (Setup.exe).
#
#    powershell -ExecutionPolicy Bypass -File scripts\build-setup.ps1
#    powershell -ExecutionPolicy Bypass -File scripts\build-setup.ps1 -JarOnly
#
#  Needs only a JDK 17+: Maven comes from the Maven Wrapper and the C#
#  compiler (csc.exe) ships with Windows.
# =====================================================================
param([switch]$JarOnly)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
[Environment]::CurrentDirectory = $root

function Step($text) { Write-Host "`n== $text" -ForegroundColor Cyan }

# --- 1. SetupApp.jar ---------------------------------------------------
Step 'Building SetupApp.jar (tools/setup-app)'
& "$root\mvnw.cmd" -B -ntp -q -f "$root\tools\setup-app\pom.xml" clean package
if ($LASTEXITCODE -ne 0) { throw 'The Maven build of tools/setup-app failed.' }
Copy-Item "$root\tools\setup-app\target\railway-setup.jar" "$root\SetupApp.jar" -Force
$mb = [math]::Round((Get-Item "$root\SetupApp.jar").Length / 1MB, 1)
Write-Host "[OK] SetupApp.jar ($mb MB)" -ForegroundColor Green

if ($JarOnly) { exit 0 }

# --- 2. Icon -------------------------------------------------------------
Step 'Drawing the icon (tools/launcher/setup.ico)'
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
& $java '-Djava.awt.headless=true' -cp "$root\SetupApp.jar" com.railway.setup.tools.IconGen "$root\tools\launcher\setup.ico"
if ($LASTEXITCODE -ne 0) { throw 'IconGen failed.' }

# --- 3. Manifest check -------------------------------------------------
# csc embeds the manifest without validating it; a broken one only shows up as
# "side-by-side configuration is incorrect" when Setup.exe is double-clicked.
Step 'Checking tools/launcher/app.manifest'
$manifest = Get-Content "$root\tools\launcher\app.manifest" -Raw
try { [xml]$manifest | Out-Null } catch { throw "app.manifest is not valid XML: $($_.Exception.Message)" }
foreach ($m in [regex]::Matches($manifest, '(?s)<!--(.*?)-->')) {
    if ($m.Groups[1].Value.Contains('--')) { throw 'app.manifest: a comment contains two hyphens in a row.' }
}
Write-Host '[OK] valid XML' -ForegroundColor Green

# --- 4. Setup.exe ------------------------------------------------------
Step 'Compiling Setup.exe (tools/launcher/SetupLauncher.cs)'
$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path $csc)) { $csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework\v4.0.30319\csc.exe' }
if (-not (Test-Path $csc)) { throw 'csc.exe (.NET Framework 4) was not found.' }
& $csc /nologo /optimize+ /target:winexe "/out:$root\Setup.exe" `
    "/win32icon:$root\tools\launcher\setup.ico" "/win32manifest:$root\tools\launcher\app.manifest" `
    /r:System.Windows.Forms.dll "$root\tools\launcher\SetupLauncher.cs"
if ($LASTEXITCODE -ne 0) { throw 'csc could not compile SetupLauncher.cs.' }
$kb = [math]::Round((Get-Item "$root\Setup.exe").Length / 1KB)
Write-Host "[OK] Setup.exe ($kb KB)" -ForegroundColor Green

Write-Host "`nDone. Commit SetupApp.jar, Setup.exe and tools/launcher/setup.ico." -ForegroundColor Cyan
