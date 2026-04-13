$ErrorActionPreference = "Stop"

$projectRoot = Join-Path $PSScriptRoot ".."
$backendRoot = Join-Path $projectRoot "BackEnd-EnglishNova"

Set-Location $backendRoot

$env:MYSQL_PORT = "3307"
$env:MYSQL_DATABASE = "english_nova"
$env:MYSQL_USERNAME = "english_nova"
$env:MYSQL_PASSWORD = "english_nova"
$env:REDIS_PORT = "6379"
$env:RABBITMQ_PORT = "5672"
$env:ELASTICSEARCH_URIS = "http://localhost:9200"

mvn -q -DskipTests package
java -jar target\BackEnd-EnglishNova-0.0.1-SNAPSHOT.jar
