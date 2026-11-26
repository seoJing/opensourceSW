package com.suwontrip.app.catalog.persistence;

import com.suwontrip.app.catalog.CatalogCafe;
import com.suwontrip.app.catalog.CatalogMenu;
import com.suwontrip.app.catalog.CatalogSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class CatalogRepository {
    private final JdbcTemplate jdbc;

    public CatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Dataset> activeDataset() {
        return jdbc.query("""
                SELECT d.* FROM catalog_dataset d
                JOIN catalog_active a ON a.data_version = d.data_version
                WHERE a.singleton_id = 1
                """, (row, index) -> new Dataset(
                row.getString("data_version"), row.getString("source_sha256"),
                row.getInt("cafe_count"), row.getInt("menu_count")
        )).stream().findFirst();
    }

    public Optional<CatalogCafe> findCafe(String id) {
        return jdbc.query("""
                SELECT c.* FROM catalog_cafe c
                JOIN catalog_active a ON a.data_version = c.data_version
                WHERE a.singleton_id = 1 AND c.cafe_id = ?
                """, (row, index) -> {
            Double latitude = row.getObject("latitude", Double.class);
            return new CatalogCafe(
                    row.getString("cafe_id"), row.getString("name"), row.getString("road_address"),
                    latitude == null ? null : new CatalogCafe.Location(
                            latitude, row.getObject("longitude", Double.class)),
                    source(row)
            );
        }, id).stream().findFirst();
    }

    public Optional<CatalogMenu> findMenu(String id) {
        return jdbc.query("""
                SELECT m.* FROM catalog_menu m
                JOIN catalog_active a ON a.data_version = m.data_version
                WHERE a.singleton_id = 1 AND m.menu_id = ?
                """, (row, index) -> menu(row), id).stream().findFirst();
    }

    public List<CatalogMenu> findMenusByCafe(String cafeId, int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new IllegalArgumentException("limit must be 1..100 and offset must be nonnegative");
        }
        return jdbc.query("""
                SELECT m.* FROM catalog_menu m
                JOIN catalog_active a ON a.data_version = m.data_version
                WHERE a.singleton_id = 1 AND m.cafe_id = ?
                ORDER BY m.ordinal LIMIT ? OFFSET ?
                """, (row, index) -> menu(row), cafeId, limit, offset);
    }

    private CatalogMenu menu(ResultSet row) throws SQLException {
        LocalDate confirmedOn = row.getObject("confirmed_on", LocalDate.class);
        return new CatalogMenu(
                row.getString("menu_id"), row.getString("cafe_id"), row.getString("name"),
                row.getString("description"), row.getObject("price_krw", Long.class),
                row.getBoolean("is_demo"), CatalogMenu.Availability.valueOf(row.getString("availability")),
                confirmedOn == null ? null : new CatalogMenu.SaleConfirmation(
                        confirmedOn, row.getString("confirmation_reference")),
                source(row)
        );
    }

    private CatalogSource source(ResultSet row) throws SQLException {
        return new CatalogSource(
                URI.create(row.getString("source_url")), row.getString("source_external_id"),
                row.getObject("source_observed_on", LocalDate.class)
        );
    }

    public record Dataset(String dataVersion, String sha256, int cafes, int menus) {
    }
}
