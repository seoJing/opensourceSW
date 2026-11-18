# Docker Compose에서 저장소 카탈로그 사용

관련 작업: [Issue #29](https://github.com/seoJing/opensourceSW/issues/29) · 선행 코드: [PR #23](https://github.com/seoJing/opensourceSW/pull/23), [PR #31](https://github.com/seoJing/opensourceSW/pull/31)

처음 환경을 받는 팀원은 [작업 착수 안내 HTML](team-getting-started.html)을 따른다. 아래 절차는 #23 → #31 → #30이 main에 병합된 뒤 사용할 수 있다. 병합 전 main만으로는 실제 카탈로그를 적재할 수 없다.

## 포함한 고정 데이터

`backend/data/catalog/haenggung-2026-11-18.2/`의 `catalog.json`, `manifest.json`, `SHA256SUMS`를 코드와 함께 관리한다. 압축파일 별도 전달이나 재수집은 필요하지 않다. JSON은 약 2.6MB이며 동일 내용을 압축파일로 중복 커밋하지 않는다. 현재 데이터는 카페 320곳·메뉴 5,011건이다.

상호·주소·좌표는 NAVER API HUB 지역 검색과 지도 목록 대조, 메뉴명·표시 가격·설명은 공개 지도 메뉴 페이지의 구조화 데이터에서 확인했다. 관찰일은 2026-11-18이며 항목별 출처 URL·외부 ID·관찰일을 JSON에 보존한다. 원문 수집 로그·스크립트·자격 증명·세션은 포함하지 않는다. API 호출이나 LLM 없이 기존 자료를 재현한다.

고정 입력의 스키마는 `schemaVersion=1`이다. `manifest.json`은 수집 범위·누락값·해시를 기록한다. 메뉴 4,971건은 `UNKNOWN`, 40건은 `SEASON_ENDED`이며 가격 227건·설명 2,275건은 미확인이다. 전체 업소의 완전한 명부나 전체 메뉴의 추천 적합성은 보장하지 않는다. 원본을 같은 버전으로 재저장하거나 줄바꿈을 바꾸지 않는다. 다른 데이터는 새 `dataVersion`을 사용한다.

## 실행

Git과 Docker Engine/Compose 또는 Docker Desktop이 필요하다. 호스트에 Java·Maven·PostgreSQL을 설치하지 않아도 된다. 저장소 루트에서 실행하고 3000·8080 포트를 사용할 수 있어야 한다. 한 PC의 여러 체크아웃은 같은 포트·기존 DB 볼륨을 공유하므로 한 세트만 실행한다.

확장 Compose의 기본 입력은 저장소 상대 경로다. `CATALOG_FILE`을 이전에 설정했다면 셸과 `.env`에서 제거해야 기본 파일을 사용한다. WSL·Linux에서는 `unset CATALOG_FILE`, PowerShell에서는 `Remove-Item Env:CATALOG_FILE -ErrorAction SilentlyContinue`로 셸 변수를 제거할 수 있다.

```sh
docker compose -p opensourcesw -f docker-compose.yml -f backend/compose.catalog.yml config --quiet
docker compose -p opensourcesw -f docker-compose.yml -f backend/compose.catalog.yml up --build -d --wait
docker compose -p opensourcesw ps
docker compose -p opensourcesw logs --tail=100 backend
```

설정 검사나 실행이 실패하면 다음 단계로 진행하지 않는다. PowerShell 명령별 오류 확인 예시는 HTML 안내서에 있다. `CATALOG_FILE`을 지정하지 않아도 기본 파일을 사용하며, 지정한 파일이 없으면 디렉터리로 자동 생성하지 않고 실패한다. 파일은 `/app/data/catalog.json`에 읽기 전용으로 연결된다. 적재 성공 뒤 조회의 기준은 PostgreSQL이며 전체 카탈로그를 메모리 Bean으로 유지하지 않는다.

최초 적재 로그는 `Catalog stored in database: cafes=320, menus=5011, inserted=true`다. 같은 버전·같은 바이트를 다시 읽으면 `inserted=false`이며 중복 저장하지 않는다. 같은 버전의 다른 바이트는 거부한다. 수량은 전달된 manifest와 대조하며 애플리케이션에 고정된 값이 아니다.

```sh
docker compose -p opensourcesw exec -T db psql -U suwon_app -d suwon_trip -c "SELECT data_version, cafe_count, menu_count, source_sha256 FROM catalog_dataset JOIN catalog_active USING (data_version);"
docker compose -p opensourcesw exec -T db psql -U suwon_app -d suwon_trip -c "SELECT COUNT(*) FROM catalog_cafe JOIN catalog_active USING (data_version); SELECT COUNT(*) FROM catalog_menu JOIN catalog_active USING (data_version);"
```

DB 사용자·이름을 바꿨다면 `-U`, `-d`도 맞춘다. 브라우저에서 `http://localhost:3000/api/health`의 최상위 status와 components.db.status가 모두 UP인지 확인한다. 기본 화면은 상태 확인용이며 카탈로그 HTTP API·추천 화면·프론트 `/api/v1` 프록시는 별도 작업이다.

## 다른 버전의 파일 사용

저장소 기본 입력 대신 다른 파일을 사용할 때만 `CATALOG_FILE`에 절대 경로를 지정한다. WSL에서는 WSL 경로, PowerShell에서는 Windows 경로를 사용한다. 파일과 상위 폴더는 컨테이너의 비루트 사용자가 읽을 수 있어야 한다.

```sh
export CATALOG_FILE="/absolute/path/new-version/catalog.json"
```

```powershell
$catalogInput = Get-Item -LiteralPath 'C:\path\new-version\catalog.json' -ErrorAction Stop
if ($catalogInput.PSIsContainer) { throw 'catalog.json 파일을 지정하세요.' }
$env:CATALOG_FILE = $catalogInput.FullName
```

새 버전은 해시와 스키마를 검증한 뒤 설정을 검사하고 백엔드를 다시 생성한다. 단순 restart는 바뀐 파일 경로를 적용하지 않는다. 이전 버전으로 전환할 때도 해당 원본을 지정하고 같은 절차를 사용한다. DB의 이전 버전은 보존한다.

```sh
docker compose -p opensourcesw -f docker-compose.yml -f backend/compose.catalog.yml config --quiet
docker compose -p opensourcesw -f docker-compose.yml -f backend/compose.catalog.yml up -d --no-deps --force-recreate --wait backend
```

## 적재 후 일반 실행과 종료

적재 후 JSON 없이 DB만 사용하는 실행은 아래와 같다. 기본 Compose로 컨테이너를 다시 생성하면 파일 연결과 적재 환경변수가 제거된다. 명령마다 같은 프로젝트 이름 `opensourcesw`를 유지한다.

```sh
docker compose -p opensourcesw -f docker-compose.yml up -d --force-recreate --wait
docker compose -p opensourcesw -f docker-compose.yml down
```

일반 down은 DB named volume `suwon_trip_postgres_data`를 보존한다. `down -v`는 사용하지 않는다. 다른 PC에는 별도의 볼륨이 있으므로 그 PC 최초 실행은 확장 Compose로 적재한다. 기본 `docker compose up`은 빈 DB에 카탈로그를 자동 적재하지 않는다.

## 검증 범위

기존 통합 로컬 검증에서 이미지 빌드·카페 320곳·메뉴 5,011건 적재·원본 전체 필드 대조·JSON 없는 재생성 후 데이터 보존과 세 서비스 health를 확인했다. 저장소 기본 입력으로 바꾼 이번 검증은 PR #30의 검증 결과에 기록한다. 다른 팀원 PC 자체의 실행은 아직 확인하지 않았다. 기본 CI smoke는 선택형 카탈로그 적재를 실행하지 않는다.
