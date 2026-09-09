package com.enterprise.licensing.model;

import java.time.Instant;
import java.util.Map;

public record RegisteredProductInstance(String instanceId, EncodedPublicKey installationPublicKey,
                                        String publicKeyFingerprint, ProductId productId,
                                        DeploymentType deploymentType, EnvironmentType environmentType,
                                        String applicationVersion, InstanceState state,
                                        Instant registeredAt, Instant updatedAt, Instant decommissionedAt,
                                        Map<String, String> metadata) {
    public RegisteredProductInstance {
        instanceId = Validation.required(instanceId, "instanceId");
        publicKeyFingerprint = Validation.required(publicKeyFingerprint, "publicKeyFingerprint");
        applicationVersion = Validation.required(applicationVersion, "applicationVersion");
        if (installationPublicKey == null || productId == null || deploymentType == null || environmentType == null || state == null || registeredAt == null || updatedAt == null)
            throw new IllegalArgumentException("instance fields are required");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
