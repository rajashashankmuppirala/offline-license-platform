package com.enterprise.licensing.engine.identity;

import com.enterprise.licensing.model.LicenseServerIdentity;
import java.security.PrivateKey;
import java.security.PublicKey;

public record IdentityMaterial(LicenseServerIdentity identity, PublicKey publicKey, PrivateKey privateKey) {
    public IdentityMaterial {
        if (identity == null || publicKey == null || privateKey == null) {
            throw new IllegalArgumentException("Complete identity material is required");
        }
    }
}
