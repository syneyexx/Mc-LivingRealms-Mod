$ErrorActionPreference = 'Stop'
$Version = '8.10.2'
$Cache = Join-Path $PSScriptRoot '.gradle-bootstrap'
$Zip = Join-Path $Cache "gradle-$Version-bin.zip"
$Home = Join-Path $Cache "gradle-$Version"
New-Item -ItemType Directory -Force -Path $Cache | Out-Null
if (!(Test-Path $Home)) {
  if (!(Test-Path $Zip)) {
    Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$Version-bin.zip" -OutFile $Zip
  }
  Expand-Archive -Force $Zip $Cache
}
& (Join-Path $Home 'bin\gradle.bat') --no-daemon clean build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "Built JAR(s) are in build\libs"
