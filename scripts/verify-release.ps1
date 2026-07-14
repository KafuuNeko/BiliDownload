param(
    [Parameter(Mandatory = $true)]
    [string]$Tag,

    [string]$ApkDirectory = "app/build/outputs/apk/release",

    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

function Read-AppVersion([string]$gradleText) {
    $codeMatch = [regex]::Match($gradleText, 'versionCode\s*=\s*([0-9_]+)')
    $nameMatch = [regex]::Match($gradleText, 'versionName\s*=\s*"([^"]+)"')
    if (-not $codeMatch.Success -or -not $nameMatch.Success) {
        throw "Unable to read versionCode/versionName from app/build.gradle.kts"
    }
    [pscustomobject]@{
        Code = [int64]($codeMatch.Groups[1].Value -replace '_', '')
        Name = $nameMatch.Groups[1].Value
    }
}

function Resolve-AndroidSdk {
    if ($env:ANDROID_SDK_ROOT) { return $env:ANDROID_SDK_ROOT }
    if ($env:ANDROID_HOME) { return $env:ANDROID_HOME }
    $sdkLine = Get-Content -LiteralPath "local.properties" -ErrorAction Stop |
        Where-Object { $_ -match '^sdk\.dir=' } |
        Select-Object -First 1
    if (-not $sdkLine) { throw "Android SDK path is not configured" }
    return (($sdkLine -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\')
}

function Resolve-Jdk17 {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += Get-Item -LiteralPath $env:JAVA_HOME -ErrorAction SilentlyContinue }
    $candidates += Get-ChildItem -Path "$env:ProgramFiles\Java\jdk-17*" -Directory -ErrorAction SilentlyContinue
    $candidates += Get-ChildItem -Path "$env:USERPROFILE\.jdks\*17*" -Directory -ErrorAction SilentlyContinue

    foreach ($candidate in $candidates) {
        $java = Join-Path $candidate.FullName "bin\java.exe"
        if (-not (Test-Path -LiteralPath $java)) { continue }
        $version = & $java --version | Select-Object -First 1
        if ($version -match '\b17\.') { return $candidate.FullName }
    }
    throw "JDK 17 was not found; set JAVA_HOME to a JDK 17 installation"
}

$current = Read-AppVersion (Get-Content -LiteralPath "app/build.gradle.kts" -Raw)
if ($current.Name -ne $Tag) {
    throw "versionName '$($current.Name)' does not match release tag '$Tag'"
}

$previousTag = git tag --list "[0-9]*.foss" |
    Where-Object { $_ -ne $Tag -and $_ -match '^\d+\.\d+\.\d+\.foss$' } |
    Sort-Object { [version]($_ -replace '\.foss$', '') } -Descending |
    Select-Object -First 1
if (-not $previousTag) { throw "No previous formal release tag was found" }

$previousGradle = git show "${previousTag}:app/build.gradle.kts"
if ($LASTEXITCODE -ne 0) { throw "Unable to read app/build.gradle.kts from $previousTag" }
$previous = Read-AppVersion ($previousGradle -join "`n")
if ($current.Code -le $previous.Code) {
    throw "versionCode $($current.Code) must be greater than $($previous.Code) from $previousTag"
}

if (-not $SkipBuild) {
    $env:JAVA_HOME = Resolve-Jdk17
    & .\gradlew.bat assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "Release build failed" }
}

$sdk = Resolve-AndroidSdk
$aapt = Get-ChildItem -LiteralPath (Join-Path $sdk "build-tools") -Directory |
    Sort-Object { [version]$_.Name } -Descending |
    ForEach-Object { Join-Path $_.FullName "aapt.exe" } |
    Where-Object { Test-Path -LiteralPath $_ } |
    Select-Object -First 1
if (-not $aapt) { throw "aapt.exe was not found in Android SDK build-tools" }

$apks = Get-ChildItem -LiteralPath $ApkDirectory -Filter "*.apk" -File -Recurse
if ($apks.Count -ne 4) {
    throw "Expected four ABI release APKs in '$ApkDirectory', found $($apks.Count)"
}

$expectedAbis = @("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
$actualAbis = @()
foreach ($apk in $apks) {
    $badging = & $aapt dump badging $apk.FullName
    if ($LASTEXITCODE -ne 0) { throw "aapt failed for $($apk.FullName)" }
    $packageLine = $badging | Where-Object { $_ -like "package:*" } | Select-Object -First 1
    $code = [regex]::Match($packageLine, "versionCode='([^']+)'" ).Groups[1].Value
    $name = [regex]::Match($packageLine, "versionName='([^']+)'" ).Groups[1].Value
    if ([int64]$code -ne $current.Code -or $name -ne $current.Name) {
        throw "$($apk.Name) has versionCode='$code' versionName='$name'"
    }
    $nativeLine = $badging | Where-Object { $_ -like "native-code:*" } | Select-Object -First 1
    $abiMatches = [regex]::Matches($nativeLine, "'([^']+)'" )
    if ($abiMatches.Count -ne 1) {
        throw "$($apk.Name) must contain exactly one ABI"
    }
    $actualAbis += $abiMatches[0].Groups[1].Value
}

$missingAbis = $expectedAbis | Where-Object { $_ -notin $actualAbis }
if ($missingAbis.Count -gt 0) {
    throw "Missing ABI APKs: $($missingAbis -join ', ')"
}

Write-Host "Release identity verified: $Tag / versionCode $($current.Code) / 4 ABI APKs"
