package com.enterprise.licensing.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalJsonTest {
    private final CanonicalJson canonical = new CanonicalJson(new ObjectMapper());

    @Test
    void canonicalizesEquivalentObjectsIdentically() {
        byte[] first = canonical.canonicalize("{\"z\":2,\"a\":1}".getBytes(StandardCharsets.UTF_8));
        byte[] second = canonical.canonicalize("{ \"a\" : 1, \"z\" : 2 }".getBytes(StandardCharsets.UTF_8));
        assertThat(first).isEqualTo(second).asString().isEqualTo("{\"a\":1,\"z\":2}");
    }
}
