package com.enterprise.licensing.model;

import java.util.Set;

public record ProductEntitlement(ProductId id, boolean enabled, int maxUsers, Set<FeatureId> features) {
    public ProductEntitlement {
        if (id == null) throw new IllegalArgumentException("id is required");
        Validation.nonNegative(maxUsers, "maxUsers");
        features = Set.copyOf(Validation.noNulls(features == null ? Set.of() : features, "features"));
    }
}
