package com.enterprise.licensing.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;

/**
 * The single executable JSON-Schema contract for commercial-license payloads.
 */
public final class CommercialLicenseSchema {
    public static final String RESOURCE = "/schema/commercial-license-1.0.schema.json";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final String SCHEMA_TEXT = loadSchemaText();
    private static final Schema SCHEMA = SchemaRegistry.withDefaultDialect(
                    SpecificationVersion.DRAFT_2020_12,
                    builder -> builder.schemaRegistryConfig(SchemaRegistryConfig.builder()
                            .formatAssertionsEnabled(true).typeLoose(false).build()))
            .getSchema(SCHEMA_TEXT, InputFormat.JSON);

    private CommercialLicenseSchema() {
    }

    public static void validate(byte[] json) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed commercial license JSON", e);
        }
        if (root == null || !root.isObject())
            throw new IllegalArgumentException("Commercial license must be a JSON object");
        JsonNode version = root.get("schemaVersion");
        if (version != null && !CommercialLicenseJsonCodec.CURRENT_SCHEMA.equals(version.asText()))
            throw new UnsupportedVersionException(version.asText());
        var errors = SCHEMA.validate(new String(json, StandardCharsets.UTF_8), InputFormat.JSON);
        if (!errors.isEmpty()) {
            var first = errors.stream().min(Comparator.comparing(Object::toString)).orElseThrow();
            throw new IllegalArgumentException("Commercial license schema violation: " + first);
        }
    }

    public static void validate(CommercialLicense license) {
        try {
            validate(JSON.writeValueAsBytes(license));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to validate commercial license", e);
        }
    }

    public static JsonNode jsonTree(CommercialLicense license) {
        validate(license);
        return JSON.valueToTree(license);
    }

    public static String schemaText() {
        return SCHEMA_TEXT;
    }

    private static String loadSchemaText() {
        try (InputStream input = CommercialLicenseSchema.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Commercial license schema resource is missing");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load commercial license schema", e);
        }
    }

    public static final class UnsupportedVersionException extends IllegalArgumentException {
        public UnsupportedVersionException(String version) {
            super("Unsupported license schema: " + version);
        }
    }
}
