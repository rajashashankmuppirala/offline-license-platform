package com.enterprise.licensing.server.api.admin;

import com.enterprise.licensing.engine.environment.DisasterRecoveryService;
import com.enterprise.licensing.engine.environment.EnvironmentLifecycleService;
import com.enterprise.licensing.engine.instance.*;
import com.enterprise.licensing.model.LicenseOperation;
import com.enterprise.licensing.model.RegisteredProductInstance;
import com.enterprise.licensing.server.api.ApiResponse;
import com.enterprise.licensing.server.security.DomainDecisionAudit;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/license/admin/instances")
public final class InstanceController {
    private final InstanceRegistrationService registrations;
    private final ProductInstanceRepository repository;
    private final EnvironmentLifecycleService lifecycle;
    private final DisasterRecoveryService disasterRecovery;
    private final DomainDecisionAudit audit;

    public InstanceController(InstanceRegistrationService registrations, ProductInstanceRepository repository,
                              EnvironmentLifecycleService lifecycle, DisasterRecoveryService disasterRecovery,
                              DomainDecisionAudit audit) {
        this.registrations=registrations;this.repository=repository;this.lifecycle=lifecycle;
        this.disasterRecovery=disasterRecovery;this.audit=audit;
    }

    @PostMapping("/register") public ApiResponse<InstanceRegistrationResult> register(@RequestBody com.enterprise.licensing.model.InstanceRegistrationRequest request) {
        var result=registrations.register(request);record(result,LicenseOperation.INSTANCE_REGISTER,request.instanceId(),request.environmentType().name(),Map.of("deploymentType",request.deploymentType(),"productId",request.productId().value()));return response(result);
    }
    @GetMapping public ApiResponse<List<RegisteredProductInstance>> list(){return ApiResponse.success(repository.list());}
    @DeleteMapping("/{id}") public ApiResponse<InstanceRegistrationResult> decommission(@PathVariable("id") String id,@RequestParam(defaultValue="false")boolean force){var result=lifecycle.decommission(id,force);record(result,LicenseOperation.INSTANCE_DECOMMISSION,id,"ENVIRONMENT",Map.of("force",force));return response(result);}
    @PostMapping("/{id}/activate") public ApiResponse<InstanceRegistrationResult> activate(@PathVariable("id") String id){var result=lifecycle.activate(id);record(result,LicenseOperation.INSTANCE_TRANSITION,id,"ENVIRONMENT",Map.of("targetState","ACTIVE"));return response(result);}
    @PostMapping("/{id}/standby") public ApiResponse<InstanceRegistrationResult> standby(@PathVariable("id") String id){var result=lifecycle.standby(id);record(result,LicenseOperation.INSTANCE_TRANSITION,id,"ENVIRONMENT",Map.of("targetState","STANDBY"));return response(result);}
    @PostMapping("/failover") public ApiResponse<InstanceRegistrationResult> failover(@RequestBody HandoffInput input){var result=disasterRecovery.failover(input.currentActiveInstanceId(),input.targetStandbyInstanceId(),input.force());record(result,LicenseOperation.INSTANCE_TRANSITION,input.targetStandbyInstanceId(),"PROD_DR_HANDOFF",Map.of("from",input.currentActiveInstanceId(),"to",input.targetStandbyInstanceId(),"force",input.force(),"direction","FAILOVER"));return response(result);}
    @PostMapping("/failback") public ApiResponse<InstanceRegistrationResult> failback(@RequestBody HandoffInput input){var result=disasterRecovery.failback(input.currentActiveInstanceId(),input.targetStandbyInstanceId(),input.force());record(result,LicenseOperation.INSTANCE_TRANSITION,input.targetStandbyInstanceId(),"PROD_DR_HANDOFF",Map.of("from",input.currentActiveInstanceId(),"to",input.targetStandbyInstanceId(),"force",input.force(),"direction","FAILBACK"));return response(result);}
    public record HandoffInput(String currentActiveInstanceId,String targetStandbyInstanceId,boolean force){}
    private void record(InstanceRegistrationResult result,LicenseOperation operation,String instance,String entitlement,Map<String,?> context){audit.record(operation,instance,entitlement,result.accepted(),result.reasonCode(),context);}
    private static ApiResponse<InstanceRegistrationResult> response(InstanceRegistrationResult result){return new ApiResponse<>(result.accepted(),result.reasonCode(),Instant.now(),result);}
}
