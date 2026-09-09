package com.enterprise.licensing.engine.cache;

import com.enterprise.licensing.model.*;

import java.util.Optional;

public interface CachedEntitlementRepository {
    void save(CachedEntitlementCertificate certificate, SignedEnvelope envelope);

    Optional<SignedEnvelope> latestFor(String instanceId);

    void revokeFor(String instanceId, java.time.Instant at);
}
