package com.enterprise.licensing.engine.ports;

import java.util.UUID;

@FunctionalInterface
public interface IdGenerator {
    UUID next();
    static IdGenerator random() { return UUID::randomUUID; }
}
