package com.enterprise.licensing.testkit;

import com.enterprise.licensing.engine.ports.TimeSource;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableTimeSource implements TimeSource {
    private final AtomicReference<Instant> value;
    public MutableTimeSource(Instant initial) { value = new AtomicReference<>(initial); }
    @Override public Instant now() { return value.get(); }
    public void set(Instant instant) { value.set(instant); }
    public void advance(Duration duration) { value.updateAndGet(i -> i.plus(duration)); }
}
