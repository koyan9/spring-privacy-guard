param(
    [ValidateSet('up', 'down')]
    [string]$Action = 'up'
)

$composeFile = Join-Path $PSScriptRoot '..\docker-compose.postgres-redis.yml'

if ($Action -eq 'down') {
    docker compose -f $composeFile down --remove-orphans
    exit $LASTEXITCODE
}

docker compose -f $composeFile up -d --build
