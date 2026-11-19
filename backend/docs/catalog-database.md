# 카탈로그 PostgreSQL 적재·조회

관련 작업: #24 · 선행 로더: #22 / PR #23 · Docker 파일 연결: #29 / PR #30

## 저장 구조

`catalog.json → CatalogLoader 검증 → CatalogImporter 트랜잭션 → PostgreSQL → CatalogRepository 조회`

JSON은 배포·재적재 원본이며, 실행 중 조회의 기준은 PostgreSQL입니다. 전체 카탈로그를 Bean으로 유지하지 않습니다. 마이그레이션은 `src/main/resources/db/migration/V1__create_catalog.sql`에 있습니다.

| 테이블 | 저장 내용 |
| --- | --- |
| `catalog_dataset` | 자료 버전, 입력 스키마 버전, 원본 SHA-256, 적재 시각, 카페·메뉴 수 |
| `catalog_cafe` | 버전별 카페 ID·이름·주소·좌표·출처 URL·외부 ID·조회일 |
| `catalog_menu` | 버전별 메뉴 ID·카페 ID·순서·이름·설명·가격·판매 상태·확인 근거·출처 |
| `catalog_active` | 현재 조회할 버전. 단일 행의 잠금으로 동시 적재를 직렬화합니다. |
| `flyway_schema_history` | 적용한 DB 스키마 마이그레이션 이력 |

카페·메뉴의 키는 `(data_version, ID)`이며 메뉴의 카페 참조에 외래키를 적용합니다. 모르는 가격·위치·설명은 `NULL`, 판매 미확인은 `UNKNOWN` 그대로입니다. 실시간 재고·재료·옵션·추천 가중치를 새로 만들지 않습니다.

기존 Spring JDBC로 SQL을 실행합니다. Flyway는 빈 DB와 이후 스키마 변경을 동일 순서로 재현하기 위해 추가합니다. 수동 SQL이나 `schema.sql` 재실행보다 적용 이력·체크섬을 관리하기에 적합합니다. PostgreSQL 지원에는 별도 Flyway 모듈을 사용합니다. Boot 기본 Flyway 11.7.2는 PostgreSQL 18에 미검증 경고를 내므로, 18을 지원하는 11.x 계열의 11.20.3으로 고정했습니다. 메이저 업그레이드는 하지 않습니다. [Spring Boot 공식 안내](https://docs.spring.io/spring-boot/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway)

## 적재와 버전 전환

- 기본 실행은 테이블만 준비하고 파일을 읽지 않습니다. 빈 DB에서는 활성 버전이 없습니다.
- `APP_CATALOG_ENABLED=true`, `APP_CATALOG_PATH`를 설정하면 시작 시 적재합니다. JSON의 검증과 SHA-256 계산은 같은 바이트에 적용합니다.
- 새 버전은 카페·메뉴 저장과 활성 버전 전환을 한 트랜잭션으로 처리합니다. SQL 실패 시 신규 데이터가 남지 않으며 이전 활성 버전도 유지됩니다. 시작 실패 원인을 수정한 뒤 다시 실행합니다.
- 같은 버전·같은 SHA-256은 중복 적재하지 않고 해당 버전을 활성화합니다. 같은 버전·다른 SHA-256은 거부합니다. 줄바꿈 수정도 해시 변경에 해당합니다.
- 이전 버전은 보존합니다. 이전 원본으로 재실행하면 해당 버전으로 되돌립니다. 자동 정리 작업은 없습니다.
- 적재 후 파일 연결을 해제하고 `APP_CATALOG_ENABLED=false`로 재시작해도 저장 데이터는 유지됩니다. 여러 앱 인스턴스를 함께 운영한다면 모두 같은 버전으로 맞춰야 합니다. 이전 파일을 가진 앱의 재시작도 해당 버전을 다시 활성화하기 때문입니다.

`CatalogRepository`는 활성 버전의 카페·메뉴 ID 조회와 카페별 메뉴 페이지 조회를 제공합니다. 메뉴 페이지 크기는 1~100입니다. 각 조회 SQL은 활성 버전과 JOIN하므로 한 조회에서 버전을 섞지 않습니다. 여러 호출 사이에 활성 버전이 바뀔 수 있으므로 향후 복합 조회 서비스에는 읽기 트랜잭션 또는 버전 고정 계약이 필요합니다. HTTP API는 이번 작업 범위가 아닙니다.

## Docker에서 시작·확인

PR #23·#24 구현과 #29의 `backend/compose.catalog.yml`이 모두 포함된 코드에서 저장소 루트 기준으로 실행합니다. 실제 고정 입력은 PR #30의 `backend/data/catalog/haenggung-2026-11-18.2/`에 포함합니다. 별도 파일 전달·재수집 없이 저장소 안의 JSON을 기본으로 사용합니다.

```sh
docker compose -f docker-compose.yml -f backend/compose.catalog.yml config --quiet
docker compose -f docker-compose.yml -f backend/compose.catalog.yml up --build -d --wait
```

Windows PowerShell에서도 같은 Docker 명령을 사용합니다. 다른 버전만 `CATALOG_FILE` 절대 경로로 지정합니다. 이전 셸·`.env`의 경로를 해제하면 저장소 기본 입력을 사용합니다. 로그의 `Catalog stored in database`와 실제 DB를 함께 확인합니다.

```sh
docker compose exec -T db psql -U suwon_app -d suwon_trip -c "SELECT data_version, cafe_count, menu_count, source_sha256 FROM catalog_dataset JOIN catalog_active USING (data_version);"
docker compose exec -T db psql -U suwon_app -d suwon_trip -c "SELECT COUNT(*) FROM catalog_cafe c JOIN catalog_active a USING (data_version); SELECT COUNT(*) FROM catalog_menu m JOIN catalog_active a USING (data_version);"
docker compose exec -T db psql -U suwon_app -d suwon_trip -c "SELECT cafe_id, menu_id, name, price_krw, availability FROM catalog_menu JOIN catalog_active USING (data_version) ORDER BY ordinal LIMIT 10;"
```

DB 사용자·이름을 변경했다면 `-U`, `-d`도 바꿉니다. 적재한 다음 원본 없이 실행하는 확인은 아래와 같습니다. 기본 Compose에는 파일 마운트·적재 설정이 없습니다.

```sh
docker compose -f docker-compose.yml up -d --force-recreate --wait
```

기본 Compose의 named volume `suwon_trip_postgres_data`가 PostgreSQL 데이터를 보관합니다. 이 볼륨 이름은 저장소의 기존 설정이며 다른 작업 폴더도 공유하므로 동시에 서로 다른 버전을 띄우지 않습니다. `down`·컨테이너 재생성은 데이터를 지우지 않지만 `down -v`는 삭제합니다. Docker 볼륨은 PC마다 따로 있으므로 다른 PC 최초 실행에는 저장소의 고정 입력으로 적재하는 과정이 필요합니다.

## 검증 명령

기본 테스트·패키징은 `backend/`에서 실행합니다.

```sh
sh mvnw -B -ntp verify
```

실제 PostgreSQL 테스트는 사용하지 않는 호스트 포트를 골라 테스트 전용 DB를 띄운 뒤 실행합니다. 아래 계정은 테스트 컨테이너에만 사용하는 공개 예시 값입니다. 업무 DB를 지정하지 않습니다. 테스트마다 고유 스키마를 만들고 해당 스키마만 제거합니다.

```sh
docker run -d --name opensw-catalog-postgres-test -p 127.0.0.1:15432:5432 \
  -e POSTGRES_DB=catalog_test -e POSTGRES_USER=catalog_test -e POSTGRES_PASSWORD=catalog_test \
  postgres:18.6-alpine3.24
docker exec opensw-catalog-postgres-test pg_isready -U catalog_test -d catalog_test
sh mvnw -B -ntp -Ppostgres verify \
  -Dcatalog.test.jdbc-url=jdbc:postgresql://127.0.0.1:15432/catalog_test
```

`pg_isready`가 아직 준비되지 않았으면 준비된 뒤 Maven을 실행합니다. 주소가 없거나 연결 실패 시 PostgreSQL 테스트를 건너뛰지 않고 실패합니다. 기본 프로필의 H2 검증과 실제 PostgreSQL 검증을 구분합니다. 완료 뒤 테스트 전용 컨테이너만 제거합니다.

```sh
docker rm -fv opensw-catalog-postgres-test
```

CI 기본 Backend Test는 H2 테스트를 실행합니다. 실제 PostgreSQL 통합 테스트는 위 별도 프로필로 실행하며, 기본 CI가 해당 프로필까지 실행한다고 주장하지 않습니다. 다른 PC에서의 실행은 설정과 안내를 제공한 범위이며 그 PC에서 직접 검증한 결과는 아닙니다.

## 2026-11-18 로컬 검증 결과

- Maven 기본 테스트 110개, 실제 PostgreSQL 18.6 통합 테스트 14개: 실패·오류·건너뜀 0개. Docker 이미지 내부 기본 테스트도 110개 통과했습니다.
- 실제 원본 `haenggung-2026-11-18.2`를 Spring Boot 시작 실행기로 적재했습니다. 활성 버전은 카페 320곳·메뉴 5,011건입니다.
- DB를 JSON으로 추출하여 원본의 모든 카페·메뉴 필드·순서·출처·날짜와 SHA-256을 대조했습니다. 미확인 가격 227건도 NULL로 보존했습니다.
- 파일 연결 없이 기본 Compose로 DB·백엔드·프론트 컨테이너를 재생성했습니다. DB 컨테이너 ID가 바뀌었고 `suwon_trip_postgres_data` 볼륨은 동일했습니다. 모든 행·활성 버전·원본 해시·적재 시각이 유지됐습니다.
- 재생성된 백엔드에는 JSON 마운트와 `APP_CATALOG_*` 설정이 없습니다. 세 서비스 healthy, 프론트 HTML·프론트 경유 `/api/health`·백엔드 health 응답을 확인했습니다.
- 인증·설문 PR은 별도 브랜치이므로 이 검증을 전체 기능 통합 완료로 해석하지 않습니다. 다른 사용자의 PC에서 직접 실행한 결과도 아닙니다.
