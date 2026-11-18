# 로컬 LLM 메뉴 맛 수치 생성·DB 적재

관련 Issue #32 · 선행 PR #31 · 아직 추천 HTTP API나 설문 수치 변환을 구현한 것은 아닙니다.

## 실행 위치와 의존성

모델은 PC의 Ollama에서 실행하고, Python 3.10 이상 표준 라이브러리만 사용하는 `backend/tools/taste-profiling/taste_batch.py`가 로컬 API를 호출합니다. 외부 유료 API·키·앱의 추가 컨테이너는 필요하지 않습니다. Ollama·Qwen3는 서버 앱 의존성이 아니라 자료 준비 도구입니다. 다른 PC는 생성 묶음을 전달받아 적재할 수 있으며 모델을 다시 설치할 필요가 없습니다.

공식 [Ollama 설치 안내](https://docs.ollama.com/linux), [Qwen3 8B](https://ollama.com/library/qwen3:8b), [JSON 스키마 출력](https://docs.ollama.com/capabilities/structured-outputs)을 참고합니다. 모델은 표본 추론의 메모리·시간·출력 품질을 확인한 뒤 사용합니다. Qwen3 8B Q4_K_M 다운로드는 약 5.2GB이며 실제 추론에는 추가 메모리가 필요합니다. 모델 digest로 실제 사용한 파일을 식별합니다.

## 맛 기준 taste-v1

사용자 확인에 따라 단맛 `sweetness`, 신맛 `sourness`, 쓴맛 `bitterness` 세 축을 사용합니다. 모두 0~1 맛 강도이며 중요도·확률·당도 측정값이 아닙니다. 0=없음/미미, 0.25=약함, 0.5=중간, 0.75=강함, 1=매우 강함을 MVP의 상대적 기준으로 사용하고 프롬프트에 명시합니다. 기준을 바꾸면 정의·프롬프트·프로필 버전을 새로 부여합니다. 실제 설문의 선택지를 이 값으로 바꾸는 규칙은 별도 작업입니다.

- `ESTIMATED`: 이름·설명 또는 일반적인 메뉴 종류에서 추정 가능한 세 값이 있습니다. 매장별 레시피를 보장하지 않습니다.
- `UNKNOWN`: 모호한 이름·불충분한 정보로 판단할 수 없습니다. 세 값 전체를 null로 유지합니다.
- `NOT_MENU`: 안내·굿즈·입장권·주류 등 이번 추천 입력에서 제외할 항목입니다. 값은 null입니다. 식품 선물세트처럼 경계에 있는 항목은 검토가 필요합니다.

설명이 없어도 일반적인 메뉴명은 추정할 수 있습니다. 입력은 `NAME_ONLY` / `NAME_AND_DESCRIPTION`으로 구분합니다. LLM이 생성한 reason은 모델의 설명이며 검증된 출처나 자신감 점수로 취급하지 않습니다. 원본 `catalog_menu`의 설명·판매 상태·가격은 수정하지 않습니다. 가격·인테리어는 입력과 점수에서 제외합니다.

## 생성

아래 명령은 저장소 루트의 Bash 기준이며, Ollama 실행 파일이 PATH에 있어야 합니다. 다른 터미널에서 로컬 서버를 먼저 실행합니다. 이미 Ollama가 실행 중이면 중복 실행하지 않습니다.

```sh
OLLAMA_HOST=127.0.0.1:11434 ollama serve
```

```sh
ollama pull qwen3:8b
python3 backend/tools/taste-profiling/taste_batch.py \
  --catalog /absolute/path/catalog.json \
  --output /absolute/path/taste-sample-v1 \
  --profile-version taste-sample-v1 \
  --limit 20
```

기본 limit은 20개입니다. 전체 5,011건을 생성하려면 표본 결과를 먼저 검토한 뒤 새 output·profile-version으로 `--limit 5011`을 명시합니다. 특정 메뉴 표본을 고를 때는 메뉴 ID를 한 줄씩 적은 파일을 `--ids /path/ids.txt`로 지정합니다. `--limit`은 이번 대상 목록의 앞 N개를 뜻하며, 재시작 때마다 N개를 추가하는 옵션이 아닙니다.

출력 폴더에는 `run.json`, `prompt.txt`, `estimates.jsonl`, 완료 시 `bundle.json`이 생깁니다. `run.json`에는 원본 해시·모델 digest·Ollama 버전·프롬프트 및 응답 스키마 해시·실행 옵션을 기록합니다. `bundle.json`은 이력 정보와 생성 레코드를 담는 내부 적재 형식이며 프론트 계약이 아닙니다. 실제 생성 결과와 데이터는 Git에 추가하지 않습니다.

같은 명령으로 재실행하면 완료된 항목은 재호출하지 않습니다. 원본·모델·프롬프트·옵션이 달라지면 같은 폴더를 재사용하지 못합니다. 실패 시 비정상 종료하고 체크포인트를 보존합니다. 손상된 줄을 조용히 버리거나 실패를 0점으로 대체하지 않습니다. 강제 종료로 `.generation.lock`이 남으면 실행 중인 생성 프로세스가 없는지 먼저 확인하고 해당 잠금 파일만 제거합니다. 같은 폴더에 두 프로세스를 실행하지 않습니다.

프로필 버전은 DB 적재 후 불변입니다. 일부 묶음을 먼저 적재한 뒤 나중에 같은 버전으로 늘리지 않습니다. 전체 생성을 재개할 경우 완료 후 적재하거나 새 프로필 버전을 사용합니다. `bundle.json`이 존재하더라도 실패한 실행 직전의 이전 결과일 수 있으므로 명령의 종료 코드와 기록 건수를 확인합니다.

## PostgreSQL 적재

V1 카탈로그는 그대로 유지하고 V2로 아래 테이블을 추가합니다.

| 테이블 | 내용 |
| --- | --- |
| `taste_profile_run` | 프로필·카탈로그 버전, 원본·묶음·프롬프트 해시, 모델 digest, 건수·적재 시각 |
| `menu_taste_profile` | 메뉴 ID, 입력 해시·근거, 상태, 세 맛 수치, 생성 시각·이유, 검토 상태 |

명시적인 `app.taste.import-path` 설정이 있을 때만 시작 실행기가 묶음을 적재합니다. 모델 서버에 접속하지 않습니다. `APP_TASTE_IMPORT_PATH` 환경변수도 사용할 수 있습니다. 선행 카탈로그 버전과 원본 해시, 메뉴별 입력 해시를 대조하고 전체를 한 트랜잭션으로 저장합니다. 같은 버전·같은 묶음은 중복 저장하지 않으며 같은 버전의 다른 묶음은 거부합니다. 정상 기본 실행은 모델·맛 수치 파일을 요구하지 않습니다.

현재 Compose에 backend 이미지가 준비되어 있고 db가 실행 중일 때, 실제 데이터 파일을 읽을 수 있도록 아래처럼 일회성 적재 컨테이너를 실행합니다. 아래 `backend` 이미지는 #32 코드를 빌드한 것이어야 합니다.

```sh
docker compose -p opensourcesw build backend
docker compose -p opensourcesw up -d --wait db
docker compose -p opensourcesw run --rm --no-deps \
  -v /absolute/path/bundle.json:/app/data/taste-bundle.json:ro \
  -e APP_TASTE_IMPORT_PATH=/app/data/taste-bundle.json \
  -e SPRING_MAIN_WEB_APPLICATION_TYPE=none backend
```

`run`은 임시 backend 실행 인스턴스이며 상시 서비스·LLM 컨테이너를 추가하지 않습니다. Spring Boot 비웹 실행기는 적재 후 프로세스가 종료됩니다. 파일 변경 시 새 프로필 버전을 사용합니다. `down -v`는 DB 데이터를 지우므로 사용하지 않습니다.

전부 `UNREVIEWED`로 저장하며 자동 승인하지 않습니다. 표본 추론·기술 검증과 실제 맛 검증은 구분합니다. 이번 구현에는 승인 API나 추천 후보 활성화가 없습니다. 미래 추천에서는 사용할 프로필 버전과 검토 정책을 명시해야 합니다. 저장된 UNKNOWN을 숫자로 바꾸거나 미검토 추정치를 검증된 사실로 안내하지 않습니다.

```sql
SELECT profile_version, model, model_digest, record_count FROM taste_profile_run;
SELECT status, input_basis, review_status, COUNT(*)
FROM menu_taste_profile GROUP BY status, input_basis, review_status;
SELECT m.name, p.sweetness, p.sourness, p.bitterness, p.status, p.reason
FROM menu_taste_profile p
JOIN catalog_menu m ON m.data_version=p.data_version AND m.menu_id=p.menu_id
WHERE p.profile_version='taste-sample-v1';
```

`TasteProfileRepository.find(profileVersion, menuId)`는 활성 카탈로그에 속한 해당 프로필만 조회합니다. 추천 점수 계산과 순위 API는 별도입니다. 모델을 끄고 원본 파일을 제거해도 저장된 DB 조회에는 영향이 없습니다.

## 테스트

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s backend/tools/taste-profiling -p 'test_*.py'
cd backend
sh mvnw -B -ntp verify
sh mvnw -B -ntp -Ppostgres verify -Dcatalog.test.jdbc-url=jdbc:postgresql://127.0.0.1:15432/catalog_test
```

마지막 명령은 테스트 전용 PostgreSQL이 필요합니다. 설정과 계정은 기존 `catalog-database.md`를 따릅니다. H2·실제 PostgreSQL에서 미확인 값·형식·해시 충돌·외래키·범위·롤백·중복 방지를 확인합니다. Python 테스트는 네트워크 오류·불완전 응답·중단 후 재개·원본 변경·손상 체크포인트를 검사합니다. 기본 Backend CI는 Maven 테스트만 실행하므로 Python·실제 모델·PostgreSQL 프로필까지 자동 실행했다고 주장하지 않습니다.

## 2026-11-18 실제 표본 검증

- Ollama 0.40.16·Qwen3 8B Q4_K_M을 로컬 RTX 3060 Ti 8GB에서 실행했습니다. 사용자 폴더 설치이며 배포 archive의 공식 SHA-256과 모델 digest를 확인했습니다.
- 실제 메뉴 24건(설명 포함 8건·이름만 16건)을 선택했습니다. 첫 프롬프트는 카페라떼에 설탕을 가정하고 캐모마일 쓴맛을 과대 평가해 DB에 적재하지 않았습니다.
- 상대 기준 메뉴 예시를 프롬프트에 추가한 새 프로필 `haenggung-qwen3-2026-11-18.2`로 다시 생성했습니다. 24건 합계 약 32.45초, 항목별 중앙값 1.38초였습니다. 첫 모델 로딩·CUDA 초기화 시간과 다운로드는 이 수치에 포함하지 않습니다. 전체 데이터 실행 시간 보장은 아닙니다.
- 새 결과는 ESTIMATED 20건·UNKNOWN 1건·NOT_MENU 3건입니다. 기술 검증 후 실제 PostgreSQL에 24건을 저장하고 모두 UNREVIEWED로 유지했습니다. 재적재 시 inserted=false로 중복이 없었습니다.
- 상대 기준 예시로 일부 수치가 개선됐으나 모델의 설명에 입력에 없는 코코아를 언급하거나 한국어·외국어가 섞이는 오류가 남았습니다. 에이드류를 NOT_MENU, 하이볼을 UNKNOWN으로 분류한 사례도 있습니다. 모델의 상태 분류를 최종 후보 정책으로 바로 사용하지 않습니다.
- 따라서 모델 설치·배치·검증·DB 연결은 동작 확인했지만 추천 품질이 승인된 것은 아닙니다. 전체 5,011건 생성은 실행하지 않았습니다. 대표 메뉴·분류·근거를 검토하고 기준을 보강한 후 새 프로필로 진행합니다.
- 일반 Java 테스트 134개, 실제 PostgreSQL 테스트 32개, Python 테스트 9개를 통과했습니다. 기본 CI와 실제 모델 검증은 구분합니다.
- Ollama를 종료한 상태에서 기본 Compose로 세 컨테이너를 재생성했습니다. 맛 프로필 24건·생성 이력·적재 시각이 유지됐고, 원본 카페·메뉴 전체와 최초 적재 시각도 이전 스냅샷과 같았습니다. 백엔드에는 모델·데이터 파일 마운트가 없으며 세 서비스 healthy와 두 health 경로를 확인했습니다.
