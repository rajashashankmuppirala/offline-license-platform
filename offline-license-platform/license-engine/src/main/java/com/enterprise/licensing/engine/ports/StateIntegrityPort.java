package com.enterprise.licensing.engine.ports;

public interface StateIntegrityPort {
    IntegrityStatus verify();

    enum IntegrityStatus {VALID, CORRUPT, ROLLED_BACK}
}
