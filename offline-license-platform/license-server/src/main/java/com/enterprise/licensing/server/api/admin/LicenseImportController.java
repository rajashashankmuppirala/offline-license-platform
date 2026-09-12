package com.enterprise.licensing.server.api.admin;

import com.enterprise.licensing.engine.activation.ActivationImportResult;
import com.enterprise.licensing.engine.activation.ActivationService;
import com.enterprise.licensing.model.LicenseOperation;
import com.enterprise.licensing.model.SignedEnvelope;
import com.enterprise.licensing.server.api.ApiResponse;
import com.enterprise.licensing.server.security.DomainDecisionAudit;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Imports a vendor-signed commercial license when installation activation is disabled. */
@RestController
@RequestMapping("/api/v1/license/admin/licenses")
public final class LicenseImportController {
    private final ActivationService licenses;
    private final DomainDecisionAudit audit;

    public LicenseImportController(ActivationService licenses, DomainDecisionAudit audit) {
        this.licenses = licenses;
        this.audit = audit;
    }

    @PostMapping("/import")
    public ApiResponse<ActivationImportResult> importLicense(@RequestBody SignedEnvelope commercialLicense) {
        var result = licenses.importArtifacts(commercialLicense, null);
        audit.record(LicenseOperation.LICENSE_IMPORT, null, "COMMERCIAL_LICENSE", result.accepted(),
                result.reasonCode(), Map.of("accepted", result.accepted(), "activationCertificateSupplied", false));
        return new ApiResponse<>(result.accepted(), result.reasonCode(), Instant.now(), result);
    }
}
