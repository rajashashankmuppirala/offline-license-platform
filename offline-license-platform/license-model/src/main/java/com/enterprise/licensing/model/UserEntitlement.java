package com.enterprise.licensing.model;

import java.util.List;

public record UserEntitlement(int maxNamedUsers, int maxConcurrentUsers,
                              List<UserCategoryEntitlement> categories) {
    public UserEntitlement {
        Validation.nonNegative(maxNamedUsers, "maxNamedUsers");
        Validation.nonNegative(maxConcurrentUsers, "maxConcurrentUsers");
        categories = List.copyOf(Validation.noNulls(categories == null ? List.of() : categories, "categories"));
    }
}
