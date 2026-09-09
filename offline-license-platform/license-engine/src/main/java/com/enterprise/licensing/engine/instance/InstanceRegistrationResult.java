package com.enterprise.licensing.engine.instance;

import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.model.RegisteredProductInstance;

public record InstanceRegistrationResult(boolean accepted, LicenseReasonCode reasonCode,
                                         RegisteredProductInstance instance) {
    public static InstanceRegistrationResult accepted(RegisteredProductInstance instance) {
        return new InstanceRegistrationResult(true, LicenseReasonCode.LICENSE_VALID, instance);
    }

    public static InstanceRegistrationResult denied(LicenseReasonCode reason) {
        return new InstanceRegistrationResult(false, reason, null);
    }
}
