CREATE OR REPLACE VIEW catalog_menu_classification_v1 AS
WITH rules (matched_name, menu_type, coffee_based) AS (
    VALUES
        ('아메리카노', 'beverage', TRUE),
        ('카페라떼', 'beverage', TRUE),
        ('카페라테', 'beverage', TRUE),
        ('플랫화이트', 'beverage', TRUE),
        ('에스프레소', 'beverage', TRUE),
        ('카푸치노', 'beverage', TRUE),
        ('카페모카', 'beverage', TRUE),
        ('필터커피', 'beverage', TRUE),
        ('자몽에이드', 'beverage', FALSE),
        ('레몬에이드', 'beverage', FALSE),
        ('청포도에이드', 'beverage', FALSE),
        ('망고스무디', 'beverage', FALSE),
        ('딸기스무디', 'beverage', FALSE),
        ('유자차', 'beverage', FALSE),
        ('라떼', 'beverage', NULL),
        ('말차라떼', 'beverage', NULL),
        ('바닐라라떼', 'beverage', NULL),
        ('딸기라떼', 'beverage', NULL),
        ('흑임자라떼', 'beverage', NULL),
        ('초코라떼', 'beverage', NULL),
        ('복숭아아이스티', 'beverage', NULL),
        ('에그타르트', 'dessert', NULL),
        ('바스크치즈케이크', 'dessert', NULL),
        ('바나나푸딩', 'dessert', NULL),
        ('브라우니', 'dessert', NULL),
        ('티라미수', 'dessert', NULL),
        ('휘낭시에', 'dessert', NULL),
        ('마들렌', 'dessert', NULL),
        ('치즈케이크', 'dessert', NULL),
        ('치아바타샌드위치', 'meal', NULL),
        ('잠봉뵈르샌드위치', 'meal', NULL),
        ('햄치즈샌드위치', 'meal', NULL),
        ('트러플화이트라구파스타', 'meal', NULL),
        ('까르보나라파스타', 'meal', NULL),
        ('뽀모도로파스타', 'meal', NULL)
)
SELECT m.data_version, m.menu_id, r.menu_type,
       CASE
           WHEN r.coffee_based = FALSE
                AND LOWER(m.description) ~ '(커피|에스프레소|콜드브루|원두|샷|coffee|espresso|shot|cold brew)'
               THEN NULL
           ELSE r.coffee_based
       END AS coffee_based,
       r.matched_name
FROM catalog_active a
JOIN catalog_menu m ON m.data_version = a.data_version
LEFT JOIN rules r ON r.matched_name = REPLACE(m.name, ' ', '')
WHERE a.singleton_id = 1;
