package com.enterprise.licensing.example.kubernetes;

import com.enterprise.licensing.client.identity.ProductInstanceIdentityStore;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;

/**
 * Every replica must mount the same durable identity file; pod names are deliberately irrelevant.
 */
public final class SharedInstallationIdentity {
    private SharedInstallationIdentity() {
    }

    public static ProductInstanceIdentityStore.Material load(Path sharedIdentityFile) {
        return new ProductInstanceIdentityStore(sharedIdentityFile, new ObjectMapper().findAndRegisterModules()).loadOrCreate();
    }
}
