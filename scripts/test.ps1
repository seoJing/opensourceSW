$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "'docker' 명령을 찾을 수 없습니다. Docker를 설치해 주세요."
}

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'Docker 데몬에 연결할 수 없습니다. Docker를 실행한 뒤 다시 시도해 주세요.'
}

Write-Host '프론트엔드 lint와 테스트를 실행합니다.'
docker build --target test --tag suwon-trip-frontend-test:local frontend
if ($LASTEXITCODE -ne 0) {
    throw '프론트엔드 검증에 실패했습니다.'
}

Write-Host '백엔드 테스트를 실행합니다.'
docker build --target test --tag suwon-trip-backend-test:local backend
if ($LASTEXITCODE -ne 0) {
    throw '백엔드 검증에 실패했습니다.'
}

Write-Host '모든 테스트가 통과했습니다.'
