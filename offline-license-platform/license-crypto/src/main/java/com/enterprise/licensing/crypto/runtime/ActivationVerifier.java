package com.enterprise.licensing.crypto.runtime;

import com.enterprise.licensing.crypto.EnvelopeJsonCodec;
import com.enterprise.licensing.model.*;
import java.security.PublicKey;

public final class ActivationVerifier {
    private final SignatureVerifier verifier; private final EnvelopeJsonCodec codec;
    public ActivationVerifier(SignatureVerifier verifier, EnvelopeJsonCodec codec) { this.verifier = verifier; this.codec = codec; }
    public Result verify(SignedEnvelope envelope, PublicKey vendorPublicKey) {
        var result = verifier.verify(envelope, ArtifactType.ACTIVATION_CERTIFICATE, vendorPublicKey);
        if (!result.valid()) return new Result(null, result.reasonCode());
        try { return new Result(codec.payload(envelope, ActivationCertificate.class), LicenseReasonCode.LICENSE_VALID); }
        catch (RuntimeException e) { return new Result(null, LicenseReasonCode.LICENSE_FORMAT_INVALID); }
    }
    public record Result(ActivationCertificate certificate, LicenseReasonCode reasonCode) { public boolean valid() { return certificate != null; } }
}
