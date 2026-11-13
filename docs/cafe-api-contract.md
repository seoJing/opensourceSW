# Leçon 2 — 행궁동 카페 MVP API 계약 검토안

작성일: 2026-11-13 (Asia/Seoul) · 관련 작업: [Issue #16](https://github.com/seoJing/opensourceSW/issues/16)

이 문서는 로그인 → 설문 저장 → 메뉴 한 장 탐색 → 카페 위치 확인 흐름을 구현하기 전에
프론트와 백엔드가 요청·응답 형식을 검토하기 위한 초안이다.
API 구현 완료나 팀 승인을 의미하지 않는다.

## 1. 근거와 검토 상태

| 표시 | 의미 |
| --- | --- |
| 원문 | 이 대화에서 DOCX 원본을 읽고 추출한 API·화면 요구사항 |
| 제안 | 원문에 빠졌거나 모호한 부분을 이번 문서에서 구체화한 내용. 팀 검토 필요 |
| 미정 | 지금 확정하지 않으며 후속 합의가 필요한 내용 |

원본은 `01_행궁동_카페_API_명세서.docx`와 `02_행궁동_카페_화면_기획서.docx`이다.
작성 시점에 두 파일은 `docs/`에서 확인되지 않는다.
따라서 원문 표시는 앞서 추출한 텍스트에 근거하며, 최종 승인 전에 원본 재대조가 필요하다.
원문 API 문서도 스스로를 계약 제안이라고 설명하므로 원문 표시를 팀 승인으로 해석하지 않는다.

현재 실행 구조는 [architecture.md](architecture.md), 협업 절차는
[collaboration.md](collaboration.md)를 따른다.
실제 백엔드는 health만 제공하며 Nginx도 `/api/health`만 프록시한다.
아래 `/api/v1` 업무 API와 프록시 확장은 아직 구현되지 않았다.
코드·DB·의존성·실행 설정은 이번 문서 작업에서 변경하지 않는다.

팀의 카페 MVP 공식 범위 승인, 프론트 계약 검토자, 최종 계약 승인자는 모두 미확인이다.
기존 [PR #15](https://github.com/seoJing/opensourceSW/pull/15)는 작성 시점에 미병합이다.
초기 구축에 의존하는 기능 코드는 해당 PR 병합 후 최신 main에서 별도 Issue로 진행한다.

## 2. 원문과 이번 제안의 경계

| 항목 | 원문 | 이번 제안 / 미정 |
| --- | --- | --- |
| 기본 경로·API | `/api/v1`, API 8개, 성공 200 | 유지 |
| 인증 | 같은 출처의 서버 세션, 사전 준비한 계정 | 쿠키·CSRF 전달과 갱신은 P03 |
| 로그인 요청 | 로그인 ID·비밀번호를 입력 | JSON 필드 `loginId`, `password`는 P01 |
| 설문 | 서버 정의·버전·선택 개수 제공 | 전체 문항 제출과 구조 검증은 P02 |
| 선호도 저장 | 계정별 전체 저장·재로그인 후 복원 | PUT 요청 구조는 P02, 구버전 반환은 P04 |
| 추천 | 메뉴 한 장, 제외 목록 0~100개, 후보 없으면 null | 빈 ID·중복 등의 처리 기준은 P02 |
| 카페 | 실제 확인한 주소, 좌표·길찾기 URL은 null 허용 | 필드 형태는 유지, 지도 서비스는 미정 |
| 오류 | HTTP 상태와 `code` 목록 | 공통 JSON·문항별 상세는 P05 |
| 시간 | `updatedAt`은 UTC, 시간 정책은 백엔드 제안 요청 | 형식은 P06, 만료·제한 수치는 미정 |

이하 제안 P01~P06과 그에 기반한 JSON 예시는 승인 전 초안이다.
예시 ID·문구·토큰은 형태를 설명하는 자리표시자이며 실제 설문·계정·카페 데이터가 아니다.

## 3. 공통 전송 규칙

**원문:** 통합 접속 주소는 `http://localhost:3000`, 상대 기본 경로는 `/api/v1`이다.
JSON 본문이 있는 요청은 `Content-Type: application/json`을 사용한다.
세션 조회와 로그인은 비로그인 접근이 가능하고 나머지 업무 API는 로그인이 필요하다.

**제안 P01:**

- 응답은 `application/json`으로 제공한다. 성공 응답에 별도의 `data` 포장은 추가하지 않는다.
- JSON 필드 이름은 아래 예시의 camelCase를 사용한다. 식별자는 비어 있지 않은 string이다.
- 로그인·선호도·추천 요청에 알 수 없는 필드, 잘못된 타입, 필수 필드 누락·null은 400이다.
- GET과 DELETE는 본문을 보내지 않는다. DELETE도 성공 시 JSON을 반환하므로 204로 바꾸지 않는다.
- 인증 오류를 로그인 HTML이나 302 redirect로 반환하지 않는다.
- 세션·개인 응답과 오류는 `Cache-Control: no-store`로 제공한다.
- 프론트는 동일 출처 세션 쿠키를 포함해 요청한다. 세션 ID를 JSON·URL·localStorage로 전달하지 않는다.

### API 목록

| 메서드 | 경로 (`/api/v1` 뒤) | 인증 | CSRF 제안 | 성공 응답 |
| --- | --- | --- | --- | --- |
| GET | `/auth/session` | 불필요 | 불필요 | Session |
| POST | `/auth/session` | 불필요 | 필요 | Session |
| DELETE | `/auth/session` | 필요 | 필요 | Session |
| GET | `/preference-questionnaire` | 필요 | 불필요 | Questionnaire |
| GET | `/me/preferences` | 필요 | 불필요 | Preferences |
| PUT | `/me/preferences` | 필요 | 필요 | Preferences |
| POST | `/menu-recommendations` | 필요 | 필요 | Recommendation |
| GET | `/cafes/{cafeId}` | 필요 | 불필요 | Cafe |

인증 열과 성공 상태 200은 원문에 근거한다. CSRF 적용 범위와 응답 객체 명칭은 이번 제안이다.

## 4. 세션과 로그인

### GET /api/v1/auth/session

**원문:** 비로그인도 200으로 `authenticated`, `user`, `csrfToken`을 받는다.
`user`는 비로그인 시 null, 로그인 시 string 타입의 `id`, `displayName`을 갖는다.

```json
{
  "authenticated": false,
  "user": null,
  "csrfToken": "csrf-placeholder-before-login"
}
```

**제안 P03:** 유효한 세션이 없으면 비로그인 세션과 CSRF 값을 준비한다.
세션 쿠키 자체는 JavaScript에서 읽지 않으며, 프론트는 응답의 CSRF 값만 메모리에 둔다.
세션 조회 자체가 선호도나 제외 목록을 수정하지 않는다.

### POST /api/v1/auth/session

**제안 P01:** 로그인 요청은 다음 JSON이다. 실제 시연 계정이나 비밀번호를 문서에 기록하지 않는다.

```json
{
  "loginId": "login-id-placeholder",
  "password": "password-placeholder"
}
```

필수 string이며 빈 값은 400이다. 비밀번호를 임의로 trim하거나 대소문자 변환하지 않는다.
로그인 ID의 길이·허용 문자·정규화 규칙, 이미 로그인된 상태에서의 재로그인 처리는 미정이다.
CSRF 값을 받은 뒤 로그인 요청 헤더에 포함한다.

**원문:** 올바른 로그인은 200, 잘못된 계정 정보는 401 `INVALID_CREDENTIALS`이다.
계정 존재 여부를 구분해서 안내하지 않는다.

```json
{
  "authenticated": true,
  "user": {
    "id": "user-example",
    "displayName": "표시 이름 예시"
  },
  "csrfToken": "csrf-placeholder-after-login"
}
```

**제안 P03:** 로그인 성공 시 세션 ID를 교체하고 성공 응답에서 새 CSRF 값을 반환한다.
프론트는 이전 CSRF 값을 버린다. 응답 후 설문 정의와 저장 선호도를 조회해 화면을 결정한다.
재로그인 후 인증을 유지하려면 서버 측 인증 상태도 새 세션에 저장되어야 한다.

### DELETE /api/v1/auth/session

**원문:** 로그아웃은 200이다. 성공 후 개인 화면 상태와 선호도 캐시를 비운다.

**제안 P03:** 기존 인증 세션과 CSRF 값을 폐기한 뒤 새 비로그인 세션을 준비하고
다음 Session을 반환한다. 따라서 로그아웃 후에도 쿠키가 있을 수 있지만 인증 상태는 false이다.
기존 인증 세션 ID로는 다시 업무 API를 사용할 수 없어야 한다.

```json
{
  "authenticated": false,
  "user": null,
  "csrfToken": "csrf-placeholder-after-logout"
}
```

이미 비로그인인 경우는 업무 API 인증 규칙에 맞춰 401 `AUTHENTICATION_REQUIRED`로 제안한다.
프론트는 이 경우에도 개인 화면을 감춘다. 반복 로그아웃을 200으로 허용할지는 검토 항목이다.

### CSRF 전달과 오류 우선순위 제안 P03

- 헤더 이름은 `X-CSRF-TOKEN`으로 제안한다. POST 로그인, DELETE 로그아웃, PUT 저장, POST 추천에 사용한다.
- 비로그인 로그인 요청도 세션 조회로 토큰을 받은 뒤 전송한다. GET에는 CSRF 헤더를 요구하지 않는다.
- 인증이 필요한 API에서 세션이 없거나 만료되면 401 `AUTHENTICATION_REQUIRED`를 우선 반환한다.
- 유효한 인증이 있거나 비로그인 허용 로그인 API라면 CSRF 누락·불일치는 403 `CSRF_INVALID`이다.
- 403이면 세션 조회로 토큰과 인증 상태를 갱신하고 사용자의 재시도를 안내한다. 변경 요청을 자동 재전송하지 않는다.
- 그다음 JSON 구조 400, 버전 충돌 409, 문항 오류 422 순으로 검증한다. 실패한 저장은 기존 DB 값을 바꾸지 않는다.

위 401 우선순위는 명시적으로 구현·검증할 제안이다.
Spring Security 기본 필터 순서만으로 세션 만료 요청이 항상 이 결과를 내는 것으로 가정하지 않는다.
현재 저장소에는 Spring Security 의존성이 없으며 이 문서가 도입 결정을 대신하지 않는다.
JSON 로그인·DELETE 로그아웃·JSON 오류 응답도 기본 form login 동작과 별도로 맞춰야 한다.

토큰 갱신과 세션 처리 검토에는 Spring Security 6.5의
[CSRF 문서](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html),
[세션 문서](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html),
[로그아웃 문서](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/logout.html)를 참고했다.
라이브러리 사용 시 실제 선택 버전의 동작을 다시 확인한다.

## 5. 설문 정의

### GET /api/v1/preference-questionnaire

**원문:** 문항은 표시 순서대로 1개 이상이며 버전은 string이다.
Question의 ID는 문항 배열 안에서, Option의 ID는 해당 문항 안에서 고유하다.
`type`은 `single` 또는 `multiple`이다.
`minSelections`는 0 이상, `maxSelections`는 min 이상이며 선택지 수 이하이다.
single의 max는 1이고 옵션은 1개 이상이다.

```json
{
  "version": "questionnaire-example-v1",
  "questions": [
    {
      "id": "question-example",
      "label": "질문 표시 문구 예시",
      "type": "single",
      "minSelections": 1,
      "maxSelections": 1,
      "options": [
        {
          "id": "option-example-a",
          "label": "선택지 표시 문구 A"
        },
        {
          "id": "option-example-b",
          "label": "선택지 표시 문구 B"
        }
      ]
    }
  ]
}
```

예시는 JSON 형태만 설명하며 실제 취향 질문이나 점수 체계를 제안하지 않는다.
프론트는 이 정의를 표시하고 가중치를 계산하지 않는다.
조회 실패 시 임의 문항을 만들지 않고 재조회한다.

## 6. 선호도 조회와 전체 저장

### GET /api/v1/me/preferences

**원문:** 미저장 응답은 다음과 같다.

```json
{
  "saved": false,
  "questionnaireVersion": null,
  "answers": [],
  "updatedAt": null
}
```

저장된 응답의 `questionnaireVersion`은 제출 버전, `answers`는 문항 순서,
`updatedAt`은 UTC 시각이다. 질문 ID와 문항 내 선택지 ID는 중복되지 않는다.

**제안 P04 — 구버전 반환은 원문 의미 보완이 필요하다:**

원문의 `saved`는 유효한 저장 응답 유무로 설명되지만, 화면은 구버전을 구분해야 한다.
필드를 추가하지 않는 제안으로 `saved`를 **저장 기록의 존재 여부**로 해석한다.
이 의미 변경은 승인이 필요하다. 구버전도 `saved: true`와 실제 저장 버전·답변·시각을 그대로 반환한다.
문항 순서는 저장 당시 설문 기준이며 현재 설문에 맞춰 조용히 변환하지 않는다.

프론트는 `saved === true`이고 저장 버전이 현재 설문 버전과 같을 때만 바로 메뉴로 이동한다.
버전이 다르면 설문 변경 안내와 새 입력 화면을 제공한다. 없는 선택지로 자동 이관하지 않는다.
이 제안이 승인되지 않으면 구버전을 구분할 별도 필드나 다른 반환 방식을 다시 합의해야 한다.

### PUT /api/v1/me/preferences

**제안 P02:** 저장 요청은 `questionnaireVersion`과 `answers`만 받는다.
계정 ID, `saved`, `updatedAt`은 요청에 넣지 않는다. 계정은 서버 세션에서 결정한다.

```json
{
  "questionnaireVersion": "questionnaire-example-v1",
  "answers": [
    {
      "questionId": "question-example",
      "optionIds": ["option-example-a"]
    }
  ]
}
```

| 제안 P02 검증 | 실패 결과 |
| --- | --- |
| 본문·필수 필드·배열·문자열 타입이 올바름 | 400 `INVALID_REQUEST` |
| 버전이 현재 제공되는 설문과 같음 | 409 `QUESTIONNAIRE_VERSION_MISMATCH` |
| 현재 모든 문항을 정확히 한 번 포함 | 422 `INVALID_ANSWERS` |
| 선택하지 않은 선택 문항도 `optionIds: []`로 포함 | 422 `INVALID_ANSWERS` |
| 질문 ID와 질문 내 선택지 ID가 실제 정의에 속함 | 422 `INVALID_ANSWERS` |
| 중복 문항·중복 선택지가 없음 | 422 `INVALID_ANSWERS` |
| 선택 개수가 각 문항의 min~max 범위 | 422 `INVALID_ANSWERS` |

`questionnaireVersion`, 질문·선택지 ID의 빈 문자열은 구조 오류 400으로 제안한다.
전체 문항 제출 규칙은 원문에 없는 구체화이며, 부분 저장은 제공하지 않는 제안이다.
요청 배열 순서는 허용하되 성공 응답은 설문 문항 순서, 선택지 ID는 설문 선택지 순서로 정렬한다.
전체 검증 후 한 번에 교체하며 실패 시 일부 문항만 저장하지 않는다.
동시에 설문이 바뀌는 경우에도 구버전 응답이 현재 버전으로 잘못 저장되지 않도록 원자적으로 검증한다.

성공 200은 저장된 Preferences 전체를 반환한다.

```json
{
  "saved": true,
  "questionnaireVersion": "questionnaire-example-v1",
  "answers": [
    {
      "questionId": "question-example",
      "optionIds": ["option-example-a"]
    }
  ],
  "updatedAt": "2026-11-13T08:00:00.000Z"
}
```

**제안 P06:** 시각은 서버에서 생성하며 UTC의 `YYYY-MM-DDTHH:mm:ss.SSSZ` 형태로 반환한다.
여기서 끝의 Z는 UTC를 나타내는 실제 문자이다. 프론트는 표시할 때만 사용자의 시간대로 변환한다.
같은 요청을 반복해도 저장 응답은 중복 생성하지 않지만 `updatedAt`은 갱신될 수 있다.
이 값만으로 동시 수정 충돌 감지를 제공하지 않는다. 다중 탭의 저장 충돌 정책은 미정이다.

## 7. 메뉴 한 장 추천

### POST /api/v1/menu-recommendations

**원문:** `excludedMenuIds`는 필수이며 고유 ID 0~100개다.

```json
{
  "excludedMenuIds": []
}
```

**제안 P02:** 누락·null·문자열 외 값·빈 ID·중복·101개 이상은 400 `INVALID_REQUEST`이다.
알 수 없는 비어 있지 않은 ID는 제외 대상과 일치하는 것이 없는 것으로 처리한다.
카드 ID를 순서대로 보낸다고 해서 추천 순위에 가중치를 주지 않는다.

**원문:** 유효한 저장 선호도가 없으면 409 `PREFERENCES_REQUIRED`이다.
추천 메뉴가 없으면 오류 대신 200과 `menu: null`을 반환한다.
제안 P04를 채택하면 저장 버전 불일치도 유효한 선호도가 없는 상태로 처리한다.

```json
{
  "menu": {
    "id": "menu-example",
    "cafeId": "cafe-example",
    "name": "메뉴 이름 예시",
    "description": "",
    "imageUrl": null,
    "imageAlt": "메뉴 사진 대체 설명 예시",
    "isDemo": true
  }
}
```

```json
{
  "menu": null
}
```

MenuCard는 원문 필드와 타입을 유지한다.
`description`이 없으면 빈 문자열, `imageUrl`은 null 또는 HTTPS URL·`/`로 시작하는 정적 경로다.
`isDemo`는 가상·시연 메뉴 여부이다. 프로토콜 상대 경로 `//...`는 정적 경로로 허용하지 않는 제안이다.
제외 ID를 반환하지 않아야 하며, 어떤 순서로 후보를 고르는지와 입력 데이터는 미정이다.
클라이언트가 보낸 제외 목록 밖의 영구 탐색 이력을 새로 만들지는 않는 제안이다.

화면 원문에 따른 동작은 다음과 같다.

- 넘기기: 현재 메뉴 ID를 제외 목록에 추가하고 다음 메뉴 요청. 선호도 변경 없음.
- 선택하기: 카드와 메뉴명을 보존한 채 `cafeId`로 카페 조회. 선호도 변경 없음.
- 카페에서 복귀: 추천 재요청 없이 직전 카드 표시.
- 제외 100개에 도달하거나 메뉴 없음: 자동 초기화하지 않고 사용자가 처음부터 다시 보기를 선택하게 함.
- 다시 보기·새로고침: 제외 목록을 비우고 새 탐색. 새로고침 시 세션·선호도도 다시 조회.
- 로딩·실패: 현재 카드와 제외 목록을 보존하고 버튼 중복 입력·응답 역전 방지.

후보 소진은 200, DB 장애 등 처리 실패는 500/503이다. 장애를 `menu: null`로 숨기지 않는다.

## 8. 카페 조회

### GET /api/v1/cafes/{cafeId}

**원문:** 카페 응답은 다음 필드를 갖는다.

| 필드 | 타입·조건 |
| --- | --- |
| `id`, `name` | string |
| `roadAddress` | 실제 확인한 도로명 주소 string |
| `location` | null 또는 아래 좌표 객체 |
| `location.latitude` | WGS84 number, -90~90 |
| `location.longitude` | WGS84 number, -180~180 |
| `directionsUrl` | null 또는 채택한 지도 서비스의 HTTPS 길찾기 URL |

```json
{
  "id": "cafe-example",
  "name": "카페명 자리표시자",
  "roadAddress": "실제 확인한 도로명 주소가 들어갈 자리",
  "location": null,
  "directionsUrl": null
}
```

위 JSON은 데이터 형식 설명이며 실제 카페 데이터나 검증된 주소가 아니다.
`location`이 객체이면 위도와 경도를 둘 다 제공하고 부분 좌표는 반환하지 않는 제안이다.
도로명 주소가 확인되지 않은 데이터를 실제 장소처럼 제공하지 않는다.
카페가 없으면 404 `CAFE_NOT_FOUND`이며, UI는 같은 ID 재시도 또는 직전 카드 복귀를 제공한다.
길찾기 URL이 없으면 주소 복사만 제공한다. 좌표·지도 서비스·외부 목적지는 실제 시연 전 별도 검증한다.
현재 위치 권한과 지도 임베드는 필수가 아니다.

## 9. 공통 오류 JSON 제안 P05

원문 오류 코드와 HTTP 상태는 유지한다. JSON 포장과 `errors` 구조는 이번 제안이다.
프론트는 한국어 메시지를 파싱하지 않고 `code`로 분기한다.

```json
{
  "code": "INVALID_ANSWERS",
  "message": "설문 응답을 확인해 주세요.",
  "errors": [
    {
      "field": "answers[0].optionIds",
      "questionId": "question-example",
      "reason": "SELECTION_COUNT",
      "message": "선택 가능한 개수를 확인해 주세요."
    }
  ]
}
```

```json
{
  "code": "AUTHENTICATION_REQUIRED",
  "message": "로그인이 필요합니다.",
  "errors": []
}
```

- 최상위 `code`, `message`, `errors`는 항상 제공한다. 상세 오류가 없으면 빈 배열이다.
- 상세의 `field`는 요청 필드 경로이며, 누락 문항처럼 해당 배열 위치가 없으면 `answers`이다.
- `questionId`는 문항 ID 또는 문항에 대응하지 않으면 null이다.
- 422 상세의 `reason`은 `MISSING_QUESTION`, `UNKNOWN_QUESTION`, `UNKNOWN_OPTION`,
  `DUPLICATE_QUESTION`, `DUPLICATE_OPTION`, `SELECTION_COUNT` 중 하나로 제안한다.
- 400 상세를 별도로 확장하기 전에는 `errors: []`로 반환한다.
- 500/503 메시지에 예외·SQL·비밀번호·세션 ID 등 내부 정보를 포함하지 않는다.
- UI는 422에서 알려진 문항 ID에 오류를 연결하고 첫 오류로 초점을 이동한다.
  알 수 없는 ID만 있으면 폼 상단 공통 안내를 사용한다. 입력은 보존한다.

| HTTP | 원문 code | 대표 위치·프론트 처리 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 요청 구조·타입·추천 제외 목록 오류. 입력 확인 |
| 401 | `INVALID_CREDENTIALS` | 로그인 실패. ID 보존, 비밀번호 재입력 |
| 401 | `AUTHENTICATION_REQUIRED` | 업무 API의 비로그인·만료. 개인 화면 감추고 로그인 진입 |
| 403 | `CSRF_INVALID` | 세션·CSRF 재조회 후 사용자 재시도 |
| 404 | `CAFE_NOT_FOUND` | 카페 재시도 또는 직전 카드로 복귀 |
| 409 | `QUESTIONNAIRE_VERSION_MISMATCH` | 저장 버전 충돌. 설문 재조회·새 입력 안내 |
| 409 | `PREFERENCES_REQUIRED` | 추천 전에 설문 입력·저장 |
| 422 | `INVALID_ANSWERS` | 문항 오류 표시, 선택 보존 |
| 429 | `TOO_MANY_ATTEMPTS` | 로그인 제한. `Retry-After` 초 뒤 재시도 |
| 500 | `INTERNAL_ERROR` | 내부 오류. 입력·카드 보존 후 안내 |
| 503 | `SERVICE_UNAVAILABLE` | 일시 장애. 입력·카드 보존 후 안내 |

429의 `Retry-After`는 0 이상의 정수 초로 제안한다. 제한 횟수·판정 기준·대기 시간은 미정이다.
네트워크 오류처럼 JSON 응답이 없는 경우는 HTTP 오류·후보 없음과 구분해 처리한다.
지원하지 않는 메서드·미디어 타입 등 프레임워크 수준 405/415 오류의 JSON·코드 정책은
원문 목록에 없으므로 구현 전에 추가 검토한다. 모든 실패를 400으로 바꾼다는 뜻은 아니다.

## 10. 화면과 API의 연결

1. 진입 시 Session 조회. 조회 실패는 로그인으로 단정하지 않고 재시도 상태로 둔다.
2. 비로그인이면 로그인. 성공 응답의 새 CSRF 값으로 교체한다.
3. 현재 설문과 저장 선호도 조회. 둘 다 확인한 뒤 화면을 결정한다.
4. 미저장·구버전이면 설문. 저장 실패 시 값을 보존하고 메뉴 화면으로 이동하지 않는다.
5. 저장 성공·현재 버전이면 메뉴 요청. 로딩 중 이전 카드 보존, 버튼 비활성화.
6. 선택하면 카페 조회. 카페 화면에서 직전 카드로 돌아올 때 추천을 다시 요청하지 않는다.
7. 401·로그아웃이면 이전 계정의 선호도·카드·제외 목록·진행 중 응답 적용 상태를 초기화한다.

계정 전환·로그아웃 뒤 도착한 이전 요청의 응답으로 개인 화면을 다시 표시하지 않아야 한다.
401에서 입력을 다른 계정으로 자동 복원·저장하지 않는다.
403·저장 실패·카페 조회 실패의 복구는 원문대로 사용자가 제어한다.

## 11. 후속 구현에서 확인할 검증 기준

아래는 테스트 작성 기준이며, 현재 업무 API 테스트가 존재하거나 통과했다는 뜻은 아니다.

| 영역 | 성공·빈 상태 | 실패·경계 조건 |
| --- | --- | --- |
| Session | 비로그인 200/null user/CSRF, 로그인 사용자 복원 | 조회 네트워크 실패, 새로고침 |
| 로그인 | 200, 새 세션·CSRF, 이후 업무 API 인증 유지 | 구조 400, 동일한 계정 오류 401, CSRF 403, 제한 429 |
| 로그아웃 | 200, 인증 폐기, 개인 상태 초기화 | 옛 인증 세션·토큰 재사용, 비로그인 401 제안, CSRF 403 |
| 설문 | 문항·선택지 순서와 범위, 선택 문항 min=0 | 비로그인 401, 조회 실패 시 임의 문항 생성 금지 |
| 선호도 조회 | 미저장 null/[], 현재 버전, 구버전 원본 반환 제안 | 다른 계정 데이터 접근 금지, 재로그인 복원 |
| 선호도 저장 | 전체 교체·정렬·서버 시각, min/max 경계 | null·타입 400, 구버전 409, 누락·중복·다른 문항 옵션·min/max 밖 422, 실패 시 이전 저장 유지 |
| 추천 | 제외 0개·100개, 단일 카드·후보 소진 null | 중복·빈 ID·101개 400, 미저장·구버전 409, 제외한 메뉴 반환 금지 |
| 카페 | 확인한 주소, 좌표 유무·범위 경계, 길찾기 유무 | 존재하지 않는 ID 404, 조회 장애, 복사 실패 |
| 화면 복구 | 카드 복귀·다시 보기·이미지 실패·320px·키보드 접근 | 응답 역전, 중복 클릭, 만료 401, 갱신 403, 저장 409/422, 장애와 빈 결과 구분 |

서비스 연동 검증은 실제 PostgreSQL과 Nginx를 포함해 수행해야 한다.
기존 `/api/health`도 계속 동작해야 한다.

## 12. 결정이 필요한 항목

| 번호 | 검토할 내용 | 현재 상태 |
| --- | --- | --- |
| D01 | 카페 MVP가 수원 여행 프로젝트의 이번 공식 구현 범위인지 | 미확인 |
| D02 | P01 로그인 JSON·성공 응답 포장·엄격한 필드 검증 | 제안 |
| D03 | P02 전체 문항 제출·정렬·추천 입력 검증 | 제안 |
| D04 | P03 CSRF 헤더·토큰 갱신·로그아웃 응답·401 우선순위 | 제안 |
| D05 | P04 `saved`를 저장 기록 존재로 해석해 구버전 반환 | 원문 의미 보완에 대한 승인 필요 |
| D06 | P05 오류 JSON·문항별 상세 reason | 제안 |
| D07 | P06 UTC 밀리초 형식 | 제안 |
| D08 | 세션 만료 시간·쿠키 속성·동시 로그인·재로그인 동작 | 미정 |
| D09 | 로그인 ID 제약·시연 계정 준비·비밀번호 저장·로그인 제한 수치 | 미정 |
| D10 | 실제 설문·카페·메뉴 자료, 추천 기준, 지도 서비스 | 미정 |
| D11 | DB 구조·마이그레이션·인증 라이브러리·설문 변경 운영 방식 | 별도 구현 Issue에서 결정 |
| D12 | 다중 탭 저장 충돌, 405/415 오류 계약 | 미정 |
| D13 | DOCX 재대조, 프론트 검토자와 계약 승인 기록 | 미완료 |

검토 결과는 승인자·날짜·Issue 댓글 또는 PR 리뷰 링크와 함께 추가한다.
제안 항목을 구현에서 사용하기 전에는 승인 상태를 확인한다.
가입·비밀번호 찾기·소셜 로그인·반응 자동 학습·여러 메뉴 조합 추천은 이번 원문 MVP 범위 밖이다.

## 13. 이 문서의 검증 기록

| 확인 항목 | 2026-11-13 결과 |
| --- | --- |
| Python 표준 `json`으로 JSON 예시 파싱 | 14개 문법 확인 |
| Session·설문·선호도·메뉴·오류 예시 | 필드와 예시 사이의 ID·버전·선택 개수 일관성 확인 |
| API 8개와 원문 오류 코드 11개 | 해당 절과 코드 포함 확인 |
| Markdown 로컬 링크 | 대상 파일 존재 확인 |
| `node scripts/repo-harness.mjs` | Repository Guard 통과 |
| `git diff --check` 및 신규 문서 공백 검사 | 통과 |
| DOCX 재대조·프론트 계약 리뷰·최종 승인 | 미완료 |

원문 재대조가 끝나지 않았으므로 위 검증은 원문과의 완전한 일치를 보증하지 않는다.
업무 API·DB·프록시는 구현하지 않았으며, 문서 작업으로 서비스 동작을 바꾸지 않아
런타임 테스트·이미지 빌드를 새로 실행하지 않았다.
