package com.enterprise.licensing.engine.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.engine.ai.AgentLeaseService;
import com.enterprise.licensing.engine.instance.InstanceRegistrationResult;
import com.enterprise.licensing.engine.instance.ProductInstanceRepository;
import com.enterprise.licensing.engine.user.UserLicenseService;
import com.enterprise.licensing.model.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class PassiveInstanceRuntimeTest {
    @Test
    void standbyCannotAcquireHumanOrAgentLease() {
        var instance = new RegisteredProductInstance("standby", new EncodedPublicKey("Ed25519", "X.509", "AA"), "fp",
                ProductId.VALIDATOR, DeploymentType.KUBERNETES, EnvironmentType.DISASTER_RECOVERY, "1",
                InstanceState.STANDBY, Instant.EPOCH, Instant.EPOCH, null, Map.of());
        ProductInstanceRepository instances = new FixedInstances(instance);
        OperationAuthorizer allowed = ignored -> LicenseValidationResult.allowed(false);
        var users = new UserLicenseService(null, null, null, null, Duration.ZERO, Duration.ZERO, allowed, instances);
        var agents = new AgentLeaseService(null, null, null, null, Duration.ZERO, allowed, instances);
        assertThat(users.acquireLease("user", AccountType.HUMAN, "standby", "session").reasonCode())
                .isEqualTo(LicenseReasonCode.INSTANCE_NOT_REGISTERED);
        assertThat(agents.acquire("agent", "execution", "standby", AiCapability.AUTONOMOUS_AGENT).reasonCode())
                .isEqualTo(LicenseReasonCode.INSTANCE_NOT_REGISTERED);
    }

    private record FixedInstances(RegisteredProductInstance instance) implements ProductInstanceRepository {
        @Override
        public Optional<RegisteredProductInstance> find(String id) {
            return Optional.of(instance);
        }

        @Override
        public List<RegisteredProductInstance> list() {
            return List.of(instance);
        }

        @Override
        public InstanceRegistrationResult register(InstanceRegistrationRequest r, String f, int e, int a, Instant n) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InstanceRegistrationResult transition(String i, InstanceState e, InstanceState t, int l, Instant n, boolean f) {
            throw new UnsupportedOperationException();
        }
    }
}
