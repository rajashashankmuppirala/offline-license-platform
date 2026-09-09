package com.enterprise.licensing.engine.activation;

import com.enterprise.licensing.crypto.CanonicalJson;
import com.enterprise.licensing.crypto.runtime.ActivationRequestProof;
import com.enterprise.licensing.engine.identity.LicenseServerIdentityService;
import com.enterprise.licensing.engine.ports.IdGenerator;
import com.enterprise.licensing.engine.ports.TimeSource;
import com.enterprise.licensing.model.ActivationRequest;

import java.util.Base64;
import java.util.Map;

public final class ActivationRequestService {
    private final LicenseServerIdentityService identityService;
    private final TimeSource timeSource;
    private final IdGenerator ids;
    private final ActivationRequestProof proof;

    public ActivationRequestService(LicenseServerIdentityService identityService, TimeSource timeSource,
                                    IdGenerator ids, CanonicalJson canonicalJson) {
        this.identityService = identityService;
        this.timeSource = timeSource;
        this.ids = ids;
        this.proof = new ActivationRequestProof(canonicalJson);
    }

    public ActivationRequest generate(String licenseId, String customerId, Map<String, String> evidence) {
        var identity = identityService.publicIdentity();
        var unsigned = new ActivationRequest("1.0", identity.licenseServerId(), identity.publicKey(),
                identity.publicKeyFingerprint(), licenseId, customerId, ids.next().toString(),
                timeSource.now(), evidence, "UNSIGNED");
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(identityService.signInstallationProof(proof.content(unsigned)));
        return new ActivationRequest(unsigned.schemaVersion(), unsigned.licenseServerId(), unsigned.installationPublicKey(),
                unsigned.publicKeyFingerprint(), unsigned.licenseId(), unsigned.customerId(), unsigned.nonce(),
                unsigned.createdAt(), unsigned.environmentEvidence(), signature);
    }
}
