package com.enterprise.licensing.example.kubernetes;

import com.enterprise.licensing.engine.instance.InstanceRegistrationResult;
import com.enterprise.licensing.model.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Drives the same registration command for every replica without needing a container runtime.
 */
public final class KubernetesReplicaSimulator {
    public List<InstanceRegistrationResult> startReplicas(Path sharedIdentity, int replicas, RegistrationApi api) {
        if (replicas < 1) throw new IllegalArgumentException("replicas must be positive");
        return IntStream.range(0, replicas).mapToObj(replica -> {
            var material = SharedInstallationIdentity.load(sharedIdentity);
            var identity = material.identity();
            return api.register(new InstanceRegistrationRequest(identity.instanceId(), identity.publicKey(), ProductId.VALIDATOR,
                    DeploymentType.KUBERNETES, EnvironmentType.PRODUCTION, "1.0", Map.of("replicaOrdinal", Integer.toString(replica))));
        }).toList();
    }

    @FunctionalInterface
    public interface RegistrationApi {
        InstanceRegistrationResult register(InstanceRegistrationRequest request);
    }
}
