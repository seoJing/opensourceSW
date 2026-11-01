#!/bin/sh
set -eu

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_root"

if ! command -v docker >/dev/null 2>&1; then
  echo "'docker' 명령을 찾을 수 없습니다." >&2
  exit 1
fi

docker compose down
echo "서비스를 종료했습니다. PostgreSQL 볼륨은 유지됩니다."
