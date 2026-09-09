package com.enterprise.licensing.engine.ai;

import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.model.RegisteredAgent;

public record AgentResult(boolean accepted, LicenseReasonCode reasonCode, RegisteredAgent agent) {
    public static AgentResult accepted(RegisteredAgent a) {
        return new AgentResult(true, LicenseReasonCode.LICENSE_VALID, a);
    }

    public static AgentResult denied(LicenseReasonCode r) {
        return new AgentResult(false, r, null);
    }
}
