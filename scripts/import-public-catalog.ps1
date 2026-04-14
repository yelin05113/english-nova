param(
    [string]$GatewayBaseUrl = "http://127.0.0.1:8080",
    [string]$AccessToken = "",
    [string]$Account = "",
    [string]$Password = "",
    [string]$WordListPath = "",
    [string]$WordListUrl = "https://raw.githubusercontent.com/first20hours/google-10000-english/master/20k.txt",
    [int]$BatchSize = 100,
    [switch]$RefreshExisting,
    [switch]$Restart,
    [string]$StatePath = ".local/public-catalog/import-state.json",
    [string]$ReportPath = ".local/public-catalog/import-report.json"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($BatchSize -lt 1 -or $BatchSize -gt 500) {
    throw "BatchSize must be between 1 and 500."
}

function Ensure-ParentDirectory {
    param([string]$Path)

    $parent = Split-Path -Path $Path -Parent
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
}

function Normalize-Word {
    param([string]$Word)

    if ([string]::IsNullOrWhiteSpace($Word)) {
        return $null
    }

    $normalized = $Word.Trim().ToLowerInvariant()
    if ($normalized -match "^[a-z][a-z\-']*$") {
        return $normalized
    }

    return $null
}

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Uri,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )

    $invokeParams = @{
        Method      = $Method
        Uri         = $Uri
        Headers     = $Headers
        ContentType = "application/json"
        TimeoutSec  = 300
    }

    if ($null -ne $Body) {
        $invokeParams.Body = ($Body | ConvertTo-Json -Depth 8)
    }

    return Invoke-RestMethod @invokeParams
}

function Resolve-AccessToken {
    param(
        [string]$BaseUrl,
        [string]$ExistingToken,
        [string]$AccountValue,
        [string]$PasswordValue
    )

    if (-not [string]::IsNullOrWhiteSpace($ExistingToken)) {
        return $ExistingToken.Trim()
    }

    if ([string]::IsNullOrWhiteSpace($AccountValue) -or [string]::IsNullOrWhiteSpace($PasswordValue)) {
        throw "Provide -AccessToken or both -Account and -Password."
    }

    $response = Invoke-JsonRequest -Method "Post" -Uri ($BaseUrl.TrimEnd("/") + "/api/auth/login") -Body @{
        account  = $AccountValue
        password = $PasswordValue
    }

    if ($null -eq $response.data -or [string]::IsNullOrWhiteSpace($response.data.accessToken)) {
        throw "Login succeeded but no access token was returned."
    }

    return $response.data.accessToken
}

function Resolve-WordListFile {
    param(
        [string]$ProvidedPath,
        [string]$DownloadUrl
    )

    if (-not [string]::IsNullOrWhiteSpace($ProvidedPath)) {
        $resolvedPath = Resolve-Path -Path $ProvidedPath
        return @{
            Path      = $resolvedPath.Path
            SourceKey = "file:" + $resolvedPath.Path
        }
    }

    $downloadPath = Join-Path -Path (Split-Path -Path $StatePath -Parent) -ChildPath "source-words.txt"
    Ensure-ParentDirectory -Path $downloadPath
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $downloadPath -TimeoutSec 300

    return @{
        Path      = (Resolve-Path -Path $downloadPath).Path
        SourceKey = "url:" + $DownloadUrl
    }
}

function Read-WordList {
    param([string]$Path)

    $ordered = New-Object System.Collections.Generic.List[string]
    $seen = New-Object System.Collections.Generic.HashSet[string]

    foreach ($line in Get-Content -Path $Path -Encoding UTF8) {
        $parts = $line -split "[\s,;]+"
        foreach ($part in $parts) {
            $word = Normalize-Word -Word $part
            if ($null -ne $word -and $seen.Add($word)) {
                $ordered.Add($word)
            }
        }
    }

    return $ordered
}

function New-EmptyReport {
    param(
        [string]$SourceKey,
        [int]$WordCount,
        [int]$CurrentBatchSize
    )

    return [ordered]@{
        sourceKey      = $SourceKey
        wordCount      = $WordCount
        batchSize      = $CurrentBatchSize
        startedAt      = (Get-Date).ToString("s")
        updatedAt      = (Get-Date).ToString("s")
        completedAt    = $null
        completed      = $false
        nextIndex      = 0
        totals         = [ordered]@{
            requested = 0
            imported  = 0
            updated   = 0
            skipped   = 0
            failed    = 0
        }
        batches        = @()
        failedWords    = @()
    }
}

function Load-ExistingReport {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }

    return Get-Content -Path $Path -Raw -Encoding UTF8 | ConvertFrom-Json
}

function Save-JsonFile {
    param(
        [string]$Path,
        [object]$Value
    )

    Ensure-ParentDirectory -Path $Path
    $Value | ConvertTo-Json -Depth 10 | Set-Content -Path $Path -Encoding UTF8
}

function Add-UniqueWords {
    param(
        [System.Collections.IEnumerable]$Existing,
        [System.Collections.IEnumerable]$Incoming
    )

    $hash = New-Object System.Collections.Generic.HashSet[string]
    $items = New-Object System.Collections.Generic.List[string]

    foreach ($entry in $Existing) {
        if (-not [string]::IsNullOrWhiteSpace([string]$entry) -and $hash.Add([string]$entry)) {
            $items.Add([string]$entry)
        }
    }

    foreach ($entry in $Incoming) {
        if (-not [string]::IsNullOrWhiteSpace([string]$entry) -and $hash.Add([string]$entry)) {
            $items.Add([string]$entry)
        }
    }

    return $items
}

Ensure-ParentDirectory -Path $StatePath
Ensure-ParentDirectory -Path $ReportPath

$token = Resolve-AccessToken -BaseUrl $GatewayBaseUrl -ExistingToken $AccessToken -AccountValue $Account -PasswordValue $Password
$listInfo = Resolve-WordListFile -ProvidedPath $WordListPath -DownloadUrl $WordListUrl
$words = Read-WordList -Path $listInfo.Path

if ($words.Count -eq 0) {
    throw "No valid words were loaded from $($listInfo.Path)."
}

$report = $null
if (-not $Restart) {
    $report = Load-ExistingReport -Path $ReportPath
}

if ($null -eq $report -or $report.sourceKey -ne $listInfo.SourceKey) {
    $report = New-EmptyReport -SourceKey $listInfo.SourceKey -WordCount $words.Count -CurrentBatchSize $BatchSize
} elseif ($report.completed -eq $true) {
    Write-Host "Existing report is already marked complete. Use -Restart to import again." -ForegroundColor Yellow
    exit 0
}

$headers = @{
    Authorization = "Bearer $token"
}

$nextIndex = [int]$report.nextIndex
while ($nextIndex -lt $words.Count) {
    $batchEnd = [Math]::Min($nextIndex + $BatchSize, $words.Count)
    $batch = @($words[$nextIndex..($batchEnd - 1)])

    Write-Host ("Importing words {0}-{1} / {2}" -f ($nextIndex + 1), $batchEnd, $words.Count)

    $response = Invoke-JsonRequest -Method "Post" -Uri ($GatewayBaseUrl.TrimEnd("/") + "/api/search/public-catalog/import") -Headers $headers -Body @{
        words           = $batch
        refreshExisting = [bool]$RefreshExisting
    }

    if ($null -eq $response.data) {
        throw "Import endpoint returned no data for batch starting at index $nextIndex."
    }

    $batchSummary = [ordered]@{
        startedIndex = $nextIndex
        endedIndex   = $batchEnd
        requested    = [int]$response.data.requestedWords
        imported     = [int]$response.data.importedWords
        updated      = [int]$response.data.updatedWords
        skipped      = [int]$response.data.skippedWords
        failed       = [int]$response.data.failedWords
        failedWords  = @($response.data.failed)
    }

    $report.batches += $batchSummary
    $report.totals.requested += $batchSummary.requested
    $report.totals.imported += $batchSummary.imported
    $report.totals.updated += $batchSummary.updated
    $report.totals.skipped += $batchSummary.skipped
    $report.totals.failed += $batchSummary.failed
    $report.failedWords = @(Add-UniqueWords -Existing $report.failedWords -Incoming $batchSummary.failedWords)
    $report.nextIndex = $batchEnd
    $report.updatedAt = (Get-Date).ToString("s")

    Save-JsonFile -Path $ReportPath -Value $report
    Save-JsonFile -Path $StatePath -Value ([ordered]@{
        sourceKey   = $report.sourceKey
        wordCount   = $report.wordCount
        batchSize   = $report.batchSize
        nextIndex   = $report.nextIndex
        updatedAt   = $report.updatedAt
        completed   = $false
        reportPath  = (Resolve-Path -Path $ReportPath).Path
    })

    $nextIndex = $batchEnd
}

$report.completed = $true
$report.completedAt = (Get-Date).ToString("s")
$report.updatedAt = $report.completedAt
Save-JsonFile -Path $ReportPath -Value $report
Save-JsonFile -Path $StatePath -Value ([ordered]@{
    sourceKey   = $report.sourceKey
    wordCount   = $report.wordCount
    batchSize   = $report.batchSize
    nextIndex   = $report.nextIndex
    updatedAt   = $report.updatedAt
    completed   = $true
    reportPath  = (Resolve-Path -Path $ReportPath).Path
})

Write-Host ""
Write-Host "Public catalog import completed." -ForegroundColor Green
Write-Host ("Requested: {0}" -f $report.totals.requested)
Write-Host ("Imported : {0}" -f $report.totals.imported)
Write-Host ("Updated  : {0}" -f $report.totals.updated)
Write-Host ("Skipped  : {0}" -f $report.totals.skipped)
Write-Host ("Failed   : {0}" -f $report.totals.failed)
Write-Host ("Report   : {0}" -f (Resolve-Path -Path $ReportPath).Path)
