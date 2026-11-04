# 초기 아키텍처

## 요청 흐름

```text
브라우저
  │ http://localhost:3000
  ▼
frontend (Nginx :80)
  │ /api/health → /actuator/health
  ▼
backend (Spring Boot :8080)
  │ JDBC: jdbc:postgresql://db:5432/suwon_trip
  ▼
db (PostgreSQL :5432, Compose 내부 전용)
```

호스트에는 프론트엔드 `3000`번 포트와 백엔드 `8080`번 포트만 공개합니다. 백엔드는 Compose 기본 네트워크에서 서비스 이름 `db`로 PostgreSQL에 접속합니다. CORS 설정 대신 Nginx가 같은 origin의 `/api/health` 요청을 프록시합니다.

## 데이터 보존

PostgreSQL 데이터는 `suwon_trip_postgres_data` named volume에 저장됩니다. `docker compose down`은 컨테이너와 네트워크만 정리하고 볼륨은 보존합니다. 데이터를 지우는 명령은 문서와 자동화 스크립트에서 실행하지 않습니다.

## 현재 범위

현재 백엔드는 Actuator health endpoint만 제공하고, 프론트엔드는 실행 상태만 표시합니다. POI, 추천, 성향 테스트, 지도, 인증, 외부 API, AI 연동, 운영 배포와 모니터링은 설계하거나 구현하지 않았습니다.

현재 배포 검증 대상은 로컬 Docker 시연입니다. CI가 이미지 빌드와 세 서비스 연결을 검증하며 외부 서버에 배포하지 않습니다. 공동 작업 하네스와 시연 점검 절차는 [공동 작업 가이드](collaboration.md)를 참고하세요.
