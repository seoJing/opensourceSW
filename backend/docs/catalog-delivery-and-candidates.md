# 카탈로그 팀 전달과 추천 후보 기준

관련 작업: [Issue #27](https://github.com/seoJing/opensourceSW/issues/27) · 기준 데이터: `haenggung-2026-11-18.2` · 2026-11-26 MVP 기준

이 문서는 [출처와 입력 기준 PR #25](https://github.com/seoJing/opensourceSW/pull/25)의 실제 카탈로그를 팀원이 **동일한 실행 입력으로** 받는 절차와 추천 후보를 고르는 기준을 정한다. 원자료를 다시 수집하는 절차는 포함하지 않는다. 속성 객체와 질문은 담당 팀원이 합의할 사항이며 이 문서는 점수 공식·HTTP 필드·속성 목록을 확정하지 않는다.

## 1. 전달 단위

기준 전달 방식은 PR #31에 포함한 `backend/data/catalog/haenggung-2026-11-18.2/`의 고정 데이터다. 팀원은 해당 PR 병합 후 저장소를 받으면 아래 파일을 함께 받는다. 별도 압축파일 전달이나 재수집은 필요하지 않다.

| 파일 | 용도 |
| --- | --- |
| `catalog.json` | 백엔드 로더가 검증해 DB에 적재하는 카페·메뉴 입력 |
| `manifest.json` | 데이터 버전, 수량, 카탈로그 SHA-256과 수집 범위 |
| `SHA256SUMS` | 두 JSON 파일의 바이트 동일성 검사 |

검토용 원문, 수집 스크립트, API 자격 증명과 세션 정보는 저장소 입력에 포함하지 않는다. 고정 입력은 **검토한 자료의 재현**을 위한 것이며 원천 페이지의 현재 상태나 새 수집 결과가 같음을 보장하지 않는다. 기존 `.tar.gz`는 동일 입력의 선택형 전달 사본이며 실행에 필수적이지 않다. 두 형식을 중복 커밋하지 않는다.

```sh
cd backend/data/catalog/haenggung-2026-11-18.2
sha256sum -c SHA256SUMS
```

macOS에서는 `shasum -a 256 -c SHA256SUMS`, Windows에서는 `Get-FileHash -Algorithm SHA256`으로 대조한다. 처음 환경을 구성하는 팀원은 PR #31의 `backend/docs/team-getting-started.html`을 따른다. 기본 Compose 확장은 저장소 안의 파일을 읽으며, 다른 버전을 사용할 때만 `CATALOG_FILE`을 지정한다. 버전 파일을 수정했다면 새 `dataVersion`을 사용한다.

이 버전의 `manifest.json`에는 `catalogSha256=77101fe31346de40225d23adca5a485f74cb4861e98a9a8e9d1cf7474ab4eb0d`, 카페 320곳, 입력 메뉴 5,011건이 기록되어 있다. `catalog.json`의 `dataVersion`도 묶음 이름과 일치해야 한다. 체크섬을 통과해도 로더 검증을 생략하지 않는다. #22 로더로 스키마·참조를 검사하고, #24가 병합된 뒤에는 `APP_CATALOG_ENABLED=true`, `APP_CATALOG_PATH`를 지정해 시작 시 PostgreSQL에 적재한다. 같은 버전·같은 SHA-256은 중복 저장하지 않고, 같은 버전의 다른 바이트는 거부한다. 새 버전은 별도 폴더에 저장·검증한 뒤 경로를 바꾼다. 직접 실행한 JVM은 재시작하고, Docker의 호스트 파일 연결을 바꿀 때는 [PR #31 실행 안내](https://github.com/seoJing/opensourceSW/pull/31)에 따라 백엔드 컨테이너를 다시 생성한다. 문제가 있으면 이전 경로로 같은 절차를 적용한다.

PR #31은 DB 적재와 Compose 실행 자료를 함께 제공한다. 기본 Compose의 PostgreSQL 볼륨은 DB 파일을 보존하며, 카탈로그 확장을 사용하면 고정 JSON을 읽어 한 트랜잭션에서 적재하고 활성 버전을 전환한다. 원본 파일을 다시 연결하지 않아도 적재된 데이터가 유지된다.

## 2. 추천 대상

MVP에서는 고정본 5,011건 전체를 판매 가능으로 간주합니다. 원본의 판매 미확인 4,971건과 시즌 종료 40건을 그대로 보존합니다. 데이터 재수집이나 판매 근거 등록 없이 같은 버전에서 동일한 추천 순위를 재현할 수 있습니다. [후보 및 동점 처리 기준](sale-confirmed-candidates.md)을 참고하세요.
