package com.enterprise.licensing.engine.identity;

import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.model.LicenseServerIdentity;
import com.enterprise.licensing.model.ArtifactType;
import com.enterprise.licensing.model.SignedEnvelope;
import com.enterprise.licensing.crypto.CanonicalJson;
import com.enterprise.licensing.crypto.runtime.LocalIdentityEnvelopeSigner;

import java.nio.charset.StandardCharsets;

public final class LicenseServerIdentityService {
    private final IdentityMaterial material;

    public LicenseServerIdentityService(IdentityProvider provider) {
        this.material = provider.loadOrCreate();
    }

    public LicenseServerIdentity publicIdentity() {
        return material.identity();
    }

    public byte[] signInstallationProof(byte[] content) {
        return Ed25519Keys.sign(material.privateKey(), content.clone());
    }

    public boolean verifiesInstallationProof(byte[] content, byte[] signature) {
        return Ed25519Keys.verify(material.publicKey(), content.clone(), signature.clone());
    }

    public String publicKeyFingerprint() {
        return material.identity().publicKeyFingerprint();
    }

    public SignedEnvelope signCachedEntitlement(Object payload, String schemaVersion, CanonicalJson canonicalJson) {
        return new LocalIdentityEnvelopeSigner(canonicalJson).signServerArtifact(payload, schemaVersion,
                ArtifactType.CACHED_ENTITLEMENT, material.privateKey(), material.publicKey());
    }
}
