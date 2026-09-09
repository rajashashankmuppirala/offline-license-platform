package com.enterprise.licensing.model;

import java.time.Instant;
import java.util.UUID;

public record ActivationCertificate(String schemaVersion, String activationId, String licenseId, String customerId,
                                    UUID licenseServerId, String publicKeyFingerprint, ActivationPolicy permittedPolicy,
                                    Instant issuedAt) {
    public ActivationCertificate {
        schemaVersion = Validation.required(schemaVersion, "schemaVersion");
        activationId = Validation.required(activationId, "activationId");
        licenseId = Validation.required(licenseId, "licenseId");
        customerId = Validation.required(customerId, "customerId");
        if (licenseServerId == null || permittedPolicy == null || issuedAt == null)
            throw new IllegalArgumentException("activation certificate fields are required");
        publicKeyFingerprint = Validation.required(publicKeyFingerprint, "publicKeyFingerprint");
    }
}
