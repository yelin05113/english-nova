param(
    [string]$RootPassword = "123456"
)

$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$schemaFile = Join-Path $projectRoot "docker\mysql\init\001-schema.sql"
$seedFile = Join-Path $projectRoot "docker\mysql\init\002-seed.sql"
$patchFile = Join-Path $projectRoot "scripts\mysql\20260413_word_detail_audio.sql"

$mysqlExe = (Get-Command mysql).Source
$schemaPath = $schemaFile.Replace("\", "/")
$seedPath = $seedFile.Replace("\", "/")
$patchPath = $patchFile.Replace("\", "/")

& $mysqlExe --default-character-set=utf8mb4 -uroot "-p$RootPassword" -e @"
DROP DATABASE IF EXISTS english_nova;
CREATE DATABASE english_nova CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'english_nova'@'localhost' IDENTIFIED BY 'english_nova';
CREATE USER IF NOT EXISTS 'english_nova'@'%' IDENTIFIED BY 'english_nova';
GRANT ALL PRIVILEGES ON english_nova.* TO 'english_nova'@'localhost';
GRANT ALL PRIVILEGES ON english_nova.* TO 'english_nova'@'%';
FLUSH PRIVILEGES;
"@

& $mysqlExe --default-character-set=utf8mb4 -uroot "-p$RootPassword" english_nova -e "source $schemaPath"
& $mysqlExe --default-character-set=utf8mb4 -uroot "-p$RootPassword" english_nova -e "source $seedPath"
& $mysqlExe --default-character-set=utf8mb4 -uroot "-p$RootPassword" english_nova -e "source $patchPath"

& $mysqlExe --default-character-set=utf8mb4 -uroot "-p$RootPassword" -e @"
USE english_nova;
SHOW TABLES;
SELECT COUNT(*) AS users_count FROM users;
SELECT COUNT(*) AS wordbooks_count FROM wordbooks;
SELECT COUNT(*) AS vocabulary_count FROM vocabulary_entries;
"@
