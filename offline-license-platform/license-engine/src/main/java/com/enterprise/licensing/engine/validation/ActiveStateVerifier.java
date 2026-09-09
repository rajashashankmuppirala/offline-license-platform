package com.enterprise.licensing.engine.validation;

import com.enterprise.licensing.engine.activation.ActiveLicenseState;

@FunctionalInterface
public interface ActiveStateVerifier {
    LicenseValidationResult verify(ActiveLicenseState state);
}
