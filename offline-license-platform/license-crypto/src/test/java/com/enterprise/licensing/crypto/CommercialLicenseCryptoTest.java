package com.enterprise.licensing.crypto;

import com.enterprise.licensing.crypto.runtime.RuntimeEnvelopeVerifier;
import com.enterprise.licensing.crypto.runtime.CommercialLicenseVerifier;
import com.enterprise.licensing.model.ArtifactType;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.model.SignedEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CommercialLicenseCryptoTest {
    private static SignedEnvelope signed(CanonicalJson canonical, java.security.KeyPair key, String json) {
        byte[] payload = canonical.canonicalize(json.getBytes(StandardCharsets.UTF_8));
        String keyId = Ed25519Keys.fingerprint(key.getPublic());
        byte[] signature = Ed25519Keys.sign(key.getPrivate(), EnvelopeSupport.signatureInput(ArtifactType.COMMERCIAL_LICENSE, "1.0", "Ed25519", keyId, payload));
        return new SignedEnvelope("1", ArtifactType.COMMERCIAL_LICENSE, "1.0", "Ed25519", keyId, Base64.getUrlEncoder().withoutPadding().encodeToString(payload), Base64.getUrlEncoder().withoutPadding().encodeToString(signature));
    }

    @Test
    void rejectsAnyProtectedPayloadChangeAndWrongKey() {
        var mapper = new ObjectMapper();
        var canonical = new CanonicalJson(mapper);
        var key = Ed25519Keys.generate();
        byte[] payload = canonical.canonicalize("{\"concurrentUsers\":20}".getBytes(StandardCharsets.UTF_8));
        String keyId = Ed25519Keys.fingerprint(key.getPublic());
        byte[] input = EnvelopeSupport.signatureInput(ArtifactType.COMMERCIAL_LICENSE, "1.0", "Ed25519", keyId, payload);
        var envelope = new SignedEnvelope("1", ArtifactType.COMMERCIAL_LICENSE, "1.0", "Ed25519", keyId,
                Base64.getUrlEncoder().withoutPadding().encodeToString(payload), Base64.getUrlEncoder().withoutPadding().encodeToString(Ed25519Keys.sign(key.getPrivate(), input)));
        var verifier = new RuntimeEnvelopeVerifier(canonical, Set.of("1.0"));
        assertThat(verifier.verify(envelope, ArtifactType.COMMERCIAL_LICENSE, key.getPublic()).valid()).isTrue();
        var changed = new SignedEnvelope(envelope.envelopeVersion(), envelope.artifactType(), envelope.payloadSchemaVersion(), envelope.algorithm(), envelope.keyId(),
                Base64.getUrlEncoder().withoutPadding().encodeToString("{\"concurrentUsers\":200}".getBytes(StandardCharsets.UTF_8)), envelope.signature());
        assertThat(verifier.verify(changed, ArtifactType.COMMERCIAL_LICENSE, key.getPublic()).valid()).isFalse();
        assertThat(verifier.verify(envelope, ArtifactType.COMMERCIAL_LICENSE, Ed25519Keys.generate().getPublic()).valid()).isFalse();
    }

    @Test
    void rejectsSignatureMutationMalformedPayloadUnsupportedSchemaAndWrongArtifactType() {
        var mapper = new ObjectMapper();
        var canonical = new CanonicalJson(mapper);
        var key = Ed25519Keys.generate();
        byte[] payload = canonical.canonicalize("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        String keyId = Ed25519Keys.fingerprint(key.getPublic());
        var valid = new SignedEnvelope("1", ArtifactType.COMMERCIAL_LICENSE, "1.0", "Ed25519", keyId, Base64.getUrlEncoder().withoutPadding().encodeToString(payload), Base64.getUrlEncoder().withoutPadding().encodeToString(Ed25519Keys.sign(key.getPrivate(), EnvelopeSupport.signatureInput(ArtifactType.COMMERCIAL_LICENSE, "1.0", "Ed25519", keyId, payload))));
        var verifier = new RuntimeEnvelopeVerifier(canonical, Set.of("1.0"));
        var mutated = new SignedEnvelope("1", ArtifactType.COMMERCIAL_LICENSE, "1.0", "Ed25519", keyId, valid.payload(), Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[64]));
        var malformed = new SignedEnvelope("1", ArtifactType.COMMERCIAL_LICENSE, "1.0", "Ed25519", keyId, "not-base64!", valid.signature());
        var unsupported = new SignedEnvelope("1", ArtifactType.COMMERCIAL_LICENSE, "2.0", "Ed25519", keyId, valid.payload(), valid.signature());
        assertThat(verifier.verify(mutated, ArtifactType.COMMERCIAL_LICENSE, key.getPublic()).reasonCode()).isEqualTo(com.enterprise.licensing.model.LicenseReasonCode.LICENSE_SIGNATURE_INVALID);
        assertThat(verifier.verify(malformed, ArtifactType.COMMERCIAL_LICENSE, key.getPublic()).reasonCode()).isEqualTo(com.enterprise.licensing.model.LicenseReasonCode.LICENSE_FORMAT_INVALID);
        assertThat(verifier.verify(unsupported, ArtifactType.COMMERCIAL_LICENSE, key.getPublic()).reasonCode()).isEqualTo(com.enterprise.licensing.model.LicenseReasonCode.LICENSE_SCHEMA_UNSUPPORTED);
        assertThat(verifier.verify(valid, ArtifactType.ACTIVATION_CERTIFICATE, key.getPublic()).reasonCode()).isEqualTo(com.enterprise.licensing.model.LicenseReasonCode.LICENSE_FORMAT_INVALID);
    }

    @Test
    void cryptographicallyValidButSchemaInvalidPayloadIsRejectedAfterSignatureVerification() {
        var mapper = new ObjectMapper();
        var canonical = new CanonicalJson(mapper);
        var key = Ed25519Keys.generate();
        var verifier = new CommercialLicenseVerifier(new RuntimeEnvelopeVerifier(canonical, Set.of("1.0")), new EnvelopeJsonCodec(mapper));

        assertThat(verifier.verify(signed(canonical, key, "{\"schemaVersion\":\"1.0\",\"unknown\":true}"), key.getPublic()).reasonCode())
                .isEqualTo(LicenseReasonCode.LICENSE_FORMAT_INVALID);
        assertThat(verifier.verify(signed(canonical, key, "{\"schemaVersion\":\"2.0\"}"), key.getPublic()).reasonCode())
                .isEqualTo(LicenseReasonCode.LICENSE_SCHEMA_UNSUPPORTED);
    }
}
