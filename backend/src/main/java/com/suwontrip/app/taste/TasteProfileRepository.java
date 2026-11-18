package com.suwontrip.app.taste;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Optional;

public final class TasteProfileRepository {
    private final JdbcTemplate jdbc;

    public TasteProfileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Profile> find(String profileVersion, String menuId) {
        return jdbc.query("""
                SELECT p.* FROM menu_taste_profile p
                JOIN catalog_active a ON a.data_version = p.data_version
                WHERE a.singleton_id = 1 AND p.profile_version = ? AND p.menu_id = ?
                """, (row, index) -> new Profile(
                row.getString("menu_id"), TasteProfileImporter.Status.valueOf(row.getString("status")),
                row.getObject("sweetness") == null ? null : new TasteVector(
                        row.getDouble("sweetness"), row.getDouble("sourness"), row.getDouble("bitterness")),
                row.getString("input_basis"), row.getString("reason"), row.getString("review_status")
        ), profileVersion, menuId).stream().findFirst();
    }

    public record Profile(String menuId, TasteProfileImporter.Status status, TasteVector taste,
                          String inputBasis, String reason, String reviewStatus) { }
}
