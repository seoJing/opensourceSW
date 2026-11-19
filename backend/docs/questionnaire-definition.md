# 설문 정의 조회

관련 작업: [Issue #20](https://github.com/seoJing/opensourceSW/issues/20)

`GET /api/v1/preference-questionnaire`는 외부 JSON에서 읽은 설문 정의를 원래 문항·선택지 순서대로 반환한다. 응답 필드는 [API 계약](../../docs/cafe-api-contract.md#5-설문-정의)을 따른다. 이 기능은 답변 저장이나 추천 점수를 계산하지 않는다.

## 입력과 실행

- `app.questionnaire.enabled=true`일 때만 정의를 로딩하고 endpoint를 등록한다. 기본값은 비활성이다.
- `app.questionnaire.path`에는 서버가 읽을 수 있는 JSON 파일 경로를 지정한다. 환경변수는 `APP_QUESTIONNAIRE_ENABLED`, `APP_QUESTIONNAIRE_PATH`이다.
- 활성화 상태에서 경로가 없거나 파일·내용이 잘못되면 시작을 거부한다. 파일 변경은 재시작 후 반영한다.
- `version`은 필수 문자열이고 `questions`는 비어 있지 않은 배열이다. 각 문항은 고유 ID, 문구, `single` 또는 `multiple` 타입, 최소·최대 선택 개수, 비어 있지 않은 선택지 배열을 가진다. 선택지 ID는 해당 문항 안에서 고유하다.
- 표시 순서는 배열 순서다. 오류가 나면 임의 문항으로 대체하지 않고 JSON 필드 경로를 포함한 예외를 반환한다.

아래 JSON은 형식 검토용 가상 문항이다. 실제 설문 문항·선택지·버전은 팀 확정 후 별도 파일로 공급해야 한다. 이 예시를 실제 추천 입력으로 사용하지 않는다.

```json
{
  "version": "review-example-v1",
  "questions": [{
    "id": "example-drink",
    "label": "[가상 예시] 어떤 음료를 고르시겠어요?",
    "type": "single",
    "minSelections": 1,
    "maxSelections": 1,
    "options": [
      {"id": "coffee", "label": "커피"},
      {"id": "tea", "label": "차"}
    ]
  }]
}
```

```sh
java -jar backend/target/backend-0.1.0-SNAPSHOT.jar \
  --app.questionnaire.enabled=true \
  --app.questionnaire.path=/absolute/path/questionnaire.json
```

현재 조회 코드는 인증 주체가 없으면 401을 반환하고, 성공·인증 오류에 `Cache-Control: no-store`를 적용한다. PR #19의 세션 인증과 실제 브라우저 흐름은 두 PR이 병합된 뒤 통합 검증해야 한다. 기본 비활성 상태에서는 설문 endpoint를 제공하지 않는다.

`backend/`에서 `sh mvnw -B -ntp verify`로 로더·검증·HTTP 응답·시작 실패와 기존 health 테스트를 실행한다.
