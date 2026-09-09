package com.enterprise.licensing.model;

import java.util.Map;

public record LicenseDecision(boolean allowed, LicenseReasonCode reasonCode, LicenseOperation operation,
                              DecisionMetadata metadata, Map<String, String> diagnosticContext) {
    public LicenseDecision {
        if (reasonCode == null || operation == null || metadata == null)
            throw new IllegalArgumentException("decision fields are required");
        diagnosticContext = diagnosticContext == null ? Map.of() : Map.copyOf(diagnosticContext);
    }
}
