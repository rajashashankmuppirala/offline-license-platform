package com.enterprise.licensing.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ActivationRequest(String schemaVersion, UUID licenseServerId, EncodedPublicKey installationPublicKey,
                                String publicKeyFingerprint, String licenseId, String customerId, String nonce,
                                Instant createdAt, Map<String, String> environmentEvidence, String proofSignature) {
    public ActivationRequest {
        schemaVersion = Validation.required(schemaVersion, "schemaVersion");
        if (licenseServerId == null || installationPublicKey == null || createdAt == null)
            throw new IllegalArgumentException("activation request fields are required");
        publicKeyFingerprint = Validation.required(publicKeyFingerprint, "publicKeyFingerprint");
        licenseId = Validation.required(licenseId, "licenseId");
        customerId = Validation.required(customerId, "customerId");
        nonce = Validation.required(nonce, "nonce");
        proofSignature = Validation.required(proofSignature, "proofSignature");
        environmentEvidence = environmentEvidence == null ? Map.of() : Map.copyOf(environmentEvidence);
    }
}
