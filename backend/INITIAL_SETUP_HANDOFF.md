# 백엔드 초기 구축 인수인계

기준일: 2026-11-13 (Asia/Seoul)

- Issue: [seoJing/opensourceSW #14](https://github.com/seoJing/opensourceSW/issues/14)
- 기존 PR: [seoJing/opensourceSW #15](https://github.com/seoJing/opensourceSW/pull/15)
- 작업 브랜치: `feature/backend/14-initial-setup`
- 이번 수정의 기준 커밋: `523e7af`

## 변경한 파일과 이유

| 파일 | 변경 내용 |
| --- | --- |
| `README.md` | 현재 제공 기능, 파일 배치, 기능 패키지 원칙, 호스트·Docker 검증, 전체 연결 확인과 협업 절차 보완. 비공식 마지막 문구 제거 |
| `src/main/java/com/suwontrip/app/SuwonTripApplication.java` | 기능과 관련 없는 Git·Docker 참고 주석 제거. 애플리케이션 실행 코드는 유지 |
| `mvnw` | 파일 내용은 그대로 두고 실행 권한을 `100644`에서 `100755`로 변경. macOS/Linux의 `./mvnw` 실행 오류 해결 |
| `INITIAL_SETUP_HANDOFF.md` | 이번 검증 결과와 미완료 항목 기록 |

기존 `HealthEndpointTests`와 `SuwonTripApplicationTests`는 수정하지 않았다.
새 의존성, 업무 API, DB 스키마, Docker 실행 설정은 추가하지 않았다.

## 이번 수정 후 실행한 검증

| 위치 | 명령 | 결과 |
| --- | --- | --- |
| `backend/` | `./mvnw -B -ntp verify` | 성공. 테스트 2개, 실패·오류·건너뜀 0개. 실행 가능한 JAR 패키징 성공 |
| 저장소 루트 | `node --test scripts/repo-harness.test.mjs` | 10개 통과 |
| 저장소 루트 | `node scripts/repo-harness.mjs` | Repository Guard 통과 |
| 저장소 루트 | `git diff --check` | 통과 |
| 저장소 루트 | `docker compose config --quiet` | 통과 |
| 저장소 루트 | `docker info --format '{{.ServerVersion}}'` | 실패. Docker Desktop Linux Engine 파이프에 연결할 수 없음 |

Java lint 도구는 현재 저장소에 설정되어 있지 않다.
백엔드 테스트는 H2를 사용하므로 실제 PostgreSQL 연결 검증을 대신하지 않는다.

## 확인하지 못한 항목

- Docker 데몬 연결 실패로 이번 수정 후 이미지 빌드와 세 서비스 health를 확인하지 못했다.
- 같은 이유로 실제 프론트엔드 HTTP 응답과 `/api/health`의 PostgreSQL 연결 상태를 확인하지 못했다.
- PowerShell 실행은 이 Linux 셸에서 검증하지 않았다.
- 이번 변경은 로컬 파일에만 적용했다. 커밋·push·PR 본문 수정·리뷰 요청·병합은 수행하지 않았다.
- 기존 커밋 `523e7af`의 CI 성공은 확인했지만, 이번 로컬 변경의 CI 결과는 아직 없다.

## 공통 파일 변경과 리뷰 시 확인 사항

`mvnw`는 `.github/harness-policy.json`에 등록된 공통 파일이다.
수정 이유는 저장소의 표준 Maven 명령을 실행할 때 발생하는 `Permission denied`를 해결하기 위해서다.
Wrapper 내용이나 Maven 버전, Windows Wrapper, 서비스 실행 구성은 바꾸지 않았다.
대안인 `sh mvnw`는 실행 가능하지만 기존 문서의 `./mvnw` 명령을 그대로 사용할 수 없어 실행 권한 수정을 선택했다.
실행 권한 변경 후 `./mvnw -B -ntp verify`로 검증했다.

Issue #14에는 이 변경의 구체적인 팀 합의 기록이 아직 확인되지 않았다.
PR 갱신 전 이유·영향·검증 결과를 Issue에서 공유하고 합의 여부를 확인해야 한다.
기존 PR의 공통 설정 합의 체크만으로 이번 변경까지 승인됐다고 간주하지 않는다.
진입점은 참고 주석을 제거한 결과 현재 `origin/main`과 같은 내용이다.
공통 파일 감지 목록에 새로 등록할 경로나 설정 파일은 없다.

PR #15는 작업 시작 시 열림·미병합 상태였으며 리뷰어 지정과 리뷰 제출은 없었다.
최소 1명의 리뷰와 아래 통합 검증을 마치기 전에는 Issue #14 전체 완료로 처리하지 않는다.

## Docker 사용 가능 환경에서 남은 검증

저장소 루트에서 순서대로 실행한다.

```sh
docker build --target test -t suwon-trip-backend-test:local backend
sh scripts/start.sh
docker compose ps
curl --fail --silent --show-error http://localhost:3000/
curl --fail --silent --show-error http://localhost:3000/api/health
sh scripts/stop.sh
```

`db`, `backend`, `frontend`가 모두 healthy인지 확인한다.
프록시 JSON에서 `status`와 `components.db.status`가 모두 `UP`이어야 한다.
중간 명령이 실패하면 로그를 확인해 원인을 해결하고 성공으로 기록하지 않는다.
종료 시 DB named volume은 유지한다.
