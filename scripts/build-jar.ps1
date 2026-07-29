param(
    [string]$Version = "0.1.0"
)

$ErrorActionPreference = "Stop"

. "$PSScriptRoot/use-java.ps1"
. "$PSScriptRoot/ensure-deps.ps1"

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    throw "javac was not found. Install JDK 21 or newer and make sure it is on PATH."
}

if (-not (Get-Command jar -ErrorAction SilentlyContinue)) {
    throw "jar was not found. Install JDK 21 or newer and make sure it is on PATH."
}

$buildRoot = "build/manual"
$classesDir = "$buildRoot/classes"
$depsDir = "$buildRoot/deps"
$libsDir = "$buildRoot/libs"
$manifestPath = "$buildRoot/MANIFEST.MF"

if (Test-Path $buildRoot) {
    Remove-Item -Recurse -Force $buildRoot
}

New-Item -ItemType Directory -Force -Path $classesDir, $depsDir, $libsDir | Out-Null

$sourceDependencyJars = Get-ChildItem -Path "libs" -Filter "*.jar" -ErrorAction SilentlyContinue
foreach ($jar in $sourceDependencyJars) {
    Copy-Item -Force $jar.FullName $depsDir
}

$dependencyJars = Get-ChildItem -Path $depsDir -Filter "*.jar" -ErrorAction SilentlyContinue
$dependencyClassPath = ($dependencyJars | ForEach-Object { $_.FullName }) -join [System.IO.Path]::PathSeparator

$sources = Get-ChildItem -Path "src/main/java" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
if (-not $sources) {
    throw "No Java source files found."
}

javac -encoding UTF-8 -d $classesDir $sources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE."
}

$manifest = @(
    "Manifest-Version: 1.0",
    "Main-Class: latexcompiler.App",
    "Class-Path: $(($dependencyJars | ForEach-Object { $_.Name }) -join ' ')",
    "Implementation-Title: LatexCompiler",
    "Implementation-Version: $Version",
    ""
)
$manifest | Set-Content -Encoding ASCII $manifestPath

$jarPath = "$libsDir/LatexCompiler-$Version.jar"
jar --create --file $jarPath --manifest $manifestPath -C $classesDir .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE."
}

foreach ($jar in $dependencyJars) {
    Copy-Item -Force $jar.FullName $libsDir
}

Write-Output (Resolve-Path $jarPath).Path
