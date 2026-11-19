package com.suwontrip.app.catalog.persistence;

import com.suwontrip.app.catalog.Catalog;
import com.suwontrip.app.catalog.CatalogCafe;
import com.suwontrip.app.catalog.CatalogLoadException;
import com.suwontrip.app.catalog.CatalogLoader;
import com.suwontrip.app.catalog.CatalogMenu;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class CatalogImporter {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final CatalogLoader loader = new CatalogLoader();

    public CatalogImporter(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public ImportResult importFile(Path path) {
        try {
            return importDocument(Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new CatalogLoadException("$", "cannot read catalog file: " + path, exception);
        }
    }

    public ImportResult importDocument(byte[] document) {
        Catalog catalog = loader.load(document);
        if (catalog.dataVersion().length() > 128) {
            throw new CatalogLoadException("$.dataVersion", "data version must be at most 128 characters");
        }
        String sha256 = digest(document);
        return transaction.execute(status -> persist(catalog, sha256));
    }

    private ImportResult persist(Catalog catalog, String sha256) {
        if (jdbc.queryForList("SELECT data_version FROM catalog_active WHERE singleton_id = 1 FOR UPDATE").size() != 1) {
            throw new IllegalStateException("catalog active-version row is missing");
        }
        List<String> existing = jdbc.queryForList(
                "SELECT source_sha256 FROM catalog_dataset WHERE data_version = ?",
                String.class,
                catalog.dataVersion()
        );
        boolean inserted = existing.isEmpty();
        if (!inserted && !existing.getFirst().equals(sha256)) {
            throw new IllegalStateException("catalog data version already exists with different content");
        }
        if (inserted) {
            jdbc.update("""
                    INSERT INTO catalog_dataset
                        (data_version, schema_version, source_sha256, cafe_count, menu_count)
                    VALUES (?, ?, ?, ?, ?)
                    """, catalog.dataVersion(), catalog.schemaVersion(), sha256,
                    catalog.cafes().size(), catalog.menus().size());
            insertCafes(catalog);
            insertMenus(catalog);
        }
        jdbc.update("UPDATE catalog_active SET data_version = ? WHERE singleton_id = 1", catalog.dataVersion());
        return new ImportResult(catalog.dataVersion(), catalog.cafes().size(), catalog.menus().size(), inserted);
    }

    private void insertCafes(Catalog catalog) {
        List<Object[]> rows = new ArrayList<>(catalog.cafes().size());
        for (CatalogCafe cafe : catalog.cafes()) {
            rows.add(new Object[]{
                    catalog.dataVersion(), cafe.id(), cafe.name(), cafe.roadAddress(),
                    cafe.location() == null ? null : cafe.location().latitude(),
                    cafe.location() == null ? null : cafe.location().longitude(),
                    cafe.source().url().toString(), cafe.source().externalId(), cafe.source().observedOn()
            });
        }
        jdbc.batchUpdate("""
                INSERT INTO catalog_cafe
                    (data_version, cafe_id, name, road_address, latitude, longitude,
                     source_url, source_external_id, source_observed_on)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rows);
    }

    private void insertMenus(Catalog catalog) {
        List<Object[]> rows = new ArrayList<>(catalog.menus().size());
        for (int i = 0; i < catalog.menus().size(); i++) {
            CatalogMenu menu = catalog.menus().get(i);
            rows.add(new Object[]{
                    catalog.dataVersion(), menu.id(), menu.cafeId(), i, menu.name(),
                    menu.description(), menu.priceKrw(), menu.isDemo(), menu.availability().name(),
                    menu.saleConfirmation() == null ? null : menu.saleConfirmation().confirmedOn(),
                    menu.saleConfirmation() == null ? null : menu.saleConfirmation().reference(),
                    menu.source().url().toString(), menu.source().externalId(), menu.source().observedOn()
            });
        }
        jdbc.batchUpdate("""
                INSERT INTO catalog_menu
                    (data_version, menu_id, cafe_id, ordinal, name, description, price_krw,
                     is_demo, availability, confirmed_on, confirmation_reference,
                     source_url, source_external_id, source_observed_on)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rows);
    }

    private String digest(byte[] document) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(document));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record ImportResult(String dataVersion, int cafes, int menus, boolean inserted) {
    }
}
