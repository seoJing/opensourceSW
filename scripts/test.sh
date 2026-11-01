#!/bin/sh
set -eu

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_root"

if ! command -v docker >/dev/null 2>&1; then
  echo "'docker' 명령을 찾을 수 없습니다. Docker를 설치해 주세요." >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker 데몬에 연결할 수 없습니다. Docker를 실행한 뒤 다시 시도해 주세요." >&2
  exit 1
fi

echo "프론트엔드 lint와 테스트를 실행합니다."
docker build --target test --tag suwon-trip-frontend-test:local frontend

echo "백엔드 테스트를 실행합니다."
docker build --target test --tag suwon-trip-backend-test:local backend

echo "모든 테스트가 통과했습니다."
