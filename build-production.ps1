$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$javaCommand = Get-Command java -ErrorAction SilentlyContinue
$javacCommand = Get-Command javac -ErrorAction SilentlyContinue
if (!$javaCommand -or !$javacCommand) {
    throw 'Java 21 JDK is required. Ensure both java and javac are on PATH.'
}

$javaInfo = New-Object System.Diagnostics.ProcessStartInfo
$javaInfo.FileName = $javaCommand.Source
$javaInfo.Arguments = '-version'
$javaInfo.UseShellExecute = $false
$javaInfo.RedirectStandardOutput = $true
$javaInfo.RedirectStandardError = $true
$javaProcess = New-Object System.Diagnostics.Process
$javaProcess.StartInfo = $javaInfo
[void]$javaProcess.Start()
$javaStdout = $javaProcess.StandardOutput.ReadToEnd()
$javaStderr = $javaProcess.StandardError.ReadToEnd()
$javaProcess.WaitForExit()
$java = $javaStdout + $javaStderr
if ($javaProcess.ExitCode -ne 0 -or $java -notmatch 'version "21(?:\.|")') {
    throw "Java 21 is required. Detected output: $java"
}

Write-Host 'Running Living Realms core gates...'
$coreOut = Join-Path $root 'build\core-test-windows'
if (Test-Path $coreOut) { Remove-Item -Recurse -Force $coreOut }
New-Item -ItemType Directory -Force -Path $coreOut | Out-Null
$sources = @()
$sources += Get-ChildItem -Path (Join-Path $root 'src\main\java\dev\livingrealms\sim') -Recurse -Filter *.java | ForEach-Object FullName
$sources += Get-ChildItem -Path (Join-Path $root 'src\testCore\java') -Recurse -Filter *.java | ForEach-Object FullName
& javac --release 21 -Xlint:all -Werror -d $coreOut @sources
if ($LASTEXITCODE -ne 0) { throw 'Core compilation failed.' }
foreach ($main in @('dev.livingrealms.CoreSimulationTest','dev.livingrealms.SpeciesPackAuditTest','dev.livingrealms.SystemCompletenessTest','dev.livingrealms.ProjectionStressTest','dev.livingrealms.SaveMigrationMatrixTest','dev.livingrealms.SaveIntegrityTest','dev.livingrealms.SaveMutationFuzzTest','dev.livingrealms.ProductionHardeningTest','dev.livingrealms.LivingWorldDensityTest','dev.livingrealms.WorldgenQualityTest','dev.livingrealms.SocietyDialogueTest','dev.livingrealms.RumorNetworkTest','dev.livingrealms.SocietyInfrastructureTest','dev.livingrealms.CivilizationLayerTest','dev.livingrealms.CitizenConversationTest','dev.livingrealms.WizardTreesTest','dev.livingrealms.LongRunSoakTest')) {
    & java -cp $coreOut $main
    if ($LASTEXITCODE -ne 0) { throw "Core test failed: $main" }
}

Write-Host 'Running Living Realms release source audit...'
$python = Get-Command python -ErrorAction SilentlyContinue
if ($python) {
    & $python.Source (Join-Path $root 'scripts\release-audit.py')
} else {
    $py = Get-Command py -ErrorAction SilentlyContinue
    if (!$py) { throw 'Python 3 is required for the mandatory release source audit.' }
    & $py.Source -3 (Join-Path $root 'scripts\release-audit.py')
}
if ($LASTEXITCODE -ne 0) { throw 'Release source audit failed.' }

$gradleVersion = '8.10.2'
$expectedSha256 = '31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26'
$cache = Join-Path $root '.gradle-bootstrap'
$gradleHome = Join-Path $cache "gradle-$gradleVersion"
$zip = Join-Path $cache "gradle-$gradleVersion-bin.zip"
if (!(Test-Path $gradleHome)) {
    New-Item -ItemType Directory -Force -Path $cache | Out-Null
    if (!(Test-Path $zip)) {
        Write-Host "Downloading Gradle $gradleVersion..."
        Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" -OutFile $zip
    }
    $actualSha256 = (Get-FileHash -Algorithm SHA256 -Path $zip).Hash.ToLowerInvariant()
    if ($actualSha256 -ne $expectedSha256) {
        Remove-Item -Force $zip -ErrorAction SilentlyContinue
        throw "Gradle $gradleVersion checksum mismatch: expected $expectedSha256, got $actualSha256"
    }
    Expand-Archive -Path $zip -DestinationPath $cache -Force
}

Write-Host 'Running full NeoForge/Create build...'
& (Join-Path $gradleHome 'bin\gradle.bat') --no-daemon clean build
if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed.' }
Write-Host 'Build completed. Check build\libs for the mod jar.'
