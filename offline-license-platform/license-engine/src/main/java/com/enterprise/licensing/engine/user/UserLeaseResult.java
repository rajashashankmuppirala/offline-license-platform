package com.enterprise.licensing.engine.user;

import com.enterprise.licensing.model.ConcurrentUserLease;
import com.enterprise.licensing.model.LicenseReasonCode;

public record UserLeaseResult(boolean accepted, LicenseReasonCode reasonCode, ConcurrentUserLease lease) {
    public static UserLeaseResult accepted(ConcurrentUserLease value) {
        return new UserLeaseResult(true, LicenseReasonCode.LICENSE_VALID, value);
    }

    public static UserLeaseResult accepted(ConcurrentUserLease value, LicenseReasonCode reason) {
        return new UserLeaseResult(true, reason, value);
    }

    public static UserLeaseResult denied(LicenseReasonCode reason) {
        return new UserLeaseResult(false, reason, null);
    }
}
