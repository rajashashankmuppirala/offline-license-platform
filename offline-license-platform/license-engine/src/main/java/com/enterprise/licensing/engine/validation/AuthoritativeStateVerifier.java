package com.enterprise.licensing.engine.validation;

import com.enterprise.licensing.model.LicenseReasonCode;

/**
 * Verifies the separately checkpointed authoritative state before a grant decision.
 */
@FunctionalInterface
public interface AuthoritativeStateVerifier {
    LicenseReasonCode verifyOrInitialize();
}
