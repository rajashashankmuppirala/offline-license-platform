package com.enterprise.licensing.engine.entitlement;

import com.enterprise.licensing.model.FeatureId;
import com.enterprise.licensing.model.ProductId;

import java.util.Locale;
import java.util.Map;

public final class LegacyFeatureCatalog {
    private static final Map<String, FeatureId> VALIDATOR_FEATURES = Map.of(
            "TABLEAU", new FeatureId("TABLEAU"),
            "ORACLE_ANALYTICS", new FeatureId("ORACLE_ANALYTICS"),
            "COGNOS", new FeatureId("COGNOS"));

    private LegacyFeatureCatalog() {
    }

    public static FeatureId normalize(ProductId productId, FeatureId featureId) {
        if (!ProductId.VALIDATOR.equals(productId)) {
            return featureId;
        }
        String normalized = featureId.value().trim().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        return VALIDATOR_FEATURES.getOrDefault(normalized, new FeatureId(normalized));
    }
}
