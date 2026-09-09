package com.enterprise.licensing.server.api.runtime;

import com.enterprise.licensing.engine.entitlement.EntitlementService;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.server.api.ApiResponse;
import com.enterprise.licensing.server.security.DomainDecisionAudit;
import java.util.HashMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/license/runtime/entitlements")
public final class EntitlementController {
    private final EntitlementService service;
    private final DomainDecisionAudit audit;

    public EntitlementController(EntitlementService service, DomainDecisionAudit audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/products/{productId}")
    public ApiResponse<LicenseDecision> product(@RequestHeader("X-License-Instance-Id") String instance,
                                                @PathVariable("productId") String productId,
                                                @RequestHeader(value="X-Correlation-Id", defaultValue="none") String correlation) {
        var decision = service.checkProduct(instance, new ProductId(productId), correlation);
        record(decision, instance, productId, correlation);
        return response(decision);
    }

    @GetMapping("/products/{productId}/features/{featureId}")
    public ApiResponse<LicenseDecision> feature(@RequestHeader("X-License-Instance-Id") String instance,
                                                @PathVariable("productId") String productId,
                                                @PathVariable("featureId") String featureId,
                                                @RequestHeader(value="X-Correlation-Id", defaultValue="none") String correlation) {
        var decision = service.checkFeature(instance, new ProductId(productId), new FeatureId(featureId), correlation);
        record(decision, instance, productId + ":" + featureId, correlation);
        return response(decision);
    }

    @GetMapping("/products/{productId}/users/{userId}/capacity")
    public ApiResponse<LicenseDecision> userCapacity(@RequestHeader("X-License-Instance-Id") String instance,
            @PathVariable("productId") String productId,@PathVariable("userId") String userId,
            @RequestHeader(value="X-Correlation-Id",defaultValue="none") String correlation){
        var decision=service.checkProductUserCapacity(instance,new ProductId(productId),userId,correlation);
        record(decision,instance,productId+":"+userId,correlation);return response(decision);
    }

    private void record(LicenseDecision decision, String instance, String entitlement, String correlation) {
        var context = new HashMap<String, Object>(decision.diagnosticContext());
        context.put("correlationId", correlation);
        audit.record(decision.operation(), instance, entitlement, decision.allowed(), decision.reasonCode(), context);
    }

    private static ApiResponse<LicenseDecision> response(LicenseDecision decision) {
        return new ApiResponse<>(decision.allowed(), decision.reasonCode(), java.time.Instant.now(), decision);
    }
}
