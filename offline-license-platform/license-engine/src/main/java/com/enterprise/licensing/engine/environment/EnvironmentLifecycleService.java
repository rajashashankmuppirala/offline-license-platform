package com.enterprise.licensing.engine.environment;

import com.enterprise.licensing.engine.activation.LicenseRepository;
import com.enterprise.licensing.engine.instance.InstanceRegistrationResult;
import com.enterprise.licensing.engine.instance.ProductInstanceRepository;
import com.enterprise.licensing.engine.ports.TimeSource;
import com.enterprise.licensing.model.InstanceState;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.model.LicenseOperation;
import com.enterprise.licensing.engine.validation.OperationAuthorizer;

public final class EnvironmentLifecycleService {
    private final LicenseRepository licenses;
    private final ProductInstanceRepository instances;
    private final TimeSource time;
    private final OperationAuthorizer authorizer;

    public EnvironmentLifecycleService(LicenseRepository licenses, ProductInstanceRepository instances, TimeSource time, OperationAuthorizer authorizer) {
        this.licenses = licenses;
        this.instances = instances;
        this.time = time;
        this.authorizer = authorizer;
    }

    public InstanceRegistrationResult decommission(String instanceId, boolean force) {
        var authorization = authorizer.evaluate(LicenseOperation.INSTANCE_DECOMMISSION);
        if (!authorization.allowed()) return InstanceRegistrationResult.denied(authorization.reasonCode());
        var current = instances.find(instanceId).orElse(null);
        if (current == null) return InstanceRegistrationResult.denied(LicenseReasonCode.INSTANCE_NOT_REGISTERED);
        return instances.transition(instanceId, current.state(), InstanceState.DECOMMISSIONED, activeLimit(), time.now(), force);
    }

    public InstanceRegistrationResult activate(String instanceId) {
        var authorization = authorizer.evaluate(LicenseOperation.INSTANCE_TRANSITION);
        if (!authorization.allowed()) return InstanceRegistrationResult.denied(authorization.reasonCode());
        var current = instances.find(instanceId).orElse(null);
        if (current == null) return InstanceRegistrationResult.denied(LicenseReasonCode.INSTANCE_NOT_REGISTERED);
        return instances.transition(instanceId, current.state(), InstanceState.ACTIVE, activeLimit(), time.now(), false);
    }

    public InstanceRegistrationResult standby(String instanceId) {
        var authorization = authorizer.evaluate(LicenseOperation.INSTANCE_TRANSITION);
        if (!authorization.allowed()) return InstanceRegistrationResult.denied(authorization.reasonCode());
        var current = instances.find(instanceId).orElse(null);
        if (current == null) return InstanceRegistrationResult.denied(LicenseReasonCode.INSTANCE_NOT_REGISTERED);
        return instances.transition(instanceId, current.state(), InstanceState.STANDBY, activeLimit(), time.now(), false);
    }

    private int activeLimit() {
        return licenses.active().map(s -> s.license().activeProductionClassLimit()).orElse(0);
    }
}
