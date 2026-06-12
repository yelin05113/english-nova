$ErrorActionPreference = "Stop"

$rootDir = "D:\Projects\english-nova"
$serviceDir = Join-Path $rootDir "BackEnd-EnglishNova\distributed\search-service"
$logDir = Join-Path $rootDir "logs\search-enrichment"
$stdoutLog = Join-Path $logDir "stdout.log"
$stderrLog = Join-Path $logDir "stderr.log"
$scriptPath = Join-Path $rootDir "scripts\start-search-enrichment.ps1"

function Normalize-ProcessPathEnvironment {
    $pathValue = [System.Environment]::GetEnvironmentVariable('Path', 'Process')
    if ([string]::IsNullOrWhiteSpace($pathValue)) {
        $pathValue = [System.Environment]::GetEnvironmentVariable('PATH', 'Process')
    }
    if (-not [string]::IsNullOrWhiteSpace($pathValue)) {
        [System.Environment]::SetEnvironmentVariable('PATH', $pathValue, 'Process')
    }
    [System.Environment]::SetEnvironmentVariable('Path', $null, 'Process')
}

if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
}

Set-Content -Path $stdoutLog -Value "" -Encoding UTF8
Set-Content -Path $stderrLog -Value "" -Encoding UTF8
Normalize-ProcessPathEnvironment

Push-Location $serviceDir
try {
    $process = Start-Process `
        -FilePath "powershell.exe" `
        -ArgumentList @(
            "-NoLogo",
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-File", $scriptPath
        ) `
        -WorkingDirectory $serviceDir `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -PassThru

    Write-Host "Started search enrichment worker. PID: $($process.Id)"
    Write-Host "stdout: $stdoutLog"
    Write-Host "stderr: $stderrLog"
}
finally {
    Pop-Location
}
