$envPath = "D:\Projects\english-nova\.env"
Get-Content $envPath | ForEach-Object {
    if ($_ -match '^(?!#)([^=]+)=(.*)$') {
        [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
    }
}

[System.Environment]::SetEnvironmentVariable('ENGLISH_NOVA_ENRICHMENT_EXAMPLE_AUDIO_ENABLED', 'false', 'Process')
[System.Environment]::SetEnvironmentVariable('OPENAI_API_KEY', '', 'Process')
[System.Environment]::SetEnvironmentVariable('OPENAI_MODEL', '', 'Process')
[System.Environment]::SetEnvironmentVariable('ENGLISH_NOVA_ENRICHMENT_TTS_MODEL', '', 'Process')
[System.Environment]::SetEnvironmentVariable('ENGLISH_NOVA_ENRICHMENT_TTS_VOICE', '', 'Process')
[System.Environment]::SetEnvironmentVariable('ENGLISH_NOVA_ENRICHMENT_BATCH_SIZE', '50', 'Process')
[System.Environment]::SetEnvironmentVariable('ENGLISH_NOVA_ENRICHMENT_WORKER_CONCURRENCY', '8', 'Process')
[System.Environment]::SetEnvironmentVariable('ENGLISH_NOVA_ENRICHMENT_WORKER_DELAY_MS', '1000', 'Process')
[System.Environment]::SetEnvironmentVariable('ENGLISH_NOVA_ENRICHMENT_BACKFILL_DELAY_MS', '10000', 'Process')
[System.Environment]::SetEnvironmentVariable('ENGLISH_NOVA_SEARCH_PUBLIC_CATALOG_IMPORT_CONCURRENCY', '8', 'Process')
[System.Environment]::SetEnvironmentVariable('ENGLISH_NOVA_SEARCH_PUBLIC_CATALOG_IMPORT_WORKER_DELAY_MS', '1000', 'Process')

Set-Location "D:\Projects\english-nova\BackEnd-EnglishNova\distributed\search-service"
& "D:\apache-maven-3.9.4\bin\mvn.cmd" -q spring-boot:run
