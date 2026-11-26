CREATE OR REPLACE VIEW catalog_menu_price_v1 AS
SELECT m.data_version, m.menu_id, m.price_krw,
       CASE
           WHEN m.price_krw IS NULL THEN NULL
           WHEN m.price_krw < 5000 THEN 'low'
           WHEN m.price_krw < 8000 THEN 'medium'
           ELSE 'high'
       END AS price_level
FROM catalog_menu m
JOIN catalog_active a ON a.data_version = m.data_version
WHERE a.singleton_id = 1;
