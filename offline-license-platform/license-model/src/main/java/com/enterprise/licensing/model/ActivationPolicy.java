package com.enterprise.licensing.model;

public record ActivationPolicy(boolean required, int maxLicenseServers, boolean renewalMayReuseActivation) {
    public ActivationPolicy {
        if (maxLicenseServers < 1) throw new IllegalArgumentException("maxLicenseServers must be positive");
    }
}
