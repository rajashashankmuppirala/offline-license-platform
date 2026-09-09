package com.enterprise.licensing.engine.entitlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.model.FeatureId;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.model.ProductId;
import org.junit.jupiter.api.Test;

class FeatureEntitlementServiceTest {
    @Test
    void validatorLegacyFeatureNamesNormalizeToStableIds() {
        var service = ProductEntitlementServiceTest.service(ProductId.VALIDATOR);
        assertThat(service.checkFeature("instance", ProductId.VALIDATOR, new FeatureId("tableau"), "c").allowed()).isTrue();
        assertThat(service.checkFeature("instance", ProductId.VALIDATOR, new FeatureId("oracle-analytics"), "c").allowed()).isTrue();
        assertThat(service.checkFeature("instance", ProductId.VALIDATOR, new FeatureId("cognos"), "c").allowed()).isTrue();
    }

    @Test
    void unknownFeatureAndDisabledProductAreDeniedPrecisely() {
        var service = ProductEntitlementServiceTest.service(ProductId.VALIDATOR);
        assertThat(service.checkFeature("instance", ProductId.VALIDATOR, new FeatureId("UNKNOWN"), "c").reasonCode())
                .isEqualTo(LicenseReasonCode.FEATURE_NOT_ENTITLED);
        assertThat(ProductEntitlementServiceTest.service(ProductId.DATA_FLOW)
                .checkFeature("instance", ProductId.DATA_FLOW, new FeatureId("ANY"), "c").reasonCode())
                .isEqualTo(LicenseReasonCode.PRODUCT_NOT_ENTITLED);
    }
}
