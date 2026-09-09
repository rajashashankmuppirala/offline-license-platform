package com.enterprise.licensing.crypto.runtime;

import com.enterprise.licensing.crypto.CanonicalJson;
import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.crypto.EnvelopeSupport;
import com.enterprise.licensing.model.ArtifactType;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.model.SignedEnvelope;

import java.security.PublicKey;
import java.util.Base64;
import java.util.Set;

public final class RuntimeEnvelopeVerifier implements SignatureVerifier {
    private final CanonicalJson canonicalJson;
    private final Set<String> supportedSchemas;

    public RuntimeEnvelopeVerifier(CanonicalJson canonicalJson, Set<String> supportedSchemas) {
        this.canonicalJson = canonicalJson;
        this.supportedSchemas = Set.copyOf(supportedSchemas);
    }

    @Override
    public EnvelopeVerification verify(SignedEnvelope envelope, ArtifactType expectedType, PublicKey key) {
        if (envelope == null || key == null || envelope.artifactType() != expectedType) return invalid(LicenseReasonCode.LICENSE_FORMAT_INVALID);
        if (!EnvelopeSupport.ENVELOPE_VERSION.equals(envelope.envelopeVersion()) || !EnvelopeSupport.ALGORITHM.equals(envelope.algorithm())) return invalid(LicenseReasonCode.LICENSE_SCHEMA_UNSUPPORTED);
        if (!supportedSchemas.contains(envelope.payloadSchemaVersion())) return invalid(LicenseReasonCode.LICENSE_SCHEMA_UNSUPPORTED);
        try {
            byte[] encodedPayload = Base64.getUrlDecoder().decode(envelope.payload());
            byte[] canonicalPayload = canonicalJson.canonicalize(encodedPayload);
            if (!java.util.Arrays.equals(encodedPayload, canonicalPayload)) return invalid(LicenseReasonCode.LICENSE_FORMAT_INVALID);
            if (!Ed25519Keys.fingerprint(key).equals(envelope.keyId())) return invalid(LicenseReasonCode.LICENSE_SIGNATURE_INVALID);
            byte[] input = EnvelopeSupport.signatureInput(expectedType, envelope.payloadSchemaVersion(), envelope.algorithm(), envelope.keyId(), canonicalPayload);
            boolean valid = Ed25519Keys.verify(key, input, Base64.getUrlDecoder().decode(envelope.signature()));
            return valid ? new EnvelopeVerification(true, LicenseReasonCode.LICENSE_VALID, canonicalPayload) : invalid(LicenseReasonCode.LICENSE_SIGNATURE_INVALID);
        } catch (RuntimeException e) { return invalid(LicenseReasonCode.LICENSE_FORMAT_INVALID); }
    }

    private static EnvelopeVerification invalid(LicenseReasonCode reason) { return new EnvelopeVerification(false, reason, null); }
}
