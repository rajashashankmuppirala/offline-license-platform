package com.enterprise.licensing.engine.validation;

import com.enterprise.licensing.model.LicenseOperation;

@FunctionalInterface
public interface OperationAuthorizer {
    LicenseValidationResult evaluate(LicenseOperation operation);
}
