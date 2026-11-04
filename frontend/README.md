# 프론트엔드

React와 Vite로 만든 초기 상태 확인 화면입니다. 비즈니스 화면이나 외부 API 연동은 포함하지 않습니다.

초기 구축 담당자는 폴더 구조·네이밍·공통 코드 사용법·테스트 작성법을 이 문서에 정리하고, 공통 진입점이나 설정이 바뀌면 `.github/harness-policy.json`의 감지 목록도 갱신합니다. 상세 인수인계와 공동 작업 규칙은 [공동 작업 가이드](../docs/collaboration.md)를 따릅니다.

## Docker로 검증

저장소 루트에서 다음 명령을 실행합니다.

```sh
docker build --target test -t suwon-trip-frontend-test:local frontend
docker build -t suwon-trip-frontend:local frontend
```

## 호스트에서 개발

Node.js 24 LTS와 `package.json`의 `packageManager`에 명시된 pnpm이 설치되어 있다면 다음 명령을 사용할 수 있습니다.

```sh
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

개발 서버만 실행하면 `/api/health` 프록시는 제공되지 않습니다. 전체 연결 확인은 루트의 Docker Compose 실행을 사용합니다.

## 파일 배치와 검증

- `src/app`은 React 진입점, 화면 조립, 전역 스타일을 관리합니다.
- `src/pages/<슬라이스>`에는 화면별 `ui`, `api` 등을 실제 코드가 생길 때 추가합니다.
- 공통 UI와 기반 코드가 필요해지면 `shared`, 도메인·사용자 행동·조합 UI는 각각 `entities`, `features`, `widgets`에 둡니다.
- 기존 상태 확인 화면은 `pages/status`에 있습니다. 테스트는 화면 파일 옆에 두고 공통 설정은 `src/test/setup.ts`에 둡니다.

프론트 디렉터리에서 `pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm build`를 실행합니다. 의존성을 바꾸면 `pnpm-lock.yaml`을 함께 반영합니다.
