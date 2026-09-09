package com.enterprise.licensing.model;

public record ProductId(String value) {
    public static final ProductId VALIDATOR = new ProductId("VALIDATOR");
    public static final ProductId DATA_MANAGER = new ProductId("DATA_MANAGER");
    public static final ProductId DATA_QUALITY = new ProductId("DATA_QUALITY");
    public static final ProductId DATA_FLOW = new ProductId("DATA_FLOW");
    @com.fasterxml.jackson.annotation.JsonCreator(mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
    public ProductId { value = Validation.required(value, "productId").toUpperCase(java.util.Locale.ROOT); }
    @com.fasterxml.jackson.annotation.JsonValue public String jsonValue() { return value; }
}
