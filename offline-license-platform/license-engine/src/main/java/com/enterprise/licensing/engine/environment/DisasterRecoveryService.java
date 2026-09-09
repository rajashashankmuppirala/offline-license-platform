package com.enterprise.licensing.engine.environment;

import com.enterprise.licensing.engine.activation.LicenseRepository;
import com.enterprise.licensing.engine.instance.InstanceRegistrationResult;
import com.enterprise.licensing.engine.instance.ProductInstanceRepository;
import com.enterprise.licensing.engine.ports.TimeSource;
import com.enterprise.licensing.engine.validation.OperationAuthorizer;
import com.enterprise.licensing.model.EnvironmentType;
import com.enterprise.licensing.model.InstanceState;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.model.LicenseOperation;

public final class DisasterRecoveryService {
    private final EnvironmentLifecycleService lifecycle;
    private final LicenseRepository licenses;
    private final ProductInstanceRepository instances;
    private final TimeSource time;
    private final OperationAuthorizer authorizer;

    public DisasterRecoveryService(EnvironmentLifecycleService lifecycle, LicenseRepository licenses,
                                   ProductInstanceRepository instances, TimeSource time, OperationAuthorizer authorizer) {
        this.lifecycle = lifecycle;
        this.licenses = licenses;
        this.instances = instances;
        this.time = time;
        this.authorizer = authorizer;
    }

    public InstanceRegistrationResult promote(String instanceId) {
        return lifecycle.activate(instanceId);
    }

    public InstanceRegistrationResult demote(String instanceId) {
        return lifecycle.standby(instanceId);
    }

    public InstanceRegistrationResult failover(String activeInstanceId, String standbyInstanceId, boolean force) {
        var authorization = authorizer.evaluate(LicenseOperation.INSTANCE_TRANSITION);
        if (!authorization.allowed()) return InstanceRegistrationResult.denied(authorization.reasonCode());
        var source = instances.find(activeInstanceId).orElse(null);
        var target = instances.find(standbyInstanceId).orElse(null);
        if (source == null || target == null)
            return InstanceRegistrationResult.denied(LicenseReasonCode.INSTANCE_NOT_REGISTERED);
        if (source.environmentType() != EnvironmentType.PRODUCTION || target.environmentType() != EnvironmentType.DISASTER_RECOVERY
                || source.state() != InstanceState.ACTIVE || target.state() != InstanceState.STANDBY)
            return InstanceRegistrationResult.denied(LicenseReasonCode.INVALID_REQUEST);
        return instances.switchActive(activeInstanceId, standbyInstanceId, activeLimit(), time.now(), force);
    }

    public InstanceRegistrationResult failback(String activeDrInstanceId, String standbyPrimaryInstanceId, boolean force) {
        var authorization = authorizer.evaluate(LicenseOperation.INSTANCE_TRANSITION);
        if (!authorization.allowed()) return InstanceRegistrationResult.denied(authorization.reasonCode());
        var source = instances.find(activeDrInstanceId).orElse(null);
        var target = instances.find(standbyPrimaryInstanceId).orElse(null);
        if (source == null || target == null)
            return InstanceRegistrationResult.denied(LicenseReasonCode.INSTANCE_NOT_REGISTERED);
        if (source.environmentType() != EnvironmentType.DISASTER_RECOVERY || target.environmentType() != EnvironmentType.PRODUCTION
                || source.state() != InstanceState.ACTIVE || target.state() != InstanceState.STANDBY)
            return InstanceRegistrationResult.denied(LicenseReasonCode.INVALID_REQUEST);
        return instances.switchActive(activeDrInstanceId, standbyPrimaryInstanceId, activeLimit(), time.now(), force);
    }

    private int activeLimit() {
        return licenses.active().map(state -> state.license().activeProductionClassLimit()).orElse(0);
    }
}
