package com.enterprise.licensing.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.engine.ai.AgentLeaseResult;
import com.enterprise.licensing.engine.ai.AgentResult;
import com.enterprise.licensing.engine.metering.UsageResult;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

class AiLicensingApiContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void agentRegistrationDenialUsesStableReasonCode() {
        assertDenial(AgentResult.denied(LicenseReasonCode.AGENT_REGISTRATION_LIMIT_EXCEEDED),
                LicenseReasonCode.AGENT_REGISTRATION_LIMIT_EXCEEDED);
    }

    @Test
    void agentExecutionDenialUsesStableReasonCode() {
        assertDenial(AgentLeaseResult.denied(LicenseReasonCode.AGENT_CONCURRENCY_LIMIT_EXCEEDED),
                LicenseReasonCode.AGENT_CONCURRENCY_LIMIT_EXCEEDED);
    }

    @Test
    void meteringDenialUsesStableReasonCode() {
        assertDenial(UsageResult.denied(LicenseReasonCode.AI_USAGE_LIMIT_EXCEEDED, null),
                LicenseReasonCode.AI_USAGE_LIMIT_EXCEEDED);
    }

    private void assertDenial(Object data, LicenseReasonCode reasonCode) {
        var response = new ApiResponse<>(false, reasonCode, java.time.Instant.EPOCH, data);
        var json = mapper.valueToTree(response);
        assertThat(json.path("success").asBoolean()).isFalse();
        assertThat(json.path("reasonCode").asText()).isEqualTo(reasonCode.name());
        assertThat(json.path("timestamp").asText()).isEqualTo("1970-01-01T00:00:00Z");
        assertThat(json.path("data").path("accepted").asBoolean()).isFalse();
    }
}
