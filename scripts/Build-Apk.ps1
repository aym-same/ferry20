param(
    [Parameter(Mandatory = $true)][string]$ProjectRoot,
    [Parameter(Mandatory = $true)][string]$RepositoryRoot,
    [Parameter(Mandatory = $true)][string]$PackageName,
    [string]$ApplicationClasses
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Android-Paths.ps1')

$mainRoot = Join-Path $ProjectRoot 'app\src\main'
$javaRoot = Join-Path $mainRoot 'java'
$resRoot = Join-Path $mainRoot 'res'
$manifest = Join-Path $mainRoot 'AndroidManifest.xml'
$buildRoot = Join-Path $ProjectRoot 'build'
$genRoot = Join-Path $buildRoot 'generated'
$compiledRes = Join-Path $buildRoot 'resources.zip'
$unsignedApk = Join-Path $buildRoot 'ferry20-unsigned.apk'
$alignedApk = Join-Path $buildRoot 'ferry20-aligned.apk'
$artifactsRoot = Join-Path $ProjectRoot 'artifacts'
$signedApk = Join-Path $artifactsRoot 'ferry20-debug.apk'
$sharedArtifactsRoot = Join-Path $RepositoryRoot 'artifacts'
# Both APKs must use this same locally generated key for instrumentation to run.
$keystore = Join-Path $sharedArtifactsRoot 'debug.keystore'
$classesRoot = Join-Path $buildRoot 'classes'
$classesJar = Join-Path $buildRoot 'classes.jar'
$dexRoot = Join-Path $buildRoot 'dex'

$sdkRoot = Get-AndroidSdkRoot
$jdkRoot = Get-AndroidJdkRoot
$buildTools = Join-Path $sdkRoot 'build-tools\36.0.0'
$androidJar = Join-Path $sdkRoot 'platforms\android-36\android.jar'
$aapt2 = Join-Path $buildTools 'aapt2.exe'
$d8 = Join-Path $buildTools 'd8.bat'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$apksigner = Join-Path $buildTools 'apksigner.bat'
$javac = Join-Path $jdkRoot 'bin\javac.exe'
$jar = Join-Path $jdkRoot 'bin\jar.exe'
$keytool = Join-Path $jdkRoot 'bin\keytool.exe'
$env:JAVA_HOME = $jdkRoot

foreach ($required in @($aapt2, $d8, $zipalign, $apksigner, $javac, $jar, $keytool, $androidJar, $manifest)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Required build input not found: $required. Install Android SDK Platform 36, Build Tools 36.0.0, and a JDK; set ANDROID_HOME/JAVA_HOME if necessary."
    }
}
if ($ApplicationClasses -and -not (Test-Path -LiteralPath $ApplicationClasses -PathType Leaf)) {
    throw "App classes not found: $ApplicationClasses. Run tests/build.ps1 to build both APKs."
}

$javaSources = @(Get-ChildItem -LiteralPath $javaRoot -Filter '*.java' -Recurse -File)
if ($javaSources.Count -eq 0) { throw "No Java sources found under $javaRoot" }

# Resolve and validate the exact build directory before any recursive cleanup.
$fullRepositoryRoot = [System.IO.Path]::GetFullPath($RepositoryRoot).TrimEnd('\', '/')
$fullProjectRoot = [System.IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\', '/')
$fullBuildRoot = [System.IO.Path]::GetFullPath($buildRoot).TrimEnd('\', '/')
$repositoryPrefix = $fullRepositoryRoot + [System.IO.Path]::DirectorySeparatorChar
$projectPrefix = $fullProjectRoot + [System.IO.Path]::DirectorySeparatorChar
if (($fullProjectRoot -ne $fullRepositoryRoot -and -not $fullProjectRoot.StartsWith($repositoryPrefix, [System.StringComparison]::OrdinalIgnoreCase)) -or
    -not $fullBuildRoot.Equals((Join-Path $fullProjectRoot 'build'), [System.StringComparison]::OrdinalIgnoreCase) -or
    -not $fullBuildRoot.StartsWith($projectPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to remove build path outside project: $fullBuildRoot"
}
$ancestor = $fullProjectRoot
while ($true) {
    if ((Get-Item -LiteralPath $ancestor -Force).Attributes -band [System.IO.FileAttributes]::ReparsePoint) {
        throw "Refusing to build through a directory link: $ancestor"
    }
    if ($ancestor -eq $fullRepositoryRoot) { break }
    $ancestor = Split-Path -Parent $ancestor
}
if (Test-Path -LiteralPath $fullBuildRoot) {
    $buildItems = @((Get-Item -LiteralPath $fullBuildRoot -Force)) + @(Get-ChildItem -LiteralPath $fullBuildRoot -Recurse -Force)
    foreach ($item in $buildItems) {
        if ($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) {
            throw "Refusing recursive cleanup containing a directory or file link: $($item.FullName)"
        }
    }
    Remove-Item -LiteralPath $fullBuildRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $buildRoot, $genRoot, $classesRoot, $dexRoot, $artifactsRoot, $sharedArtifactsRoot | Out-Null

Write-Host 'Compiling Android resources...'
& $aapt2 compile --dir $resRoot -o $compiledRes
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed with exit code $LASTEXITCODE" }

Write-Host 'Linking Android resources and generating R.java...'
& $aapt2 link -I $androidJar -o $unsignedApk -R $compiledRes --manifest $manifest --java $genRoot --auto-add-overlay --min-sdk-version 24 --target-sdk-version 32 --rename-manifest-package $PackageName
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed with exit code $LASTEXITCODE" }

$generatedSources = @(Get-ChildItem -LiteralPath $genRoot -Filter '*.java' -Recurse -File)
$allSources = @($javaSources + $generatedSources)
$classPath = if ($ApplicationClasses) { "$androidJar;$ApplicationClasses" } else { $androidJar }
Write-Host ("Compiling {0} Java sources..." -f $allSources.Count)
& $javac -encoding UTF-8 -source 8 -target 8 -classpath $classPath -d $classesRoot @($allSources.FullName)
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

Write-Host 'Packaging Java class files...'
Push-Location $classesRoot
try {
    & $jar -cf $classesJar '.'
    if ($LASTEXITCODE -ne 0) { throw "jar class packaging failed with exit code $LASTEXITCODE" }
}
finally { Pop-Location }

Write-Host 'Converting bytecode to DEX...'
$dexArgs = @('--lib', $androidJar, '--min-api', '24', '--output', $dexRoot)
if ($ApplicationClasses) { $dexArgs += @('--classpath', $ApplicationClasses) }
& $d8 @dexArgs $classesJar
if ($LASTEXITCODE -ne 0) { throw "d8 failed with exit code $LASTEXITCODE" }
$classesDex = Join-Path $dexRoot 'classes.dex'
if (-not (Test-Path -LiteralPath $classesDex)) { throw 'd8 did not produce classes.dex' }

Write-Host 'Adding DEX to APK...'
Push-Location $dexRoot
try {
    & $jar -uf $unsignedApk 'classes.dex'
    if ($LASTEXITCODE -ne 0) { throw "jar update failed with exit code $LASTEXITCODE" }
}
finally { Pop-Location }

Write-Host 'Aligning APK...'
& $zipalign -f 4 $unsignedApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw "zipalign failed with exit code $LASTEXITCODE" }

if (-not (Test-Path -LiteralPath $keystore)) {
    Write-Host 'Generating shared local debug keystore...'
    & $keytool -genkeypair -keystore $keystore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname 'CN=Android Debug,O=Android,C=US'
    if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE" }
}

Write-Host 'Signing APK...'
if (Test-Path -LiteralPath $signedApk) { Remove-Item -LiteralPath $signedApk -Force }
& $apksigner sign --ks $keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $signedApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw "apksigner failed with exit code $LASTEXITCODE" }
& $apksigner verify --verbose $signedApk
if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed with exit code $LASTEXITCODE" }

Write-Host ("APK ready: {0}" -f $signedApk)
Get-Item -LiteralPath $signedApk | Select-Object FullName, Length, LastWriteTime
