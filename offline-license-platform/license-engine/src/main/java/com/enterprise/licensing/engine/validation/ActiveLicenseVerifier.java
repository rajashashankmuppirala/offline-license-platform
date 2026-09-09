package com.enterprise.licensing.engine.validation;

import com.enterprise.licensing.crypto.runtime.ActivationVerifier;
import com.enterprise.licensing.crypto.runtime.CommercialLicenseVerifier;
import com.enterprise.licensing.engine.activation.ActiveLicenseState;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.model.LicenseServerIdentity;

import java.security.PublicKey;
import java.util.function.Supplier;

/**
 * Re-establishes trust in persisted signed artifacts before they may authorize a decision.
 */
public final class ActiveLicenseVerifier implements ActiveStateVerifier {
    private final CommercialLicenseVerifier licenses;
    private final ActivationVerifier activations;
    private final Supplier<PublicKey> vendorPublicKey;
    private final Supplier<LicenseServerIdentity> localIdentity;

    public ActiveLicenseVerifier(CommercialLicenseVerifier licenses, ActivationVerifier activations,
                                 Supplier<PublicKey> vendorPublicKey,
                                 Supplier<LicenseServerIdentity> localIdentity) {
        this.licenses = licenses;
        this.activations = activations;
        this.vendorPublicKey = vendorPublicKey;
        this.localIdentity = localIdentity;
    }

    @Override
    public LicenseValidationResult verify(ActiveLicenseState state) {
        if (state == null) {
            return LicenseValidationResult.denied(LicenseReasonCode.LICENSE_SERVER_NOT_ACTIVATED);
        }
        final PublicKey vendorKey;
        try {
            vendorKey = vendorPublicKey.get();
        } catch (RuntimeException exception) {
            return LicenseValidationResult.denied(LicenseReasonCode.LICENSE_SIGNATURE_INVALID);
        }

        var verifiedLicense = licenses.verify(state.licenseEnvelope(), vendorKey);
        if (!verifiedLicense.valid()) {
            return LicenseValidationResult.denied(verifiedLicense.reasonCode());
        }
        if (!verifiedLicense.license().equals(state.license())) {
            return LicenseValidationResult.denied(LicenseReasonCode.STATE_INTEGRITY_FAILURE);
        }

        boolean hasCertificate = state.activation() != null;
        boolean hasEnvelope = state.activationEnvelope() != null;
        if (!hasCertificate || !hasEnvelope) {
            if (hasCertificate != hasEnvelope) {
                return LicenseValidationResult.denied(LicenseReasonCode.STATE_INTEGRITY_FAILURE);
            }
            return state.license().activation().required()
                    ? LicenseValidationResult.denied(LicenseReasonCode.LICENSE_SERVER_NOT_ACTIVATED)
                    : LicenseValidationResult.allowed(false);
        }

        var verifiedActivation = activations.verify(state.activationEnvelope(), vendorKey);
        if (!verifiedActivation.valid()) {
            return LicenseValidationResult.denied(verifiedActivation.reasonCode());
        }
        if (!verifiedActivation.certificate().equals(state.activation())) {
            return LicenseValidationResult.denied(LicenseReasonCode.STATE_INTEGRITY_FAILURE);
        }

        var license = verifiedLicense.license();
        var certificate = verifiedActivation.certificate();
        final LicenseServerIdentity identity;
        try {
            identity = localIdentity.get();
        } catch (RuntimeException exception) {
            return LicenseValidationResult.denied(LicenseReasonCode.LICENSE_SERVER_IDENTITY_MISMATCH);
        }
        if (!certificate.licenseId().equals(license.licenseId())
                || !certificate.customerId().equals(license.customer().customerId())
                || !certificate.licenseServerId().equals(identity.licenseServerId())
                || !certificate.publicKeyFingerprint().equals(identity.publicKeyFingerprint())
                || !certificate.permittedPolicy().equals(license.activation())) {
            return LicenseValidationResult.denied(LicenseReasonCode.LICENSE_SERVER_IDENTITY_MISMATCH);
        }
        return LicenseValidationResult.allowed(false);
    }
}
