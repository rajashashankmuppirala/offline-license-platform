package com.enterprise.licensing.model;

public record UserCategoryEntitlement(UserCategory category, int limit) {
    public UserCategoryEntitlement {
        if (category == null) throw new IllegalArgumentException("category is required");
        Validation.nonNegative(limit, "limit");
    }
}
