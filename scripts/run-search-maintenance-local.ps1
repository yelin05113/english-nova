$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendRoot = Join-Path $projectRoot "BackEnd-EnglishNova\distributed"
$logsRoot = Join-Path $projectRoot ".local\logs\backend"
$launcherScript = Join-Path $PSScriptRoot "start-java-service.ps1"
$powershellExe = (Get-Command powershell).Source
$service = "search-service"

New-Item -ItemType Directory -Path $logsRoot -Force | Out-Null

. (Join-Path $PSScriptRoot "load-runtime-env.ps1")

$running = Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^java(?:w)?\.exe$' -and $_.CommandLine -like "*$service-0.0.1-SNAPSHOT.jar*"
}

foreach ($process in $running) {
    Stop-Process -Id $process.ProcessId -Force
}

Start-Sleep -Seconds 2

Set-Location $backendRoot
mvn -q -DskipTests package -pl $service -am

$jarPath = Join-Path $backendRoot "$service\target\$service-0.0.1-SNAPSHOT.jar"
$stdoutLog = Join-Path $logsRoot "$service.log"
$stderrLog = Join-Path $logsRoot "$service.err.log"

Set-Content -Path $stdoutLog -Value ""
Set-Content -Path $stderrLog -Value ""

$argumentList = @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", $launcherScript,
    "-JarPath", $jarPath,
    "-StdoutLog", $stdoutLog,
    "-StderrLog", $stderrLog
)

$process = Start-Process -FilePath $powershellExe -ArgumentList $argumentList -WorkingDirectory $backendRoot -WindowStyle Hidden -PassThru

[PSCustomObject]@{
    Service = $service
    Pid = $process.Id
    Log = $stdoutLog
} | Format-Table -AutoSize
