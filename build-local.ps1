$ErrorActionPreference = 'Stop'
$Version = '8.10.2'
$Cache = Join-Path $PSScriptRoot '.gradle-bootstrap'
$Zip = Join-Path $Cache "gradle-$Version-bin.zip"
$GradleHome = Join-Path $Cache "gradle-$Version"
New-Item -ItemType Directory -Force -Path $Cache | Out-Null
if (!(Test-Path $GradleHome)) {
  if (!(Test-Path $Zip)) {
    Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$Version-bin.zip" -OutFile $Zip
  }
  Expand-Archive -Force $Zip $Cache
}
$Log = Join-Path $PSScriptRoot 'build-local.log'
$GradleExe = Join-Path $GradleHome 'bin\gradle.bat'
$GradleCommand = "`"$GradleExe`" --no-daemon clean build --stacktrace 2>&1"
& $env:ComSpec /d /s /c $GradleCommand |
  Tee-Object -FilePath $Log
$GradleExit = $LASTEXITCODE
if ($GradleExit -ne 0) {
  Write-Host ""
  Write-Host "Build failed. Full log: $Log"
  exit $GradleExit
}
Write-Host "Built JAR(s) are in build\libs"
Write-Host "Build log: $Log"
