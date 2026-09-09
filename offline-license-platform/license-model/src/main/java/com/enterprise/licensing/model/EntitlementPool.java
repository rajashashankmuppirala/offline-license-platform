package com.enterprise.licensing.model;

public final class EntitlementPool {
    public static final String CUSTOMER_WIDE = "CUSTOMER_WIDE";

    private EntitlementPool() {
    }

    public static String normalize(String value) {
        return value == null || value.isBlank() ? CUSTOMER_WIDE : Validation.required(value, "poolId");
    }
}
