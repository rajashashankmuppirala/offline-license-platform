package com.enterprise.licensing.engine.validation;

import com.enterprise.licensing.engine.activation.*;
import com.enterprise.licensing.engine.ports.TimeSource;
import com.enterprise.licensing.engine.time.TrustedTimeService;
import com.enterprise.licensing.model.*;

import java.time.Instant;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class LicenseValidationService implements OperationAuthorizer {
    private static final Set<LicenseOperation> DENIED_IN_GRACE = Set.of(
            LicenseOperation.INSTANCE_REGISTER, LicenseOperation.NAMED_USER_ASSIGN, LicenseOperation.AGENT_REGISTER);
    private final LicenseRepository licenses;
    private final TrustedTimeService trustedTime;
    private final TimeSource time;
    private final ActiveStateVerifier activeStateVerifier;
    private final AuthoritativeStateVerifier authoritativeStateVerifier;
    private final Consumer<LicenseReasonCode> failureObserver;
    private final BooleanSupplier recoveryReady;
    private final Supplier<LicenseReasonCode> recoveryReason;

    public LicenseValidationService(LicenseRepository licenses, TrustedTimeService trustedTime, TimeSource time,
                                    ActiveStateVerifier activeStateVerifier) {
        this(licenses, trustedTime, time, activeStateVerifier, () -> LicenseReasonCode.LICENSE_VALID,
                ignored -> {
                }, () -> true, () -> LicenseReasonCode.LICENSE_VALID);
    }

    public LicenseValidationService(LicenseRepository licenses, TrustedTimeService trustedTime, TimeSource time,
                                    ActiveStateVerifier activeStateVerifier,
                                    AuthoritativeStateVerifier authoritativeStateVerifier,
                                    Consumer<LicenseReasonCode> failureObserver,
                                    BooleanSupplier recoveryReady, Supplier<LicenseReasonCode> recoveryReason) {
        this.licenses = licenses;
        this.trustedTime = trustedTime;
        this.time = time;
        this.activeStateVerifier = activeStateVerifier;
        this.authoritativeStateVerifier = authoritativeStateVerifier;
        this.failureObserver = failureObserver;
        this.recoveryReady = recoveryReady;
        this.recoveryReason = recoveryReason;
    }

    @Override
    public LicenseValidationResult evaluate(LicenseOperation operation) {
        var state = licenses.active().orElse(null);
        var result = validateSchema(state);
        if (result != null) return result;
        if (!recoveryReady.getAsBoolean()) return LicenseValidationResult.denied(recoveryReason.get());
        var integrityReason = authoritativeStateVerifier.verifyOrInitialize();
        if (integrityReason != LicenseReasonCode.LICENSE_VALID) {
            failureObserver.accept(integrityReason);
            return LicenseValidationResult.denied(integrityReason);
        }
        result = activeStateVerifier.verify(state);
        if (!result.allowed()) {
            failureObserver.accept(result.reasonCode());
            return result;
        }
        result = validateStateIntegrity();
        if (result != null) {
            failureObserver.accept(result.reasonCode());
            return result;
        }
        result = validateRevocation(state, time.now());
        if (result != null) return result;
        return validateSubscriptionAndGrace(state, operation, time.now());
    }

    private LicenseValidationResult validateSchema(ActiveLicenseState state) {
        if (state == null) return LicenseValidationResult.denied(LicenseReasonCode.LICENSE_SERVER_NOT_ACTIVATED);
        if (!"1.0".equals(state.license().schemaVersion())
                || state.activation() != null && !"1.0".equals(state.activation().schemaVersion()))
            return LicenseValidationResult.denied(LicenseReasonCode.LICENSE_SCHEMA_UNSUPPORTED);
        return null;
    }

    private LicenseValidationResult validateStateIntegrity() {
        var trusted = trustedTime.validateAndAdvance();
        return trusted.accepted() ? null : LicenseValidationResult.denied(trusted.reasonCode());
    }

    private LicenseValidationResult validateRevocation(ActiveLicenseState state, Instant now) {
        return licenses.isRevoked("LICENSE", state.license().licenseId(), now)
                ? LicenseValidationResult.denied(LicenseReasonCode.LICENSE_REVOKED) : null;
    }

    private LicenseValidationResult validateSubscriptionAndGrace(ActiveLicenseState state, LicenseOperation operation, Instant now) {
        var subscription = state.license().subscription();
        if (now.isBefore(subscription.validFrom()))
            return LicenseValidationResult.denied(LicenseReasonCode.LICENSE_NOT_YET_VALID);
        if (now.isAfter(subscription.graceUntil()))
            return LicenseValidationResult.denied(LicenseReasonCode.LICENSE_EXPIRED);
        boolean grace = now.isAfter(subscription.validUntil());
        if (grace && DENIED_IN_GRACE.contains(operation))
            return LicenseValidationResult.denied(LicenseReasonCode.LICENSE_EXPIRED);
        return LicenseValidationResult.allowed(grace);
    }
}
