package com.enterprise.licensing.server.api.admin;

import com.enterprise.licensing.engine.activation.LicenseRepository;
import com.enterprise.licensing.engine.audit.AuditRepository;
import com.enterprise.licensing.engine.recovery.RecoveryService;
import com.enterprise.licensing.engine.status.LicenseStatusService;
import com.enterprise.licensing.server.api.ApiResponse;

import java.util.Map;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/license/admin")
public final class StatusController {
    private final LicenseStatusService status;
    private final LicenseRepository licenses;
    private final AuditRepository audit;
    private final RecoveryService recovery;

    public StatusController(LicenseStatusService s, LicenseRepository l, AuditRepository a, RecoveryService r) {
        status = s;
        licenses = l;
        audit = a;
        recovery = r;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.success(status.status());
    }

    @GetMapping("/entitlements")
    public ApiResponse<Object> entitlements() {
        return ApiResponse.success(licenses.active().map(s -> s.license()).orElse(null));
    }

    @GetMapping("/utilization")
    public ApiResponse<Map<String, Object>> utilization() {
        return ApiResponse.success(status.utilization());
    }

    @GetMapping("/diagnostics")
    public ApiResponse<Map<String, Object>> diagnostics() {
        return ApiResponse.success(Map.of("recoveryState", recovery.state(), "reasonCode", recovery.reasonCode(), "auditChainValid", audit.verifyChain()));
    }

    @PostMapping("/recovery/reconcile")
    public ApiResponse<Map<String, Object>> reconcile(@RequestBody RecoveryInput input) {
        var state = recovery.reconcile(input.actor(), input.justification());
        return ApiResponse.success(Map.of("recoveryState", state, "reasonCode", recovery.reasonCode()));
    }

    public record RecoveryInput(String actor, String justification) {
    }
}
