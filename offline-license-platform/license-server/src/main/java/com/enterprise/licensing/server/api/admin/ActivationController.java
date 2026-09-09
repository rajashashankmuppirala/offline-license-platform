package com.enterprise.licensing.server.api.admin;

import com.enterprise.licensing.engine.activation.*;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.server.api.ApiResponse;
import com.enterprise.licensing.server.security.DomainDecisionAudit;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/license/admin/activation")
public final class ActivationController {
    private final ActivationRequestService requests;
    private final ActivationService activation;
    private final DomainDecisionAudit audit;

    public ActivationController(ActivationRequestService requests, ActivationService activation, DomainDecisionAudit audit) {
        this.requests = requests;
        this.activation = activation;
        this.audit = audit;
    }

    @PostMapping("/request")
    public ApiResponse<ActivationRequest> request(@RequestBody ActivationRequestInput input) {
        var result = requests.generate(input.licenseId(), input.customerId(), input.environmentEvidence());
        audit.record(LicenseOperation.ACTIVATION_REQUEST, null, "ACTIVATION", true, LicenseReasonCode.LICENSE_VALID,
                Map.of("requestedLicenseId", input.licenseId(), "requestedCustomerId", input.customerId(), "nonce", result.nonce()));
        return ApiResponse.success(result);
    }

    @PostMapping("/import")
    public ApiResponse<ActivationImportResult> importArtifacts(@RequestBody ActivationImportInput input) {
        var result = activation.importArtifacts(input.commercialLicense(), input.activationCertificate());
        audit.record(LicenseOperation.ACTIVATION_IMPORT, null, "ACTIVATION", result.accepted(), result.reasonCode(), Map.of("accepted", result.accepted()));
        return new ApiResponse<>(result.accepted(), result.reasonCode(), Instant.now(), result);
    }

    public record ActivationRequestInput(String licenseId, String customerId, Map<String, String> environmentEvidence) {
    }

    public record ActivationImportInput(SignedEnvelope commercialLicense, SignedEnvelope activationCertificate) {
    }
}
