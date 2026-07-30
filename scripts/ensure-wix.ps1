$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path "$PSScriptRoot\.."
$toolsRoot = Join-Path $projectRoot "build\tools"
$wixRoot = Join-Path $toolsRoot "wix314"
$wixZip = Join-Path $toolsRoot "wix314-binaries.zip"
$wixUrl = "https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip"

function Use-WixFromDirectory {
    param([string]$Directory)

    if (-not (Test-Path $Directory)) {
        return $false
    }

    $candle = Get-ChildItem -Path $Directory -Recurse -Filter "candle.exe" -ErrorAction SilentlyContinue |
        Select-Object -First 1
    $light = Get-ChildItem -Path $Directory -Recurse -Filter "light.exe" -ErrorAction SilentlyContinue |
        Select-Object -First 1

    if ($null -eq $candle -or $null -eq $light) {
        return $false
    }

    $wixPaths = @($candle.Directory.FullName, $light.Directory.FullName) |
        Select-Object -Unique
    $env:PATH = ($wixPaths -join [System.IO.Path]::PathSeparator) +
        [System.IO.Path]::PathSeparator +
        $env:PATH
    return $true
}

function Test-ZipArchive {
    param([string]$Path)

    if (-not (Test-Path $Path) -or (Get-Item $Path).Length -eq 0) {
        return $false
    }

    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue
        $zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
        $zip.Dispose()
        return $true
    } catch {
        return $false
    }
}

if ((Get-Command candle.exe -ErrorAction SilentlyContinue) -and
    (Get-Command light.exe -ErrorAction SilentlyContinue)) {
    return
}

if (Use-WixFromDirectory $wixRoot) {
    return
}

New-Item -ItemType Directory -Force -Path $toolsRoot, $wixRoot | Out-Null

if (-not (Test-ZipArchive $wixZip)) {
    Remove-Item -Force $wixZip -ErrorAction SilentlyContinue
    Write-Host "Downloading WiX Toolset 3.14 portable binaries..."
    curl.exe -L -o $wixZip $wixUrl
    if ($LASTEXITCODE -ne 0 -or -not (Test-ZipArchive $wixZip)) {
        throw "Could not download WiX Toolset. Download it manually from $wixUrl and extract it to $wixRoot."
    }
}

Expand-Archive -Path $wixZip -DestinationPath $wixRoot -Force

if (-not (Use-WixFromDirectory $wixRoot)) {
    throw "WiX Toolset was downloaded, but candle.exe and light.exe were not found under $wixRoot."
}
