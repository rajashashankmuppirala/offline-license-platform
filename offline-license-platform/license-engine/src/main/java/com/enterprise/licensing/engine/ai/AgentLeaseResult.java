package com.enterprise.licensing.engine.ai;

import com.enterprise.licensing.model.AgentExecutionLease;
import com.enterprise.licensing.model.LicenseReasonCode;

public record AgentLeaseResult(boolean accepted, LicenseReasonCode reasonCode, AgentExecutionLease lease) {
    public static AgentLeaseResult accepted(AgentExecutionLease l) {
        return new AgentLeaseResult(true, LicenseReasonCode.LICENSE_VALID, l);
    }

    public static AgentLeaseResult accepted(AgentExecutionLease l, LicenseReasonCode r) {
        return new AgentLeaseResult(true, r, l);
    }

    public static AgentLeaseResult denied(LicenseReasonCode r) {
        return new AgentLeaseResult(false, r, null);
    }
}
