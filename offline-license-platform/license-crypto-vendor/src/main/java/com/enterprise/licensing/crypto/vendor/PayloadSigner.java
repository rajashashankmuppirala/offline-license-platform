package com.enterprise.licensing.crypto.vendor;

import com.enterprise.licensing.model.ArtifactType;
import com.enterprise.licensing.model.SignedEnvelope;

import java.security.KeyPair;

public interface PayloadSigner {
    SignedEnvelope sign(Object payload, String schemaVersion, ArtifactType artifactType, KeyPair vendorKeyPair);
}
