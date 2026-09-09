package com.enterprise.licensing.server.api.runtime;

import com.enterprise.licensing.engine.instance.InstanceRegistrationResult;
import com.enterprise.licensing.engine.instance.InstanceRegistrationService;
import com.enterprise.licensing.model.InstanceRegistrationRequest;
import com.enterprise.licensing.model.LicenseOperation;
import com.enterprise.licensing.server.api.ApiResponse;
import com.enterprise.licensing.server.security.DomainDecisionAudit;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/license/runtime/instances")
public final class InstanceRuntimeController {
    private final InstanceRegistrationService registrations;
    private final DomainDecisionAudit audit;

    public InstanceRuntimeController(InstanceRegistrationService registrations, DomainDecisionAudit audit) {
        this.registrations = registrations;
        this.audit = audit;
    }

    @PostMapping("/reconnect")
    public ApiResponse<InstanceRegistrationResult> reconnect(@RequestBody InstanceRegistrationRequest request) {
        var result = registrations.register(request);
        audit.record(LicenseOperation.INSTANCE_REGISTER, request.instanceId(), request.environmentType().name(), result.accepted(), result.reasonCode(), Map.of("deploymentType", request.deploymentType(), "productId", request.productId().value(), "reconnect", true));
        return new ApiResponse<>(result.accepted(), result.reasonCode(), Instant.now(), result);
    }
}
