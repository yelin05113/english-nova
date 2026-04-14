param(
    [string]$GatewayBaseUrl = "http://127.0.0.1:8080",
    [string]$AccessToken = "",
    [string]$Account = "",
    [string]$Password = "",
    [string]$MySqlExe = "C:\Program Files\MySQL\MySQL Server 9.2\bin\mysql.exe",
    [string]$MySqlHost = "127.0.0.1",
    [int]$MySqlPort = 3306,
    [string]$MySqlDatabase = "english_nova",
    [string]$MySqlUser = "english_nova",
    [string]$MySqlPassword = "english_nova",
    [long]$PublicUserId = 1103,
    [string]$WordListPath = ".local/public-catalog/repair-words.txt",
    [string]$StatePath = ".local/public-catalog/repair-state.json",
    [string]$ReportPath = ".local/public-catalog/repair-report.json"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Ensure-ParentDirectory {
    param([string]$Path)

    $parent = Split-Path -Path $Path -Parent
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
}

function Invoke-MySqlLines {
    param([string]$Sql)

    & $MySqlExe `
        --batch `
        --skip-column-names `
        -h $MySqlHost `
        -P $MySqlPort `
        -u $MySqlUser `
        "-p$MySqlPassword" `
        -D $MySqlDatabase `
        -e $Sql
}

function Sync-PublicWordbookCounts {
    $sql = @"
UPDATE wordbooks w
SET word_count = (
    SELECT COUNT(*)
    FROM vocabulary_entries v
    WHERE v.wordbook_id = w.id
)
WHERE w.user_id = $PublicUserId;
"@

    & $MySqlExe -h $MySqlHost -P $MySqlPort -u $MySqlUser "-p$MySqlPassword" -D $MySqlDatabase -e $sql | Out-Null
}

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$resolvedWordListPath = Join-Path $projectRoot $WordListPath
$resolvedStatePath = Join-Path $projectRoot $StatePath
$resolvedReportPath = Join-Path $projectRoot $ReportPath

Ensure-ParentDirectory -Path $resolvedWordListPath
Ensure-ParentDirectory -Path $resolvedStatePath
Ensure-ParentDirectory -Path $resolvedReportPath

$refreshTargetQuery = @"
SELECT DISTINCT word
FROM vocabulary_entries
WHERE user_id = $PublicUserId
  AND visibility = 'PUBLIC'
  AND (
    wordbook_id = 1
    OR meaning_cn REGEXP '[A-Za-z]'
    OR meaning_cn LIKE '%?%'
  )
ORDER BY word;
"@

$words = @(Invoke-MySqlLines -Sql $refreshTargetQuery | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })

if ($words.Count -eq 0) {
    Sync-PublicWordbookCounts
    Write-Host "No suspect public catalog rows were found." -ForegroundColor Yellow
    exit 0
}

Set-Content -Path $resolvedWordListPath -Value ($words -join [Environment]::NewLine) -Encoding UTF8

$importScript = Join-Path $PSScriptRoot "import-public-catalog.ps1"
$importArgs = @(
    "-GatewayBaseUrl", $GatewayBaseUrl,
    "-WordListPath", $resolvedWordListPath,
    "-StatePath", $resolvedStatePath,
    "-ReportPath", $resolvedReportPath,
    "-RefreshExisting",
    "-Restart"
)

if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
    $importArgs += @("-AccessToken", $AccessToken)
} else {
    $importArgs += @("-Account", $Account, "-Password", $Password)
}

& powershell -NoProfile -ExecutionPolicy Bypass -File $importScript @importArgs

Sync-PublicWordbookCounts

$remainingQuery = @"
SELECT COUNT(*)
FROM vocabulary_entries
WHERE user_id = $PublicUserId
  AND visibility = 'PUBLIC'
  AND (
    meaning_cn REGEXP '[A-Za-z]'
    OR meaning_cn LIKE '%?%'
  );
"@

$remaining = [int]((Invoke-MySqlLines -Sql $remainingQuery | Select-Object -First 1))

Write-Host ""
Write-Host ("Repair targets refreshed : {0}" -f $words.Count)
Write-Host ("Remaining suspect rows   : {0}" -f $remaining)
Write-Host ("Repair report            : {0}" -f $resolvedReportPath)
