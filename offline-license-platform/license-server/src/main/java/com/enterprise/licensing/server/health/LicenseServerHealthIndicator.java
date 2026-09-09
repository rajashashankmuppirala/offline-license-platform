package com.enterprise.licensing.server.health;

import com.enterprise.licensing.engine.activation.LicenseRepository;
import com.enterprise.licensing.engine.identity.LicenseServerIdentityService;
import com.enterprise.licensing.engine.recovery.RecoveryService;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.storage.integrity.AuthoritativeStateIntegrityService;
import com.enterprise.licensing.storage.sqlite.SqliteImmediateTransactionRunner;
import org.springframework.boot.health.contributor.*;
import org.springframework.stereotype.Component;

@Component("licenseServer")
public final class LicenseServerHealthIndicator implements HealthIndicator {
    private final LicenseServerIdentityService identity;
    private final LicenseRepository licenses;
    private final RecoveryService recovery;
    private final SqliteImmediateTransactionRunner transactions;
    private final AuthoritativeStateIntegrityService integrity;
    private final StartupDiagnosticState startup;

    public LicenseServerHealthIndicator(LicenseServerIdentityService identity,LicenseRepository licenses,
                                        RecoveryService recovery,SqliteImmediateTransactionRunner transactions,
                                        AuthoritativeStateIntegrityService integrity,StartupDiagnosticState startup){
        this.identity=identity;this.licenses=licenses;this.recovery=recovery;this.transactions=transactions;this.integrity=integrity;this.startup=startup;
    }

    @Override public Health health(){
        try {
            if(startup.safeMode())return Health.down().withDetail("reasonCode",startup.reasonCode()).withDetail("diagnosticCode",startup.diagnosticCode()).build();
            var verified=integrity.verifyOrInitialize();
            if(verified!=LicenseReasonCode.LICENSE_VALID){recovery.observeValidationFailure(verified);return details(Health.down());}
            integrity.beginVerifiedRequest();
            try {transactions.write(()->{recovery.assess();return null;});integrity.seal();}
            finally {integrity.endVerifiedRequest();}
            return details(recovery.ready()?Health.up():Health.down());
        } catch(RuntimeException exception){return Health.down().withDetail("reasonCode",LicenseReasonCode.STATE_INTEGRITY_FAILURE).withDetail("diagnosticCode","STARTUP_OR_INTEGRITY_FAILURE").build();}
    }

    private Health details(Health.Builder builder){return builder.withDetail("serverId",identity.publicIdentity().licenseServerId())
            .withDetail("activationState",licenses.active().isPresent()?"ACTIVE":"UNACTIVATED")
            .withDetail("recoveryState",recovery.state()).withDetail("reasonCode",recovery.reasonCode()).build();}
}
