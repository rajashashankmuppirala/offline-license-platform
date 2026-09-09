package com.enterprise.licensing.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Ed25519KeysTest {
    @Test void signsAndVerifiesWithoutPrivateKeyAtVerificationTime() {
        var pair = Ed25519Keys.generate();
        byte[] payload = "licensed=20".getBytes(StandardCharsets.UTF_8);
        byte[] signature = Ed25519Keys.sign(pair.getPrivate(), payload);
        assertThat(Ed25519Keys.verify(pair.getPublic(), payload, signature)).isTrue();
        assertThat(Ed25519Keys.verify(pair.getPublic(), "licensed=200".getBytes(StandardCharsets.UTF_8), signature)).isFalse();
        assertThat(Ed25519Keys.decodePublic(Ed25519Keys.encodePublic(pair.getPublic())).getEncoded()).isEqualTo(pair.getPublic().getEncoded());
        assertThat(Ed25519Keys.decodePrivate(Ed25519Keys.encodePrivate(pair.getPrivate())).getEncoded()).isEqualTo(pair.getPrivate().getEncoded());
    }
}
