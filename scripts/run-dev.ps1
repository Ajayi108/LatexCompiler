param(
    [string]$Version = "0.1.0"
)

$ErrorActionPreference = "Stop"

. "$PSScriptRoot/use-java.ps1"

$jarPath = & "$PSScriptRoot/build-jar.ps1" -Version $Version
java -jar $jarPath
