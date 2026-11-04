# 공동 작업과 저장소 하네스

## 담당 방식

프론트엔드와 백엔드는 전담 소유 영역이 아닙니다. 누구나 두 영역을 함께 수정할 수 있으며, Issue에는 실제로 수정할 파일과 담당자를 적습니다. 두 영역을 함께 수정하는 브랜치는 `feature/fullstack/<issue>-<name>`을 사용합니다.

초기 프론트 구축과 초기 백엔드 구축은 각각 한 명이 담당합니다. 각 담당자는 해당 영역의 구조, 컨벤션, 기본 테스트를 한 PR로 정리합니다. 다른 팀원은 합의된 독립 파일을 작업할 수 있지만, 초기 구축이 끝나기 전 같은 진입점·전역 스타일·의존성 파일을 동시에 수정하지 않습니다. 의존하는 기능 작업은 초기 구축 PR을 먼저 병합한 뒤 최신 `main`에서 시작합니다.

초기 구축 PR은 다음 인수인계를 포함합니다.

| 항목 | 기록 위치 | 완료 기준 |
| --- | --- | --- |
| 폴더 구조와 파일 배치 | 영역별 README | 새 파일을 어디에 추가할지 설명 |
| 코드 컨벤션과 공통 코드 사용법 | 영역별 README, 필요 시 해당 영역의 AGENTS.md | 네이밍, 공통 진입점, 테스트 작성법 설명 |
| 표준 검증 | 기존 설정 및 테스트 | lint·테스트·빌드 통과 |
| 연결 확인 | 기존 Docker Compose | 세 서비스 healthy, 화면 및 `/api/health` 정상 |
| 합의 사항 | Issue 및 PR | 공통 파일 변경 이유, API 계약, 후속 작업 명시 |

하네스는 이후 추가되는 코드에도 파일·PR 단위 검사를 자동 적용합니다. 폴더 구조나 업무 API를 생성하지 않으며, 코드 네이밍 등 의미상 컨벤션은 해당 초기 구축 Issue에서 정하고 문서·lint에 반영합니다.

## 공통 파일 변경

일반 기능 작업에서는 기존 구조와 컨벤션, 의존성, 실행 구성을 유지합니다. 변경이 필요하면 먼저 Issue에 필요성·대안·영향·검증 방법을 적고 팀에서 합의합니다. 변경은 가능하면 기능 구현과 별도 PR로 진행합니다.

자동 감지 목록은 `.github/harness-policy.json`이 기준입니다. 현재 다음 경로를 포함합니다.

| 구분 | 주요 경로 |
| --- | --- |
| 저장소 규칙 및 자동화 | AGENTS.md, CONTRIBUTING.md, .github/, scripts/, 편집기·Git 설정 |
| 로컬 실행 | docker-compose.yml, Dockerfile, .dockerignore, Nginx 설정 |
| 프론트 기반 | package.json, lockfile, Vite·ESLint·테스트 설정, main.jsx, App.jsx, 전역 스타일 |
| 백엔드 기반 | pom.xml, Maven Wrapper, 애플리케이션 진입점, main/test resources |
| DB 기반 | database/ |

공통 경로 변경 시 PR 본문에 합의한 Issue를 연결하고 다음 항목을 체크해야 합니다.

```markdown
- [x] 공통 설정 변경을 Issue에서 합의했습니다.
```

이 체크는 변경을 드러내기 위한 자기 확인입니다. 실제 팀 합의와 리뷰 승인을 자동 판정하지 않습니다. 공통 파일 삭제·이동도 감지합니다. 초기 구축으로 공통 진입점이 바뀌거나 새 설정 파일이 생기면 목록을 함께 갱신하세요. 자동 목록에 없더라도 공통 구조를 바꾸면 같은 합의 규칙이 적용됩니다.

## 자동 검사

| 검사 | 동작 |
| --- | --- |
| Repository Guard | 커밋 대상 환경 파일·생성 파일과 남은 병합 충돌 표시를 발견하면 실패 |
| PR Coordination | 공통 경로 변경의 Issue 연결·합의 체크 누락과 GitHub가 확인한 병합 충돌이면 실패 |
| 열린 PR 파일 중복 | 같은 main 대상 PR끼리 수정 경로를 비교해 Actions 경고와 Summary에 표시 |
| Frontend Lint / Frontend Test | 프론트 lint, 테스트 및 빌드 |
| Backend Test | 백엔드 테스트 및 패키징 |
| Docker Build / Compose Smoke Test | 이미지 빌드, Compose 설정, 세 서비스 health와 프론트·DB 포함 프록시 응답 |

필수 검사로 지정할 작업은 경로 필터 없이 모든 `main` 대상 PR과 `main` push에서 실행합니다. 문서 PR도 검사가 끝나므로 필수 검사가 Pending에 남는 문제를 피합니다. 각 검사 이름은 고유하게 유지합니다.

PR Coordination은 `pull_request_target`에서 **기준 브랜치의 코드만** 실행하고 읽기 권한으로 PR 메타데이터를 조회합니다. PR head 코드는 실행하지 않으며 댓글도 작성하지 않습니다. 따라서 이 하네스를 처음 추가하는 PR에서는 작동하지 않고 main에 병합된 뒤 다음 PR부터 적용됩니다. PR 본문 수정·새 커밋·재오픈 시 다시 확인합니다. 다른 PR이 나중에 바뀌면 이전 보고서가 자동 갱신되는 것은 아닙니다. 병합 직전에 최신 main과 열린 PR을 다시 확인하세요.

파일 중복은 실제 병합 충돌을 뜻하지 않습니다. 겹친 파일의 담당자끼리 순서를 정하고 먼저 병합된 변경을 나머지 브랜치에 반영합니다. 같은 파일의 서로 다른 부분, API 의미의 충돌, DB 변경 순서까지 자동 판단하지는 않습니다. GitHub가 병합 가능 여부를 아직 계산 중이면 경고하며 PR 화면에서 재확인해야 합니다.

로컬 파일 검사와 하네스 테스트는 Node.js 24가 있으면 저장소 루트에서 실행할 수 있습니다. 추가 npm 설치는 필요 없습니다.

```sh
node scripts/repo-harness.mjs
node --test scripts/repo-harness.test.mjs
```

이미 커밋한 브랜치의 공통 변경 파일도 확인하려면 `node scripts/repo-harness.mjs origin/main`을 실행합니다. 비교 기준 ref가 로컬에 있어야 합니다. 로컬 실행은 미추적 파일도 검사하고, CI는 checkout된 커밋 파일을 검사합니다. 이 검사는 비밀정보 내용 탐지 도구를 대신하지 않습니다.

## main 보호 설정

문서와 CI 파일만 추가해서는 직접 push나 실패한 PR 병합을 막을 수 없습니다. 저장소 관리자가 GitHub Settings → Branches 또는 Rules → Rulesets에서 main에 다음을 적용해야 합니다.

- PR을 통한 변경과 최소 1명 승인 필수
- 새 커밋이 올라오면 기존 승인 취소
- 리뷰 대화 해결 필수
- 병합 전에 최신 main 반영 필수
- 강제 push 및 브랜치 삭제 금지
- 관리자에게도 동일 규칙 적용
- 필수 검사: `Repository Guard`, `PR Coordination`, `Frontend Lint`, `Frontend Test`, `Backend Test`, `Docker Build`, `Compose Smoke Test`

하네스 PR을 병합하고 다음 PR에서 검사 이름이 실제로 나타나는 것을 확인한 뒤 필수 검사로 등록합니다. 특히 PR Coordination은 기준 브랜치에 먼저 있어야 합니다. 팀원 이름과 GitHub ID가 아직 정해지지 않아 CODEOWNERS를 임의로 만들지 않습니다. 필요하면 초기 구축 담당자가 정해진 후 공통 파일의 리뷰 담당자를 지정하세요.

## 로컬 시연 점검

현재 배포 범위는 개인 PC에서 Docker Compose로 실행하는 시연입니다. 외부 서버 배포, 도메인, TLS, 운영 인증정보, 원격 DB나 배포 자동화는 포함하지 않습니다.

기존 설정은 3개 서비스, DB → 백엔드 → 프론트엔드 health 의존성, DB named volume, Nginx `/api/health` 프록시를 제공합니다. 프론트 이미지는 lint·테스트·빌드를, 백엔드 이미지는 테스트·패키징을 거쳐 생성됩니다. 백엔드는 일반 사용자로 실행됩니다. Compose smoke CI는 로컬 기본 설정으로 전체 연결을 확인합니다.

실제 시연 PC에서는 아래 순서로 검증합니다.

```sh
docker compose config --quiet
docker compose up --build -d
docker compose ps
```

세 서비스가 healthy인지 확인한 뒤 브라우저에서 `http://localhost:3000/`와 `http://localhost:3000/api/health`를 엽니다. JSON의 최상위 status와 components.db.status가 모두 UP이어야 합니다. 종료는 `docker compose down`을 사용하며 DB 볼륨은 유지합니다. Windows에서도 위 Docker 명령을 그대로 사용할 수 있고, 표준 start/test/stop 스크립트도 유지합니다.

호스트 3000·8080 포트는 현재 모든 인터페이스에 바인딩됩니다. 개인 PC 외부 접속을 의도하지 않는 시연이라면 공통 실행 설정 변경 Issue에서 loopback 바인딩을 합의할 수 있습니다. 동시에 실행하는 여러 체크아웃은 같은 포트와 DB 볼륨 이름을 공유하므로 한 PC에서는 한 세트만 실행합니다.
