# 백엔드

Java 21과 Spring Boot 3.5 기반 백엔드다. Actuator health와 시연 계정의 세션 인증 API를 제공한다. Docker Compose 실행 시 health에 PostgreSQL 연결 상태를 포함한다.

## Docker로 검증

저장소 루트에서 다음 명령을 실행합니다.

```sh
docker build --target test -t suwon-trip-backend-test:local backend
docker build -t suwon-trip-backend:local backend
```

## 호스트에서 테스트

Java 21이 설치되어 있다면 Maven Wrapper를 사용할 수 있습니다.

```sh
cd backend
sh mvnw test
```

Windows PowerShell에서는 `./mvnw.cmd test`를 실행합니다. 테스트 프로필은 PostgreSQL 대신 메모리 H2를 사용합니다.

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
