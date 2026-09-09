package com.enterprise.licensing.server.security;

import com.enterprise.licensing.engine.activation.LicenseRepository;
import com.enterprise.licensing.engine.audit.AuditService;
import com.enterprise.licensing.model.LicenseOperation;
import com.enterprise.licensing.model.LicenseReasonCode;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public final class DomainDecisionAudit {
    static final String RECORDED_ATTRIBUTE = DomainDecisionAudit.class.getName() + ".recorded";
    private final AuditService audit;
    private final LicenseRepository licenses;

    public DomainDecisionAudit(AuditService audit, LicenseRepository licenses) {
        this.audit = audit;
        this.licenses = licenses;
    }

    private static java.util.Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return java.util.Optional.of(attributes.getRequest());
        }
        return java.util.Optional.empty();
    }

    public void record(LicenseOperation operation, String instanceId, String entitlement,
                       boolean allowed, LicenseReasonCode reasonCode, Map<String, ?> values) {
        var context = new TreeMap<String, String>();
        if (values != null) values.forEach((key, value) -> {
            if (value != null) context.put(key, String.valueOf(value));
        });
        var active = licenses.active();
        audit.record(
                active.map(state -> state.license().customer().customerId()).orElse(null),
                active.map(state -> state.license().licenseId()).orElse(null),
                instanceId,
                operation,
                entitlement,
                allowed,
                reasonCode,
                context);
        currentRequest().ifPresent(request -> request.setAttribute(RECORDED_ATTRIBUTE, Boolean.TRUE));
    }
}
