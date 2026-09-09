package com.enterprise.licensing.crypto.runtime;

import com.enterprise.licensing.crypto.EnvelopeJsonCodec;
import com.enterprise.licensing.model.*;
import java.security.PublicKey;
import java.util.Base64;

public final class CommercialLicenseVerifier {
    private final SignatureVerifier verifier; private final EnvelopeJsonCodec codec;
    public CommercialLicenseVerifier(SignatureVerifier verifier, EnvelopeJsonCodec codec) { this.verifier = verifier; this.codec = codec; }
    public Result verify(SignedEnvelope envelope, PublicKey vendorPublicKey) {
        var verification = verifier.verify(envelope, ArtifactType.COMMERCIAL_LICENSE, vendorPublicKey);
        if (!verification.valid()) return new Result(null, verification.reasonCode());
        try { CommercialLicenseSchema.validate(Base64.getUrlDecoder().decode(envelope.payload()));return new Result(codec.payload(envelope, CommercialLicense.class), LicenseReasonCode.LICENSE_VALID); }
        catch (CommercialLicenseSchema.UnsupportedVersionException e) { return new Result(null, LicenseReasonCode.LICENSE_SCHEMA_UNSUPPORTED); }
        catch (RuntimeException e) { return new Result(null, LicenseReasonCode.LICENSE_FORMAT_INVALID); }
    }
    public record Result(CommercialLicense license, LicenseReasonCode reasonCode) { public boolean valid() { return license != null; } }
}
