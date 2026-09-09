package com.enterprise.licensing.model;

public record EncodedPublicKey(String algorithm, String format, String encoded) {
    public EncodedPublicKey {
        algorithm = Validation.required(algorithm, "algorithm");
        format = Validation.required(format, "format");
        encoded = Validation.required(encoded, "encoded");
    }
}
