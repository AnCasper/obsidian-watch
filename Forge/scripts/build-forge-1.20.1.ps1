$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot
$project = Join-Path $repo "platforms\forge-1.20.1"
$gradle = Join-Path $env:USERPROFILE "Downloads\gradle-portable\gradle-8.10.2\bin\gradle.bat"

Set-Location $project
& $gradle clean build
