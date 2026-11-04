# 기여 가이드

이 문서는 Git과 Pull Request가 익숙하지 않은 팀원도 같은 방식으로 작업하기 위한 규칙입니다.

## 브랜치 구조

```text
main
├─ feature/frontend/<issue>-<name>
├─ feature/backend/<issue>-<name>
├─ feature/infra/<issue>-<name>
├─ feature/fullstack/<issue>-<name>
├─ fix/<area>/<issue>-<name>
├─ refactor/<area>/<issue>-<name>
├─ docs/<issue>-<name>
└─ chore/<issue>-<name>
```

예를 들어 12번 Issue의 프론트엔드 작업 브랜치는 `feature/frontend/12-status-card`처럼 만듭니다.

## Issue와 PR 제목

- Issue 제목은 `[유형]: 제목` 형식입니다. 예시 `[Feature]: 상태 카드 추가`
- PR 제목은 `[영역] 유형(작업범위): 제목 (#이슈번호)` 형식입니다. 예시 `[FE] feature(status-card): 상태 카드 추가 (#12)`
- 영역은 `FE`(프론트), `BE`(백엔드), `FULLSTACK`(양쪽 기능), `INFRA`(Docker·CI·배포), `REPO`(저장소 공통 규칙) 중 하나를 사용합니다.
- 작업범위에는 영역 안에서 바뀐 화면, 모듈, 설정 이름을 적습니다. 예시 `status-card`, `health-api`, `compose`
- Issue 유형은 `Feature`, `Fix`, `Refactor`, `Chore`, `Docs` 중 하나를 사용합니다. PR 유형은 같은 이름을 소문자로 씁니다.
- 유형의 의미는 `feature`(기능 추가), `fix`(오류 수정), `refactor`(동작을 유지하는 구조 개선), `chore`(초기 구성·도구·의존성), `docs`(문서) 중 하나를 사용합니다.
- Issue 하나에 작업 브랜치 하나와 PR 하나를 연결합니다. 예를 들어 Issue #12의 브랜치는 `feature/frontend/12-status-card`이고 PR 제목 끝에는 `(#12)`, 본문에는 `Closes #12`를 적습니다.

## 작업 순서

1. 작업을 시작하기 전에 GitHub Issue를 만듭니다.
2. 최신 `main`에서 Issue 번호를 포함한 작업 브랜치 하나를 만듭니다.
3. 한 PR에는 한 가지 목적의 변경만 담습니다.
4. 변경한 영역의 테스트를 로컬에서 실행합니다.
5. 작업 브랜치에서 `main`을 대상으로 PR 하나를 만들고 같은 Issue 번호를 제목과 본문에 적습니다.
6. 최소 1명의 리뷰를 받고 가능하면 Squash merge합니다.

`main`에는 직접 push하지 않습니다. 저장소 설정에서 `main`에 보호 규칙을 적용하는 것을 권장합니다.

## 역할을 넘나드는 작업

누구나 프론트·백엔드를 함께 수정할 수 있습니다. Issue에 수정할 경로와 담당자를 적고, 같은 파일을 수정하는 열린 PR이 있으면 병합 순서를 먼저 정합니다. 초기 프론트·백엔드 구축은 각각 한 명이 구조와 컨벤션을 정리한 뒤 인수인계합니다.

공통 설정을 바꾸려면 Issue에서 필요성과 영향을 합의하고 PR의 공통 설정 변경 항목을 체크합니다. 자동 검사는 변경을 알리고 누락을 잡는 장치이며 실제 리뷰 승인은 별도로 필요합니다. 전체 절차와 초기 구축 완료 조건, 자동 검사, main 보호 설정은 [docs/collaboration.md](docs/collaboration.md)를 따릅니다.

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
