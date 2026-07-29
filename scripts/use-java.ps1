$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path "$PSScriptRoot\.."

# Prefer the portable JDK downloaded into this project, then fall back to Java on PATH.
$localJdkJava = Get-ChildItem -Path "$projectRoot\build\tools\jdk21" -Recurse -Filter "java.exe" -ErrorAction SilentlyContinue |
    Select-Object -First 1

if ($localJdkJava) {
    $jdkBin = $localJdkJava.Directory.FullName
    $env:JAVA_HOME = Split-Path -Parent $jdkBin
    $env:PATH = $jdkBin + [System.IO.Path]::PathSeparator + $env:PATH
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "java was not found. Install JDK 21 or use the portable JDK under build\tools\jdk21."
}
