package com.suwontrip.app.recommendation;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

@Repository
public class MenuRecommendationRepository {
    private static final String SCORES = """
            SELECT menu_id FROM (
                SELECT m.menu_id, m.ordinal, 0 AS points
                FROM catalog_menu m
                JOIN catalog_active a ON a.singleton_id = 1 AND a.data_version = m.data_version
                WHERE m.data_version = :version AND NOT m.is_demo
                UNION ALL
                SELECT menu_id, NULL,
                       CASE WHEN menu_type = :menuType THEN 1 ELSE 0 END
                     + CASE WHEN coffee_based = :coffee THEN 1 ELSE 0 END
                FROM catalog_menu_classification_v1 WHERE data_version = :version
                UNION ALL
                SELECT menu_id, NULL, CASE WHEN price_level = :price THEN 1 ELSE 0 END
                FROM catalog_menu_price_v1 WHERE data_version = :version
                UNION ALL
                SELECT menu_id, NULL, CASE WHEN temperature = :temperature OR
                      (temperature = 'both' AND CAST(:temperature AS VARCHAR) IN ('hot', 'cold'))
                      THEN 1 ELSE 0 END
                FROM catalog_menu_temperature_v1 WHERE data_version = :version
                UNION ALL
                SELECT menu_id, NULL,
                       CASE WHEN sweetness = :sweetness THEN 1 ELSE 0 END
                     + CASE WHEN is_signature = :signature THEN 1 ELSE 0 END
                FROM catalog_menu_reviewed_traits_v1 WHERE data_version = :version
            ) scored
            """;
    private static final String FIRST = """
             GROUP BY menu_id HAVING MAX(ordinal) IS NOT NULL
             ORDER BY SUM(points) DESC, MAX(ordinal) LIMIT 1
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public MenuRecommendationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> first(String version, Collection<String> excludedIds, PreferenceProfile profile) {
        var parameters = new MapSqlParameterSource("version", version)
                .addValue("menuType", label(profile.menuType()), Types.VARCHAR)
                .addValue("price", label(profile.priceLevel()), Types.VARCHAR)
                .addValue("sweetness", label(profile.sweetness()), Types.VARCHAR)
                .addValue("coffee", profile.menuType() == PreferenceProfile.MenuType.BEVERAGE
                        ? profile.coffeeBased() : null, Types.BOOLEAN)
                .addValue("temperature", label(profile.temperature()), Types.VARCHAR)
                .addValue("signature", profile.signature(), Types.BOOLEAN);
        String sql = SCORES;
        if (!excludedIds.isEmpty()) {
            sql += " WHERE menu_id NOT IN (:excludedIds)";
            parameters.addValue("excludedIds", excludedIds);
        }
        return jdbc.queryForList(sql + FIRST, parameters, String.class)
                .stream().findFirst();
    }

    private static String label(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }
}
