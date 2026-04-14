$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendRoot = Join-Path $projectRoot "BackEnd-EnglishNova\distributed"
$logsRoot = Join-Path $projectRoot ".local\logs\backend"
$launcherScript = Join-Path $PSScriptRoot "start-java-service.ps1"
$powershellExe = (Get-Command powershell).Source

New-Item -ItemType Directory -Path $logsRoot -Force | Out-Null

. (Join-Path $PSScriptRoot "load-runtime-env.ps1")

Set-Location $backendRoot
mvn -q -DskipTests package

$services = @(
    "auth-service",
    "system-service",
    "study-service",
    "search-service",
    "import-service",
    "quiz-service",
    "gateway-service"
)

$processes = @()

foreach ($service in $services) {
    $jarPath = Join-Path $backendRoot "$service\target\$service-0.0.1-SNAPSHOT.jar"
    if (-not (Test-Path $jarPath)) {
        throw "Jar not found for ${service}: $jarPath"
    }

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

    $processes += [PSCustomObject]@{
        Service = $service
        Pid = $process.Id
        Log = $stdoutLog
    }

    Start-Sleep -Seconds 2
}

$processes | Format-Table -AutoSize
