# 메뉴 제공 온도 (Q5)

`catalog_menu_temperature_v1`은 활성 카탈로그의 모든 메뉴에 대해 버전, 메뉴 ID, 제공 온도를 반환한다.
이름 끝의 명시적 영문 온도 표기나 설명 끝의 제공 표시를 판정한다. 예를 들어
‘라떼 (Hot)’은 hot, ‘차 (iced only)’는 cold, ‘아메리카노 *(ice, hot)’은 both다.
서로 다른 온도로 등록된 두 메뉴는 각각의 ID로 유지한다.

판별 범위 밖의 표현, 명시적인 제한 또는 상충하는 온도 표시는 NULL로 둔다.
원두를 추출하는 온도, 재료 온도, hot dog나 ice cream 같은 상품명으로 제공 온도를 추정하지 않는다.
근거는 같은 버전·메뉴 ID의 원본 이름과 설명에서 확인할 수 있다.

```sql
SELECT temperature, count(*) AS menus
FROM catalog_menu_temperature_v1 GROUP BY temperature ORDER BY temperature;
```
