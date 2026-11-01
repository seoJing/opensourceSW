$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot

function Assert-Command {
    param([Parameter(Mandatory = $true)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "'$Name' 명령을 찾을 수 없습니다. Docker Desktop 또는 Docker Engine을 설치해 주세요."
    }
}

Assert-Command -Name 'docker'
docker compose version | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Compose를 사용할 수 없습니다.'
}

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'Docker 데몬에 연결할 수 없습니다. Docker를 실행한 뒤 다시 시도해 주세요.'
}

if (-not (Test-Path -LiteralPath '.env')) {
    Copy-Item -LiteralPath '.env.example' -Destination '.env'
    Write-Host '.env.example을 복사해 .env를 만들었습니다.'
} else {
    Write-Host '기존 .env를 그대로 사용합니다.'
}

docker compose up --build -d
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Compose 실행에 실패했습니다.'
}

$services = @('db', 'backend', 'frontend')
$deadline = (Get-Date).AddSeconds(180)
$ready = $false

Write-Host '서비스가 준비될 때까지 기다립니다...'
while ((Get-Date) -lt $deadline) {
    $allHealthy = $true

    foreach ($service in $services) {
        $containerId = docker compose ps --all --quiet $service
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
            $allHealthy = $false
            continue
        }

        $status = docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $containerId
        if ($LASTEXITCODE -ne 0 -or $status -ne 'healthy') {
            $allHealthy = $false
        }
    }

    if ($allHealthy) {
        $ready = $true
        break
    }

    Start-Sleep -Seconds 2
}

if (-not $ready) {
    Write-Host ''
    docker compose ps
    Write-Error "180초 안에 모든 서비스가 healthy 상태가 되지 않았습니다. 'docker compose logs --tail=200'으로 로그를 확인하세요."
}

Write-Host ''
docker compose ps
Write-Host ''
Write-Host '초기 개발 환경이 준비되었습니다.'
Write-Host '프론트엔드: http://localhost:3000'
Write-Host '백엔드 상태: http://localhost:8080/actuator/health'
Write-Host '프록시 상태: http://localhost:3000/api/health'
