# 메뉴 종류와 커피 기반 여부

`catalog_menu_classification_v1`은 활성 카탈로그의 모든 메뉴에 대해 설문 Q1의
`menu_type`(beverage, dessert, meal)과 Q4의 `coffee_based`(true, false)를 조회한다.
미확인 값과 비음료의 커피 기반 여부는 NULL이다. #31의 Flyway가 카탈로그 테이블을 생성한 뒤 뷰를 설치한다.

메뉴명에서 일반 공백을 제거한 전체 문자열이 규칙과 일치하면 `matched_name`에 일치한 이름이 남는다.
예를 들어 ‘카페 라떼’는 음료·커피 기반, ‘라떼’는 음료·커피 여부 미확인이다.
‘아메리카노 + 케이크 세트’처럼 부가 문구가 붙으면 전체 이름이 달라지므로 두 값이 모두 NULL이다.
레몬에이드 등 비커피 규칙에 해당해도 설명에 커피·샷 관련 표현이 있으면 커피 여부를 미확인으로 둔다.
이는 메뉴 종류에 대한 보수적인 이름 규칙이다. 카페인 유무, 현재 판매, 재료, 추천 적합성은 각각 별도 판단이다.

```sql
SELECT menu_type, coffee_based, count(*) AS menus
FROM catalog_menu_classification_v1
GROUP BY menu_type, coffee_based
ORDER BY menu_type, coffee_based;
```

가격대 뷰 등과 연결할 때는 `(data_version, menu_id)`로 조인한다.
이름이 같은 다른 매장의 메뉴도 서로 다른 행으로 유지한다.
원본 이름과 설명은 같은 키로 `catalog_menu`에서 확인할 수 있다.
프론트의 설문 조회·제출 계약은 그대로 사용한다.
