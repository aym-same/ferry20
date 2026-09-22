$ErrorActionPreference = 'Stop'
$gameRoot = Split-Path -Parent $PSScriptRoot

# Always compile the matching app sources before compiling the separate tests APK.
& (Join-Path $gameRoot 'build.ps1')
& (Join-Path $gameRoot 'scripts\Build-Apk.ps1') `
    -ProjectRoot $PSScriptRoot `
    -RepositoryRoot $gameRoot `
    -PackageName 'com.smartglasses.ferry20.tests' `
    -ApplicationClasses (Join-Path $gameRoot 'build\classes.jar')
