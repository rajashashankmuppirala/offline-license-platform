package com.enterprise.licensing.model;

import java.util.List;
import java.util.Set;
import java.time.Instant;

public record CommercialLicense(String schemaVersion, String licenseId, CustomerIdentity customer,
                                SubscriptionEntitlement subscription, ActivationPolicy activation,
                                List<EnvironmentEntitlement> environments, UserEntitlement users,
                                List<ProductEntitlement> products, AIEntitlement ai,
                                Instant issuedAt, long licenseSequence,
                                int activeProductionClassLimit, Set<String> revocationIds) {
    public CommercialLicense(String schemaVersion, String licenseId, CustomerIdentity customer, SubscriptionEntitlement subscription,
                             ActivationPolicy activation, List<EnvironmentEntitlement> environments, UserEntitlement users,
                             List<ProductEntitlement> products, AIEntitlement ai, int activeProductionClassLimit, Set<String> revocationIds) {
        this(schemaVersion, licenseId, customer, subscription, activation, environments, users, products, ai,
                subscription == null ? Instant.EPOCH : subscription.validFrom(), 1, activeProductionClassLimit, revocationIds);
    }

    public CommercialLicense {
        schemaVersion = Validation.required(schemaVersion, "schemaVersion");
        licenseId = Validation.required(licenseId, "licenseId");
        if (customer == null || subscription == null || activation == null || users == null || ai == null)
            throw new IllegalArgumentException("license sections are required");
        environments = List.copyOf(Validation.noNulls(environments == null ? List.of() : environments, "environments"));
        products = List.copyOf(Validation.noNulls(products == null ? List.of() : products, "products"));
        if (issuedAt == null) throw new IllegalArgumentException("issuedAt is required");
        if (licenseSequence < 1) throw new IllegalArgumentException("licenseSequence must be positive");
        if (activeProductionClassLimit < 1)
            throw new IllegalArgumentException("activeProductionClassLimit must be positive");
        revocationIds = Set.copyOf(Validation.noNulls(revocationIds == null ? Set.of() : revocationIds, "revocationIds"));
    }
}
