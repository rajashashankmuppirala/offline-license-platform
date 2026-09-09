package com.enterprise.licensing.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommercialLicenseContractTest {
    @Test void roundTripsVersionedTypedEntitlements() throws Exception {
        var license = new CommercialLicense("1.0", "LIC-1", new CustomerIdentity("C1", "Customer", "c@example.com"),
                new SubscriptionEntitlement("SUBSCRIPTION", "TEAMS", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"), 7),
                new ActivationPolicy(true, 1, true), List.of(new EnvironmentEntitlement(EnvironmentType.PRODUCTION, 1, false)),
                new UserEntitlement(10, 2, List.of()), List.of(new ProductEntitlement(ProductId.VALIDATOR, true, 10, Set.of(new FeatureId("TABLEAU")))),
                new AIEntitlement(false, 0, 0, Set.of(), List.of()), 1, Set.of());
        var mapper = mapper();
        var decoded = new CommercialLicenseJsonCodec(mapper).readVersioned(mapper.writeValueAsBytes(license));
        assertThat(decoded).isEqualTo(license);
        assertThat(decoded.products()).first().extracting(ProductEntitlement::id).isEqualTo(ProductId.VALIDATOR);
        assertThat(decoded.issuedAt()).isEqualTo(license.subscription().validFrom());
        assertThat(decoded.licenseSequence()).isEqualTo(1);
    }

    @Test void mapsLegacyBusinessFieldsWithoutGrantingAiOrImplicitEnvironments() {
        String legacy = """
                {"companyName":"A Inc.","contactEmail":"a@d.com","licenseType":"Subscription","startDate":"2025-08-11","endDate":"2026-08-14","subscriptionType":"Teams","totalNamedUsers":500,"concurrentUser":20,"totalReportingUsers":100,"totalDQIssueManagerUsers":100,"productCollection":[{"name":"Validator","noOfUsers":500,"subProducts":["tableau","oracle-analytics","cognos"]}]}
                """;
        var codec = new CommercialLicenseJsonCodec(mapper());
        var mapped = codec.read(legacy.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new CommercialLicenseJsonCodec.LegacyDefaults("LIC-AINC-LEGACY", "AINC", new ActivationPolicy(true, 1, true)));
        assertThat(mapped.users().maxConcurrentUsers()).isEqualTo(20);
        assertThat(mapped.products()).singleElement().satisfies(product -> {
            assertThat(product.id()).isEqualTo(ProductId.VALIDATOR);
            assertThat(product.features()).contains(new FeatureId("TABLEAU"), new FeatureId("ORACLE_ANALYTICS"), new FeatureId("COGNOS"));
        });
        assertThat(mapped.ai().enabled()).isFalse();
        assertThat(mapped.environments()).isEmpty();
    }

    @Test void rejectsUnsupportedVersionAndMalformedLegacyLicense() {
        var codec = new CommercialLicenseJsonCodec(mapper());
        var defaults = new CommercialLicenseJsonCodec.LegacyDefaults("LIC", "C", new ActivationPolicy(true, 1, true));
        assertThatThrownBy(() -> codec.read("{\"schemaVersion\":\"9.0\"}".getBytes(), defaults)).hasMessageContaining("Unsupported");
        assertThatThrownBy(() -> codec.read("{\"companyName\":\"A\"}".getBytes(), defaults)).hasMessageContaining("Missing legacy field");
    }

    @Test void strictSchemaRejectsUnknownMissingAndOutOfRangeFields() throws Exception {
        var mapper = mapper();
        var codec = new CommercialLicenseJsonCodec(mapper);
        var valid = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(mapper.writeValueAsBytes(validLicense()));

        var unknown = valid.deepCopy(); unknown.put("unlicensedExtension", true);
        assertThatThrownBy(() -> codec.readVersioned(mapper.writeValueAsBytes(unknown))).hasMessageContaining("schema violation");
        var missing = valid.deepCopy(); missing.remove("customer");
        assertThatThrownBy(() -> codec.readVersioned(mapper.writeValueAsBytes(missing))).hasMessageContaining("schema violation");
        var range = valid.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) range.path("users")).put("maxConcurrentUsers", -1);
        assertThatThrownBy(() -> codec.readVersioned(mapper.writeValueAsBytes(range))).hasMessageContaining("schema violation");
        var version = valid.deepCopy(); version.put("schemaVersion", "2.0");
        assertThatThrownBy(() -> codec.readVersioned(mapper.writeValueAsBytes(version))).isInstanceOf(CommercialLicenseSchema.UnsupportedVersionException.class);
    }

    @Test void publishedAndBundledSchemasAreByteForByteIdentical() throws Exception {
        assertThat(Files.readString(Path.of("../../specs/001-central-offline-license-server/contracts/commercial-license.schema.json")))
                .isEqualTo(Files.readString(Path.of("src/main/resources/schema/commercial-license-1.0.schema.json")));
    }

    private static CommercialLicense validLicense() {
        return new CommercialLicense("1.0", "LIC-1", new CustomerIdentity("C1", "Customer", "c@example.com"),
                new SubscriptionEntitlement("SUBSCRIPTION", "TEAMS", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"), 7),
                new ActivationPolicy(true, 1, true), List.of(new EnvironmentEntitlement(EnvironmentType.PRODUCTION, 1, false)),
                new UserEntitlement(10, 2, List.of()), List.of(), new AIEntitlement(false, 0, 0, Set.of(), List.of()), 1, Set.of());
    }

    private static ObjectMapper mapper() { return new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); }
}
