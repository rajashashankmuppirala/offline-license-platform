package com.enterprise.licensing.engine.activation;

import com.enterprise.licensing.model.LicenseReasonCode;

import java.util.List;

public record ActivationImportResult(boolean accepted, LicenseReasonCode reasonCode, ActiveLicenseState state,
                                     List<String> warnings) {
    public ActivationImportResult {
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }

    public static ActivationImportResult denied(LicenseReasonCode reason) {
        return new ActivationImportResult(false, reason, null, List.of());
    }

    public static ActivationImportResult accepted(ActiveLicenseState state) {
        return accepted(state, List.of());
    }

    public static ActivationImportResult accepted(ActiveLicenseState state, List<String> warnings) {
        return new ActivationImportResult(true, LicenseReasonCode.LICENSE_VALID, state, warnings);
    }
}
