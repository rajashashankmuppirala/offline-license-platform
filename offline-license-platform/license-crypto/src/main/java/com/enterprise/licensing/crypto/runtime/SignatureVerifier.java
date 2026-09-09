package com.enterprise.licensing.crypto.runtime;

import com.enterprise.licensing.model.ArtifactType;
import com.enterprise.licensing.model.SignedEnvelope;

import java.security.PublicKey;

public interface SignatureVerifier {
    EnvelopeVerification verify(SignedEnvelope envelope, ArtifactType expectedType, PublicKey key);
}
