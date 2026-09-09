package com.enterprise.licensing.model;

import java.util.Collection;

final class Validation {
    private Validation() {
    }

    static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    static int nonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be nonnegative");
        return value;
    }

    static long positive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    static <T extends Collection<?>> T noNulls(T value, String name) {
        if (value == null || value.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException(name + " must not contain nulls");
        }
        return value;
    }
}
