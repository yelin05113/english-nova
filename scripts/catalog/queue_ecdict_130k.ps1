$mysql = "C:\Program Files\MySQL\MySQL Server 9.2\bin\mysql.exe"
$manifestPath = "D:\Projects\english-nova\.local\public-catalog\manifest.json"
$baseDir = Split-Path $manifestPath -Parent
$manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
$sources = $manifest.sources | Where-Object { $_.sequence -ge 6 -and $_.sequence -le 18 }

foreach ($source in $sources) {
    $sourceName = [string]$source.name
    $wordCount = [int]$source.wordCount
    $existingJob = & $mysql -h 127.0.0.1 -P 4407 -uenglish_nova -penglish_nova -D english_nova -N -B -e "SELECT id FROM public_catalog_import_jobs WHERE source_name = '$sourceName' ORDER BY id DESC LIMIT 1;"
    $jobIdText = ($existingJob | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1)
    if ($jobIdText) {
        $jobId = [int]$jobIdText.Trim()
    } else {
        $insertOutput = & $mysql -h 127.0.0.1 -P 4407 -uenglish_nova -penglish_nova -D english_nova -N -B -e "INSERT INTO public_catalog_import_jobs(source_name, status, total_words, refresh_existing, batch_size, created_by_user_id) VALUES ('$sourceName', 'PENDING', $wordCount, 0, 500, 1103); SELECT LAST_INSERT_ID();"
        $jobId = [int](($insertOutput | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1).Trim())
    }

    $filePath = Join-Path $baseDir ([string]$source.file)
    $words = Get-Content $filePath | Select-Object -Skip 1 | ForEach-Object {
        if ($_ -and $_.Contains("`t")) {
            ($_ -split "`t")[0].Trim().ToLower()
        }
    } | Where-Object { $_ }

    for ($i = 0; $i -lt $words.Count; $i += 500) {
        $end = [Math]::Min($i + 499, $words.Count - 1)
        $batch = @($words[$i..$end])
        $values = ($batch | ForEach-Object { "($jobId, '" + ($_ -replace "'", "''") + "', 'PENDING')" }) -join ","
        & $mysql -h 127.0.0.1 -P 4407 -uenglish_nova -penglish_nova -D english_nova -N -B -e "INSERT IGNORE INTO public_catalog_import_items(job_id, word, status) VALUES $values" | Out-Null
    }

    $itemCount = & $mysql -h 127.0.0.1 -P 4407 -uenglish_nova -penglish_nova -D english_nova -N -B -e "SELECT COUNT(*) FROM public_catalog_import_items WHERE job_id = $jobId;"
    Write-Output "queued source=$sourceName job_id=$jobId words=$($words.Count) items=$((($itemCount | Select-Object -First 1).Trim()))"
}
