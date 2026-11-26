CREATE OR REPLACE VIEW catalog_menu_temperature_v1 AS
WITH labels AS (
    SELECT m.data_version, m.menu_id,
           LOWER(TRIM(m.name)) AS title,
           LOWER(TRIM(COALESCE(m.description, ''))) AS detail
    FROM catalog_active a
    JOIN catalog_menu m ON m.data_version = a.data_version
    WHERE a.singleton_id = 1
), patterns AS (
    SELECT '(hot[ ]*(/|,| or )[ ]*iced?|iced?[ ]*(/|,| or )[ ]*hot)' AS pair,
           '(only +hot|hot +only)' AS hot_only,
           '(only +iced?|iced? +only)' AS cold_only
), signals AS (
    SELECT l.*,
           title ~ ('(^|[ (])' || pair || '[)]?$')
               OR detail ~ ('(^|[(])' || pair || '[)]?$')
               OR detail ~ ('(^| )' || pair || ' +가격동일$') AS both_label,
           (title ~ '(^|[ (])hot[)]?$' AND NOT title ~ ('(^|[ (])' || pair || '[)]?$')) OR title ~ ('(^|[ (])' || hot_only || '[)]?$')
               OR detail ~ ('(^|[ (])' || hot_only || '[)]?$')
               OR detail ~ '[(]hot[)]$' AS hot_label,
           (title ~ '(^|[ (])iced?[)]?$' AND NOT title ~ ('(^|[ (])' || pair || '[)]?$')) OR title ~ ('(^|[ (])' || cold_only || '[)]?$')
               OR detail ~ ('(^|[ (])' || cold_only || '[)]?$')
               OR detail ~ '[(]iced?[)]$' AS cold_label,
           (title || ' ' || detail) ~ '(불가|미제공|제공하지|제외|not available|not served|not +(hot|ice)|no +(hot|ice))' AS restricted,
           (title || ' ' || detail) ~ ('(^|[^a-z])' || hot_only || '([^a-z]|$)') OR (title || ' ' || detail) ~ '[(]hot[)]' AS mentions_hot_only,
           (title || ' ' || detail) ~ ('(^|[^a-z])' || cold_only || '([^a-z]|$)') OR (title || ' ' || detail) ~ '[(]iced?[)]' AS mentions_cold_only
    FROM labels l CROSS JOIN patterns
), resolved AS (
    SELECT data_version, menu_id,
           CASE
               WHEN restricted OR (hot_label AND cold_label)
                   OR (both_label AND (hot_label OR cold_label OR mentions_hot_only OR mentions_cold_only))
                   OR (hot_label AND mentions_cold_only) OR (cold_label AND mentions_hot_only) THEN NULL
               WHEN both_label THEN 'both'
               WHEN hot_label THEN 'hot'
               WHEN cold_label THEN 'cold'
           END AS temperature
    FROM signals
)
SELECT data_version, menu_id, temperature FROM resolved;
