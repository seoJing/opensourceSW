# 백엔드

Java 21과 Spring Boot 3.5로 만든 초기 애플리케이션입니다. 도메인 API는 없으며 Actuator의 `/actuator/health`만 외부에 노출합니다. Docker Compose 환경에서는 이 상태 확인에 PostgreSQL 연결 상태가 포함됩니다.

초기 구축 담당자는 패키지 구조·네이밍·공통 코드 사용법·테스트 작성법을 이 문서에 정리하고, 공통 진입점이나 설정이 바뀌면 `.github/harness-policy.json`의 감지 목록도 갱신합니다. 상세 인수인계와 공동 작업 규칙은 [공동 작업 가이드](../docs/collaboration.md)를 따릅니다.

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
./mvnw test
```

Windows PowerShell에서는 `./mvnw.cmd test`를 실행합니다. 테스트 프로필은 PostgreSQL 대신 메모리 H2를 사용합니다.
