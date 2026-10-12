package com.enterprise.licensing.example.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.client.LicenseClient;
import com.enterprise.licensing.model.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SampleProductApplicationTest {
    @Test
    void exercisesStatusFeatureUserLeaseAndMeteredAiFlowAndReleasesBothLeases() {
        FakeLicenseClient client = new FakeLicenseClient();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        new SampleProductApplication(client).exercise(
                "user-1", "session-1", "agent-1", "execution-1", "request-1", new PrintStream(bytes));

        assertThat(bytes.toString()).containsSubsequence(
                "STATUS ACTIVE",
                "INSTANCE ACTIVE instance-1",
                "PRODUCT VALIDATOR LICENSE_VALID",
                "FEATURE TABLEAU LICENSE_VALID",
                "AI_AGENT ACTIVE agent-1",
                "USER_LEASE ACTIVE user-lease-1",
                "AI_EXECUTION_LEASE ACTIVE agent-lease-1",
                "AI_METER ACCEPTED 1/100",
                "SAMPLE_PRODUCT_FLOW COMPLETE");
        assertThat(client.capacityUser).isEqualTo("user-1");
        assertThat(client.meterRequest).isEqualTo("request-1");
        assertThat(client.userReleased).isTrue();
        assertThat(client.agentReleased).isTrue();
    }

    private static final class FakeLicenseClient implements LicenseClient {
        private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        private boolean userReleased;
        private boolean agentReleased;
        private String capacityUser;
        private String meterRequest;

        @Override
        public Map<String, Object> status() {
            return Map.of("activationState", "ACTIVE");
        }

        @Override
        public RegisteredProductInstance register(DeploymentType deployment, EnvironmentType environment,
                                                  ProductId product, String version, Map<String, String> metadata) {
            var key = new EncodedPublicKey("Ed25519", "X.509", "AA");
            return new RegisteredProductInstance("instance-1", key, "fingerprint", product, deployment,
                    environment, version, InstanceState.REGISTERED, now, now, null, metadata);
        }

        @Override
        public RegisteredProductInstance activateInstance() {
            var key = new EncodedPublicKey("Ed25519", "X.509", "AA");
            return new RegisteredProductInstance("instance-1", key, "fingerprint", ProductId.VALIDATOR,
                    DeploymentType.OTHER, EnvironmentType.PRODUCTION, "1.0.0", InstanceState.ACTIVE,
                    now, now, null, Map.of());
        }

        @Override
        public RegisteredAgent registerAgent(String agentId, String displayName) {
            return new RegisteredAgent(agentId, displayName, "instance-1", now, AgentStatus.ACTIVE);
        }

        @Override
        public LicenseDecision product(ProductId product) {
            return allowed(LicenseOperation.PRODUCT_CHECK);
        }

        @Override
        public LicenseDecision feature(ProductId product, FeatureId feature) {
            return allowed(LicenseOperation.FEATURE_CHECK);
        }

        @Override
        public LicenseDecision productUserCapacity(ProductId product, String userId) {
            capacityUser = userId;
            return allowed(LicenseOperation.PRODUCT_USER_CAPACITY_CHECK);
        }

        @Override
        public ConcurrentUserLease acquireUser(String userId, AccountType type, String sessionId) {
            return new ConcurrentUserLease("user-lease-1", userId, type, "instance-1", sessionId,
                    now, now, now.plusSeconds(120), null, null);
        }

        @Override
        public ConcurrentUserLease heartbeatUser(String leaseId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void releaseUser(String leaseId) {
            userReleased = true;
        }

        @Override
        public AgentExecutionLease acquireAgentExecution(String agentId, String executionId,
                                                         AiCapability capability) {
            return new AgentExecutionLease("agent-lease-1", agentId, executionId, "instance-1", capability,
                    now, now, now.plusSeconds(120), null, null);
        }

        @Override
        public AgentExecutionLease heartbeatAgentExecution(String leaseId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void releaseAgentExecution(String leaseId) {
            agentReleased = true;
        }

        @Override
        public UsageCounter consumeAction(String meterId, String actionType, Map<String, String> dimensions) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UsageCounter consumeAction(String requestId, String meterId, String actionType,
                                          Map<String, String> dimensions) {
            meterRequest = requestId;
            return new UsageCounter("customer-1", meterId, now, now.plusSeconds(3600), 1, 100);
        }

        @Override
        public SignedEnvelope refreshCachedEntitlement() {
            throw new UnsupportedOperationException();
        }

        private LicenseDecision allowed(LicenseOperation operation) {
            return new LicenseDecision(true, LicenseReasonCode.LICENSE_VALID, operation,
                    new DecisionMetadata(UUID.randomUUID(), now, "sample-test"), Map.of());
        }
    }
}
