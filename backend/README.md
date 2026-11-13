# 백엔드

Java 21, Spring Boot 3.5, Maven 기반의 초기 백엔드입니다.
현재 제공하는 HTTP API는 Actuator의 `GET /actuator/health`이며,
Docker Compose 실행 시 PostgreSQL 연결 상태를 포함합니다.
업무 API와 도메인 스키마는 후속 Issue에서 합의한 뒤 추가합니다.

관련 규칙은 [기여 가이드](../CONTRIBUTING.md)와
[공동 작업 가이드](../docs/collaboration.md)를 따릅니다.
현재 작업은 팀 원본의 [Issue #14](https://github.com/seoJing/opensourceSW/issues/14)와
[PR #15](https://github.com/seoJing/opensourceSW/pull/15)에 연결되어 있습니다.

### 기술 스택

- Java 21
- Spring Boot 3.5
- Apache Maven
- PostgreSQL (개발 및 실행 환경)
- H2 (자동 테스트 환경)
- JUnit 5 / Spring Boot Test / MockMvc

### 패키지 구성 원칙

기본 패키지는 `com.suwontrip.app`을 사용한다.

```text
backend/
├── pom.xml                         Maven 의존성과 Java 버전
├── mvnw, mvnw.cmd                  Maven Wrapper
├── Dockerfile                      테스트·패키징·실행 이미지
└── src/
    ├── main/
    │   ├── java/com/suwontrip/app/  애플리케이션 및 기능 코드
    │   └── resources/              실행 설정
    └── test/
        ├── java/com/suwontrip/app/  자동 테스트
        └── resources/              H2 테스트 프로필
```

업무 기능이 시작되면 기능 단위로 코드를 구성하며,
사용하지 않는 Controller, Service, Repository를 미리 생성하지 않는다.
새 기능의 코드는 기본 패키지 아래 합의된 기능 패키지에 배치하고,
테스트도 같은 패키지 경로에 배치한다.
공통 코드는 둘 이상의 기능에서 실제 필요성이 확인되면 합의한 뒤 분리한다.

### Java 코드 컨벤션

- 클래스: PascalCase (`CafeController`)
- 메서드 및 변수: camelCase (`findCafeById`)
- 상수: UPPER_SNAKE_CASE (`MAX_RETRY_COUNT`)
- 패키지: 소문자 (`com.suwontrip.app`)
- 테스트 메서드: 검증하는 동작을 이름으로 표현

Spring Bean의 필수 의존성에는 생성자 주입을 우선 사용한다.

주석은 코드만으로 알기 어려운 설계 이유,
Annotation의 주요 동작, 테스트의 제약사항을 중심으로 작성한다.

### 테스트 전략

1. Unit Test
   - 비즈니스 로직을 개별적으로 검증한다.
   - 가능한 경우 Spring Context를 실행하지 않는다.

2. Integration Test
   - Spring Bean 및 여러 구성 요소의 연동을 검증한다.
   - `@SpringBootTest`와 `MockMvc` 등을 사용한다.

3. Container Integration Test
   - Docker Compose를 이용하여 실제 PostgreSQL과의 연결,
     서비스 실행 및 HTTP 응답을 검증한다.

현재 테스트는 다음을 포함한다.

- `SuwonTripApplicationTests`: Spring Context 로딩 검증
- `HealthEndpointTests`: HTTP 200 및 Health 상태 검증

테스트 프로필에서는 H2를 사용하므로
실제 PostgreSQL 연결 검증은 Docker Compose에서 별도로 수행한다.

### 호스트에서 테스트와 패키징

Java 21이 설치되어 있다면 `backend/`에서 실행한다.
Maven은 별도로 설치하지 않아도 된다.

macOS/Linux:

```sh
./mvnw -B -ntp verify
```

Windows PowerShell:

```powershell
./mvnw.cmd -B -ntp verify
```

`verify`는 테스트, JAR 패키징과 Maven 검증 단계를 실행한다.
테스트가 사용하는 H2의 PostgreSQL 호환 모드는 실제 PostgreSQL 검증을 대체하지 않는다.
별도의 Java lint 도구는 현재 설정되어 있지 않다.

### Docker 검증

Java를 호스트에 설치하지 않은 환경에서는 저장소 루트에서 실행한다.
Docker 데몬이 실행 중이어야 한다.

```sh
docker build --target test -t suwon-trip-backend-test:local backend
```

실행 결과를 직접 확인:

```sh
docker run --rm suwon-trip-backend-test:local mvn -B -ntp test
```

백엔드 실행 이미지 빌드:

```sh
docker build -t suwon-trip-backend:local backend
```

### 전체 연결 확인

저장소 루트의 표준 명령을 사용한다.

macOS/Linux:

```sh
sh scripts/start.sh
sh scripts/test.sh
sh scripts/stop.sh
```

Windows PowerShell:

```powershell
./scripts/start.ps1
./scripts/test.ps1
./scripts/stop.ps1
```

시작 후 다음을 확인한다.

- `docker compose ps`에서 `db`, `backend`, `frontend` 모두 healthy
- `http://localhost:3000/`에서 상태 화면 표시
- `http://localhost:3000/api/health`에서 `status`와 `components.db.status` 모두 `UP`
- `http://localhost:8080/actuator/health`에서 백엔드 직접 응답

오류가 나면 `docker compose logs --tail=200 backend db`로 원인을 확인한다.
일반 종료는 DB named volume을 보존한다.
접속 설정은 기존 Compose 환경변수와 `application.yml`을 따르며,
실제 인증정보는 커밋하지 않는다.

### 변경과 인수인계

- `main`에 직접 push하지 않고 Issue 하나에 작업 브랜치와 PR 하나를 연결한다.
- `pom.xml`, Maven Wrapper, 실행 설정 등 공통 파일 변경은 Issue에서 이유·영향·검증 방법을 합의한다.
- API 계약을 변경하면 프론트와 합의하고 문서와 관련 테스트를 함께 갱신한다.
- PR에는 실행한 검증과 미검증 항목을 구분하고, 최소 1명의 리뷰를 받는다.
- 초기 구축 결과와 남은 확인 사항은 [초기 구축 인수인계](INITIAL_SETUP_HANDOFF.md)를 참고한다.

### 향후 배포 및 자동화 고려사항

현재 실행 환경은 Docker Compose를 기반으로 한다.

향후 Kubernetes 및 CI/CD 확장을 고려하여 다음 원칙을 따른다.

- 환경별 설정은 가능한 한 외부에서 주입한다.
- 인증정보를 소스코드에 하드코딩하지 않는다.
- 애플리케이션 상태 확인은 Health Endpoint를 활용한다.
- 테스트와 이미지 빌드가 자동화 가능한 상태를 유지한다.
- DB 데이터는 애플리케이션 컨테이너와 독립적으로 관리한다.

Kubernetes 매니페스트, Container Registry 및 배포 자동화는
실제 도입 시점에 별도 Issue와 PR로 구현한다.
