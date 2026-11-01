# 프론트엔드

React와 Vite로 만든 초기 상태 확인 화면입니다. 비즈니스 화면이나 외부 API 연동은 포함하지 않습니다.

## Docker로 검증

저장소 루트에서 다음 명령을 실행합니다.

```sh
docker build --target test -t suwon-trip-frontend-test:local frontend
docker build -t suwon-trip-frontend:local frontend
```

## 호스트에서 개발

Node.js 24 LTS가 설치되어 있다면 다음 명령을 사용할 수 있습니다.

```sh
cd frontend
npm ci
npm run dev
```

개발 서버만 실행하면 `/api/health` 프록시는 제공되지 않습니다. 전체 연결 확인은 루트의 Docker Compose 실행을 사용합니다.
