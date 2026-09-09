package com.enterprise.licensing.model;

public record SignedEnvelope(String envelopeVersion, ArtifactType artifactType, String payloadSchemaVersion,
                             String algorithm, String keyId, String payload, String signature) {
    public SignedEnvelope {
        envelopeVersion = Validation.required(envelopeVersion, "envelopeVersion");
        if (artifactType == null) throw new IllegalArgumentException("artifactType is required");
        payloadSchemaVersion = Validation.required(payloadSchemaVersion, "payloadSchemaVersion");
        algorithm = Validation.required(algorithm, "algorithm");
        keyId = Validation.required(keyId, "keyId");
        payload = Validation.required(payload, "payload");
        signature = Validation.required(signature, "signature");
    }
}
