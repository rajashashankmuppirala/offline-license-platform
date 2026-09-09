package com.enterprise.licensing.engine.metering;

import java.util.Map;

public final class ConfiguredAEUPolicy implements AEUPolicy {
    private final Map<String, Long> weights;

    public ConfiguredAEUPolicy(Map<String, Long> w) {
        weights = Map.copyOf(w);
        if (weights.isEmpty() || weights.entrySet().stream().anyMatch(e -> e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || e.getValue() <= 0))
            throw new IllegalArgumentException("AEU actions and weights must be configured and positive");
    }

    public long unitsFor(String action) {
        var value = weights.get(action);
        if (value == null) throw new IllegalArgumentException("Unknown AEU action: " + action);
        return value;
    }
}
