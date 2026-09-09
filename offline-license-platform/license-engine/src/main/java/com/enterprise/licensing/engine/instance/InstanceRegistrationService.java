package com.enterprise.licensing.engine.instance;

import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.engine.activation.LicenseRepository;
import com.enterprise.licensing.engine.ports.TimeSource;
import com.enterprise.licensing.model.EnvironmentEntitlement;
import com.enterprise.licensing.model.InstanceRegistrationRequest;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.engine.validation.OperationAuthorizer;
import com.enterprise.licensing.model.LicenseOperation;

public final class InstanceRegistrationService {
    private final LicenseRepository licenses; private final ProductInstanceRepository instances; private final TimeSource time; private final OperationAuthorizer authorizer;
    public InstanceRegistrationService(LicenseRepository licenses, ProductInstanceRepository instances, TimeSource time, OperationAuthorizer authorizer) {
        this.licenses = licenses; this.instances = instances; this.time = time; this.authorizer=authorizer;
    }
    public InstanceRegistrationResult register(InstanceRegistrationRequest request) {
        var authorization=authorizer.evaluate(LicenseOperation.INSTANCE_REGISTER);
        if(!authorization.allowed())return InstanceRegistrationResult.denied(authorization.reasonCode());
        var active = licenses.active().orElse(null);
        if (active == null) return InstanceRegistrationResult.denied(LicenseReasonCode.LICENSE_SERVER_NOT_ACTIVATED);
        var product = active.license().products().stream().filter(p -> p.id().equals(request.productId()) && p.enabled()).findFirst();
        if (product.isEmpty()) return InstanceRegistrationResult.denied(LicenseReasonCode.PRODUCT_NOT_ENTITLED);
        int limit = active.license().environments().stream().filter(e -> e.type() == request.environmentType())
                .mapToInt(EnvironmentEntitlement::maxInstances).findFirst().orElse(0);
        if (limit == 0) return InstanceRegistrationResult.denied(LicenseReasonCode.ENVIRONMENT_NOT_ENTITLED);
        try {
            var key = Ed25519Keys.decodePublic(request.installationPublicKey().encoded());
            return instances.register(request, Ed25519Keys.fingerprint(key), limit,
                    active.license().activeProductionClassLimit(), time.now());
        } catch (RuntimeException e) { return InstanceRegistrationResult.denied(LicenseReasonCode.INSTANCE_AUTHENTICATION_FAILED); }
    }
}
