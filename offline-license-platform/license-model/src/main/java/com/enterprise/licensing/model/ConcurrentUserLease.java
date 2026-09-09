package com.enterprise.licensing.model;

import java.time.Instant;

public record ConcurrentUserLease(String leaseId, String userId, AccountType accountType, String productInstanceId,
                                  String poolId,
                                  String sessionId, Instant acquiredAt, Instant heartbeatAt, Instant expiresAt,
                                  Instant releasedAt, Instant revokedAt) {
    public ConcurrentUserLease(String leaseId, String userId, AccountType accountType, String productInstanceId, String sessionId,
                               Instant acquiredAt, Instant heartbeatAt, Instant expiresAt, Instant releasedAt, Instant revokedAt) {
        this(leaseId, userId, accountType, productInstanceId, EntitlementPool.CUSTOMER_WIDE, sessionId, acquiredAt, heartbeatAt, expiresAt, releasedAt, revokedAt);
    }

    public ConcurrentUserLease {
        leaseId = Validation.required(leaseId, "leaseId");
        userId = Validation.required(userId, "userId");
        productInstanceId = Validation.required(productInstanceId, "productInstanceId");
        sessionId = Validation.required(sessionId, "sessionId");
        poolId = EntitlementPool.normalize(poolId);
        if (accountType == null || acquiredAt == null || heartbeatAt == null || expiresAt == null)
            throw new IllegalArgumentException("lease fields are required");
    }

    public boolean activeAt(Instant instant) {
        return releasedAt == null && revokedAt == null && expiresAt.isAfter(instant);
    }
}
