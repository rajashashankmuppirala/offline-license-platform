package com.enterprise.licensing.model;

import java.util.Map;

public record InstanceRegistrationRequest(String instanceId, EncodedPublicKey installationPublicKey,
                                          ProductId productId, DeploymentType deploymentType,
                                          EnvironmentType environmentType, String applicationVersion,
                                          Map<String, String> metadata) {
    public InstanceRegistrationRequest {
        instanceId = Validation.required(instanceId, "instanceId");
        applicationVersion = Validation.required(applicationVersion, "applicationVersion");
        if (installationPublicKey == null || productId == null || deploymentType == null || environmentType == null)
            throw new IllegalArgumentException("registration fields are required");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
