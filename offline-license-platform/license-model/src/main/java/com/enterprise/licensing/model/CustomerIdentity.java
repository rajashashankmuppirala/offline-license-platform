package com.enterprise.licensing.model;

public record CustomerIdentity(String customerId, String companyName, String contactEmail) {
    public CustomerIdentity {
        customerId = Validation.required(customerId, "customerId");
        companyName = Validation.required(companyName, "companyName");
        contactEmail = Validation.required(contactEmail, "contactEmail");
    }
}
