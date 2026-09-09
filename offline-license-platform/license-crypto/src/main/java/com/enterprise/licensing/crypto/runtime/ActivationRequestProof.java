package com.enterprise.licensing.crypto.runtime;

import com.enterprise.licensing.crypto.CanonicalJson;
import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.model.ActivationRequest;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;

public final class ActivationRequestProof {
    private final CanonicalJson canonicalJson;

    public ActivationRequestProof(CanonicalJson canonicalJson) {
        this.canonicalJson = canonicalJson;
    }

    public byte[] content(ActivationRequest request) {
        var proof = new LinkedHashMap<String, Object>();
        proof.put("schemaVersion", request.schemaVersion());
        proof.put("licenseServerId", request.licenseServerId());
        proof.put("installationPublicKey", request.installationPublicKey());
        proof.put("publicKeyFingerprint", request.publicKeyFingerprint());
        proof.put("licenseId", request.licenseId());
        proof.put("customerId", request.customerId());
        proof.put("nonce", request.nonce());
        proof.put("createdAt", request.createdAt());
        proof.put("environmentEvidence", request.environmentEvidence());
        return canonicalJson.canonicalize(proof);
    }

    public boolean verify(ActivationRequest request) {
        try {
            PublicKey key = Ed25519Keys.decodePublic(request.installationPublicKey().encoded());
            if (!Ed25519Keys.fingerprint(key).equals(request.publicKeyFingerprint())) return false;
            return Ed25519Keys.verify(key, content(request), Base64.getUrlDecoder().decode(request.proofSignature()));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
