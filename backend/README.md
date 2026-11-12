## 프로젝트 구조와 개발 규칙

### 기술 스택

- Java 21
- Spring Boot 3.5
- Apache Maven
- PostgreSQL (개발 및 실행 환경)
- H2 (자동 테스트 환경)
- JUnit 5 / Spring Boot Test / MockMvc

### 패키지 구성 원칙

기본 패키지는 `com.suwontrip.app`을 사용한다.

업무 기능이 시작되면 기능 단위로 코드를 구성하며,
사용하지 않는 Controller, Service, Repository를 미리 생성하지 않는다.

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

### Maven 검증

프로젝트 루트에서 Docker 기반 테스트:

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

다 읽었으면 빨랑빨랑 움직이라구~