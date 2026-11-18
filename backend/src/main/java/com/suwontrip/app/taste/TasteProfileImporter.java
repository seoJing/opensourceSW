package com.suwontrip.app.taste;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;

public final class TasteProfileImporter {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .withCoercionConfig(LogicalType.Textual, config -> config
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .build();

    public TasteProfileImporter(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
    }

    public Result importFile(Path path) {
        try {
            return importDocument(Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new IllegalArgumentException("cannot read taste bundle", exception);
        }
    }

    public Result importDocument(byte[] bytes) {
        Bundle bundle;
        try {
            bundle = mapper.readValue(bytes, Bundle.class);
        } catch (IOException exception) {
            throw new IllegalArgumentException("invalid taste bundle JSON", exception);
        }
        validate(bundle);
        String sha256 = sha256(bytes);
        return transaction.execute(status -> persist(bundle, sha256));
    }

    private void validate(Bundle bundle) {
        require(bundle != null && Integer.valueOf(1).equals(bundle.schemaVersion()), "unsupported bundle schema");
        require(text(bundle.profileVersion()) && bundle.profileVersion().length() <= 128, "invalid profile version");
        require(text(bundle.dataVersion()) && bundle.dataVersion().length() <= 128, "invalid data version");
        require("taste-v1".equals(bundle.definitionVersion()), "unsupported taste definition");
        require(hash(bundle.catalogSha256()) && hash(bundle.promptSha256()), "invalid catalog/prompt hash");
        require(text(bundle.model()) && text(bundle.modelDigest()) && text(bundle.ollamaVersion()), "model provenance is required");
        require(bundle.records() != null && !bundle.records().isEmpty(), "records must not be empty");
        Set<String> ids = new HashSet<>();
        for (Estimate record : bundle.records()) {
            require(record != null, "null estimate");
            require(record.menuId() != null && record.menuId().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"), "invalid menu id");
            require(ids.add(record.menuId()), "duplicate menu id");
            require(hash(record.inputSha256()), "invalid input hash");
            require(Set.of("NAME_ONLY", "NAME_AND_DESCRIPTION").contains(record.inputBasis() == null ? "" : record.inputBasis()), "invalid input basis");
            require(record.status() != null, "status is required");
            require((record.status() == Status.ESTIMATED) == (record.taste() != null), "status and taste disagree");
            require(text(record.reason()) && record.reason().length() <= 2000, "invalid reason");
            require(record.generatedAt() != null && !record.generatedAt().isAfter(Instant.now()), "invalid generation timestamp");
        }
    }

    private Result persist(Bundle bundle, String bundleSha256) {
        List<String> hashes = jdbc.queryForList(
                "SELECT source_sha256 FROM catalog_dataset WHERE data_version = ? FOR UPDATE",
                String.class, bundle.dataVersion());
        require(hashes.size() == 1 && hashes.getFirst().equals(bundle.catalogSha256()), "catalog version/hash mismatch");
        List<String> existing = jdbc.queryForList(
                "SELECT bundle_sha256 FROM taste_profile_run WHERE profile_version = ?",
                String.class, bundle.profileVersion());
        if (!existing.isEmpty()) {
            require(existing.getFirst().equals(bundleSha256), "profile version already has different content");
            return new Result(bundle.profileVersion(), bundle.records().size(), false);
        }
        jdbc.update("""
                INSERT INTO taste_profile_run
                    (profile_version, data_version, catalog_sha256, bundle_sha256, definition_version,
                     model, model_digest, prompt_sha256, ollama_version, record_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, bundle.profileVersion(), bundle.dataVersion(), bundle.catalogSha256(), bundleSha256,
                bundle.definitionVersion(), bundle.model(), bundle.modelDigest(), bundle.promptSha256(),
                bundle.ollamaVersion(), bundle.records().size());
        Map<String, MenuInput> inputs = jdbc.query(
                "SELECT menu_id, name, description FROM catalog_menu WHERE data_version = ?",
                (row, index) -> new MenuInput(row.getString("menu_id"), row.getString("name"), row.getString("description")),
                bundle.dataVersion()).stream().collect(Collectors.toMap(MenuInput::id, input -> input));
        List<Object[]> rows = new ArrayList<>();
        for (Estimate estimate : bundle.records()) {
            MenuInput input = inputs.get(estimate.menuId());
            require(input != null, "menu does not exist in catalog");
            String name = input.name();
            String description = input.description();
            String basis = description == null || description.isBlank() ? "NAME_ONLY" : "NAME_AND_DESCRIPTION";
            require(basis.equals(estimate.inputBasis()), "input basis mismatch");
            String inputSha256 = sha256((name + "\0" + (description == null ? "" : description)).getBytes(StandardCharsets.UTF_8));
            require(inputSha256.equals(estimate.inputSha256()), "menu text/hash mismatch");
            TasteVector taste = estimate.taste();
            rows.add(new Object[]{bundle.profileVersion(), bundle.dataVersion(), estimate.menuId(),
                    estimate.inputSha256(), estimate.inputBasis(), estimate.status().name(),
                    taste == null ? null : taste.sweetness(), taste == null ? null : taste.sourness(),
                    taste == null ? null : taste.bitterness(), estimate.reason(), estimate.generatedAt().atOffset(java.time.ZoneOffset.UTC)});
        }
        jdbc.batchUpdate("""
                INSERT INTO menu_taste_profile
                    (profile_version, data_version, menu_id, input_sha256, input_basis, status,
                     sweetness, sourness, bitterness, reason, generated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rows);
        return new Result(bundle.profileVersion(), bundle.records().size(), true);
    }

    private static boolean hash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record MenuInput(String id, String name, String description) { }

    public enum Status { ESTIMATED, UNKNOWN, NOT_MENU }

    public record Bundle(Integer schemaVersion, String profileVersion, String dataVersion, String catalogSha256,
                         String definitionVersion, String model, String modelDigest, String promptSha256,
                         String ollamaVersion, List<Estimate> records) { }

    public record Estimate(String menuId, String inputSha256, String inputBasis, Instant generatedAt,
                           Status status, TasteVector taste, String reason) { }

    public record Result(String profileVersion, int records, boolean inserted) { }
}
