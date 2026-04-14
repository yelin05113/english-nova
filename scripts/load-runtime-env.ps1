param(
    [string]$InfraHost = "192.168.80.128"
)

$ErrorActionPreference = "Stop"

function Set-DefaultEnv {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($Name, "Process"))) {
        [Environment]::SetEnvironmentVariable($Name, $Value, "Process")
    }
}

Set-DefaultEnv -Name "MYSQL_HOST" -Value "127.0.0.1"
Set-DefaultEnv -Name "MYSQL_PORT" -Value "3306"
Set-DefaultEnv -Name "MYSQL_DATABASE" -Value "english_nova"
Set-DefaultEnv -Name "MYSQL_USERNAME" -Value "english_nova"
Set-DefaultEnv -Name "MYSQL_PASSWORD" -Value "english_nova"

Set-DefaultEnv -Name "REDIS_HOST" -Value $InfraHost
Set-DefaultEnv -Name "REDIS_PORT" -Value "6379"
Set-DefaultEnv -Name "REDIS_PASSWORD" -Value "123321"

Set-DefaultEnv -Name "RABBITMQ_HOST" -Value $InfraHost
Set-DefaultEnv -Name "RABBITMQ_PORT" -Value "5672"
Set-DefaultEnv -Name "RABBITMQ_USERNAME" -Value "nightfall"
Set-DefaultEnv -Name "RABBITMQ_PASSWORD" -Value "123321"
Set-DefaultEnv -Name "RABBITMQ_VHOST" -Value "/"

Set-DefaultEnv -Name "ELASTICSEARCH_HOST" -Value $InfraHost
Set-DefaultEnv -Name "ELASTICSEARCH_PORT" -Value "9200"

Set-DefaultEnv -Name "NACOS_SERVER_ADDR" -Value ($InfraHost + ":8848")
Set-DefaultEnv -Name "NACOS_USERNAME" -Value "nacos"
Set-DefaultEnv -Name "NACOS_PASSWORD" -Value "nacos"
Set-DefaultEnv -Name "NACOS_NAMESPACE" -Value "public"
Set-DefaultEnv -Name "NACOS_GROUP" -Value "DEFAULT_GROUP"

Set-DefaultEnv -Name "JWT_SECRET" -Value "english-nova-local-jwt-secret-must-be-32-char"
Set-DefaultEnv -Name "JWT_EXPIRATION_HOURS" -Value "72"
