# 단맛과 시그니처 판정 (Q3·Q6)

`catalog_menu_reviewed_traits_v1`은 활성 카탈로그의 메뉴마다 한 행을 반환한다.
`data_version`과 `menu_id`로 다른 메뉴 속성 뷰와 조인한다.

| 문항 | DB 열 | 판정 근거와 고정본 결과 |
|---|---|---|
| Q3 단맛 | `sweetness`, `sweetness_evidence` | 메뉴 전체의 단맛 강도가 적힌 경우만 반영한다. ‘단맛이 적고’와 ‘은은한 단맛’은 low, ‘진한 달콤함’은 high다. low 10건, medium 0건, high 1건, NULL 5,000건이다. |
| Q6 시그니처 | `is_signature`, `signature_evidence` | 매장이 해당 메뉴를 시그니처로 명시하면 true다. true 119건, NULL 4,892건이다. 이 버전에서 일반 메뉴로 확인한 행은 없다. |

판정 원본은 `haenggung-2026-11-18.2`다. 활성 버전의 매장 ID·메뉴 ID·이름·설명 전체가
검토 원본과 일치하면 판정을 적용한다. 새 버전에서 판매 상태·확인일·가격만 바뀌어도 판정은
유지된다. 설명에 예전 근거 문구가 남아 있더라도 전체 설명이 바뀌면 재검토 전까지 NULL이다.
이전 버전을 DB에 먼저 적재할 필요가 없다.

근거 열은 원본에서 발췌한 문구다. 재료의 당도, 당도 조절 옵션, 시그니처 소스나 드레싱만 언급한 경우 해당 속성은 NULL이다. 추천에서 NULL에 중간 단맛이나 일반 메뉴 점수를 부여하면 안 된다.

```sql
SELECT r.menu_id, m.name, r.sweetness, r.sweetness_evidence,
       r.is_signature, r.signature_evidence
FROM catalog_menu_reviewed_traits_v1 r
JOIN catalog_menu m USING (data_version, menu_id)
WHERE r.sweetness IS NOT NULL OR r.is_signature IS TRUE
ORDER BY m.ordinal;
```
