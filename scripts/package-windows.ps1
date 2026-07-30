param(
    [string]$Version = "0.1.0"
)

$ErrorActionPreference = "Stop"

. "$PSScriptRoot/use-java.ps1"
. "$PSScriptRoot/ensure-wix.ps1"

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw "jpackage was not found. Install JDK 21 or newer and make sure it is on PATH."
}

$displayName = "LaTeX Compiler"
$artifactName = "LaTeX-Compiler"
$appImagePath = "build/install/$artifactName"
if (Get-Command gradle -ErrorAction SilentlyContinue) {
    # Avoid `clean` here because the portable JDK/WiX cache also lives under build\tools.
    gradle installDist
    $appImage = Resolve-Path $appImagePath
} else {
    $jarPath = & "$PSScriptRoot/build-jar.ps1" -Version $Version
    if (Test-Path $appImagePath) {
        Remove-Item -Recurse -Force $appImagePath
    }
    New-Item -ItemType Directory -Force -Path "$appImagePath/lib" | Out-Null
    Copy-Item -Force "build/manual/libs/*.jar" "$appImagePath/lib/"
    $appImage = Resolve-Path $appImagePath
}

$toolsPath = "tools"
if (Test-Path $toolsPath) {
    Copy-Item -Recurse -Force $toolsPath "$appImage/tools"
}

$mainJar = Get-ChildItem "$appImage/lib/$artifactName-*.jar" | Select-Object -First 1
if ($null -eq $mainJar) {
    throw "Could not find the $artifactName jar in $appImage/lib."
}

$outputDir = "installer"
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

jpackage `
    --type exe `
    --name $displayName `
    --app-version $Version `
    --input "$appImage" `
    --main-jar "lib/$($mainJar.Name)" `
    --main-class "latexcompiler.App" `
    --dest $outputDir `
    --vendor "Ajayi" `
    --win-menu `
    --win-shortcut

if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed with exit code $LASTEXITCODE."
}

$spacedInstaller = Join-Path $outputDir "$displayName-$Version.exe"
$safeInstaller = Join-Path $outputDir "$artifactName-$Version.exe"
if ((Test-Path $spacedInstaller) -and ($spacedInstaller -ne $safeInstaller)) {
    Move-Item -Force $spacedInstaller $safeInstaller
}

Write-Host "Installer written to $outputDir"
