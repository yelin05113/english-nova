param(
    [int]$TopLimit = 10000,
    [int]$PublicLimit = 199213,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$rootDir = "D:\Projects\english-nova"
$envPath = Join-Path $rootDir ".env"
$defaultMySql = "C:\Program Files\MySQL\MySQL Server 9.2\bin\mysql.exe"
$priorityTimestamp = "2000-01-01 00:00:00"

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

function Resolve-MySqlCommand {
    if (Test-Path $defaultMySql) {
        return $defaultMySql
    }
    $command = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }
    throw "MySQL executable was not found. Install MySQL client or update the script path."
}

function New-MySqlArgumentList {
    param(
        [string]$database
    )

    $dbHost = Get-EnvValue @("MYSQL_HOST")
    if ([string]::IsNullOrWhiteSpace($dbHost)) {
        $dbHost = "127.0.0.1"
    }

    $portText = Get-EnvValue @("MYSQL_PORT")
    $port = if ([string]::IsNullOrWhiteSpace($portText)) { "4407" } else { $portText }

    $username = Get-EnvValue @("MYSQL_USERNAME")
    if ([string]::IsNullOrWhiteSpace($username)) {
        throw "Missing MYSQL_USERNAME in the current environment."
    }

    $password = Get-EnvValue @("MYSQL_PASSWORD")
    if ([string]::IsNullOrWhiteSpace($password)) {
        throw "Missing MYSQL_PASSWORD in the current environment."
    }

    $dbName = if ([string]::IsNullOrWhiteSpace($database)) {
        Get-EnvValue @("MYSQL_DATABASE")
    } else {
        $database
    }
    if ([string]::IsNullOrWhiteSpace($dbName)) {
        throw "Missing MYSQL_DATABASE in the current environment."
    }

    return @(
        "--default-character-set=utf8mb4",
        "-h", $dbHost,
        "-P", $port,
        "-u", $username,
        "-p$password",
        $dbName
    )
}

function Invoke-MySqlLines {
    param(
        [string]$sql,
        [string]$database
    )

    $args = New-MySqlArgumentList -database $database
    $args += @("-N", "-B", "-r", "-e", $sql)
    $output = & $script:mysqlCommand @args
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL query failed."
    }
    return @($output | Where-Object { $_ -ne $null })
}

function Invoke-MySqlScriptFile {
    param(
        [string]$path,
        [string]$database
    )

    $args = New-MySqlArgumentList -database $database
    $args += @("-N", "-B", "-r")
    $output = Get-Content -Path $path -Raw | & $script:mysqlCommand @args
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL script execution failed."
    }
    return @($output | Where-Object { $_ -ne $null })
}

function Resolve-ExampleAudioDirectory {
    $configured = Get-EnvValue @("ENGLISH_NOVA_ENRICHMENT_EXAMPLE_AUDIO_DIR")
    if ([string]::IsNullOrWhiteSpace($configured)) {
        $configured = "upload/example-audio"
    }
    if ([System.IO.Path]::IsPathRooted($configured)) {
        return [System.IO.Path]::GetFullPath($configured)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $rootDir $configured))
}

function Get-StableHash([string]$value) {
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($value)
        $digest = $sha256.ComputeHash($bytes)
        $builder = New-Object System.Text.StringBuilder
        for ($index = 0; $index -lt 8 -and $index -lt $digest.Length; $index++) {
            [void]$builder.Append($digest[$index].ToString("x2"))
        }
        return $builder.ToString()
    }
    finally {
        $sha256.Dispose()
    }
}

function Resolve-PublicExampleAudioFile {
    param(
        [long]$entryId,
        [string]$correctedEnglish
    )

    $fileName = "public-entry-$entryId-$(Get-StableHash $correctedEnglish).mp3"
    return Join-Path $script:audioDirectory $fileName
}

function Test-PublicExampleAudioFile {
    param(
        [long]$entryId,
        [string]$correctedEnglish
    )

    if ([string]::IsNullOrWhiteSpace($correctedEnglish)) {
        return $false
    }
    $path = Resolve-PublicExampleAudioFile -entryId $entryId -correctedEnglish $correctedEnglish
    return Test-Path -LiteralPath $path -PathType Leaf
}

function Convert-ToSqlValueList {
    param(
        [long[]]$entryIds
    )

    if ($entryIds.Count -eq 0) {
        return @()
    }

    $chunks = @()
    $batchSize = 500
    for ($offset = 0; $offset -lt $entryIds.Count; $offset += $batchSize) {
        $count = [Math]::Min($batchSize, $entryIds.Count - $offset)
        $slice = $entryIds[$offset..($offset + $count - 1)]
        $values = $slice | ForEach-Object { "($_)" }
        $chunks += ,("INSERT INTO tmp_priority_audio_ids(entry_id) VALUES " + ($values -join ",") + ";")
    }
    return $chunks
}

function Write-KeyValue {
    param(
        [string]$name,
        [object]$value
    )

    Write-Host ("{0}={1}" -f $name, $value)
}

if ($TopLimit -le 0) {
    throw "TopLimit must be greater than zero."
}
if ($PublicLimit -lt $TopLimit) {
    throw "PublicLimit must be greater than or equal to TopLimit."
}

Import-DotEnv $envPath
$script:mysqlCommand = Resolve-MySqlCommand
$script:audioDirectory = Resolve-ExampleAudioDirectory

$query = @"
SELECT JSON_OBJECT(
    'id', v.id,
    'word', v.word,
    'correctedEnglish', COALESCE(v.corrected_english, ''),
    'chineseSentence', COALESCE(v.chinese_sentence, ''),
    'exampleAudioUrl', COALESCE(v.example_audio_url, ''),
    'needsText',
        (
            v.corrected_english IS NULL OR v.corrected_english = ''
            OR v.chinese_sentence IS NULL OR v.chinese_sentence = ''
            OR LOWER(TRIM(v.corrected_english)) REGEXP '^(n|v|vt|vi|adj|adv|prep|pron|conj|art|aux|num|int|det|abbr|phr|pref|suf|modal)\\.'
            OR LOWER(TRIM(v.corrected_english)) = LOWER(TRIM(v.word))
            OR TRIM(v.corrected_english) NOT LIKE '% %'
            OR LOWER(v.corrected_english) NOT LIKE CONCAT('%', LOWER(v.word), '%')
            OR v.corrected_english NOT REGEXP '[A-Za-z]'
        )
)
FROM (
    SELECT id
    FROM (
        SELECT
            id,
            CASE WHEN bnc_rank IS NOT NULL AND bnc_rank > 0 THEN bnc_rank ELSE 999999999 END AS sort_bnc_rank,
            CASE WHEN frq_rank IS NOT NULL AND frq_rank > 0 THEN frq_rank ELSE 999999999 END AS sort_frq_rank
        FROM public_vocabulary_entries
        ORDER BY
            sort_bnc_rank ASC,
            sort_frq_rank ASC,
            id ASC
        LIMIT $PublicLimit
    ) public_scope
    ORDER BY
        sort_bnc_rank ASC,
        sort_frq_rank ASC,
        id ASC
    LIMIT $TopLimit
) ranked_public_entries
JOIN public_vocabulary_entries v
    ON v.id = ranked_public_entries.id
ORDER BY v.id ASC;
"@

$rows = Invoke-MySqlLines -sql $query -database (Get-EnvValue @("MYSQL_DATABASE"))

$top10kMissingAudio = 0
$top10kAudioOnlyMissing = 0
$top10kLeftOnMixedFlow = 0
$top10kFileMissingOnly = 0
$audioOnlyIds = New-Object System.Collections.Generic.List[long]
$sampleAudioOnlyIds = New-Object System.Collections.Generic.List[long]
$sampleMixedFlowIds = New-Object System.Collections.Generic.List[long]
$sampleMissingFileIds = New-Object System.Collections.Generic.List[long]

foreach ($line in $rows) {
    $row = $line | ConvertFrom-Json
    $hasAudioUrl = -not [string]::IsNullOrWhiteSpace($row.exampleAudioUrl)
    $hasFile = Test-PublicExampleAudioFile -entryId ([long]$row.id) -correctedEnglish $row.correctedEnglish
    $audioIncomplete = (-not $hasAudioUrl) -or (-not $hasFile)
    if (-not $audioIncomplete) {
        continue
    }

    $top10kMissingAudio++
    if ($hasAudioUrl -and -not $hasFile) {
        $top10kFileMissingOnly++
        if ($sampleMissingFileIds.Count -lt 3) {
            $sampleMissingFileIds.Add([long]$row.id)
        }
    }

    if ([bool]$row.needsText) {
        $top10kLeftOnMixedFlow++
        if ($sampleMixedFlowIds.Count -lt 3) {
            $sampleMixedFlowIds.Add([long]$row.id)
        }
        continue
    }

    $top10kAudioOnlyMissing++
    $audioOnlyIds.Add([long]$row.id)
    if ($sampleAudioOnlyIds.Count -lt 3) {
        $sampleAudioOnlyIds.Add([long]$row.id)
    }
}

$totalMissingAudioQuery = "SELECT COUNT(*) FROM public_vocabulary_entries WHERE COALESCE(TRIM(example_audio_url), '') = '';"
$totalMissingAudio = [int](Invoke-MySqlLines -sql $totalMissingAudioQuery -database (Get-EnvValue @("MYSQL_DATABASE")) | Select-Object -First 1)

Write-KeyValue -name "top10k_missing_audio" -value $top10kMissingAudio
Write-KeyValue -name "top10k_audio_only_missing" -value $top10kAudioOnlyMissing
Write-KeyValue -name "top10k_requeued_audio_only" -value $audioOnlyIds.Count
Write-KeyValue -name "top10k_left_on_mixed_flow" -value $top10kLeftOnMixedFlow
Write-KeyValue -name "top10k_missing_file_only" -value $top10kFileMissingOnly
Write-KeyValue -name "public_limit" -value $PublicLimit
Write-KeyValue -name "total_missing_audio" -value $totalMissingAudio
Write-KeyValue -name "sample_audio_only_ids" -value (($sampleAudioOnlyIds | ForEach-Object { $_ }) -join ",")
Write-KeyValue -name "sample_left_on_mixed_flow_ids" -value (($sampleMixedFlowIds | ForEach-Object { $_ }) -join ",")
Write-KeyValue -name "sample_missing_file_ids" -value (($sampleMissingFileIds | ForEach-Object { $_ }) -join ",")

if ($DryRun) {
    if ($audioOnlyIds.Count -gt 0) {
        $valueList = Convert-ToSqlValueList -entryIds ($audioOnlyIds.ToArray())
        $sqlLines = @(
            "DROP TEMPORARY TABLE IF EXISTS tmp_priority_audio_ids;",
            "CREATE TEMPORARY TABLE tmp_priority_audio_ids (entry_id BIGINT PRIMARY KEY);"
        ) + $valueList + @(
            "SELECT CONCAT('affected_status_before_', status_name, CHAR(9), row_count) FROM (SELECT COALESCE(t.status, 'NO_TASK') AS status_name, COUNT(*) AS row_count FROM tmp_priority_audio_ids x LEFT JOIN example_enrichment_tasks t ON t.entry_type = 'PUBLIC' AND t.entry_id = x.entry_id GROUP BY COALESCE(t.status, 'NO_TASK')) grouped_statuses ORDER BY status_name;"
        )
        $tempPath = Join-Path ([System.IO.Path]::GetTempPath()) ("top10k-audio-dryrun-" + [System.Guid]::NewGuid().ToString("N") + ".sql")
        try {
            Set-Content -Path $tempPath -Value ($sqlLines -join [Environment]::NewLine) -Encoding UTF8
            $statusLines = Invoke-MySqlScriptFile -path $tempPath -database (Get-EnvValue @("MYSQL_DATABASE"))
        }
        finally {
            Remove-Item -LiteralPath $tempPath -Force -ErrorAction SilentlyContinue
        }

        foreach ($statusLine in $statusLines) {
            $parts = $statusLine -split "`t", 2
            if ($parts.Length -eq 2) {
                Write-KeyValue -name $parts[0] -value $parts[1]
            }
        }
    }
    Write-Host "Dry run only. No database rows were changed."
    return
}

if ($audioOnlyIds.Count -eq 0) {
    Write-Host "No top-ranked public entries require audio-only requeue."
    return
}

$insertStatements = Convert-ToSqlValueList -entryIds ($audioOnlyIds.ToArray())
$sqlStatements = @(
    "DROP TEMPORARY TABLE IF EXISTS tmp_priority_audio_ids;",
    "CREATE TEMPORARY TABLE tmp_priority_audio_ids (entry_id BIGINT PRIMARY KEY);"
) + $insertStatements + @(
    "SELECT CONCAT('affected_status_before_', status_name, CHAR(9), row_count) FROM (SELECT COALESCE(t.status, 'NO_TASK') AS status_name, COUNT(*) AS row_count FROM tmp_priority_audio_ids x LEFT JOIN example_enrichment_tasks t ON t.entry_type = 'PUBLIC' AND t.entry_id = x.entry_id GROUP BY COALESCE(t.status, 'NO_TASK')) grouped_statuses ORDER BY status_name;",
    "UPDATE example_enrichment_tasks t JOIN tmp_priority_audio_ids x ON t.entry_type = 'PUBLIC' AND t.entry_id = x.entry_id SET t.status = 'PENDING', t.attempt_count = 0, t.last_error = NULL, t.locked_at = NULL, t.finished_at = NULL, t.updated_at = '$priorityTimestamp';",
    "SELECT CONCAT('updated_existing_tasks', CHAR(9), ROW_COUNT());",
    "INSERT INTO example_enrichment_tasks(entry_type, entry_id, status, attempt_count, last_error, locked_at, finished_at, updated_at) SELECT 'PUBLIC', x.entry_id, 'PENDING', 0, NULL, NULL, NULL, '$priorityTimestamp' FROM tmp_priority_audio_ids x LEFT JOIN example_enrichment_tasks t ON t.entry_type = 'PUBLIC' AND t.entry_id = x.entry_id WHERE t.id IS NULL;",
    "SELECT CONCAT('inserted_missing_tasks', CHAR(9), ROW_COUNT());",
    "SELECT CONCAT('affected_status_after_', status_name, CHAR(9), row_count) FROM (SELECT COALESCE(t.status, 'NO_TASK') AS status_name, COUNT(*) AS row_count FROM tmp_priority_audio_ids x LEFT JOIN example_enrichment_tasks t ON t.entry_type = 'PUBLIC' AND t.entry_id = x.entry_id GROUP BY COALESCE(t.status, 'NO_TASK')) grouped_statuses ORDER BY status_name;",
    "SELECT CONCAT('task_status_', status, CHAR(9), COUNT(*)) FROM example_enrichment_tasks GROUP BY status ORDER BY status;"
)

$scriptPath = Join-Path ([System.IO.Path]::GetTempPath()) ("top10k-audio-requeue-" + [System.Guid]::NewGuid().ToString("N") + ".sql")
try {
    Set-Content -Path $scriptPath -Value ($sqlStatements -join [Environment]::NewLine) -Encoding UTF8
    $resultLines = Invoke-MySqlScriptFile -path $scriptPath -database (Get-EnvValue @("MYSQL_DATABASE"))
}
finally {
    Remove-Item -LiteralPath $scriptPath -Force -ErrorAction SilentlyContinue
}

foreach ($resultLine in $resultLines) {
    $parts = $resultLine -split "`t", 2
    if ($parts.Length -ne 2) {
        continue
    }
    Write-KeyValue -name $parts[0] -value $parts[1]
}

Write-Host "Top-ranked audio-only tasks were requeued with high priority."
