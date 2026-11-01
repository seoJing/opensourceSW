#!/bin/sh
set -eu

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_root"

if ! command -v docker >/dev/null 2>&1; then
  echo "'docker' 명령을 찾을 수 없습니다. Docker Desktop 또는 Docker Engine을 설치해 주세요." >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose를 사용할 수 없습니다." >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker 데몬에 연결할 수 없습니다. Docker를 실행한 뒤 다시 시도해 주세요." >&2
  exit 1
fi

if [ ! -f .env ]; then
  cp .env.example .env
  echo ".env.example을 복사해 .env를 만들었습니다."
else
  echo "기존 .env를 그대로 사용합니다."
fi

docker compose up --build -d

services="db backend frontend"
deadline=$(( $(date +%s) + 180 ))
ready=false

echo "서비스가 준비될 때까지 기다립니다..."
while [ "$(date +%s)" -lt "$deadline" ]; do
  all_healthy=true

  for service in $services; do
    container_id=$(docker compose ps --all --quiet "$service" 2>/dev/null || true)
    if [ -z "$container_id" ]; then
      all_healthy=false
      continue
    fi

    status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id" 2>/dev/null || true)
    if [ "$status" != "healthy" ]; then
      all_healthy=false
    fi
  done

  if [ "$all_healthy" = "true" ]; then
    ready=true
    break
  fi

  sleep 2
done

if [ "$ready" != "true" ]; then
  docker compose ps
  echo "180초 안에 모든 서비스가 healthy 상태가 되지 않았습니다." >&2
  echo "docker compose logs --tail=200 명령으로 로그를 확인하세요." >&2
  exit 1
fi

docker compose ps
echo
echo "초기 개발 환경이 준비되었습니다."
echo "프론트엔드: http://localhost:3000"
echo "백엔드 상태: http://localhost:8080/actuator/health"
echo "프록시 상태: http://localhost:3000/api/health"
