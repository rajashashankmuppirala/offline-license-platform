package com.enterprise.licensing.crypto.runtime;

import com.enterprise.licensing.crypto.CanonicalJson;
import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.crypto.EnvelopeSupport;
import com.enterprise.licensing.model.ArtifactType;
import com.enterprise.licensing.model.SignedEnvelope;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

public final class LocalIdentityEnvelopeSigner {
    private final CanonicalJson canonicalJson;

    public LocalIdentityEnvelopeSigner(CanonicalJson canonicalJson) { this.canonicalJson = canonicalJson; }

    public SignedEnvelope signServerArtifact(Object payload, String schemaVersion, ArtifactType type,
                                             PrivateKey privateKey, PublicKey publicKey) {
        if (type != ArtifactType.CACHED_ENTITLEMENT) throw new IllegalArgumentException("runtime signer may sign only server cached entitlements");
        byte[] canonical = canonicalJson.canonicalize(payload);
        String keyId = Ed25519Keys.fingerprint(publicKey);
        byte[] input = EnvelopeSupport.signatureInput(type, schemaVersion, EnvelopeSupport.ALGORITHM, keyId, canonical);
        return new SignedEnvelope(EnvelopeSupport.ENVELOPE_VERSION, type, schemaVersion, EnvelopeSupport.ALGORITHM,
                keyId, Base64.getUrlEncoder().withoutPadding().encodeToString(canonical),
                Base64.getUrlEncoder().withoutPadding().encodeToString(Ed25519Keys.sign(privateKey, input)));
    }
}
