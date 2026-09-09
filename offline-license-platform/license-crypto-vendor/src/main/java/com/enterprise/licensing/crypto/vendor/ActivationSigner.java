package com.enterprise.licensing.crypto.vendor;

import com.enterprise.licensing.model.ActivationCertificate;
import com.enterprise.licensing.model.ArtifactType;
import com.enterprise.licensing.model.SignedEnvelope;

import java.security.KeyPair;

public final class ActivationSigner {
    private final PayloadSigner signer;

    public ActivationSigner(PayloadSigner signer) {
        this.signer = signer;
    }

    public SignedEnvelope sign(ActivationCertificate certificate, KeyPair vendorKey) {
        return signer.sign(certificate, certificate.schemaVersion(), ArtifactType.ACTIVATION_CERTIFICATE, vendorKey);
    }
}
