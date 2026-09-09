package com.enterprise.licensing.crypto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.erdtman.jcs.JsonCanonicalizer;

import java.nio.charset.StandardCharsets;

public final class CanonicalJson {
    private final ObjectMapper objectMapper;

    public CanonicalJson(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public byte[] canonicalize(Object value) {
        try {
            return canonicalize(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException e) {
            throw new CryptoException("Unable to serialize signed payload", e);
        }
    }

    public byte[] canonicalize(byte[] json) {
        try {
            return new JsonCanonicalizer(new String(json, StandardCharsets.UTF_8)).getEncodedUTF8();
        } catch (Exception e) {
            throw new CryptoException("Malformed JSON payload", e);
        }
    }
}
