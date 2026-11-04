# 프론트엔드 작업 규칙

- 루트 작업 규칙과 공동 작업 가이드를 따릅니다. 공통 CI, 통합 실행, 배포 설정 변경은 루트 정책에 따라 Issue에서 합의합니다.
- FSD 의존 방향은 app → pages → widgets → features → entities → shared입니다. 슬라이스는 하위 레이어 슬라이스의 공개 진입점인 index.ts를 통해 가져옵니다. 같은 레이어의 다른 슬라이스를 직접 가져오지 않습니다. 슬라이스 내부는 상대 경로를 사용합니다.
- 레이어와 세그먼트는 첫 실제 코드가 생길 때 추가합니다. 컴포넌트는 PascalCase, API 함수 파일은 camelCase, 슬라이스 폴더는 kebab-case로 작성합니다.
- 화면 테스트는 파일 곁에 두고 사용자에게 보이는 동작을 검증합니다. 백엔드 계약은 팀에서 합의한 값을 사용합니다.
- 변경 후 pnpm lint, pnpm typecheck, pnpm test, pnpm build를 실행하고 결과를 인수인계합니다.
