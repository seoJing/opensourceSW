# 수원 랜덤 여행 코스 — 초기 개발 환경

사용자 선호에 맞는 수원시 랜덤 여행 코스 서비스를 만들기 위한 5인 팀 프로젝트의 초기 모노레포입니다.

## 프로젝트 배경

수원시의 랜덤 여행 코스 추천 웹 서비스를 주제로, 5명의 팀원이 웹 개발과 오픈소스 협업 과정을 학습하는 프로젝트입니다. GitHub Issue와 PR로 작업을 관리하고, Docker로 동일한 개발 환경을 구성합니다.

| 항목 | 내용 |
| --- | --- |
| 주제 | 수원시 랜덤 여행 코스 추천 웹 서비스 |
| 목적 | 웹 서비스 개발과 팀 협업 학습 |
| 진행 단계 | 초기 저장소 및 공통 개발 환경 구성 |

## 팀원 소개

| 구분 | 이름 | 역할 |
| :---: | :---: | --- |
| 팀원 1 |  |  |
| 팀원 2 |  |  |
| 팀원 3 |  |  |
| 팀원 4 |  |  |
| 팀원 5 |  |  |

## 현재 구현 범위

현재는 React 프론트엔드, Spring Boot 백엔드, PostgreSQL을 한 번에 실행하고 협업을 시작할 수 있는 기반만 구현했습니다. 화면은 세 서비스의 연결 상태를 확인하는 용도이며 POI, 추천, 성향 테스트, 지도, 외부 API, 로그인과 실제 여행 기능은 아직 구현하지 않았습니다.

## 구조

```text
frontend/             React, Vite, Nginx
backend/              Java 21, Spring Boot, Maven
database/             향후 DB 초기화 자료 위치
scripts/              Windows 및 macOS/Linux 명령
docs/                 아키텍처 문서
.github/               PR 템플릿과 CI
docker-compose.yml     frontend, backend, db 통합 실행
```

상세 연결 구조는 [docs/architecture.md](docs/architecture.md)를 참고하세요.

## 필요한 프로그램

- Git
- Docker Desktop 또는 Docker Engine(Compose 플러그인 포함)

Node.js, Java, Maven, PostgreSQL은 호스트에 설치하지 않아도 됩니다.

## 저장소를 받은 뒤 실행

원격 저장소가 준비되면 다음과 같이 복제합니다. `<저장소-URL>`과 `<저장소-폴더>`는 팀 저장소 값으로 바꾸세요.

```sh
git clone <저장소-URL>
cd <저장소-폴더>
```

### Windows PowerShell

```powershell
./scripts/start.ps1
```

### macOS/Linux

```sh
sh scripts/start.sh
```

시작 스크립트는 Docker 사용 가능 여부를 확인한 뒤 이미지를 빌드하고 세 서비스를 실행합니다. 모든 health check가 통과하면 접속 주소를 출력합니다.

## 접속 주소

- 프론트엔드: <http://localhost:3000>
- Nginx를 통한 백엔드 상태: <http://localhost:3000/api/health>
- Spring Boot Actuator 직접 확인: <http://localhost:8080/actuator/health>

PostgreSQL은 Compose 내부의 `db:5432`에서만 사용하며 호스트 포트로 공개하지 않습니다.

## 수동 Docker Compose 실행

스크립트 없이 실행하려면 저장소 루트에서 다음 명령을 사용합니다.

```sh
docker compose up --build -d
docker compose ps
```

## 테스트

Docker만으로 프론트엔드 lint·테스트와 백엔드 테스트를 실행합니다.

Windows PowerShell:

```powershell
./scripts/test.ps1
```

macOS/Linux:

```sh
sh scripts/test.sh
```

영역별로 직접 검증하려면 다음 명령을 사용할 수 있습니다.

```sh
docker build --target test -t suwon-trip-frontend-test:local frontend
docker build --target test -t suwon-trip-backend-test:local backend
docker compose config
docker compose build
```

## 종료

Windows PowerShell:

```powershell
./scripts/stop.ps1
```

macOS/Linux:

```sh
sh scripts/stop.sh
```

일반 종료는 PostgreSQL named volume `suwon_trip_postgres_data`를 삭제하지 않으므로 로컬 데이터가 유지됩니다. 볼륨 삭제 명령은 자동화하지 않았습니다.

## 자주 발생하는 문제

### Docker가 실행되지 않음

Docker Desktop 또는 Docker 데몬을 먼저 실행한 뒤 `docker info`와 `docker compose version`이 성공하는지 확인합니다.

### 3000 또는 8080 포트 충돌

다른 프로그램이 포트를 사용 중인지 확인하고 해당 프로그램을 종료합니다. 팀 공통 주소를 유지하기 위해 Compose 포트를 임의로 바꾸기 전 팀과 합의합니다.

### 컨테이너 health check 실패

상태와 최근 로그를 확인합니다.

```sh
docker compose ps
docker compose logs --tail=200
```

DB가 healthy가 아니면 백엔드와 프론트엔드도 순서대로 시작되지 않습니다. 위 명령으로 DB 컨테이너의 상태와 로그를 먼저 확인합니다.

### 이전 이미지 캐시가 의심됨

소스와 의존성을 다시 반영해 빌드합니다.

```sh
docker compose build --no-cache
docker compose up -d
```

## 협업 규칙 요약

`main`에 직접 push하지 않습니다. 먼저 Issue를 만들고 최신 `main`에서 Issue 하나당 작업 브랜치 하나를 만든 뒤 `main` 대상으로 PR을 올립니다. 최소 1명의 리뷰를 받고 가능하면 Squash merge합니다.

브랜치 이름, 테스트와 PR 작성법은 [CONTRIBUTING.md](CONTRIBUTING.md), AI 도구의 작업 규칙은 [AGENTS.md](AGENTS.md)를 참고하세요.

프론트·백 구분 없이 공동 작업할 때의 초기 구축 절차, 공통 설정 변경 규칙, PR 충돌 가능성 감지와 로컬 시연 점검은 [공동 작업 가이드](docs/collaboration.md)에 정리되어 있습니다.
