package com.enterprise.licensing.engine.activation;

import com.enterprise.licensing.model.ActivationCertificate;
import com.enterprise.licensing.model.CommercialLicense;
import com.enterprise.licensing.model.LicenseServerIdentity;
import com.enterprise.licensing.model.SignedEnvelope;
import java.time.Instant;
import java.util.Optional;

public interface LicenseRepository {
    void activate(LicenseServerIdentity serverIdentity, CommercialLicense license, SignedEnvelope licenseEnvelope,
                  ActivationCertificate certificate, SignedEnvelope activationEnvelope, Instant importedAt);
    Optional<ActiveLicenseState> active();
    boolean isRevoked(String artifactType, String artifactId, Instant at);
}
