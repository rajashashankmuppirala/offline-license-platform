package com.enterprise.licensing.engine.activation;

import com.enterprise.licensing.model.ActivationCertificate;
import com.enterprise.licensing.model.CommercialLicense;
import com.enterprise.licensing.model.SignedEnvelope;
import java.time.Instant;

public record ActiveLicenseState(CommercialLicense license, SignedEnvelope licenseEnvelope,
                                 ActivationCertificate activation, SignedEnvelope activationEnvelope,
                                 Instant importedAt) {}
