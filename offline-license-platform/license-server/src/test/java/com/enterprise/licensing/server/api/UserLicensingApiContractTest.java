package com.enterprise.licensing.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.engine.user.NamedUserResult;
import com.enterprise.licensing.engine.user.UserLeaseResult;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

class UserLicensingApiContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void namedUserDenialHasStableEnvelopeAndReasonCode() {
        var result = NamedUserResult.denied(LicenseReasonCode.NAMED_USER_LIMIT_EXCEEDED);
        var response = new ApiResponse<>(false, result.reasonCode(), java.time.Instant.EPOCH, result);
        var json = mapper.valueToTree(response);

        assertThat(json.path("success").asBoolean()).isFalse();
        assertThat(json.path("reasonCode").asText()).isEqualTo("NAMED_USER_LIMIT_EXCEEDED");
        assertThat(json.path("timestamp").asText()).isEqualTo("1970-01-01T00:00:00Z");
        assertThat(json.path("data").path("accepted").asBoolean()).isFalse();
    }

    @Test
    void concurrentLeaseDenialHasStableEnvelopeAndReasonCode() {
        var result = UserLeaseResult.denied(LicenseReasonCode.CONCURRENT_USER_LIMIT_EXCEEDED);
        var response = new ApiResponse<>(false, result.reasonCode(), java.time.Instant.EPOCH, result);
        var json = mapper.valueToTree(response);

        assertThat(json.path("success").asBoolean()).isFalse();
        assertThat(json.path("reasonCode").asText()).isEqualTo("CONCURRENT_USER_LIMIT_EXCEEDED");
        assertThat(json.path("data").path("accepted").asBoolean()).isFalse();
    }
}
