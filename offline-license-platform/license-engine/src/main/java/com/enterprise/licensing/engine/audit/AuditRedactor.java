package com.enterprise.licensing.engine.audit;

import java.util.*;

public final class AuditRedactor {
    private static final List<String> SENSITIVE = List.of("private", "secret", "password", "token", "seed", "credential", "authorization", "api-key", "apikey", "cookie");

    private static boolean isSensitive(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return SENSITIVE.stream().anyMatch(k::contains);
    }

    private static String bounded(String value) {
        if (value == null) return "";
        return value.length() <= 1024 ? value : value.substring(0, 1024);
    }

    public Map<String, String> redact(Map<String, String> input) {
        var output = new TreeMap<String, String>();
        if (input != null) input.forEach((k, v) -> output.put(k, isSensitive(k) ? "[REDACTED]" : bounded(v)));
        return Map.copyOf(output);
    }
}
