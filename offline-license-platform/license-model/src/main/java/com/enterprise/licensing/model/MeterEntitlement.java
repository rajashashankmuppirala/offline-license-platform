package com.enterprise.licensing.model;

public record MeterEntitlement(String id, MeterPeriod period, long limit, String poolId) {
    public MeterEntitlement(String id, MeterPeriod period, long limit) {
        this(id, period, limit, EntitlementPool.CUSTOMER_WIDE);
    }

    public MeterEntitlement {
        id = Validation.required(id, "meterId");
        if (period == null) throw new IllegalArgumentException("period is required");
        Validation.positive(limit, "limit");
        poolId = EntitlementPool.normalize(poolId);
    }
}
