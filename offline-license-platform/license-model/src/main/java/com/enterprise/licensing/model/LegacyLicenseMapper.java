package com.enterprise.licensing.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class LegacyLicenseMapper {
    public CommercialLicense map(JsonNode root, CommercialLicenseJsonCodec.LegacyDefaults defaults) {
        require(root, "companyName", "contactEmail", "startDate", "endDate");
        var products = new ArrayList<ProductEntitlement>();
        for (JsonNode item : root.path("productCollection")) {
            ProductId id = new ProductId(normalize(item.path("name").asText()));
            Set<FeatureId> features = java.util.stream.StreamSupport.stream(item.path("subProducts").spliterator(), false)
                    .map(JsonNode::asText).map(LegacyLicenseMapper::normalize).map(FeatureId::new).collect(Collectors.toUnmodifiableSet());
            products.add(new ProductEntitlement(id, true, item.path("noOfUsers").asInt(0), features));
        }
        List<UserCategoryEntitlement> categories = List.of(
                new UserCategoryEntitlement(UserCategory.REPORTING, root.path("totalReportingUsers").asInt(0)),
                new UserCategoryEntitlement(UserCategory.DQ_ISSUE_MANAGER, root.path("totalDQIssueManagerUsers").asInt(0)));
        var subscription = new SubscriptionEntitlement(root.path("licenseType").asText("Subscription").toUpperCase(Locale.ROOT),
                root.path("subscriptionType").asText("LEGACY").toUpperCase(Locale.ROOT),
                LocalDate.parse(root.path("startDate").asText()).atStartOfDay().toInstant(ZoneOffset.UTC),
                LocalDate.parse(root.path("endDate").asText()).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusNanos(1), 0);
        return new CommercialLicense("1.0", defaults.licenseId(),
                new CustomerIdentity(defaults.customerId(), root.path("companyName").asText(), root.path("contactEmail").asText()),
                subscription, defaults.activationPolicy(), List.of(),
                new UserEntitlement(root.path("totalNamedUsers").asInt(0), root.path("concurrentUser").asInt(0), categories),
                products, new AIEntitlement(false, 0, 0, Set.of(), List.of()), subscription.validFrom(), 1, 1, Set.of());
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static void require(JsonNode root, String... names) {
        for (String name : names) if (!root.hasNonNull(name) || root.path(name).asText().isBlank())
            throw new IllegalArgumentException("Missing legacy field: " + name);
    }
}
