package com.enterprise.licensing.example.tomcat;

import com.enterprise.licensing.client.identity.ProductInstanceIdentityStore;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;

/**
 * Store outside the exploded WAR so a redeploy or Tomcat restart retains the licensed identity.
 */
public final class TomcatInstallationIdentity {
    private TomcatInstallationIdentity() {
    }

    public static ProductInstanceIdentityStore.Material load(Path tomcatDataDirectory) {
        return new ProductInstanceIdentityStore(tomcatDataDirectory.resolve("license-instance-identity.json"), new ObjectMapper().findAndRegisterModules()).loadOrCreate();
    }
}
