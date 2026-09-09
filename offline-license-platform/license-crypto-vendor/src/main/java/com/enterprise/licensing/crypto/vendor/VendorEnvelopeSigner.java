package com.enterprise.licensing.crypto.vendor;

import com.enterprise.licensing.crypto.CanonicalJson;
import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.crypto.EnvelopeSupport;
import com.enterprise.licensing.model.ArtifactType;
import com.enterprise.licensing.model.SignedEnvelope;

import java.security.KeyPair;
import java.util.Base64;

public final class VendorEnvelopeSigner implements PayloadSigner {
    private final CanonicalJson canonicalJson;
    public VendorEnvelopeSigner(CanonicalJson canonicalJson) { this.canonicalJson = canonicalJson; }

    @Override
    public SignedEnvelope sign(Object payload, String schemaVersion, ArtifactType artifactType, KeyPair vendorKeyPair) {
        if (artifactType == ArtifactType.CACHED_ENTITLEMENT) throw new IllegalArgumentException("vendor signer does not sign server cache artifacts");
        byte[] canonical = canonicalJson.canonicalize(payload);
        String keyId = Ed25519Keys.fingerprint(vendorKeyPair.getPublic());
        byte[] input = EnvelopeSupport.signatureInput(artifactType, schemaVersion, EnvelopeSupport.ALGORITHM, keyId, canonical);
        return new SignedEnvelope(EnvelopeSupport.ENVELOPE_VERSION, artifactType, schemaVersion, EnvelopeSupport.ALGORITHM,
                keyId, Base64.getUrlEncoder().withoutPadding().encodeToString(canonical),
                Base64.getUrlEncoder().withoutPadding().encodeToString(Ed25519Keys.sign(vendorKeyPair.getPrivate(), input)));
    }
}
