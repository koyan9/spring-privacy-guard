param(
    [ValidateSet('up', 'down', 'logs', 'ps')]
    [string]$Action = 'up'
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$sampleDir = Split-Path -Parent $scriptDir
$composeFile = Join-Path $sampleDir 'docker-compose.redis.yml'

if (-not (Test-Path $composeFile)) {
    throw "Compose file not found: $composeFile"
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if ($null -eq $docker) {
    throw "Docker CLI was not found on PATH. Install Docker Desktop or run Redis manually on localhost:6379."
}

$baseArgs = @('compose', '-f', $composeFile)

switch ($Action) {
    'up' {
        & $docker.Source @baseArgs 'up' '-d'
    }
    'down' {
        & $docker.Source @baseArgs 'down'
    }
    'logs' {
        & $docker.Source @baseArgs 'logs' '--tail' '100'
    }
    'ps' {
        & $docker.Source @baseArgs 'ps'
    }
}

if ($LASTEXITCODE -ne 0) {
    throw "docker compose command failed with exit code $LASTEXITCODE"
}
