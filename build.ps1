$ErrorActionPreference = 'Stop'

& (Join-Path $PSScriptRoot 'scripts\Build-Apk.ps1') `
    -ProjectRoot $PSScriptRoot `
    -RepositoryRoot $PSScriptRoot `
    -PackageName 'com.smartglasses.ferry20'
