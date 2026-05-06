$envPath = "D:\Projects\english-nova\.env"

if (Test-Path $envPath) {
    Get-Content $envPath | ForEach-Object {
        if ($_ -match '^(?!#)([^=]+)=(.*)$') {
            [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
        }
    }
}

if (-not [System.Environment]::GetEnvironmentVariable('DEEPSEEK_BASE_URL', 'Process')) {
    [System.Environment]::SetEnvironmentVariable('DEEPSEEK_BASE_URL', 'https://api.deepseek.com', 'Process')
}

[System.Environment]::SetEnvironmentVariable('DEEPSEEK_MODEL', 'deepseek-v4-flash', 'Process')

[System.Environment]::SetEnvironmentVariable('ENGLISH_NOVA_ENRICHMENT_EXAMPLE_AUDIO_ENABLED', 'false', 'Process')
[System.Environment]::SetEnvironmentVariable('ENGLISH_NOVA_ENRICHMENT_BATCH_SIZE', '30', 'Process')
[System.Environment]::SetEnvironmentVariable('ENGLISH_NOVA_ENRICHMENT_WORKER_CONCURRENCY', '12', 'Process')
[System.Environment]::SetEnvironmentVariable('ENGLISH_NOVA_ENRICHMENT_WORKER_DELAY_MS', '1000', 'Process')
[System.Environment]::SetEnvironmentVariable('ENGLISH_NOVA_ENRICHMENT_BACKFILL_DELAY_MS', '10000', 'Process')
[System.Environment]::SetEnvironmentVariable('ENGLISH_NOVA_ENRICHMENT_PUBLIC_LIMIT', '35000', 'Process')

if (-not [System.Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY', 'Process')) {
    Write-Error "DEEPSEEK_API_KEY is not configured. Add it to D:\Projects\english-nova\.env or set it in this PowerShell session."
    exit 1
}

Set-Location "D:\Projects\english-nova\BackEnd-EnglishNova\distributed\search-service"
& "D:\apache-maven-3.9.4\bin\mvn.cmd" -q spring-boot:run
