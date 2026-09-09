package com.enterprise.licensing.engine.entitlement;

import com.enterprise.licensing.model.LicenseDecision;

public final class LicenseDeniedException extends RuntimeException {
    private final LicenseDecision decision;

    public LicenseDeniedException(LicenseDecision d) {
        super(d.reasonCode().name());
        decision = d;
    }

    public LicenseDecision decision() {
        return decision;
    }
}
