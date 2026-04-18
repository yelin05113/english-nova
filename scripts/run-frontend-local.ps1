$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$frontendRoot = Join-Path $projectRoot "FrontEnd-EnglishNova"
$npmExe = (Get-Command npm.cmd).Source
$viteConfig = Join-Path $frontendRoot "vite.config.mjs"

Set-Location $frontendRoot
& $npmExe run dev -- --config $viteConfig --configLoader native --host 0.0.0.0 --port 3000
