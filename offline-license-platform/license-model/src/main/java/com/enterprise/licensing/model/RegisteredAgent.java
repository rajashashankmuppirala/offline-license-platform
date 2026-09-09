package com.enterprise.licensing.model;

import java.time.Instant;

public record RegisteredAgent(String agentId, String displayName, String productInstanceId, String poolId,
                              Instant registeredAt, AgentStatus status) {
    public RegisteredAgent(String agentId, String displayName, String productInstanceId, Instant registeredAt, AgentStatus status) {
        this(agentId, displayName, productInstanceId, EntitlementPool.CUSTOMER_WIDE, registeredAt, status);
    }

    public RegisteredAgent {
        agentId = Validation.required(agentId, "agentId");
        displayName = Validation.required(displayName, "displayName");
        productInstanceId = Validation.required(productInstanceId, "productInstanceId");
        poolId = EntitlementPool.normalize(poolId);
        if (registeredAt == null || status == null) throw new IllegalArgumentException("agent fields are required");
    }
}
