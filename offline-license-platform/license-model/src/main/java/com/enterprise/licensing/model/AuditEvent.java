package com.enterprise.licensing.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEvent(UUID auditId, Instant occurredAt, String customerId, String licenseId, String instanceId,
                         LicenseOperation operation, String entitlement, boolean allowed, LicenseReasonCode reasonCode,
                         Map<String, String> context, String previousHash, String eventHash) {
    public AuditEvent {
        if (auditId == null || occurredAt == null || operation == null || reasonCode == null)
            throw new IllegalArgumentException("audit fields are required");
        context = context == null ? Map.of() : Map.copyOf(context);
        eventHash = Validation.required(eventHash, "eventHash");
    }
}
