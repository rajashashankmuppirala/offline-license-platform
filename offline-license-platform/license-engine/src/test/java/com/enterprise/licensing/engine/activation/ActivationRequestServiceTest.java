package com.enterprise.licensing.engine.activation;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.crypto.CanonicalJson;
import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.crypto.runtime.ActivationRequestProof;
import com.enterprise.licensing.engine.identity.IdentityMaterial;
import com.enterprise.licensing.engine.identity.LicenseServerIdentityService;
import com.enterprise.licensing.model.EncodedPublicKey;
import com.enterprise.licensing.model.LicenseServerIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ActivationRequestServiceTest {
    @Test
    void requestProvesPossessionWithoutContainingPrivateKey() {
        var pair = Ed25519Keys.generate();
        var identity = new LicenseServerIdentity(UUID.randomUUID(),
                new EncodedPublicKey("Ed25519", "X.509", Ed25519Keys.encodePublic(pair.getPublic())),
                Ed25519Keys.fingerprint(pair.getPublic()), Instant.parse("2026-01-01T00:00:00Z"), Map.of());
        var identityService = new LicenseServerIdentityService(() -> new IdentityMaterial(identity, pair.getPublic(), pair.getPrivate()));
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var canonical = new CanonicalJson(mapper);
        var service = new ActivationRequestService(identityService, () -> Instant.parse("2026-01-02T00:00:00Z"),
                () -> UUID.fromString("00000000-0000-0000-0000-000000000001"), canonical);

        var request = service.generate("LIC-1", "CUSTOMER-1", Map.of("site", "primary"));

        assertThat(new ActivationRequestProof(canonical).verify(request)).isTrue();
        assertThat(mapper.valueToTree(request).toString()).doesNotContain(Ed25519Keys.encodePrivate(pair.getPrivate()));
    }
}
