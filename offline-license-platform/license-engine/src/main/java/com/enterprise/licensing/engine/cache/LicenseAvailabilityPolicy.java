package com.enterprise.licensing.engine.cache;

import com.enterprise.licensing.model.LicenseOperation;

import java.util.Set;

public final class LicenseAvailabilityPolicy {
    private final Set<LicenseOperation> cachedAllowed;

    public LicenseAvailabilityPolicy(Set<LicenseOperation> s) {
        cachedAllowed = Set.copyOf(s);
    }

    public static LicenseAvailabilityPolicy pocDefaults() {
        return new LicenseAvailabilityPolicy(Set.of(LicenseOperation.PRODUCT_CHECK, LicenseOperation.FEATURE_CHECK));
    }

    public boolean mayUseCache(LicenseOperation operation) {
        return cachedAllowed.contains(operation);
    }
}
