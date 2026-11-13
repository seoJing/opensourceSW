# 백엔드

Java 21, Spring Boot 3.5, Maven 기반의 백엔드다.
Actuator health와 시연 계정의 세션 인증 API를 제공한다.
Docker Compose 실행 시 health에 PostgreSQL 연결 상태를 포함한다.

관련 규칙은 [기여 가이드](../CONTRIBUTING.md)와
[공동 작업 가이드](../docs/collaboration.md)를 따릅니다.
초기 구축 이력은 팀 원본의 [Issue #14](https://github.com/seoJing/opensourceSW/issues/14)와
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

## 인증 기능 (Issue #18)

회원가입·비밀번호 찾기·소셜 로그인·계정 DB 관리는 구현 범위에 포함하지 않는다.
작업 근거는 [Issue #18](https://github.com/seoJing/opensourceSW/issues/18)이다.
공통 실행 설정, 프론트 프록시, 전역 문서의 변경은 별도 담당자와 합의한다.

### 패키지 구조

`src/main/java/com/suwontrip/app/auth/`를 인증 기능의 경계로 사용한다.

```text
auth/
├── controller/   HTTP endpoint, 입력 검증 진입점, 예외 응답 변환
├── dto/          요청·응답 계약
├── service/      인증 처리 순서, 로그인 시도 제한
├── security/     Spring Security principal·filter·세션·CSRF 연동
├── config/       외부 설정 바인딩, 시간 공급, JSON 입력 정책
├── exception/    오류 코드와 인증 예외
└── support/      시연 계정 준비용 해시 CLI
```

- Controller는 HTTP 입출력만 담당하며 세션 저장이나 토큰 발급을 직접 수행하지 않는다.
- `AuthSessionService`는 계정 준비 확인 → 시도 제한 → 인증 → 세션 갱신 순서를 보장한다.
- `SessionSecurity`는 Servlet 세션과 Spring Security context·CSRF 저장소의 연동을 담당한다.
- 서비스는 DTO·설정·보안 어댑터·오류 계약에 의존하며, 하위 구성 요소가 Controller를 참조하지 않는다.
- 단일 환경설정 계정에 사용하지 않는 Repository·DB 모델·별도 인터페이스는 만들지 않는다.
- 테스트는 동일 책임 경로와 `integration`, `support`로 구분한다. 테스트 전용 endpoint는 프로덕션 소스에 포함하지 않는다.

### API

기본 확인 주소는 `http://localhost:8080`이다.

| 메서드 | 경로 | 동작 |
| --- | --- | --- |
| GET | `/api/v1/auth/session` | 인증 상태·공개 사용자 정보·CSRF 값 조회 |
| POST | `/api/v1/auth/session` | 시연 계정 인증, 세션 ID·CSRF 교체 |
| DELETE | `/api/v1/auth/session` | 인증 세션 폐기 후 비로그인 세션 준비 |
| GET | `/actuator/health` | 앱·DB 연결 상태 확인, 인증 불필요 |

로그인 본문은 `loginId`, `password` 두 필드만 허용한다. 필수 값·타입·알 수 없는 필드·중복 JSON 키를 검증한다.
본문은 JSON 객체 하나여야 하며, 뒤에 다른 JSON 값이나 잘못된 문자가 붙으면 400 `INVALID_REQUEST`로 거절한다. JSON 뒤의 공백·줄바꿈은 허용한다.
Jackson의 숫자·불리언 → 문자열 자동 변환도 허용하지 않는다.
ID는 공백만으로 구성할 수 없으며 Java String 길이 기준 최대 128자다. 비밀번호는 비어 있을 수 없고 UTF-8 기준 최대 72바이트다.
ID·비밀번호는 임의로 trim하거나 대소문자를 변경하지 않는다.

응답은 `authenticated`, `user`, `csrfToken`이다. 비로그인 `user`는 null, 로그인 `user`는 `id`, `displayName`만 포함한다.
principal·자격 증명·해시는 응답에 직렬화하지 않는다. 성공·인증 오류 응답은 `Cache-Control: no-store`를 사용한다.

#### 요청 순서

1. GET으로 세션 쿠키와 CSRF 값을 받는다.
2. 같은 쿠키와 `X-CSRF-TOKEN` 헤더, JSON 로그인 본문으로 POST한다.
3. 로그인 성공 응답의 새 CSRF 값을 이후 요청에 사용한다.
4. 같은 쿠키의 GET에서 로그인 상태가 유지되는지 확인한다.
5. 현재 CSRF 헤더를 포함한 DELETE로 로그아웃한다.

기존 인증 쿠키는 로그인·로그아웃에서 교체된다. Spring의 XOR 마스킹으로 인해 같은 세션의 조회 응답에서 CSRF 표시 값이 달라질 수 있다.
재로그인은 자격 증명을 다시 검증한다. 인증 실패 시 기존 세션 상태를 수정하지 않는다.
보호된 API의 비로그인·만료 요청은 CSRF 오류보다 401이 우선한다.

#### 오류 계약

`code`, `message`, `errors`를 반환하며 인증 오류의 `errors`는 빈 배열이다. 클라이언트는 메시지 문구 대신 code로 분기한다.

| HTTP | code | 조건 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | JSON 구조·타입·입력 제약 위반 |
| 401 | `INVALID_CREDENTIALS` | 계정 정보 오류. 계정 존재 여부를 구분하지 않음 |
| 401 | `AUTHENTICATION_REQUIRED` | 비로그인·만료된 보호 요청 |
| 403 | `CSRF_INVALID` | CSRF 누락·불일치 |
| 403 | `ACCESS_DENIED` | 일반 권한 거부 fallback |
| 429 | `TOO_MANY_ATTEMPTS` | 로그인 시도 제한, 정수 초 `Retry-After` 포함 |
| 500 | `INTERNAL_ERROR` | 예상하지 못한 세션 API 오류, 내부 상세는 응답에서 제외 |
| 503 | `SERVICE_UNAVAILABLE` | 시연 계정 미설정 또는 제한기 저장 용량 부족 |

예상하지 못한 세션 API 예외는 서버에 기록하고 500 `INTERNAL_ERROR`로 응답한다. 지원하지 않는 메서드·미디어 타입의 405/415 상태는 유지하며, 그 응답의 JSON·코드 계약은 후속 검토 대상이다.

### 실행 설정

백엔드 프로세스에 다음 네 환경변수를 함께 제공한다. 실제 값·원문 비밀번호·해시를 저장소나 Issue에 기록하지 않는다.

| 변수 | 용도 |
| --- | --- |
| `DEMO_USER_ID` | 재기동 후에도 동일하게 유지할 사용자 ID |
| `DEMO_DISPLAY_NAME` | 표시 이름 |
| `DEMO_LOGIN_ID` | 로그인 ID |
| `DEMO_PASSWORD_HASH` | BCrypt 해시 |

모두 비어 있으면 서버·health·세션 조회는 실행되며 로그인은 503이다. 일부만 설정하거나 해시 형식·cost가 잘못되면 시작 시 오류로 중단한다.

| 변수 | 기본값 | 범위·제약 |
| --- | --- | --- |
| `SESSION_TIMEOUT` | `30m` | 유휴 세션 만료 |
| `SESSION_COOKIE_SECURE` | `false` | 로컬 HTTP용. HTTPS 배포 정책은 별도 합의 |
| `AUTH_LOGIN_MAX_ATTEMPTS` | `5` | 양수. 성공·실패 모두 집계 |
| `AUTH_LOGIN_WINDOW` | `60s` | 1초~1시간의 고정 구간 |
| `AUTH_LOGIN_MAX_TRACKED_CLIENTS` | `1000` | 양수. 만료 기록을 회수해도 가득 차면 새 IP에 503 |

- 세션 쿠키는 HttpOnly·SameSite=Lax이며 URL에 세션 ID를 추가하지 않는다.
- 계정과 시도 제한·세션은 로컬 메모리 기준이다. 서버 재시작 시 세션·제한 이력이 사라지며 여러 replica가 공유하지 않는다.
- 신뢰할 프록시 범위가 정해지기 전에는 `X-Forwarded-For`를 시도 제한 키로 사용하지 않는다.
- 프록시 경유 요청은 프록시 IP의 제한을 공유할 수 있으므로 배포 전 실제 클라이언트 IP 처리와 공유 제한기를 검토한다.

#### 비밀번호 해시 준비

Java 21이 있는 macOS/Linux·WSL 환경의 `backend/`에서 실행한다. CLI도 Maven 표준 소스에 포함해 컴파일한다.

```sh
sh mvnw -B -ntp compile dependency:build-classpath -Dmdep.outputFile=/tmp/suwon-auth-classpath.txt
java --class-path "target/classes:$(cat /tmp/suwon-auth-classpath.txt)" com.suwontrip.app.auth.support.PasswordHashTool
```

대화형 터미널에서 원문 입력을 숨기며 기본 cost=10의 BCrypt 해시를 출력한다.
출력된 값을 실행 환경의 `DEMO_PASSWORD_HASH`로 주입한다. 셸·환경 파일에서는 `$`가 확장되지 않도록 인용한다.
이 도구는 계정을 저장·등록하지 않는다.

#### 기존 Compose에서 백엔드만 시연

현재 공통 Compose는 계정 변수를 자동으로 전달하지 않는다. 루트 `.env`에 값만 추가해도 백엔드 컨테이너에는 적용되지 않는다.
네 계정 변수를 현재 터미널에 export한 상태이고 기존 backend가 8080을 사용하지 않을 때, 저장소 루트에서 다음 명령으로 일회성 백엔드를 실행할 수 있다.

```sh
docker compose run --build --rm --service-ports \
  -e DEMO_USER_ID -e DEMO_DISPLAY_NAME -e DEMO_LOGIN_ID -e DEMO_PASSWORD_HASH backend
```

DB 의존성은 기존 Compose를 사용한다. 선택 설정을 바꾸려면 해당 환경변수도 명시적으로 전달한다.
현재 Nginx에는 `/api/v1` 프록시가 없으므로 인증은 8080의 직접 주소에서 확인한다.
프론트 통합 프록시·공통 환경 템플릿 반영은 해당 담당자의 후속 작업이다.

### 검증

```sh
sh mvnw -B -ntp clean verify
```

- `controller`: 입력·인증·CSRF·오류 계약과 공개 health
- `service`: 시도 제한, 인증 실패 시 세션 미변경, 처리 순서, 제한 시간·동시성·저장 한도
- `config`: 설정 경계와 비밀정보가 오류에 포함되지 않는지 확인
- `dto`: 자격 증명 문자열의 마스킹
- `integration`: 실제 Servlet 서버의 쿠키 속성·로그인 유지·세션 교체·로그아웃 후 재사용 거부

H2 기반 테스트와 실제 PostgreSQL을 연결한 backend 컨테이너 검증은 구분한다.
회원가입·설문·추천 endpoint는 검증을 위한 목적으로도 프로덕션에 추가하지 않는다.

### 의존성과 인수인계

- `spring-boot-starter-security`: 세션 인증·CSRF·비밀번호 검증. 별도 보안 처리를 직접 작성하는 대안 대신 Spring 기능 사용.
- `spring-boot-starter-validation`: 타입 DTO의 입력 제약. Controller의 수동 JSON 검사 대신 Bean Validation 사용.
- `spring-security-test`: 테스트 범위. 실제 쿠키 검증을 Mock 인증으로 대체하지 않음.
- 라이브러리 버전은 기존 Spring Boot BOM에서 관리한다. 빌드 도구·ORM·DB 마이그레이션은 변경하지 않는다.
- backend 보안·JSON 설정은 공통 동작에 영향을 준다. `.github/harness-policy.json`의 경로 등록은 저장소 담당자에게 인계하며 이번 변경에는 포함하지 않는다.
- 공통 Compose·Nginx·루트 문서·`docs/`는 수정하지 않는다. API 문서 반영과 팀 최종 승인은 별도 확인이 필요하다.
