package com.enterprise.licensing.engine.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.engine.ai.AgentLeaseService;
import com.enterprise.licensing.engine.ai.AgentRegistryService;
import com.enterprise.licensing.engine.cache.CachedEntitlementService;
import com.enterprise.licensing.engine.entitlement.EntitlementService;
import com.enterprise.licensing.engine.environment.DisasterRecoveryService;
import com.enterprise.licensing.engine.environment.EnvironmentLifecycleService;
import com.enterprise.licensing.engine.instance.InstanceRegistrationService;
import com.enterprise.licensing.engine.metering.MeteringService;
import com.enterprise.licensing.engine.user.UserLicenseService;
import com.enterprise.licensing.model.*;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EngineAuthorizationBoundaryTest {
    @Test
    void directEngineCallsCannotBypassSecurityValidation() {
        Set<LicenseOperation> observed = new HashSet<>();
        OperationAuthorizer denied = operation -> {
            observed.add(operation);
            return LicenseValidationResult.denied(LicenseReasonCode.CLOCK_ROLLBACK_DETECTED);
        };

        assertDenied(new InstanceRegistrationService(null, null, null, denied).register(null).reasonCode());
        var lifecycle = new EnvironmentLifecycleService(null, null, null, denied);
        assertDenied(lifecycle.activate("i").reasonCode());
        assertDenied(lifecycle.standby("i").reasonCode());
        assertDenied(lifecycle.decommission("i", false).reasonCode());
        var dr = new DisasterRecoveryService(lifecycle, null, null, null, denied);
        assertDenied(dr.failover("p", "d", false).reasonCode());
        assertDenied(dr.failback("d", "p", false).reasonCode());

        var users = new UserLicenseService(null, null, null, null, Duration.ZERO, Duration.ZERO, denied, null);
        assertDenied(users.assignNamed("u", AccountType.HUMAN, ProductId.VALIDATOR, null).reasonCode());
        assertDenied(users.acquireLease("u", AccountType.HUMAN, "i", "s").reasonCode());
        assertDenied(users.heartbeat("i", "l").reasonCode());
        assertDenied(new AgentRegistryService(null, null, null, denied, null).register("a", "a", "i").reasonCode());
        var agentLeases = new AgentLeaseService(null, null, null, null, Duration.ZERO, denied, null);
        assertDenied(agentLeases.acquire("a", "e", "i", AiCapability.AUTONOMOUS_AGENT).reasonCode());
        assertDenied(agentLeases.heartbeat("i", "l").reasonCode());
        assertDenied(new MeteringService(null, null, null, null, denied, null, null)
                .consumeAction("r", "i", "AI_EXECUTION_UNIT", "INTERACTIVE_AI_REQUEST", Map.of()).reasonCode());
        assertDenied(new CachedEntitlementService(null, null, null, null, null, null, Duration.ZERO, null, denied)
                .issue("i").reasonCode());

        var entitlements = new EntitlementService(null, null, () -> Instant.EPOCH, () -> java.util.UUID.randomUUID(), denied);
        assertDenied(entitlements.checkProduct("i", ProductId.VALIDATOR, "c").reasonCode());
        assertDenied(entitlements.checkFeature("i", ProductId.VALIDATOR, new FeatureId("F"), "c").reasonCode());

        assertThat(observed).containsExactlyInAnyOrder(
                LicenseOperation.INSTANCE_REGISTER, LicenseOperation.INSTANCE_TRANSITION,
                LicenseOperation.INSTANCE_DECOMMISSION, LicenseOperation.NAMED_USER_ASSIGN,
                LicenseOperation.USER_LEASE_ACQUIRE, LicenseOperation.USER_LEASE_HEARTBEAT,
                LicenseOperation.AGENT_REGISTER, LicenseOperation.AGENT_LEASE_ACQUIRE,
                LicenseOperation.AGENT_LEASE_HEARTBEAT, LicenseOperation.USAGE_CONSUME,
                LicenseOperation.CACHED_ENTITLEMENT_ISSUE, LicenseOperation.PRODUCT_CHECK,
                LicenseOperation.FEATURE_CHECK);
    }

    private static void assertDenied(LicenseReasonCode reasonCode) {
        assertThat(reasonCode).isEqualTo(LicenseReasonCode.CLOCK_ROLLBACK_DETECTED);
    }
}
