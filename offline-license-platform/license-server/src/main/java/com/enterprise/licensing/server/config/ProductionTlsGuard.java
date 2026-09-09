package com.enterprise.licensing.server.config;

import java.util.Arrays;

public final class ProductionTlsGuard {
    public ProductionTlsGuard(String[] activeProfiles,boolean tlsEnabled){
        if(Arrays.stream(activeProfiles).anyMatch("production"::equals)&&!tlsEnabled)
            throw new IllegalStateException("The production profile requires License Server HTTPS");
    }
}
