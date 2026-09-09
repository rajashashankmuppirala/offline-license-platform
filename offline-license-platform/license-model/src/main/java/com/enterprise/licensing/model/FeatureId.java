package com.enterprise.licensing.model;

public record FeatureId(String value) {
    @com.fasterxml.jackson.annotation.JsonCreator(mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
    public FeatureId { value = Validation.required(value, "featureId").toUpperCase(java.util.Locale.ROOT); }
    @com.fasterxml.jackson.annotation.JsonValue public String jsonValue() { return value; }
}
