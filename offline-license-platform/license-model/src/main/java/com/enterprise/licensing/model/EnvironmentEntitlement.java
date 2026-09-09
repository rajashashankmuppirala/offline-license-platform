package com.enterprise.licensing.model;

public record EnvironmentEntitlement(EnvironmentType type, int maxInstances, boolean activePassive, String poolId) {
    public EnvironmentEntitlement(EnvironmentType type, int maxInstances, boolean activePassive) {
        this(type, maxInstances, activePassive, EntitlementPool.CUSTOMER_WIDE);
    }

    public EnvironmentEntitlement {
        if (type == null) throw new IllegalArgumentException("type is required");
        Validation.nonNegative(maxInstances, "maxInstances");
        poolId = EntitlementPool.normalize(poolId);
    }
}
