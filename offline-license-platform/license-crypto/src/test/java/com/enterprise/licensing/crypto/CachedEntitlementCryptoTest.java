package com.enterprise.licensing.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.crypto.runtime.CachedEntitlementVerifier;
import com.enterprise.licensing.crypto.runtime.LocalIdentityEnvelopeSigner;
import com.enterprise.licensing.crypto.runtime.RuntimeEnvelopeVerifier;
import com.enterprise.licensing.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CachedEntitlementCryptoTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final CanonicalJson canonical = new CanonicalJson(mapper);

    @Test
    void verifiesSignatureAndEveryIdentityBinding() {
        var keys = Ed25519Keys.generate();
        var envelope = signed(keys, now.plusSeconds(300));
        var verifier = verifier();

        assertThat(verifier.verify(envelope, keys.getPublic(), "server", "instance", "fingerprint", now).valid()).isTrue();
        assertThat(verifier.verify(envelope, keys.getPublic(), "other", "instance", "fingerprint", now).reasonCode())
                .isEqualTo(LicenseReasonCode.LICENSE_SERVER_IDENTITY_MISMATCH);
        assertThat(verifier.verify(envelope, keys.getPublic(), "server", "other", "fingerprint", now).valid()).isFalse();
        assertThat(verifier.verify(envelope, keys.getPublic(), "server", "instance", "other", now).valid()).isFalse();
        assertThat(verifier.verify(envelope, Ed25519Keys.generate().getPublic(), "server", "instance", "fingerprint", now).reasonCode())
                .isEqualTo(LicenseReasonCode.LICENSE_SIGNATURE_INVALID);
    }

    @Test
    void rejectsExpiryAndEnvelopeTampering() {
        var keys = Ed25519Keys.generate();
        var envelope = signed(keys, now.plusSeconds(1));
        assertThat(verifier().verify(envelope, keys.getPublic(), "server", "instance", "fingerprint", now.plusSeconds(2)).reasonCode())
                .isEqualTo(LicenseReasonCode.CACHED_ENTITLEMENT_EXPIRED);
        var tampered = new SignedEnvelope(envelope.envelopeVersion(), envelope.artifactType(), envelope.payloadSchemaVersion(),
                envelope.algorithm(), envelope.keyId(), envelope.payload(), envelope.signature().substring(1) + "A");
        assertThat(verifier().verify(tampered, keys.getPublic(), "server", "instance", "fingerprint", now).valid()).isFalse();
    }

    private SignedEnvelope signed(java.security.KeyPair keys, Instant expiry) {
        var certificate = new CachedEntitlementCertificate("1.0", "cache", "license", "server", "instance",
                "fingerprint", now, expiry, List.of(), Set.of(LicenseOperation.PRODUCT_CHECK));
        return new LocalIdentityEnvelopeSigner(canonical).signServerArtifact(certificate, "1.0",
                ArtifactType.CACHED_ENTITLEMENT, keys.getPrivate(), keys.getPublic());
    }

    private CachedEntitlementVerifier verifier() {
        return new CachedEntitlementVerifier(new RuntimeEnvelopeVerifier(canonical, Set.of("1.0")),
                new EnvelopeJsonCodec(mapper));
    }
}
