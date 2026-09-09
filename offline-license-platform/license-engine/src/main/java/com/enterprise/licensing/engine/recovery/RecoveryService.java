package com.enterprise.licensing.engine.recovery;

import com.enterprise.licensing.engine.activation.LicenseRepository;
import com.enterprise.licensing.engine.audit.AuditService;
import com.enterprise.licensing.engine.identity.LicenseServerIdentityService;
import com.enterprise.licensing.engine.time.TrustedTimeService;
import com.enterprise.licensing.model.*;

import java.util.Map;

public final class RecoveryService {
    private final LicenseRepository licenses;
    private final LicenseServerIdentityService identity;
    private final TrustedTimeService trustedTime;
    private final AuditService audit;
    private volatile RecoveryState state;
    private volatile LicenseReasonCode reasonCode;

    public RecoveryService(LicenseRepository licenses, LicenseServerIdentityService identity,
                           TrustedTimeService trustedTime, AuditService audit) {
        this(licenses, identity, trustedTime, audit, true);
    }

    public RecoveryService(LicenseRepository licenses, LicenseServerIdentityService identity,
                           TrustedTimeService trustedTime, AuditService audit, boolean assessOnCreate) {
        this.licenses = licenses;
        this.identity = identity;
        this.trustedTime = trustedTime;
        this.audit = audit;
        this.state = RecoveryState.UNACTIVATED;
        this.reasonCode = LicenseReasonCode.LICENSE_SERVER_NOT_ACTIVATED;
        if (assessOnCreate) assess();
    }

    public synchronized RecoveryState assess() {
        var active = licenses.active();
        if (active.isEmpty()) return update(RecoveryState.UNACTIVATED, LicenseReasonCode.LICENSE_SERVER_NOT_ACTIVATED);
        var activation = active.get().activation();
        var server = identity.publicIdentity();
        if (activation == null || !activation.licenseServerId().equals(server.licenseServerId())
                || !activation.publicKeyFingerprint().equals(server.publicKeyFingerprint()))
            return update(RecoveryState.RECOVERY_REQUIRED, LicenseReasonCode.LICENSE_SERVER_IDENTITY_MISMATCH);
        var trusted = trustedTime.validateAndAdvance();
        if (!trusted.accepted()) return update(RecoveryState.RECOVERY_REQUIRED, trusted.reasonCode());
        var subscription = active.get().license().subscription();
        var now = trusted.highestAcceptedTime();
        return update(now.isAfter(subscription.validUntil()) ? RecoveryState.DEGRADED : RecoveryState.ACTIVE,
                now.isAfter(subscription.graceUntil()) ? LicenseReasonCode.LICENSE_EXPIRED
                        : now.isAfter(subscription.validUntil()) ? LicenseReasonCode.LICENSE_GRACE_PERIOD : LicenseReasonCode.LICENSE_VALID);
    }

    public synchronized RecoveryState reconcile(String actor, String justification) {
        if (actor == null || actor.isBlank() || justification == null || justification.isBlank())
            throw new IllegalArgumentException("actor and justification are required");
        var before = state;
        var trusted = trustedTime.reconcile();
        var active = licenses.active().orElse(null);
        audit.record(active == null ? null : active.license().customer().customerId(), active == null ? null : active.license().licenseId(), null,
                LicenseOperation.RECOVERY_RECONCILE, "trusted-time", true, LicenseReasonCode.LICENSE_VALID,
                Map.of("actor", actor, "justification", justification, "previousState", String.valueOf(before), "sequence", Long.toString(trusted.sequence())));
        return assess();
    }

    public RecoveryState state() {
        return state;
    }

    public LicenseReasonCode reasonCode() {
        return reasonCode;
    }

    public synchronized void observeValidationFailure(LicenseReasonCode reason) {
        if (reason == LicenseReasonCode.CLOCK_ROLLBACK_DETECTED || reason == LicenseReasonCode.STATE_INTEGRITY_FAILURE
                || reason == LicenseReasonCode.STATE_ROLLBACK_DETECTED || reason == LicenseReasonCode.LICENSE_SERVER_IDENTITY_MISMATCH
                || reason == LicenseReasonCode.LICENSE_SIGNATURE_INVALID) {
            update(RecoveryState.RECOVERY_REQUIRED, reason);
        }
    }

    public boolean ready() {
        return state != RecoveryState.RECOVERY_REQUIRED;
    }

    private RecoveryState update(RecoveryState value, LicenseReasonCode reason) {
        state = value;
        reasonCode = reason;
        return value;
    }
}
