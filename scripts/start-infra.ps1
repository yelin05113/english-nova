$ErrorActionPreference = "Stop"

Set-Location (Join-Path $PSScriptRoot "..")
docker compose --env-file .env.docker up -d mysql redis rabbitmq elasticsearch
