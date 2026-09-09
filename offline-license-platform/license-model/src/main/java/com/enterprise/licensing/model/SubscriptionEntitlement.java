package com.enterprise.licensing.model;

import java.time.Instant;

public record SubscriptionEntitlement(String type, String tier, Instant validFrom, Instant validUntil,
                                      int gracePeriodDays) {
    public SubscriptionEntitlement {
        type = Validation.required(type, "type");
        tier = Validation.required(tier, "tier");
        if (validFrom == null || validUntil == null || !validUntil.isAfter(validFrom)) throw new IllegalArgumentException("invalid subscription interval");
        Validation.nonNegative(gracePeriodDays, "gracePeriodDays");
    }

    public Instant graceUntil() {
        return validUntil.plus(java.time.Duration.ofDays(gracePeriodDays));
    }
}
