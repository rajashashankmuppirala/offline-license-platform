package com.enterprise.licensing.engine.ports;

import java.time.Instant;

@FunctionalInterface
public interface TimeSource {
    Instant now();
    static TimeSource systemUtc() { return java.time.Clock.systemUTC()::instant; }
}
