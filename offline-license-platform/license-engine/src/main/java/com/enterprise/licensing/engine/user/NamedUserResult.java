package com.enterprise.licensing.engine.user;

import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.model.NamedUserAssignment;

public record NamedUserResult(boolean accepted, LicenseReasonCode reasonCode, NamedUserAssignment assignment) {
    public static NamedUserResult accepted(NamedUserAssignment value) { return new NamedUserResult(true, LicenseReasonCode.LICENSE_VALID, value); }
    public static NamedUserResult denied(LicenseReasonCode reason) { return new NamedUserResult(false, reason, null); }
}
