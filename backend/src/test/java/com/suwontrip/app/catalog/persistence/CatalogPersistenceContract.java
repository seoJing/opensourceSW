package com.suwontrip.app.catalog.persistence;

import com.suwontrip.app.catalog.Catalog;
import com.suwontrip.app.catalog.CatalogLoadException;
import com.suwontrip.app.catalog.CatalogLoader;
import com.suwontrip.app.recommendation.CatalogMenuCardLookup;
import com.suwontrip.app.recommendation.PreferenceProfile;
import com.suwontrip.app.recommendation.MenuRecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.suwontrip.app.catalog.persistence.CatalogDatabaseFixture.document;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class CatalogPersistenceContract {
    protected JdbcTemplate jdbc;
    protected CatalogImporter importer;
    protected CatalogRepository repository;

    protected abstract DataSource createDatabase();

    @BeforeEach
    void connect() {
        DataSource dataSource = createDatabase();
        jdbc = new JdbcTemplate(dataSource);
        importer = new CatalogImporter(jdbc, new JdbcTransactionManager(dataSource));
        repository = new CatalogRepository(jdbc);
    }

    @Test
    void emptyDatabaseHasNoActiveCatalog() {
        assertThat(repository.activeDataset()).isEmpty();
        assertThat(repository.findCafe("missing")).isEmpty();
        assertThat(repository.findMenu("missing")).isEmpty();
        assertThat(repository.findMenusByCafe("missing", 10, 0)).isEmpty();
    }

    @Test
    void preservesAllFieldsNullsAndMenuOrderThroughSql() {
        byte[] bytes = document("version-1");
        Catalog expected = new CatalogLoader().load(bytes);

        assertThat(importer.importDocument(bytes))
                .isEqualTo(new CatalogImporter.ImportResult("version-1", 2, 3, true));
        for (var cafe : expected.cafes()) {
            assertThat(repository.findCafe(cafe.id())).contains(cafe);
        }
        for (var menu : expected.menus()) {
            assertThat(repository.findMenu(menu.id())).contains(menu);
        }
        assertThat(repository.findMenusByCafe("cafe-1", 1, 0)).containsExactly(expected.menus().get(0));
        assertThat(repository.findMenusByCafe("cafe-1", 1, 1)).containsExactly(expected.menus().get(1));
        assertThat(repository.findMenusByCafe("cafe-1", 1, 2)).isEmpty();
        assertThat(repository.activeDataset().orElseThrow().sha256()).hasSize(64);
    }

    @Test
    void repositoryReadsDatabaseInsteadOfAnInMemoryCatalog() {
        importer.importDocument(document("version-1"));
        jdbc.update("UPDATE catalog_menu SET name = ? WHERE menu_id = ?", "DB에서 변경한 이름", "menu-1");
        assertThat(new CatalogRepository(jdbc).findMenu("menu-1").orElseThrow().name())
                .isEqualTo("DB에서 변경한 이름");
    }

    @Test
    void resolvesCatalogCardsInOrderWithNullableFields() {
        importer.importDocument(replace(document("v2"), "\"isDemo\": true", "\"isDemo\": false"));
        var candidates = new MenuRecommendationRepository(new NamedParameterJdbcTemplate(jdbc));
        var profile = new PreferenceProfile(null, null, null, null, null, null);
        var lookup = new CatalogMenuCardLookup(repository, candidates);
        jdbc.update("UPDATE catalog_cafe SET name = ? WHERE cafe_id = 'cafe-1'", "카페 A/B, #1");
        var card = lookup.next(profile, Set.of()).orElseThrow();
        assertThat(card.id()).isEqualTo("menu-1");
        assertThat(card.cafe().id()).isEqualTo(card.cafeId());
        assertThat(card.cafe().roadAddress()).isEqualTo("테스트 주소");
        assertThat(card.cafe().location().latitude()).isEqualTo(37.28);
        assertThat(card.cafe().directionsUrl()).startsWith("https://map.naver.com/p/directions/-/")
                .contains("%20A%2FB%2C%20%231").endsWith(",,ADDRESS_POI/-/walk");
        assertThat(lookup.next(profile, Set.of("menu-1")).orElseThrow().id()).isEqualTo("menu-2");
        assertThat(lookup.next(profile, Set.of("menu-1", "menu-2", "menu-3"))).isEmpty();
        jdbc.update("UPDATE catalog_menu SET description = NULL WHERE menu_id = 'menu-1'");
        jdbc.update("UPDATE catalog_cafe SET latitude = NULL, longitude = NULL WHERE cafe_id = 'cafe-1'");
        var noLocation = lookup.next(profile, Set.of()).orElseThrow();
        assertThat(noLocation.description()).isEmpty();
        assertThat(noLocation.cafe().location()).isNull();
        assertThat(noLocation.cafe().directionsUrl()).isNull();
        importer.importDocument(document("new-version"));
        assertThat(lookup.next(profile, Set.of()).orElseThrow().id()).isEqualTo("menu-2");
    }

    @Test
    void ranksAllCatalogMenusUsingMigratedAttributeViews() {
        importer.importDocument(replace(document("v2"), "\"isDemo\": true", "\"isDemo\": false"));
        var candidates = new MenuRecommendationRepository(new NamedParameterJdbcTemplate(jdbc));
        var profile = new PreferenceProfile(null, PreferenceProfile.Level.LOW, null, null, null, null);
        assertThat(candidates.first("v2", Set.of(), profile)).contains("menu-1");
        assertThat(candidates.first("v2", Set.of("menu-1"), profile)).contains("menu-3");
        assertThat(candidates.first("v2", Set.of("menu-1", "menu-3"), profile)).contains("menu-2");
        assertThat(candidates.first("v1", Set.of(), profile)).isEmpty();
    }

    @Test
    void repeatedImportDoesNotDuplicateRows() {
        importer.importDocument(document("version-1"));
        assertThat(importer.importDocument(document("version-1")).inserted()).isFalse();
        assertCounts(1, 2, 3);
    }

    @Test
    void sameVersionWithDifferentBytesIsRejectedWithoutChangingData() {
        importer.importDocument(document("version-1"));
        byte[] changed = replace(document("version-1"), "라테", "다른 음료");
        assertThatThrownBy(() -> importer.importDocument(changed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different content");
        assertCounts(1, 2, 3);
        assertThat(repository.findMenu("menu-1").orElseThrow().name()).isEqualTo("라테");
    }

    @Test
    void newVersionIsActivatedAndPreviousVersionCanBeReactivated() {
        importer.importDocument(document("version-1"));
        importer.importDocument(replace(document("version-2"), "라테", "새 메뉴"));
        assertCounts(2, 4, 6);
        assertThat(repository.activeDataset().orElseThrow().dataVersion()).isEqualTo("version-2");
        assertThat(repository.findMenu("menu-1").orElseThrow().name()).isEqualTo("새 메뉴");
        assertThat(importer.importDocument(document("version-1")).inserted()).isFalse();
        assertThat(repository.activeDataset().orElseThrow().dataVersion()).isEqualTo("version-1");
        assertThat(repository.findMenu("menu-1").orElseThrow().name()).isEqualTo("라테");
        assertCounts(2, 4, 6);
    }

    @Test
    void databaseFailureRollsBackDatasetCafesAndMenusAndKeepsTheActiveVersion() {
        importer.importDocument(document("version-1"));
        jdbc.execute("ALTER TABLE catalog_menu ADD CONSTRAINT reject_test_name CHECK (name <> 'DB-REJECT')");
        byte[] rejected = replace(document("version-2"), "라테", "DB-REJECT");
        assertThatThrownBy(() -> importer.importDocument(rejected)).isInstanceOf(DataAccessException.class);
        assertCounts(1, 2, 3);
        assertThat(repository.activeDataset().orElseThrow().dataVersion()).isEqualTo("version-1");
        assertThat(repository.findMenu("menu-1").orElseThrow().name()).isEqualTo("라테");
    }

    @Test
    void invalidInputDoesNotCreateDatabaseRows() {
        byte[] invalid = replace(document("version-1"), "\"cafeId\": \"cafe-1\"", "\"cafeId\": \"missing\"");
        assertThatThrownBy(() -> importer.importDocument(invalid)).isInstanceOf(CatalogLoadException.class);
        assertCounts(0, 0, 0);
        assertThat(repository.activeDataset()).isEmpty();
    }

    @Test
    void overlongVersionIsRejectedBeforeWriting() {
        assertThatThrownBy(() -> importer.importDocument(document("v".repeat(129))))
                .isInstanceOf(CatalogLoadException.class).hasMessageContaining("dataVersion");
        assertCounts(0, 0, 0);
    }

    @Test
    void concurrentIdenticalImportsInsertExactlyOneVersion() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return importer.importDocument(document("version-1"));
            });
            var second = executor.submit(() -> {
                start.await();
                return importer.importDocument(document("version-1"));
            });
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS).inserted())
                    .isNotEqualTo(second.get(15, TimeUnit.SECONDS).inserted());
        }
        assertCounts(1, 2, 3);
    }

    @Test
    void concurrentDifferentVersionsLeaveACompleteActiveDataset() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return importer.importDocument(document("version-1"));
            });
            var second = executor.submit(() -> {
                start.await();
                return importer.importDocument(document("version-2"));
            });
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS).inserted()).isTrue();
            assertThat(second.get(15, TimeUnit.SECONDS).inserted()).isTrue();
        }
        assertCounts(2, 4, 6);
        assertThat(repository.activeDataset().orElseThrow().dataVersion()).isIn("version-1", "version-2");
        assertThat(repository.findMenusByCafe("cafe-1", 10, 0)).hasSize(2);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "101,0", "1,-1"})
    void rejectsInvalidPagination(int limit, int offset) {
        assertThatThrownBy(() -> repository.findMenusByCafe("cafe-1", limit, offset))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertCounts(int datasets, int cafes, int menus) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_dataset", Integer.class)).isEqualTo(datasets);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_cafe", Integer.class)).isEqualTo(cafes);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_menu", Integer.class)).isEqualTo(menus);
    }

    private byte[] replace(byte[] bytes, String oldValue, String newValue) {
        return new String(bytes, StandardCharsets.UTF_8).replace(oldValue, newValue).getBytes(StandardCharsets.UTF_8);
    }
}
