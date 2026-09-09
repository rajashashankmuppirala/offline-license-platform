package com.enterprise.licensing.model;

import java.time.Instant;

public record NamedUserAssignment(String assignmentId, String userId, AccountType accountType,
                                  ProductId productId, UserCategory category, Instant assignedAt,
                                  Instant releasedAt, Instant cooldownUntil) {
    public NamedUserAssignment {
        assignmentId = Validation.required(assignmentId, "assignmentId"); userId = Validation.required(userId, "userId");
        if (accountType == null || assignedAt == null) throw new IllegalArgumentException("assignment fields are required");
    }
    public boolean countsAt(Instant instant) { return releasedAt == null || (cooldownUntil != null && cooldownUntil.isAfter(instant)); }
}
