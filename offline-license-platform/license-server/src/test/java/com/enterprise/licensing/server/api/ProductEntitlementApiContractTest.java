package com.enterprise.licensing.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.model.DecisionMetadata;
import com.enterprise.licensing.model.LicenseDecision;
import com.enterprise.licensing.model.LicenseOperation;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

class ProductEntitlementApiContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void deniedFeatureDecisionHasStableEnvelopeAndDiagnosticContext() {
        var decision = new LicenseDecision(false, LicenseReasonCode.FEATURE_NOT_ENTITLED,
                LicenseOperation.FEATURE_CHECK, new DecisionMetadata(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                Instant.EPOCH, "correlation"), Map.of("productId", "VALIDATOR", "featureId", "UNKNOWN"));
        var json = mapper.valueToTree(new ApiResponse<>(false, decision.reasonCode(), Instant.EPOCH, decision));

        assertThat(json.path("success").asBoolean()).isFalse();
        assertThat(json.path("reasonCode").asText()).isEqualTo("FEATURE_NOT_ENTITLED");
        assertThat(json.path("data").path("operation").asText()).isEqualTo("FEATURE_CHECK");
        assertThat(json.path("data").path("diagnosticContext").path("productId").asText()).isEqualTo("VALIDATOR");
        assertThat(json.path("data").path("diagnosticContext").path("featureId").asText()).isEqualTo("UNKNOWN");
    }
}
