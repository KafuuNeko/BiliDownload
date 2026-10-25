param(
    [Parameter(Mandatory = $true)]
    [string]$Destination
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$settings = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'android-studio.properties') -Raw | ConvertFrom-StringData
$installRoot = [IO.Path]::GetFullPath($Destination)

if ($IsWindows) {
    $platform = 'windows'
    $extension = 'zip'
    $studioHome = Join-Path $installRoot 'android-studio'
} elseif ($IsMacOS) {
    $platform = if ([Runtime.InteropServices.RuntimeInformation]::OSArchitecture -eq 'Arm64') { 'mac_arm' } else { 'mac' }
    $extension = 'dmg'
    $studioHome = Join-Path $installRoot 'Android Studio.app/Contents'
} else {
    $platform = 'linux'
    $extension = 'tar.gz'
    $studioHome = Join-Path $installRoot 'android-studio'
}

$buildFile = Join-Path $studioHome $(if ($IsMacOS) { 'Resources/build.txt' } else { 'build.txt' })
if (!(Test-Path -LiteralPath $buildFile)) {
    if ((Test-Path -LiteralPath $installRoot) -and (Get-ChildItem -LiteralPath $installRoot -Force | Select-Object -First 1)) {
        throw "Installation directory is not empty: $installRoot"
    }
    New-Item -ItemType Directory -Path $installRoot -Force | Out-Null
    $archiveName = "$($settings.archive)-$platform.$extension"
    $archive = Join-Path $installRoot $archiveName
    $downloadKind = if ($IsMacOS) { 'install' } else { 'ide-zips' }
    $url = "https://dl.google.com/dl/android/studio/$downloadKind/$($settings.release)/$archiveName"
    Write-Host "Downloading Android Studio $($settings.build) from $url"
    Invoke-WebRequest -Uri $url -OutFile $archive
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $settings["sha256.$platform"]) {
        throw "Android Studio archive checksum mismatch: $archive"
    }

    if ($IsWindows) {
        Expand-Archive -LiteralPath $archive -DestinationPath $installRoot
    } elseif ($IsMacOS) {
        $mountPoint = Join-Path $installRoot 'mounted'
        & hdiutil attach $archive -mountpoint $mountPoint -nobrowse -readonly -quiet
        if ($LASTEXITCODE -ne 0) { throw 'Unable to mount Android Studio archive' }
        try {
            & ditto (Join-Path $mountPoint 'Android Studio.app') (Join-Path $installRoot 'Android Studio.app')
            if ($LASTEXITCODE -ne 0) { throw 'Unable to copy Android Studio application' }
        } finally {
            & hdiutil detach $mountPoint -quiet
            if ($LASTEXITCODE -ne 0) { throw 'Unable to detach Android Studio archive' }
        }
    } else {
        & tar -xzf $archive -C $installRoot
        if ($LASTEXITCODE -ne 0) { throw 'Unable to extract Android Studio archive' }
    }
    Remove-Item -LiteralPath $archive
}

if (!(Test-Path -LiteralPath $buildFile) -or [IO.File]::ReadAllText($buildFile).Trim() -ne $settings.build) {
    throw "Expected Android Studio $($settings.build) in $studioHome"
}
Write-Host "ANDROID_STUDIO_HOME=$studioHome"
if ($env:GITHUB_ENV) {
    [IO.File]::AppendAllText($env:GITHUB_ENV, "ANDROID_STUDIO_HOME=$studioHome`n", [Text.UTF8Encoding]::new($false))
}
