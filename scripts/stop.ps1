$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "'docker' 명령을 찾을 수 없습니다."
}

docker compose down
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Compose 종료에 실패했습니다.'
}

Write-Host '서비스를 종료했습니다. PostgreSQL 볼륨은 유지됩니다.'
