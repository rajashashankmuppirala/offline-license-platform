package com.enterprise.licensing.engine.metering;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.engine.activation.ActiveLicenseState;
import com.enterprise.licensing.engine.activation.LicenseRepository;
import com.enterprise.licensing.engine.instance.InstanceRegistrationResult;
import com.enterprise.licensing.engine.instance.ProductInstanceRepository;
import com.enterprise.licensing.engine.ports.IdGenerator;
import com.enterprise.licensing.engine.ports.TimeSource;
import com.enterprise.licensing.engine.validation.LicenseValidationResult;
import com.enterprise.licensing.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class MeteringServicePolicyTest {
    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

    private static LicenseRepository licenses() {
        var license = new CommercialLicense("1.0", "license", new CustomerIdentity("customer", "Customer", "a@b.com"),
                new SubscriptionEntitlement("SUBSCRIPTION", "TIER", NOW.minusSeconds(60), NOW.plusSeconds(3600), 0),
                new ActivationPolicy(false, 1, false),
                List.of(new EnvironmentEntitlement(EnvironmentType.PRODUCTION, 1, false)),
                new UserEntitlement(0, 0, List.of()), List.of(),
                new AIEntitlement(true, 0, 0, Set.of(), List.of(new MeterEntitlement("AI_EXECUTION_UNIT", MeterPeriod.MONTHLY, 10))),
                1, Set.of());
        return new LicenseRepository() {
            public void activate(LicenseServerIdentity identity, CommercialLicense value, SignedEnvelope envelope, ActivationCertificate certificate, SignedEnvelope activationEnvelope, Instant importedAt) {
                throw new UnsupportedOperationException();
            }

            public Optional<ActiveLicenseState> active() {
                return Optional.of(new ActiveLicenseState(license, null, null, null, NOW));
            }

            public boolean isRevoked(String type, String id, Instant at) {
                return false;
            }
        };
    }

    private static ProductInstanceRepository instances() {
        var key = new EncodedPublicKey("Ed25519", "X.509", "encoded");
        var instance = new RegisteredProductInstance("instance", key, "fingerprint", ProductId.VALIDATOR, DeploymentType.TOMCAT,
                EnvironmentType.PRODUCTION, "1", InstanceState.ACTIVE, NOW, NOW, null, Map.of());
        return new ProductInstanceRepository() {
            public InstanceRegistrationResult register(InstanceRegistrationRequest request, String fingerprint, int environmentLimit, int activeLimit, Instant now) {
                throw new UnsupportedOperationException();
            }

            public Optional<RegisteredProductInstance> find(String id) {
                return Optional.of(instance);
            }

            public List<RegisteredProductInstance> list() {
                return List.of(instance);
            }

            public InstanceRegistrationResult transition(String id, InstanceState expected, InstanceState target, int limit, Instant now, boolean force) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void productActionIsConvertedToServerOwnedWeightBeforeConsumption() {
        var captured = new AtomicLong();
        MeteringRepository repository = new MeteringRepository() {
            public UsageResult consume(String request, String customer, String instance, String pool, String meter, String action, long amount, Instant start, Instant end, long limit, Instant now, Map<String, String> dimensions) {
                captured.set(amount);
                return UsageResult.accepted(new UsageCounter(customer, pool, meter, start, end, amount, limit));
            }

            public List<UsageCounter> counters() {
                return List.of();
            }
        };
        var service = new MeteringService(licenses(), repository, () -> NOW, IdGenerator.random(),
                ignored -> LicenseValidationResult.allowed(false), instances(),
                new ConfiguredAEUPolicy(Map.of("AUTONOMOUS_AGENT_RUN", 5L)));

        var result = service.consumeAction("request", "instance", "AI_EXECUTION_UNIT", "AUTONOMOUS_AGENT_RUN", Map.of("model", "small"));

        assertThat(result.accepted()).isTrue();
        assertThat(captured.get()).isEqualTo(5);
        assertThat(service.consumeAction("unknown", "instance", "AI_EXECUTION_UNIT", "CALLER_CHOSEN_UNITS", Map.of()).reasonCode())
                .isEqualTo(LicenseReasonCode.INVALID_REQUEST);
        assertThat(captured.get()).isEqualTo(5);
    }
}
