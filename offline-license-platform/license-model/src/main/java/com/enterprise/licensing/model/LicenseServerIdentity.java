package com.enterprise.licensing.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record LicenseServerIdentity(UUID licenseServerId, EncodedPublicKey publicKey, String publicKeyFingerprint,
                                    Instant createdAt, Map<String, String> installationMetadata) {
    public LicenseServerIdentity {
        if (licenseServerId == null || publicKey == null || createdAt == null)
            throw new IllegalArgumentException("identity fields are required");
        publicKeyFingerprint = Validation.required(publicKeyFingerprint, "publicKeyFingerprint");
        installationMetadata = installationMetadata == null ? Map.of() : Map.copyOf(installationMetadata);
    }
}
