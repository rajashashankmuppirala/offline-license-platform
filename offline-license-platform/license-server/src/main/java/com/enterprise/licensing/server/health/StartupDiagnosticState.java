package com.enterprise.licensing.server.health;

import com.enterprise.licensing.model.LicenseReasonCode;

public final class StartupDiagnosticState {
    private volatile boolean safeMode;
    private volatile LicenseReasonCode reasonCode = LicenseReasonCode.LICENSE_VALID;
    private volatile String diagnosticCode = "NONE";

    public void enter(String diagnosticCode, LicenseReasonCode reasonCode) {
        this.diagnosticCode = diagnosticCode;
        this.reasonCode = reasonCode;
        safeMode = true;
    }

    public boolean safeMode() {
        return safeMode;
    }

    public LicenseReasonCode reasonCode() {
        return reasonCode;
    }

    public String diagnosticCode() {
        return diagnosticCode;
    }
}
