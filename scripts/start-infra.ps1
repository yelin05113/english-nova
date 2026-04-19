$ErrorActionPreference = "Stop"

Set-Location (Join-Path $PSScriptRoot "..")
docker compose --env-file .env up -d mysql nacos redis rabbitmq elasticsearch
docker compose --env-file .env up seeder
