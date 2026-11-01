# 기여 가이드

이 문서는 Git과 Pull Request가 익숙하지 않은 팀원도 같은 방식으로 작업하기 위한 규칙입니다.

## 브랜치 구조

```text
main
└─ develop
   ├─ feature/frontend/<issue>-<name>
   ├─ feature/backend/<issue>-<name>
   ├─ feature/infra/<issue>-<name>
   ├─ fix/<area>/<issue>-<name>
   ├─ docs/<issue>-<name>
   └─ chore/<issue>-<name>
```

예를 들어 12번 Issue의 프론트엔드 작업 브랜치는 `feature/frontend/12-status-card`처럼 만듭니다.

## 작업 순서

1. 작업을 시작하기 전에 GitHub Issue를 만듭니다.
2. 최신 `develop`에서 Issue 하나당 브랜치 하나를 만듭니다.
3. 한 PR에는 한 가지 목적의 변경만 담습니다.
4. 변경한 영역의 테스트를 로컬에서 실행합니다.
5. 작업 브랜치에서 `develop`을 대상으로 PR을 만듭니다.
6. 최소 1명의 리뷰를 받고 가능하면 Squash merge합니다.

`main`과 `develop`에는 직접 push하지 않습니다. 저장소 설정에서 두 브랜치에 보호 규칙을 적용하는 것을 권장합니다.

## 변경 전 확인

- 루트 `AGENTS.md`와 영역별 README를 먼저 확인합니다.
- 프론트엔드는 `npm run lint`, `npm run test`, `npm run build`를 실행합니다.
- 백엔드는 `./mvnw test`와 `./mvnw package`를 실행합니다.
- 통합 환경 변경은 `docker compose config`와 전체 실행을 확인합니다.
- `.env`, 키, 토큰, 비밀번호, 개인 IDE 설정은 커밋하지 않습니다.

Docker만 설치된 환경에서는 다음 한 줄로 프론트엔드와 백엔드 테스트를 실행할 수 있습니다.

```sh
sh scripts/test.sh
```

Windows PowerShell에서는 다음 명령을 사용합니다.

```powershell
./scripts/test.ps1
```
