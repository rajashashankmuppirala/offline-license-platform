package com.enterprise.licensing.engine.activation;

import com.enterprise.licensing.crypto.runtime.ActivationVerifier;
import com.enterprise.licensing.crypto.runtime.CommercialLicenseVerifier;
import com.enterprise.licensing.engine.identity.LicenseServerIdentityService;
import com.enterprise.licensing.engine.ports.TimeSource;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.model.SignedEnvelope;
import java.security.PublicKey;
import java.time.Instant;
import java.util.function.Supplier;

public final class ActivationService {
    private final CommercialLicenseVerifier licenseVerifier;
    private final ActivationVerifier activationVerifier;
    private final Supplier<PublicKey> vendorPublicKey;
    private final LicenseServerIdentityService identityService;
    private final LicenseRepository repository;
    private final TimeSource timeSource;
    private final RenewalImpactAssessment renewalImpact;

    public ActivationService(CommercialLicenseVerifier licenseVerifier, ActivationVerifier activationVerifier,
                             PublicKey vendorPublicKey, LicenseServerIdentityService identityService,
                             LicenseRepository repository, TimeSource timeSource) {
        this(licenseVerifier, activationVerifier, () -> vendorPublicKey, identityService, repository, timeSource,
                RenewalImpactAssessment.none());
    }

    public ActivationService(CommercialLicenseVerifier licenseVerifier, ActivationVerifier activationVerifier,
                             Supplier<PublicKey> vendorPublicKey, LicenseServerIdentityService identityService,
                             LicenseRepository repository, TimeSource timeSource) {
        this(licenseVerifier, activationVerifier, vendorPublicKey, identityService, repository, timeSource,
                RenewalImpactAssessment.none());
    }

    public ActivationService(CommercialLicenseVerifier licenseVerifier, ActivationVerifier activationVerifier,
                             Supplier<PublicKey> vendorPublicKey, LicenseServerIdentityService identityService,
                             LicenseRepository repository, TimeSource timeSource,
                             RenewalImpactAssessment renewalImpact) {
        this.licenseVerifier=licenseVerifier;this.activationVerifier=activationVerifier;this.vendorPublicKey=vendorPublicKey;
        this.identityService=identityService;this.repository=repository;this.timeSource=timeSource;
        this.renewalImpact=renewalImpact;
    }

    public ActivationImportResult importArtifacts(SignedEnvelope licenseEnvelope, SignedEnvelope activationEnvelope) {
        PublicKey trustedVendorKey;
        try { trustedVendorKey = vendorPublicKey.get(); }
        catch (RuntimeException e) { return ActivationImportResult.denied(LicenseReasonCode.LICENSE_SIGNATURE_INVALID); }
        var verifiedLicense = licenseVerifier.verify(licenseEnvelope, trustedVendorKey);
        if (!verifiedLicense.valid()) return ActivationImportResult.denied(verifiedLicense.reasonCode());
        var license = verifiedLicense.license();
        Instant now = timeSource.now();
        var current = repository.active().orElse(null);
        if (current != null && current.license().licenseId().equals(license.licenseId())) {
            if (license.licenseSequence() < current.license().licenseSequence()
                    || license.licenseSequence() == current.license().licenseSequence()
                    && !licenseEnvelope.payload().equals(current.licenseEnvelope().payload()))
                return ActivationImportResult.denied(LicenseReasonCode.LICENSE_FORMAT_INVALID);
        }
        if (repository.isRevoked("COMMERCIAL_LICENSE", license.licenseId(), now)) return ActivationImportResult.denied(LicenseReasonCode.LICENSE_REVOKED);
        if (now.isBefore(license.subscription().validFrom())) return ActivationImportResult.denied(LicenseReasonCode.LICENSE_NOT_YET_VALID);
        if (now.isAfter(license.subscription().graceUntil())) return ActivationImportResult.denied(LicenseReasonCode.LICENSE_EXPIRED);
        if (license.activation().required() && activationEnvelope == null) {
            var previous = current;
            var identity = identityService.publicIdentity();
            if (previous == null || previous.activation() == null || previous.activationEnvelope() == null
                    || !license.activation().renewalMayReuseActivation() ||
                    !previous.activation().permittedPolicy().renewalMayReuseActivation() ||
                    !previous.activation().permittedPolicy().equals(license.activation()) ||
                    !previous.license().licenseId().equals(license.licenseId()) ||
                    !previous.activation().licenseId().equals(license.licenseId()) ||
                    !previous.license().customer().customerId().equals(license.customer().customerId()) ||
                    !previous.activation().licenseServerId().equals(identity.licenseServerId()) ||
                    !previous.activation().publicKeyFingerprint().equals(identity.publicKeyFingerprint())) {
                return ActivationImportResult.denied(LicenseReasonCode.LICENSE_SERVER_NOT_ACTIVATED);
            }
            var priorVerification = activationVerifier.verify(previous.activationEnvelope(), trustedVendorKey);
            if (!priorVerification.valid()) return ActivationImportResult.denied(priorVerification.reasonCode());
            if (repository.isRevoked("ACTIVATION_CERTIFICATE", previous.activation().activationId(), now)) return ActivationImportResult.denied(LicenseReasonCode.LICENSE_REVOKED);
            repository.activate(identity, license, licenseEnvelope, previous.activation(), previous.activationEnvelope(), now);
            return ActivationImportResult.accepted(repository.active().orElseThrow(), renewalImpact.warnings(license, now));
        }

        var verifiedActivation = activationVerifier.verify(activationEnvelope, trustedVendorKey);
        if (!verifiedActivation.valid()) return ActivationImportResult.denied(verifiedActivation.reasonCode());
        var certificate = verifiedActivation.certificate();
        var identity = identityService.publicIdentity();
        if (!certificate.licenseId().equals(license.licenseId()) ||
                !certificate.customerId().equals(license.customer().customerId()) ||
                !certificate.licenseServerId().equals(identity.licenseServerId()) ||
                !certificate.publicKeyFingerprint().equals(identity.publicKeyFingerprint())) {
            return ActivationImportResult.denied(LicenseReasonCode.LICENSE_SERVER_IDENTITY_MISMATCH);
        }
        if (repository.isRevoked("ACTIVATION_CERTIFICATE", certificate.activationId(), now)) {
            return ActivationImportResult.denied(LicenseReasonCode.LICENSE_REVOKED);
        }
        if (certificate.issuedAt().isAfter(now.plusSeconds(300))) return ActivationImportResult.denied(LicenseReasonCode.LICENSE_NOT_YET_VALID);
        if (!certificate.permittedPolicy().equals(license.activation())) return ActivationImportResult.denied(LicenseReasonCode.LICENSE_SERVER_IDENTITY_MISMATCH);

        repository.activate(identity, license, licenseEnvelope, certificate, activationEnvelope, now);
        return ActivationImportResult.accepted(repository.active().orElseThrow(), renewalImpact.warnings(license, now));
    }
}
