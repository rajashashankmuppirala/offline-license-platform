package com.enterprise.licensing.crypto.runtime;

import com.enterprise.licensing.model.LicenseReasonCode;

public record EnvelopeVerification(boolean valid, LicenseReasonCode reasonCode, byte[] canonicalPayload) {
    public EnvelopeVerification {
        canonicalPayload = canonicalPayload == null ? new byte[0] : canonicalPayload.clone();
    }

    @Override
    public byte[] canonicalPayload() {
        return canonicalPayload.clone();
    }
}
