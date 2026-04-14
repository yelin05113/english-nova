param(
    [Parameter(Mandatory = $true)]
    [string]$JarPath,
    [Parameter(Mandatory = $true)]
    [string]$StdoutLog,
    [Parameter(Mandatory = $true)]
    [string]$StderrLog
)

$ErrorActionPreference = "Stop"

$machinePath = [Environment]::GetEnvironmentVariable("Path", "Machine")
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
$env:Path = @($machinePath, $userPath) -join ";"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$runtimeHome = Join-Path $projectRoot ".local\runtime-home"
New-Item -ItemType Directory -Path $runtimeHome -Force | Out-Null
$env:USERPROFILE = $runtimeHome
$env:HOME = $runtimeHome

$nacosLogDir = Join-Path $env:USERPROFILE "logs\nacos"
New-Item -ItemType Directory -Path $nacosLogDir -Force | Out-Null

. (Join-Path $PSScriptRoot "load-runtime-env.ps1")

New-Item -ItemType Directory -Path (Split-Path $StdoutLog -Parent) -Force | Out-Null
New-Item -ItemType File -Path $StdoutLog -Force | Out-Null
New-Item -ItemType File -Path $StderrLog -Force | Out-Null

$javaHomeArg = "-Duser.home=$runtimeHome"
& java $javaHomeArg -jar $JarPath 1>> $StdoutLog 2>> $StderrLog
