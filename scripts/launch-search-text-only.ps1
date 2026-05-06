$envPath = "D:\Projects\english-nova\.env"
$serviceDir = "D:\Projects\english-nova\BackEnd-EnglishNova\distributed\search-service"
$logDir = "D:\Projects\english-nova\logs\search-service"
$stdoutLog = Join-Path $logDir "stdout.log"
$stderrLog = Join-Path $logDir "stderr.log"

if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
}

if (Test-Path $envPath) {
    Get-Content $envPath | ForEach-Object {
        if ($_ -match '^(?!#)([^=]+)=(.*)$') {
            [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
        }
    }
}

[System.Environment]::SetEnvironmentVariable('ENGLISH_NOVA_ENRICHMENT_EXAMPLE_AUDIO_ENABLED', 'false', 'Process')

$command = "start ""search-service-text-only"" /min powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File ""D:\Projects\english-nova\scripts\start-search-text-only.ps1"" 1>>""$stdoutLog"" 2>>""$stderrLog"""

Push-Location $serviceDir
try {
    cmd.exe /c $command
}
finally {
    Pop-Location
}
