package com.enterprise.licensing.client.availability;

import com.enterprise.licensing.model.LicenseOperation;
import java.util.Set;

/** Defines which operations may be resolved from a valid signed cache while the server is unavailable. */
public final class LicenseAvailabilityPolicy {
    private final Set<LicenseOperation> cachedOperations;

    public LicenseAvailabilityPolicy(Set<LicenseOperation> cachedOperations) {
        this.cachedOperations = Set.copyOf(cachedOperations);
    }

    public static LicenseAvailabilityPolicy pocDefaults() {
        return new LicenseAvailabilityPolicy(Set.of(
                LicenseOperation.PRODUCT_CHECK,
                LicenseOperation.FEATURE_CHECK));
    }

    public boolean mayUseCache(LicenseOperation operation) {
        return cachedOperations.contains(operation);
    }
}
