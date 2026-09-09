package com.enterprise.licensing.server.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.engine.activation.*;
import com.enterprise.licensing.engine.audit.*;
import com.enterprise.licensing.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class DomainDecisionAuditTest {
    private static void invoke(AuditDecisionFilter filter, DomainDecisionAudit decisions, boolean allowed,
                               LicenseReasonCode reason, Map<String, ?> context) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/license/runtime/usage");
        var response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                decisions.record(LicenseOperation.USAGE_CONSUME, "instance", "AI_EXECUTION_UNIT", allowed, reason, context));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void exactAllowedAndDeniedDecisionsEachProduceOneRedactedRecordWithoutFilterDuplicates() throws Exception {
        var repository = new MemoryAuditRepository();
        var service = new AuditService(repository, new AuditRedactor(), () -> Instant.EPOCH, UUID::randomUUID, false);
        var decisions = new DomainDecisionAudit(service, new EmptyLicenseRepository());
        var filter = new AuditDecisionFilter(service, new EmptyLicenseRepository(), new ObjectMapper());

        invoke(filter, decisions, true, LicenseReasonCode.LICENSE_VALID, Map.of(
                "poolId", "CUSTOMER_WIDE", "usedAmount", 2, "licensedLimit", 10, "authorization", "never-store"));
        invoke(filter, decisions, false, LicenseReasonCode.AI_USAGE_LIMIT_EXCEEDED, Map.of(
                "poolId", "CUSTOMER_WIDE", "usedAmount", 10, "licensedLimit", 10));

        assertThat(repository.events).hasSize(2);
        assertThat(repository.events).extracting(AuditEvent::allowed).containsExactly(true, false);
        assertThat(repository.events).extracting(AuditEvent::reasonCode)
                .containsExactly(LicenseReasonCode.LICENSE_VALID, LicenseReasonCode.AI_USAGE_LIMIT_EXCEEDED);
        assertThat(repository.events.getFirst().context())
                .containsEntry("usedAmount", "2").containsEntry("licensedLimit", "10")
                .containsEntry("authorization", "[REDACTED]");
    }

    private static final class EmptyLicenseRepository implements LicenseRepository {
        public void activate(LicenseServerIdentity identity, CommercialLicense license, SignedEnvelope licenseEnvelope,
                             ActivationCertificate certificate, SignedEnvelope activationEnvelope, Instant importedAt) {
        }

        public Optional<ActiveLicenseState> active() {
            return Optional.empty();
        }

        public boolean isRevoked(String artifactType, String artifactId, Instant at) {
            return false;
        }
    }

    private static final class MemoryAuditRepository implements AuditRepository {
        private final List<AuditEvent> events = new ArrayList<>();

        public AuditEvent append(UUID id, Instant at, String customer, String license, String instance,
                                 LicenseOperation operation, String entitlement, boolean allowed,
                                 LicenseReasonCode reason, Map<String, String> context) {
            var event = new AuditEvent(id, at, customer, license, instance, operation, entitlement, allowed, reason, context, null, "hash-" + events.size());
            events.add(event);
            return event;
        }

        public List<AuditEvent> page(int offset, int limit) {
            return List.copyOf(events);
        }

        public boolean verifyChain() {
            return true;
        }
    }
}
