package com.suwontrip.app.taste;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.suwontrip.app.catalog.persistence.CatalogDatabaseFixture;
import com.suwontrip.app.catalog.persistence.CatalogImporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class TastePersistenceContract {
    protected JdbcTemplate jdbc;
    protected DataSource dataSource;
    private TasteProfileImporter importer;
    private TasteProfileRepository repository;
    private final JsonMapper mapper = new JsonMapper();
    private String catalogHash;

    protected abstract DataSource database();

    @BeforeEach
    void initialize() throws Exception {
        dataSource = database();
        jdbc = new JdbcTemplate(dataSource);
        var transactionManager = new JdbcTransactionManager(dataSource);
        byte[] catalog = CatalogDatabaseFixture.document("catalog-test-v1");
        catalogHash = hash(catalog);
        new CatalogImporter(jdbc, transactionManager).importDocument(catalog);
        importer = new TasteProfileImporter(jdbc, transactionManager);
        repository = new TasteProfileRepository(jdbc);
    }

    @Test
    void storesEstimatedTasteWithProvenanceAndNoAutomaticApproval() throws Exception {
        var document = bundle();
        assertThat(importer.importDocument(mapper.writeValueAsBytes(document)).inserted()).isTrue();
        var profile = repository.find("profile-v1", "menu-1").orElseThrow();
        assertThat(profile.taste()).isEqualTo(new TasteVector(0.25, 0.1, 0.3));
        assertThat(profile.inputBasis()).isEqualTo("NAME_AND_DESCRIPTION");
        assertThat(profile.reviewStatus()).isEqualTo("UNREVIEWED");
        assertThat(jdbc.queryForObject("SELECT model_digest FROM taste_profile_run", String.class)).isEqualTo("test-model-digest");
        assertThat(jdbc.queryForObject("SELECT name FROM catalog_menu WHERE menu_id = 'menu-1'", String.class)).isEqualTo("라테");
    }

    @Test
    void repeatedIdenticalBundleDoesNotDuplicate() throws Exception {
        byte[] bytes = mapper.writeValueAsBytes(bundle());
        importer.importDocument(bytes);
        assertThat(importer.importDocument(bytes).inserted()).isFalse();
        assertCounts(1, 1);
    }

    @Test
    void immutableProfileRejectsChangedContent() throws Exception {
        importer.importDocument(mapper.writeValueAsBytes(bundle()));
        var changed = bundle().put("model", "different-model");
        assertThatThrownBy(() -> importer.importDocument(mapper.writeValueAsBytes(changed)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("different content");
        assertCounts(1, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "NOT_MENU"})
    void missingTasteStaysNull(String status) throws Exception {
        var document = bundle();
        var record = (ObjectNode) document.withArray("records").get(0);
        record.put("status", status).putNull("taste");
        importer.importDocument(mapper.writeValueAsBytes(document));
        assertThat(repository.find("profile-v1", "menu-1").orElseThrow().taste()).isNull();
        assertThat(jdbc.queryForObject("SELECT sweetness FROM menu_taste_profile", Double.class)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "-0.1", "1.1", "true", "\"0.5\""})
    void invalidAxisIsRejectedBeforeDatabaseWrites(String value) throws Exception {
        String json = mapper.writeValueAsString(bundle()).replace("\"sweetness\":0.25", "\"sweetness\":" + value);
        assertThatThrownBy(() -> importer.importDocument(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertCounts(0, 0);
    }

    @Test
    void statusCannotCarryInventedZeroScores() throws Exception {
        var document = bundle();
        ((ObjectNode) document.withArray("records").get(0)).put("status", "UNKNOWN");
        assertThatThrownBy(() -> importer.importDocument(mapper.writeValueAsBytes(document)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("disagree");
        assertCounts(0, 0);
    }

    @Test
    void wrongCatalogHashIsRejected() throws Exception {
        var document = bundle().put("catalogSha256", "0".repeat(64));
        assertThatThrownBy(() -> importer.importDocument(mapper.writeValueAsBytes(document)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("catalog version/hash mismatch");
        assertCounts(0, 0);
    }

    @Test
    void changedMenuInputRollsBackRunAndProfiles() throws Exception {
        var document = bundle();
        ((ObjectNode) document.withArray("records").get(0)).put("inputSha256", "0".repeat(64));
        assertThatThrownBy(() -> importer.importDocument(mapper.writeValueAsBytes(document)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("menu text/hash mismatch");
        assertCounts(0, 0);
    }

    @Test
    void missingMenuIsRejected() throws Exception {
        var document = bundle();
        ((ObjectNode) document.withArray("records").get(0)).put("menuId", "missing");
        assertThatThrownBy(() -> importer.importDocument(mapper.writeValueAsBytes(document)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("menu does not exist");
        assertCounts(0, 0);
    }

    @Test
    void duplicateMenuIsRejected() throws Exception {
        var document = bundle();
        ArrayNode records = document.withArray("records");
        records.add(records.get(0).deepCopy());
        assertThatThrownBy(() -> importer.importDocument(mapper.writeValueAsBytes(document)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate menu");
        assertCounts(0, 0);
    }

    @Test
    void batchInsertFailureRollsBackEveryRow() throws Exception {
        jdbc.execute("ALTER TABLE menu_taste_profile ADD CONSTRAINT reject_reason CHECK (reason <> 'REJECT')");
        var document = bundle();
        ObjectNode second = document.withArray("records").get(0).deepCopy();
        second.put("menuId", "menu-2").put("inputSha256", hash("가격 미확인 음료\0".getBytes(StandardCharsets.UTF_8)))
                .put("inputBasis", "NAME_ONLY").put("reason", "REJECT");
        document.withArray("records").add(second);
        assertThatThrownBy(() -> importer.importDocument(mapper.writeValueAsBytes(document)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertCounts(0, 0);
    }

    @Test
    void profilesFromInactiveCatalogAreNotReturned() throws Exception {
        importer.importDocument(mapper.writeValueAsBytes(bundle()));
        new CatalogImporter(jdbc, new JdbcTransactionManager(dataSource))
                .importDocument(CatalogDatabaseFixture.document("catalog-test-v2"));
        assertThat(repository.find("profile-v1", "menu-1")).isEmpty();
    }

    @Test
    void foreignKeysAndRangeChecksProtectDirectSqlWrites() throws Exception {
        importer.importDocument(mapper.writeValueAsBytes(bundle()));
        assertThatThrownBy(() -> jdbc.update("UPDATE menu_taste_profile SET sweetness = 2"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE menu_taste_profile SET menu_id = 'missing'"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(repository.find("profile-v1", "menu-1").orElseThrow().taste().sweetness()).isEqualTo(0.25);
    }

    private ObjectNode bundle() throws Exception {
        ObjectNode record = mapper.createObjectNode().put("menuId", "menu-1")
                .put("inputSha256", hash("라테\0테스트 음료".getBytes(StandardCharsets.UTF_8)))
                .put("inputBasis", "NAME_AND_DESCRIPTION").put("generatedAt", "2020-01-01T00:00:00Z")
                .put("status", "ESTIMATED").put("reason", "테스트 전용 추정치");
        record.putObject("taste").put("sweetness", 0.25).put("sourness", 0.1).put("bitterness", 0.3);
        ObjectNode bundle = mapper.createObjectNode().put("schemaVersion", 1).put("profileVersion", "profile-v1")
                .put("dataVersion", "catalog-test-v1").put("catalogSha256", catalogHash)
                .put("definitionVersion", "taste-v1").put("model", "test-model").put("modelDigest", "test-model-digest")
                .put("promptSha256", "a".repeat(64)).put("ollamaVersion", "test-version");
        bundle.putArray("records").add(record);
        return bundle;
    }

    private void assertCounts(int runs, int profiles) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM taste_profile_run", Integer.class)).isEqualTo(runs);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM menu_taste_profile", Integer.class)).isEqualTo(profiles);
    }

    private String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
