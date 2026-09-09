package com.enterprise.licensing.engine.instance;

import com.enterprise.licensing.model.InstanceRegistrationRequest;
import com.enterprise.licensing.model.InstanceState;
import com.enterprise.licensing.model.RegisteredProductInstance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProductInstanceRepository {
    InstanceRegistrationResult register(InstanceRegistrationRequest request, String fingerprint,
                                        int environmentLimit, int activeProductionClassLimit, Instant now);

    Optional<RegisteredProductInstance> find(String instanceId);

    List<RegisteredProductInstance> list();

    InstanceRegistrationResult transition(String instanceId, InstanceState expected, InstanceState target,
                                          int activeProductionClassLimit, Instant now, boolean force);

    default InstanceRegistrationResult switchActive(String activeInstanceId, String standbyInstanceId,
                                                    int activeProductionClassLimit, Instant now, boolean force) {
        throw new UnsupportedOperationException("Atomic active-instance switching is not supported");
    }
}
