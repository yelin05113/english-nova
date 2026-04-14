$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$frontendRoot = Join-Path $projectRoot "FrontEnd-EnglishNova"
$logsRoot = Join-Path $projectRoot ".local\logs\frontend"

New-Item -ItemType Directory -Path $logsRoot -Force | Out-Null

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$stdoutLog = Join-Path $logsRoot "vite-$timestamp.log"
$stderrLog = Join-Path $logsRoot "vite-$timestamp.err.log"
$cmdExe = (Get-Command cmd).Source
$nodeExe = (Get-Command node).Source
$viteJs = Join-Path $frontendRoot "node_modules\vite\bin\vite.js"
$viteConfig = Join-Path $frontendRoot "vite.config.mjs"

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $cmdExe
$psi.WorkingDirectory = $frontendRoot
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true
$psi.Arguments = '/c ""{0}" "{1}" --config "{2}" --host 0.0.0.0 --port 3000 --configLoader native 1>> "{3}" 2>> "{4}""' -f $nodeExe, $viteJs, $viteConfig, $stdoutLog, $stderrLog

$process = [System.Diagnostics.Process]::Start($psi)
Start-Sleep -Seconds 3
$process.Refresh()

[PSCustomObject]@{
    Service = "frontend"
    Pid = $process.Id
    Log = $stdoutLog
    HasExited = $process.HasExited
    ExitCode = if ($process.HasExited) { $process.ExitCode } else { $null }
} | Format-Table -AutoSize
