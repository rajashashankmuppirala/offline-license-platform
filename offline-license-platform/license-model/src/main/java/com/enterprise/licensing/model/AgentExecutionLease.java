package com.enterprise.licensing.model;

import java.time.Instant;

public record AgentExecutionLease(String leaseId, String agentId, String executionId, String productInstanceId,
                                  String poolId,
                                  AiCapability capability, Instant acquiredAt, Instant heartbeatAt, Instant expiresAt,
                                  Instant releasedAt, Instant revokedAt) {
    public AgentExecutionLease(String leaseId, String agentId, String executionId, String productInstanceId, AiCapability capability, Instant acquiredAt, Instant heartbeatAt, Instant expiresAt, Instant releasedAt, Instant revokedAt) {
        this(leaseId, agentId, executionId, productInstanceId, EntitlementPool.CUSTOMER_WIDE, capability, acquiredAt, heartbeatAt, expiresAt, releasedAt, revokedAt);
    }

    public AgentExecutionLease {
        leaseId = Validation.required(leaseId, "leaseId");
        agentId = Validation.required(agentId, "agentId");
        executionId = Validation.required(executionId, "executionId");
        productInstanceId = Validation.required(productInstanceId, "productInstanceId");
        poolId = EntitlementPool.normalize(poolId);
        if (capability == null || acquiredAt == null || heartbeatAt == null || expiresAt == null)
            throw new IllegalArgumentException("agent lease fields are required");
    }

    public boolean activeAt(Instant at) {
        return releasedAt == null && revokedAt == null && expiresAt.isAfter(at);
    }
}
