package com.enterprise.licensing.client;

import com.enterprise.licensing.model.*;

import java.util.Map;

public interface LicenseClient {
    Map<String, Object> status();

    RegisteredProductInstance register(DeploymentType deployment, EnvironmentType environment, ProductId product, String version, Map<String, String> metadata);

    /** Administrator-authorized transition of this client's instance to ACTIVE. */
    default RegisteredProductInstance activateInstance() {
        throw new UnsupportedOperationException("Instance activation is not supported by this client");
    }

    /** Administrator-authorized registration of an agent belonging to this client's instance. */
    default RegisteredAgent registerAgent(String agentId, String displayName) {
        throw new UnsupportedOperationException("Agent registration is not supported by this client");
    }

    LicenseDecision product(ProductId product);

    LicenseDecision feature(ProductId product, FeatureId feature);

    LicenseDecision productUserCapacity(ProductId product, String userId);

    default void requireProduct(ProductId p) {
        var d = product(p);
        if (!d.allowed()) throw new LicenseClientDeniedException(d.reasonCode());
    }

    default void requireFeature(ProductId p, FeatureId f) {
        var d = feature(p, f);
        if (!d.allowed()) throw new LicenseClientDeniedException(d.reasonCode());
    }

    ConcurrentUserLease acquireUser(String userId, AccountType type, String sessionId);

    ConcurrentUserLease heartbeatUser(String leaseId);

    void releaseUser(String leaseId);

    AgentExecutionLease acquireAgentExecution(String agentId, String executionId, AiCapability capability);

    AgentExecutionLease heartbeatAgentExecution(String leaseId);

    void releaseAgentExecution(String leaseId);

    UsageCounter consumeAction(String meterId, String actionType, Map<String, String> dimensions);

    UsageCounter consumeAction(String requestId, String meterId, String actionType, Map<String, String> dimensions);

    SignedEnvelope refreshCachedEntitlement();
}
