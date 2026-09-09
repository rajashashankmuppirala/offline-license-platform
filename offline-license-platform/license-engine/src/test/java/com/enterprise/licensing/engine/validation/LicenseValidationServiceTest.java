package com.enterprise.licensing.engine.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.engine.activation.*;
import com.enterprise.licensing.engine.time.*;
import com.enterprise.licensing.model.*;

import java.time.*;
import java.util.*;

import org.junit.jupiter.api.Test;

class LicenseValidationServiceTest {
    private static CommercialLicense license() {
        return new CommercialLicense("1.0", "L", new CustomerIdentity("C", "C", "c@x"), new SubscriptionEntitlement("SUBSCRIPTION", "T", Instant.parse("2026-01-02T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z"), 7), new ActivationPolicy(true, 1, true), List.of(), new UserEntitlement(1, 1, List.of()), List.of(), new AIEntitlement(false, 0, 0, Set.of(), List.of()), 1, Set.of());
    }

    private static CommercialLicense renewed() {
        var old = license();
        return new CommercialLicense("1.0", "L2", old.customer(), new SubscriptionEntitlement("SUBSCRIPTION", "T", Instant.parse("2026-01-02T00:00:00Z"), Instant.parse("2027-02-01T00:00:00Z"), 7), old.activation(), old.environments(), old.users(), old.products(), old.ai(), 2, Set.of());
    }

    @Test
    void enforcesFutureExpiryAndRestrictedGraceWithExplicitCodes() {
        var mutable = new Mutable(Instant.parse("2026-01-01T00:00:00Z"));
        var repository = new Repo(license());
        var trusted = new TrustedTimeService((now, tolerance) -> new TrustedTimeResult(true, LicenseReasonCode.LICENSE_VALID, now, 1), mutable, Duration.ofMinutes(5));
        var service = new LicenseValidationService(repository, trusted, mutable, state -> LicenseValidationResult.allowed(false));
        assertThat(service.evaluate(LicenseOperation.PRODUCT_CHECK).reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_NOT_YET_VALID);
        mutable.now = Instant.parse("2026-01-15T00:00:00Z");
        assertThat(service.evaluate(LicenseOperation.PRODUCT_CHECK).reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_VALID);
        mutable.now = Instant.parse("2026-02-02T00:00:00Z");
        assertThat(service.evaluate(LicenseOperation.PRODUCT_CHECK).reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_GRACE_PERIOD);
        assertThat(service.evaluate(LicenseOperation.INSTANCE_REGISTER).reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_EXPIRED);
        repository.license = renewed();
        assertThat(service.evaluate(LicenseOperation.PRODUCT_CHECK).reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_VALID);
        repository.license = license();
        mutable.now = Instant.parse("2026-02-08T00:00:01Z");
        assertThat(service.evaluate(LicenseOperation.PRODUCT_CHECK).reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_EXPIRED);
    }

    private static final class Mutable implements com.enterprise.licensing.engine.ports.TimeSource {
        Instant now;

        Mutable(Instant n) {
            now = n;
        }

        public Instant now() {
            return now;
        }
    }

    private static final class Repo implements LicenseRepository {
        CommercialLicense license;

        Repo(CommercialLicense license) {
            this.license = license;
        }

        public Optional<ActiveLicenseState> active() {
            return Optional.of(new ActiveLicenseState(license, null, null, null, Instant.EPOCH));
        }

        public void activate(LicenseServerIdentity i, CommercialLicense l, SignedEnvelope le, ActivationCertificate a, SignedEnvelope ae, Instant at) {
        }

        public boolean isRevoked(String t, String i, Instant a) {
            return false;
        }
    }
}
