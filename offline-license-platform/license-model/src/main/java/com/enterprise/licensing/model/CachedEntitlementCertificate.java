package com.enterprise.licensing.model;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record CachedEntitlementCertificate(String schemaVersion, String certificateId, String licenseId,
                                           String licenseServerId, String instanceId,
                                           String instancePublicKeyFingerprint, Instant issuedAt, Instant expiresAt,
                                           List<ProductEntitlement> products,
                                           Set<LicenseOperation> permittedOfflineOperations) {
    public CachedEntitlementCertificate {
        schemaVersion = Validation.required(schemaVersion, "schemaVersion");
        certificateId = Validation.required(certificateId, "certificateId");
        licenseId = Validation.required(licenseId, "licenseId");
        licenseServerId = Validation.required(licenseServerId, "licenseServerId");
        instanceId = Validation.required(instanceId, "instanceId");
        instancePublicKeyFingerprint = Validation.required(instancePublicKeyFingerprint, "instancePublicKeyFingerprint");
        if (issuedAt == null || expiresAt == null || !expiresAt.isAfter(issuedAt))
            throw new IllegalArgumentException("invalid cached entitlement interval");
        products = List.copyOf(products == null ? List.of() : products);
        permittedOfflineOperations = Set.copyOf(permittedOfflineOperations == null ? Set.of() : permittedOfflineOperations);
    }
}
