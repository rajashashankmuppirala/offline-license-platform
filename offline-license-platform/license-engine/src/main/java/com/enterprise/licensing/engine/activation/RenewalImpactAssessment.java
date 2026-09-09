package com.enterprise.licensing.engine.activation;

import com.enterprise.licensing.model.CommercialLicense;

import java.time.Instant;
import java.util.List;

@FunctionalInterface
public interface RenewalImpactAssessment {
    static RenewalImpactAssessment none() {
        return (license, now) -> List.of();
    }

    List<String> warnings(CommercialLicense renewedLicense, Instant now);
}
