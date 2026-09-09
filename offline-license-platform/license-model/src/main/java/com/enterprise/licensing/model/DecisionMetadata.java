package com.enterprise.licensing.model;

import java.time.Instant;
import java.util.UUID;

public record DecisionMetadata(UUID decisionId, Instant decidedAt, String correlationId) {
    public DecisionMetadata {
        if (decisionId == null || decidedAt == null) throw new IllegalArgumentException("decision metadata is required");
        correlationId = Validation.required(correlationId, "correlationId");
    }
}
