package com.enterprise.licensing.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.client.availability.LicenseAvailabilityPolicy;
import com.enterprise.licensing.model.LicenseOperation;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

class LicenseAvailabilityPolicyTest {
    @Test
    void outagePolicyAllowsOnlyReadOnlyEntitlementChecks() {
        var policy = LicenseAvailabilityPolicy.pocDefaults();
        assertThat(policy.mayUseCache(LicenseOperation.PRODUCT_CHECK)).isTrue();
        assertThat(policy.mayUseCache(LicenseOperation.FEATURE_CHECK)).isTrue();

        var centralOnly = EnumSet.allOf(LicenseOperation.class);
        centralOnly.remove(LicenseOperation.PRODUCT_CHECK);
        centralOnly.remove(LicenseOperation.FEATURE_CHECK);
        assertThat(centralOnly).allSatisfy(operation -> assertThat(policy.mayUseCache(operation)).isFalse());
    }
}
