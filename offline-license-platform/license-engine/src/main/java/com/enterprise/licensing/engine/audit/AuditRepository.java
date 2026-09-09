package com.enterprise.licensing.engine.audit;

import com.enterprise.licensing.model.*;

import java.time.Instant;
import java.util.*;

public interface AuditRepository {
    AuditEvent append(UUID id, Instant at, String customer, String license, String instance, LicenseOperation operation, String entitlement, boolean allowed, LicenseReasonCode reason, Map<String, String> context);

    List<AuditEvent> page(int offset, int limit);

    boolean verifyChain();
}
