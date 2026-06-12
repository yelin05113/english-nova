$ErrorActionPreference = "Stop"

$rootDir = "D:\Projects\english-nova"
$envPath = Join-Path $rootDir ".env"
$serviceDir = Join-Path $rootDir "BackEnd-EnglishNova\distributed\search-service"
$jarPath = Join-Path $serviceDir "target\search-service-0.0.1-SNAPSHOT.jar"

function Import-DotEnv([string]$path) {
    if (-not (Test-Path $path)) {
        return
    }
    Get-Content $path | ForEach-Object {
        if ($_ -match '^(?!#)([^=]+)=(.*)$') {
            [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
        }
    }
}

function Get-EnvValue([string[]]$names) {
    foreach ($name in $names) {
        $value = [System.Environment]::GetEnvironmentVariable($name, 'Process')
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            return $value
        }
    }
    return $null
}

function Set-EnvDefault([string]$name, [string]$value) {
    $current = [System.Environment]::GetEnvironmentVariable($name, 'Process')
    if ([string]::IsNullOrWhiteSpace($current)) {
        [System.Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}

function Resolve-JavaCommand {
    $javaHome = [System.Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
    if (-not [string]::IsNullOrWhiteSpace($javaHome)) {
        $candidate = Join-Path $javaHome "bin\java.exe"
        if (Test-Path $candidate) {
            return $candidate
        }
    }
    $command = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }
    $command = Get-Command java -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }
    throw "Java executable was not found. Install Java or set JAVA_HOME."
}

function Test-TcpPort([string]$serverHost, [int]$serverPort, [int]$timeoutMs = 1500) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($serverHost, $serverPort, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne($timeoutMs, $false)) {
            return $false
        }
        $client.EndConnect($async)
        return $true
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

Import-DotEnv $envPath

$openAiBaseUrlFromEnv = [System.Environment]::GetEnvironmentVariable('OPENAI_BASE_URL', 'Process')
$openAiModelFromEnv = [System.Environment]::GetEnvironmentVariable('OPENAI_MODEL', 'Process')
if (($openAiBaseUrlFromEnv -like '*xiaomimimo.com*') -or ($openAiModelFromEnv -like 'mimo-*')) {
    [System.Environment]::SetEnvironmentVariable('DEEPSEEK_BASE_URL', $null, 'Process')
    [System.Environment]::SetEnvironmentVariable('DEEPSEEK_API_KEY', $null, 'Process')
    [System.Environment]::SetEnvironmentVariable('DEEPSEEK_MODEL', $null, 'Process')
}

[System.Environment]::SetEnvironmentVariable('ENGLISH_NOVA_SEARCH_REBUILD_ON_STARTUP', 'false', 'Process')
Set-EnvDefault 'ENGLISH_NOVA_ENRICHMENT_EXAMPLE_AUDIO_ENABLED' 'true'
Set-EnvDefault 'ENGLISH_NOVA_ENRICHMENT_BATCH_SIZE' '30'
Set-EnvDefault 'ENGLISH_NOVA_ENRICHMENT_WORKER_CONCURRENCY' '12'
Set-EnvDefault 'ENGLISH_NOVA_ENRICHMENT_TTS_CONCURRENCY' '6'
Set-EnvDefault 'ENGLISH_NOVA_ENRICHMENT_WORKER_DELAY_MS' '1000'
Set-EnvDefault 'ENGLISH_NOVA_ENRICHMENT_BACKFILL_DELAY_MS' '10000'
Set-EnvDefault 'ENGLISH_NOVA_ENRICHMENT_PUBLIC_LIMIT' '35000'
Set-EnvDefault 'ENGLISH_NOVA_ENRICHMENT_TEXT_ONLY_MODE' 'false'
Set-EnvDefault 'ENGLISH_NOVA_ENRICHMENT_AUDIO_ONLY_MODE' 'false'
Set-EnvDefault 'ENGLISH_NOVA_ENRICHMENT_AUDIO_SCOPE_FIELD' 'created_at'
Set-EnvDefault 'ENGLISH_NOVA_ENRICHMENT_AUDIO_SCOPE_ORDER' 'asc'
Set-EnvDefault 'ENGLISH_NOVA_ENRICHMENT_AUDIO_SCOPE_LIMIT' '50000'

$apiKey = Get-EnvValue @('OPENAI_API_KEY', 'DEEPSEEK_API_KEY')
$textModel = Get-EnvValue @('OPENAI_MODEL', 'DEEPSEEK_MODEL')
$ttsModel = Get-EnvValue @('ENGLISH_NOVA_ENRICHMENT_TTS_MODEL')
$ttsVoice = Get-EnvValue @('ENGLISH_NOVA_ENRICHMENT_TTS_VOICE')
$textOnlyMode = (Get-EnvValue @('ENGLISH_NOVA_ENRICHMENT_TEXT_ONLY_MODE'))
$audioOnlyMode = (Get-EnvValue @('ENGLISH_NOVA_ENRICHMENT_AUDIO_ONLY_MODE'))
$audioScopeField = Get-EnvValue @('ENGLISH_NOVA_ENRICHMENT_AUDIO_SCOPE_FIELD')
$audioScopeOrder = Get-EnvValue @('ENGLISH_NOVA_ENRICHMENT_AUDIO_SCOPE_ORDER')
$audioScopeLimit = Get-EnvValue @('ENGLISH_NOVA_ENRICHMENT_AUDIO_SCOPE_LIMIT')
$textOnlyEnabled = $textOnlyMode -eq 'true'
$audioOnlyEnabled = $audioOnlyMode -eq 'true'

if ([string]::IsNullOrWhiteSpace($apiKey)) {
    throw "Missing OPENAI_API_KEY or DEEPSEEK_API_KEY in the current environment."
}
if ($textOnlyEnabled -and $audioOnlyEnabled) {
    throw "ENGLISH_NOVA_ENRICHMENT_TEXT_ONLY_MODE and ENGLISH_NOVA_ENRICHMENT_AUDIO_ONLY_MODE cannot both be true."
}
if (($textOnlyEnabled -or -not $audioOnlyEnabled) -and [string]::IsNullOrWhiteSpace($textModel)) {
    throw "Missing OPENAI_MODEL or DEEPSEEK_MODEL in the current environment."
}
if ($audioOnlyEnabled -and [string]::IsNullOrWhiteSpace($ttsModel)) {
    throw "Missing ENGLISH_NOVA_ENRICHMENT_TTS_MODEL in the current environment."
}
if ($audioOnlyEnabled -and [string]::IsNullOrWhiteSpace($ttsVoice)) {
    throw "Missing ENGLISH_NOVA_ENRICHMENT_TTS_VOICE in the current environment."
}

$mysqlHost = Get-EnvValue @('MYSQL_HOST')
if ([string]::IsNullOrWhiteSpace($mysqlHost)) {
    $mysqlHost = '127.0.0.1'
}
$mysqlPortText = Get-EnvValue @('MYSQL_PORT')
$mysqlPort = if ([string]::IsNullOrWhiteSpace($mysqlPortText)) { 4407 } else { [int]$mysqlPortText }
if (-not (Test-TcpPort $mysqlHost $mysqlPort)) {
    throw "MySQL is not reachable at ${mysqlHost}:${mysqlPort}. Start infra first with scripts/start-infra.ps1."
}

$checks = @(
    @{ Name = 'Elasticsearch'; Host = if ((Get-EnvValue @('ELASTICSEARCH_HOST'))) { Get-EnvValue @('ELASTICSEARCH_HOST') } else { '127.0.0.1' }; Port = if ((Get-EnvValue @('ELASTICSEARCH_PORT'))) { [int](Get-EnvValue @('ELASTICSEARCH_PORT')) } else { 9200 } },
    @{ Name = 'RabbitMQ'; Host = if ((Get-EnvValue @('RABBITMQ_HOST'))) { Get-EnvValue @('RABBITMQ_HOST') } else { '127.0.0.1' }; Port = if ((Get-EnvValue @('RABBITMQ_PORT'))) { [int](Get-EnvValue @('RABBITMQ_PORT')) } else { 5672 } },
    @{ Name = 'Nacos HTTP'; Host = if ((Get-EnvValue @('NACOS_SERVER_ADDR'))) { (Get-EnvValue @('NACOS_SERVER_ADDR')).Split(':')[0] } else { '127.0.0.1' }; Port = if ((Get-EnvValue @('NACOS_SERVER_ADDR'))) { [int]((Get-EnvValue @('NACOS_SERVER_ADDR')).Split(':')[1]) } else { 8848 } },
    @{ Name = 'Nacos gRPC'; Host = '127.0.0.1'; Port = 9848 }
)

foreach ($check in $checks) {
    if (-not (Test-TcpPort $check.Host $check.Port)) {
        Write-Warning "$($check.Name) is not reachable at $($check.Host):$($check.Port). search-service can start, but enrichment may not run cleanly."
    }
}

$javaCommand = Resolve-JavaCommand
if (-not (Test-Path $jarPath)) {
    throw "search-service jar was not found at $jarPath. Build it first with mvn -q -DskipTests package."
}

Write-Host "Starting search-service enrichment worker from jar with example audio enabled..."
Write-Host "Text-only mode: $textOnlyEnabled"
Write-Host "Audio-only mode: $audioOnlyEnabled"
if ($audioOnlyEnabled) {
    Write-Host "Audio scope: $audioScopeField $audioScopeOrder limit=$audioScopeLimit"
}
if (-not [string]::IsNullOrWhiteSpace($textModel)) {
    Write-Host "Text model: $textModel"
}
if (-not [string]::IsNullOrWhiteSpace($ttsModel)) {
    Write-Host "TTS model: $ttsModel"
}

Set-Location $serviceDir
& $javaCommand -jar $jarPath
