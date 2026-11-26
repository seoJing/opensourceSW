# 메뉴 가격대 조회

PostgreSQL 뷰 `catalog_menu_price_v1`은 활성 카탈로그의 메뉴별 원화 가격과
설문 v0.1.0의 Q2에 대응하는 가격대를 제공한다. #31의 Flyway가 카탈로그 테이블을 만든 뒤
뷰를 설치한다. 기존 DB에도 다음 애플리케이션 시작 시 적용되며 JSON을 다시 적재할 필요가 없다.

| price_krw | price_level |
|---|---|
| 0~4,999원 | low |
| 5,000~7,999원 | medium |
| 8,000원 이상 | high |
| 미확인 | NULL |

```sql
SELECT data_version, price_level, COUNT(*) AS menus
FROM catalog_menu_price_v1
GROUP BY data_version, price_level
ORDER BY data_version, price_level;
```

`haenggung-2026-11-18.2`의 결과는 low 1,131건, medium 2,578건,
high 1,075건, NULL 227건이다. 가격은 `catalog_menu.price_krw`에 저장하고
뷰에서 가격대를 계산하므로 활성 버전을 바꾸면 새 메뉴와 가격이 함께 적용된다.
미확인 가격은 비추천이나 0원을 뜻하지 않는다. 세트·입장료가 포함된 가격도 원본의
표시 가격으로 분류되며, 활성 카탈로그의 추천 정렬에 사용한다.
후보와 연결할 때는 `data_version`과 `menu_id`를 함께 사용한다.

이 뷰는 백엔드 내부 조회용이다. 프론트의 설문 제출 형식은 기존 계약을 따른다.
