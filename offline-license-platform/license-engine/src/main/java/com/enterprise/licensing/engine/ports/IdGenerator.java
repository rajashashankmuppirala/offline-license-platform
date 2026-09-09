package com.enterprise.licensing.engine.ports;

import java.util.UUID;

@FunctionalInterface
public interface IdGenerator {
    static IdGenerator random() {
        return UUID::randomUUID;
    }

    UUID next();
}
