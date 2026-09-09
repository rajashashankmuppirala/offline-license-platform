package com.enterprise.licensing.engine.time;

import com.enterprise.licensing.engine.ports.TimeSource;

import java.time.Duration;

public final class TrustedTimeService {
    private final TrustedTimeRepository repository;
    private final TimeSource time;
    private final Duration tolerance;

    public TrustedTimeService(TrustedTimeRepository repository, TimeSource time, Duration tolerance) {
        this.repository = repository;
        this.time = time;
        this.tolerance = tolerance;
        if (tolerance.isNegative()) throw new IllegalArgumentException("rollback tolerance cannot be negative");
    }

    public TrustedTimeResult validateAndAdvance() {
        return repository.observe(time.now(), tolerance);
    }

    public boolean recoveryRequired() {
        return repository.recoveryRequired();
    }

    public TrustedTimeResult reconcile() {
        return repository.reconcile(time.now(), 0);
    }
}
