package com.enterprise.licensing.engine.identity;

public interface IdentityProvider {
    IdentityMaterial loadOrCreate();
}
