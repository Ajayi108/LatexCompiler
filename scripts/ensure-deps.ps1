$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path "$PSScriptRoot\.."
$libsDir = Join-Path $projectRoot "libs"
New-Item -ItemType Directory -Force -Path $libsDir | Out-Null

$pdfBoxVersion = "3.0.8"
$pdfBoxJar = Join-Path $libsDir "pdfbox-app-$pdfBoxVersion.jar"

if (-not (Test-Path $pdfBoxJar)) {
    $url = "https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox-app/$pdfBoxVersion/pdfbox-app-$pdfBoxVersion.jar"
    Write-Host "Downloading pdfbox-app-$pdfBoxVersion.jar..."
    curl.exe -L -o $pdfBoxJar $url
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $pdfBoxJar) -or (Get-Item $pdfBoxJar).Length -eq 0) {
        throw "Could not download pdfbox-app-$pdfBoxVersion.jar."
    }
}
