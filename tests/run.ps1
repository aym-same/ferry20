param(
    [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$DeviceSerial
)

$ErrorActionPreference = 'Stop'
$testRoot = $PSScriptRoot
$gameRoot = Split-Path -Parent $testRoot
. (Join-Path $gameRoot 'scripts\Android-Paths.ps1')
$adb = Join-Path (Get-AndroidSdkRoot) 'platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb -PathType Leaf)) { throw "ADB not found: $adb" }

$deviceState = & $adb -s $DeviceSerial get-state
if ($LASTEXITCODE -ne 0 -or ($deviceState -join '').Trim() -ne 'device') {
    throw 'The specified device is not ready. Check the serial and USB debugging authorization.'
}

# This compiles both APKs with the same local signing key.
& (Join-Path $testRoot 'build.ps1')
& $adb -s $DeviceSerial install -r (Join-Path $gameRoot 'artifacts\ferry20-debug.apk')
if ($LASTEXITCODE -ne 0) {
    throw 'Game install failed. If signatures differ, use the original signing key or a separate test device. This script never uninstalls the app or clears user data.'
}
& $adb -s $DeviceSerial install -r (Join-Path $testRoot 'artifacts\ferry20-debug.apk')
if ($LASTEXITCODE -ne 0) { throw 'Test APK install failed. Existing apps and data were not uninstalled or cleared.' }

$result = & $adb -s $DeviceSerial shell am instrument -w com.smartglasses.ferry20.tests/com.smartglasses.ferry20.FerryInstrumentation
$instrumentExitCode = $LASTEXITCODE
$reportPath = Join-Path $gameRoot 'artifacts\verification.txt'
$result | Set-Content -Encoding utf8 -LiteralPath $reportPath
$result
if ($instrumentExitCode -ne 0 -or ($result -join "`n") -notmatch '(?m)^TOTAL passed=17 failed=0\s*$') {
    throw 'Device verification failed; see artifacts/verification.txt.'
}
& $adb -s $DeviceSerial shell am start -n com.smartglasses.ferry20/.MainActivity
if ($LASTEXITCODE -ne 0) { throw 'Tests passed, but restarting the game failed.' }
