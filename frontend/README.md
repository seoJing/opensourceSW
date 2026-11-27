# 프론트엔드

React·TypeScript·Vite와 SEED Design으로 만든 프론트엔드입니다. `/`는 상수 mock 기반 중간 발표 시연 화면, `/status`는 기존 연결 상태 확인 화면입니다. 업무 API 연동은 포함하지 않습니다.

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

## SEED 디자인 시스템과 AI 스킬

- SEED React·CSS 3.3.0, Vite 플러그인 2.1.1과 CLI 1.7.4를 고정하고 기존 pnpm·FSD·ESLint 규칙을 유지합니다.
- 기존 상태 화면은 SEED Box·Text·ActionButton과 공개 토큰을 사용합니다. 연결 다시 확인 버튼은 확인 중 비활성화되고 오류 후 재시도할 수 있습니다. `/api/health` 계약은 유지합니다.
- 공식 CLI 스니펫은 `src/shared/ui/seed-design`에 두고 앱에서는 `src/shared/ui/index.ts`를 사용합니다. `seed-design.json`·TypeScript paths·Vite alias는 같은 경로를 가리킵니다.
- 발표 환경은 라이트 모드로 고정합니다. 시스템 폰트 fallback을 유지하고 외부 폰트·이미지를 추가하지 않습니다.
- `pnpm exec seed-design add ui:<이름>`으로 실제 필요한 스니펫만 추가하고 `pnpm exec seed-design compat`로 호환성을 확인합니다. 기존 파일을 갱신하기 전 로컬 수정과 요구 버전을 비교합니다.
- 팀 공유 공식 스킬은 [seed-design/SKILL.md](../.agents/skills/seed-design/SKILL.md)에 있습니다. Codex에서는 다음 턴부터 사용할 수 있고, 다른 도구에서는 이 링크로 읽습니다. [출처와 갱신 절차](../.agents/skills/seed-design/UPSTREAM.md)를 함께 유지합니다.

의존성 선택 이유·토큰·접근성·스니펫 변경 기록·검증 방법은 [SEED 사용 기준](../docs/frontend-design-system.md)을 따릅니다.

## 중간 발표 시연 이력 (Issue #36)

Issue #36에서 표지 → 로컬 로그인 → 고정 5문항 → 지도 → 메뉴 카드·카페 바텀시트를 구현했습니다. 서비스명은 **카페 지도**입니다. 현재 실행 및 테스트 방법은 아래 API 연동 개발 절을 따릅니다.

- 시연용 가상 카페·메뉴와 고정 문항의 회귀 테스트 데이터는 `src/pages/demo/mock/index.ts`에 있습니다.
- 지도는 `public/maps/haenggung.svg`와 Leaflet 1.9.4를 사용합니다. [지도 출처·범위](public/maps/README.md)를 유지하세요. 공식 SEED 아이콘 1.35.0과 BottomSheet·TextField 스니펫을 추가했습니다.
- 메뉴 카드와 카페 핀이 연동되고, 카페 시트에서 네이버·카카오 지도의 시연 지점으로 연결합니다. 현재 위치는 버튼을 누르고 브라우저에서 허용했을 때만 표시합니다.
- 기존 상태 화면은 `/status`, health는 `/api/health`입니다. 기본 Docker 포트·서비스·DB 볼륨은 유지합니다.
- 검수 절차·로컬 상태·실제 백엔드와의 경계·스니펫 변경 이유는 [시연 안내](../docs/demo.md)를 참고하세요. [Issue #36](https://github.com/seoJing/opensourceSW/issues/36)의 합의 범위이며 실제 업무 API 계약을 확정하지 않습니다.

## API 연동 개발

기본 실행은 실제 세션·설문·추천 API를 사용합니다. Vite는 `/api/v1` 경로를 호스트의 8080번 포트로 전달하고, Compose의 Nginx는 같은 경로를 `backend:8080`에 전달합니다. 브라우저에서는 프론트 주소의 `/api/v1/auth/session`이 200 JSON을 반환하는지 확인합니다. 기존 `/api/health` 확인은 Compose의 3000번 포트에서 수행합니다.

실제 설문·추천 검증은 [PR #26 인계 문서](https://github.com/mascot-vicky/opensourceSW/blob/a81f001bf5519bbd41f0edb17aeee98918543f41/backend/docs/preferences-handoff.md)의 설문·카탈로그 Compose 설정을 사용합니다. 세션 인증으로 테스트할 때는 백엔드에 검토용 계정을 설정합니다.

Vite 개발 서버에서 Mock을 명시적으로 켜려면 PowerShell에서 다음 명령을 실행합니다.

```powershell
$env:VITE_DEMO_API_MOCK = 'true'
corepack pnpm dev
```

Mock 로그인에는 아무 테스트용 아이디와 비밀번호를 사용할 수 있습니다. 같은 탭의 새로고침에서 Mock 사용자와 저장 답변을 복원하며, 비밀번호·토큰은 저장하지 않습니다. 문항 JSON은 #26의 `a81f001`을 복사했고 나머지 Mock은 프론트에서 만든 테스트 데이터 또는 기존 시연 상수를 사용합니다. 실 API 경로로 복귀하려면 해당 환경변수를 제거하고 Vite를 다시 시작합니다.

실제 카드의 가격과 태그는 API가 제공할 때 연결합니다. 전체 매장 메뉴와 전체 카페 검색은 합의된 조회 계약이 생기면 연결하며, 현재 검색과 전체 메뉴 상수는 개발용 Mock에만 표시합니다. 추천 이력은 페이지 세션에 들고 있으며, 100개 도달 또는 후속 후보 소진 시 사용자가 목록을 초기화할 수 있습니다.
