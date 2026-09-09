package com.enterprise.licensing.engine.time;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface TrustedTimeRepository {
    TrustedTimeResult observe(Instant current, Duration rollbackTolerance);

    default Optional<TrustedTimeResult> current() { return Optional.empty(); }

    default TrustedTimeResult reconcile(Instant baseline, long minimumSequence) {
        throw new UnsupportedOperationException("Trusted-time reconciliation is not supported");
    }

    default boolean recoveryRequired() { return false; }
}
