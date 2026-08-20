param(
    [string]$Version = "0.1.0",
    [switch]$MachineInstall
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
$packageInputPath = "build/jpackage-input"
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

if (Test-Path $packageInputPath) {
    Remove-Item -Recurse -Force $packageInputPath
}
New-Item -ItemType Directory -Force -Path $packageInputPath | Out-Null
# jpackage reads from one input directory, so stage every runtime jar beside the main jar.
Copy-Item -Force "$appImage/lib/*.jar" $packageInputPath
if (Test-Path "$appImage/tools") {
    Copy-Item -Recurse -Force "$appImage/tools" "$packageInputPath/tools"
}

$stagedMainJar = Get-ChildItem "$packageInputPath/$artifactName-*.jar" | Select-Object -First 1
if ($null -eq $stagedMainJar) {
    throw "Could not stage the $artifactName jar for jpackage."
}

$outputDir = "installer"
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$jpackageArgs = @(
    "--type", "exe",
    "--name", $displayName,
    "--app-version", $Version,
    "--input", "$packageInputPath",
    "--main-jar", "$($stagedMainJar.Name)",
    "--main-class", "latexcompiler.App",
    "--dest", $outputDir,
    "--vendor", "Ajayi",
    "--win-upgrade-uuid", "6c067019-8e94-48f5-8c81-89b353afd169",
    "--win-menu",
    "--win-shortcut"
)

if (-not $MachineInstall) {
    # Per-user installers avoid admin prompts and are easier to replace during testing.
    $jpackageArgs += "--win-per-user-install"
}

$iconPath = "assets/app-icon.ico"
if (Test-Path $iconPath) {
    $jpackageArgs += @("--icon", (Resolve-Path $iconPath).Path)
}

jpackage @jpackageArgs

if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed with exit code $LASTEXITCODE."
}

$spacedInstaller = Join-Path $outputDir "$displayName-$Version.exe"
$safeInstaller = Join-Path $outputDir "$artifactName-$Version.exe"
if ((Test-Path $spacedInstaller) -and ($spacedInstaller -ne $safeInstaller)) {
    Move-Item -Force $spacedInstaller $safeInstaller
}

Write-Host "Installer written to $outputDir"
