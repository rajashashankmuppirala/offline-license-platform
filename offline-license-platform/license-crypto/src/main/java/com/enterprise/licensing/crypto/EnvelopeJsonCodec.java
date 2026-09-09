package com.enterprise.licensing.crypto;

import com.enterprise.licensing.model.SignedEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class EnvelopeJsonCodec {
    private final ObjectMapper mapper;

    public EnvelopeJsonCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(SignedEnvelope envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new CryptoException("Unable to encode signed envelope", e);
        }
    }

    public SignedEnvelope read(String json) {
        try {
            return mapper.readValue(json, SignedEnvelope.class);
        } catch (Exception e) {
            throw new CryptoException("Unable to decode signed envelope", e);
        }
    }

    public <T> T payload(SignedEnvelope envelope, Class<T> type) {
        try {
            return mapper.readValue(java.util.Base64.getUrlDecoder().decode(envelope.payload()), type);
        } catch (Exception e) {
            throw new CryptoException("Unable to decode signed payload", e);
        }
    }
}
