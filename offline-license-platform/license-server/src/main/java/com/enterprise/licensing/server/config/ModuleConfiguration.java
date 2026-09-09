package com.enterprise.licensing.server.config;

import com.enterprise.licensing.engine.validation.ActiveLicenseVerifier;
import com.enterprise.licensing.engine.validation.LicenseValidationService;
import com.enterprise.licensing.storage.integrity.FileTrustedTimeCheckpoint;
import com.enterprise.licensing.storage.integrity.AuthoritativeStateIntegrityService;
import com.enterprise.licensing.engine.status.LicenseStatusService;
import com.enterprise.licensing.engine.recovery.RecoveryService;
import com.enterprise.licensing.server.health.StartupDiagnosticState;
import com.enterprise.licensing.model.*;

import com.enterprise.licensing.crypto.*;
import com.enterprise.licensing.crypto.runtime.*;
import com.enterprise.licensing.engine.activation.*;
import com.enterprise.licensing.engine.ai.*;
import com.enterprise.licensing.engine.audit.*;
import com.enterprise.licensing.engine.cache.*;
import com.enterprise.licensing.engine.entitlement.*;
import com.enterprise.licensing.engine.environment.*;
import com.enterprise.licensing.engine.identity.*;
import com.enterprise.licensing.engine.instance.*;
import com.enterprise.licensing.engine.metering.*;
import com.enterprise.licensing.engine.ports.*;
import com.enterprise.licensing.engine.time.*;
import com.enterprise.licensing.engine.user.*;
import com.enterprise.licensing.storage.identity.*;
import com.enterprise.licensing.storage.operations.*;
import com.enterprise.licensing.storage.repository.*;
import com.enterprise.licensing.storage.sqlite.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration
public class ModuleConfiguration {
    @Bean
    ProductionTlsGuard productionTlsGuard(org.springframework.core.env.Environment environment, @Value("${server.ssl.enabled:false}") boolean enabled) {
        return new ProductionTlsGuard(environment.getActiveProfiles(), enabled);
    }

    @Bean
    ObjectMapper legacyObjectMapper() {
        return new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    CanonicalJson canonicalJson(ObjectMapper m) {
        return new CanonicalJson(m);
    }

    @Bean
    TimeSource timeSource() {
        return TimeSource.systemUtc();
    }

    @Bean
    IdGenerator idGenerator() {
        return IdGenerator.random();
    }

    @Bean(destroyMethod = "close")
    SqliteDatabase sqliteDatabase(@Value("${licensing.data-file}") String file) {
        return new SqliteDatabase(Path.of(file));
    }

    @Bean
    SqliteImmediateTransactionRunner transactions(SqliteDatabase d) {
        return new SqliteImmediateTransactionRunner(d);
    }

    @Bean
    SqliteBackupService sqliteBackupService(SqliteDatabase d, SqliteImmediateTransactionRunner t) {
        return new SqliteBackupService(d, t);
    }

    @Bean
    StartupDiagnosticState startupDiagnosticState() {
        return new StartupDiagnosticState();
    }

    @Bean
    LicenseServerIdentityService identityService(@Value("${licensing.identity-directory}") String directory, TimeSource time, StartupDiagnosticState diagnostics) {
        try {
            return new LicenseServerIdentityService(new SoftwareIdentityProvider(Path.of(directory).resolve("identity.json"), time, Map.of("provider", "software")));
        } catch (RuntimeException exception) {
            diagnostics.enter("IDENTITY_UNREADABLE", LicenseReasonCode.STATE_INTEGRITY_FAILURE);
            var keys = Ed25519Keys.generate();
            var identity = new LicenseServerIdentity(UUID.randomUUID(), new EncodedPublicKey("Ed25519", "X.509", Ed25519Keys.encodePublic(keys.getPublic())), Ed25519Keys.fingerprint(keys.getPublic()), time.now(), Map.of("provider", "diagnostic-ephemeral"));
            return new LicenseServerIdentityService(() -> new IdentityMaterial(identity, keys.getPublic(), keys.getPrivate()));
        }
    }

    @Bean
    SqliteLicenseRepository licenseRepository(SqliteDatabase d, SqliteImmediateTransactionRunner t, ObjectMapper m) {
        return new SqliteLicenseRepository(d, t, m);
    }

    @Bean
    SqliteProductInstanceRepository instanceRepository(SqliteDatabase d, SqliteImmediateTransactionRunner t, ObjectMapper m) {
        return new SqliteProductInstanceRepository(d, t, m);
    }

    @Bean
    SqliteUserLicenseRepository userRepository(SqliteDatabase d, SqliteImmediateTransactionRunner t) {
        return new SqliteUserLicenseRepository(d, t);
    }

    @Bean
    SqliteAiRepository aiRepository(SqliteDatabase d, SqliteImmediateTransactionRunner t) {
        return new SqliteAiRepository(d, t);
    }

    @Bean
    SqliteMeteringRepository meteringRepository(SqliteDatabase d, SqliteImmediateTransactionRunner t, ObjectMapper m) {
        return new SqliteMeteringRepository(d, t, m);
    }

    @Bean
    SqliteCachedEntitlementRepository cacheRepository(SqliteDatabase d, SqliteImmediateTransactionRunner t, ObjectMapper m) {
        return new SqliteCachedEntitlementRepository(d, t, m);
    }

    @Bean
    SqliteAuditRepository auditRepository(SqliteDatabase d, SqliteImmediateTransactionRunner t, ObjectMapper m) {
        return new SqliteAuditRepository(d, t, m);
    }

    @Bean
    SqliteTrustedTimeRepository trustedTimeRepository(SqliteDatabase d, SqliteImmediateTransactionRunner t) {
        return new SqliteTrustedTimeRepository(d, t);
    }

    @Bean
    RuntimeEnvelopeVerifier envelopeVerifier(CanonicalJson c) {
        return new RuntimeEnvelopeVerifier(c, Set.of("1.0"));
    }

    @Bean
    EnvelopeJsonCodec envelopeJsonCodec(ObjectMapper m) {
        return new EnvelopeJsonCodec(m);
    }

    @Bean
    ActivationRequestService activationRequestService(LicenseServerIdentityService i, TimeSource t, IdGenerator ids, CanonicalJson c) {
        return new ActivationRequestService(i, t, ids, c);
    }

    @Bean
    RenewalImpactAssessment renewalImpactAssessment(SqliteProductInstanceRepository instances, SqliteUserLicenseRepository users, SqliteAiRepository agents, SqliteMeteringRepository meters) {
        return new RepositoryRenewalImpactAssessment(instances, users, agents, meters);
    }

    @Bean
    ActivationService activationService(RuntimeEnvelopeVerifier v, EnvelopeJsonCodec codec, @Value("${licensing.vendor-public-key-file:}") String keyFile, LicenseServerIdentityService i, SqliteLicenseRepository r, TimeSource t, RenewalImpactAssessment impact) {
        return new ActivationService(new CommercialLicenseVerifier(v, codec), new ActivationVerifier(v, codec), () -> {
            if (keyFile.isBlank()) throw new IllegalStateException("Vendor public key file is not configured");
            try {
                return Ed25519Keys.decodePublic(Files.readString(Path.of(keyFile)).trim());
            } catch (Exception e) {
                throw new IllegalStateException("Cannot load vendor public key", e);
            }
        }, i, r, t, impact);
    }

    @Bean
    InstanceRegistrationService instanceRegistrationService(SqliteLicenseRepository l, SqliteProductInstanceRepository i, TimeSource t, LicenseValidationService v) {
        return new InstanceRegistrationService(l, i, t, v);
    }

    @Bean
    EnvironmentLifecycleService lifecycleService(SqliteLicenseRepository l, SqliteProductInstanceRepository i, TimeSource t, LicenseValidationService v) {
        return new EnvironmentLifecycleService(l, i, t, v);
    }

    @Bean
    DisasterRecoveryService drService(EnvironmentLifecycleService l, SqliteLicenseRepository licenses, SqliteProductInstanceRepository instances, TimeSource time, LicenseValidationService v) {
        return new DisasterRecoveryService(l, licenses, instances, time, v);
    }

    @Bean
    UserLicenseService userLicenseService(SqliteLicenseRepository l, SqliteUserLicenseRepository u, SqliteProductInstanceRepository instances, TimeSource t, IdGenerator ids, @Value("${licensing.user-lease-duration:PT5M}") Duration lease, LicenseValidationService v) {
        return new UserLicenseService(l, u, t, ids, lease, Duration.ofDays(30), v, instances);
    }

    @Bean
    AgentRegistryService agentRegistryService(SqliteLicenseRepository l, SqliteAiRepository a, SqliteProductInstanceRepository instances, TimeSource t, LicenseValidationService v) {
        return new AgentRegistryService(l, a, t, v, instances);
    }

    @Bean
    AgentLeaseService agentLeaseService(SqliteLicenseRepository l, SqliteAiRepository a, SqliteProductInstanceRepository instances, TimeSource t, IdGenerator ids, @Value("${licensing.agent-lease-duration:PT5M}") Duration duration, LicenseValidationService v) {
        return new AgentLeaseService(l, a, t, ids, duration, v, instances);
    }

    @Bean
    AEUPolicy aeuPolicy(@Value("${licensing.aeu-weights.INTERACTIVE_AI_REQUEST:1}") long interactive, @Value("${licensing.aeu-weights.AUTONOMOUS_AGENT_RUN:5}") long autonomous, @Value("${licensing.aeu-weights.AGENT_TOOL_INVOCATION:1}") long tool, @Value("${licensing.aeu-weights.SCHEDULED_AGENT_RUN:5}") long scheduled) {
        return new ConfiguredAEUPolicy(Map.of("INTERACTIVE_AI_REQUEST", interactive, "AUTONOMOUS_AGENT_RUN", autonomous, "AGENT_TOOL_INVOCATION", tool, "SCHEDULED_AGENT_RUN", scheduled));
    }

    @Bean
    MeteringService meteringService(SqliteLicenseRepository l, SqliteMeteringRepository m, SqliteProductInstanceRepository instances, TimeSource t, IdGenerator ids, LicenseValidationService v, AEUPolicy policy) {
        return new MeteringService(l, m, t, ids, v, instances, policy);
    }

    @Bean
    EntitlementService entitlementService(SqliteLicenseRepository l, SqliteProductInstanceRepository i, SqliteUserLicenseRepository u, TimeSource t, IdGenerator ids, LicenseValidationService v) {
        return new EntitlementService(l, i, u, t, ids, v);
    }

    @Bean
    LicenseStatusService licenseStatusService(LicenseServerIdentityService i, SqliteLicenseRepository l, SqliteProductInstanceRepository p, SqliteUserLicenseRepository u, SqliteAiRepository a, SqliteMeteringRepository m, TimeSource t, RecoveryService r) {
        return new LicenseStatusService(i, l, p, u, a, m, t, r);
    }

    @Bean
    CachedEntitlementService cachedEntitlementService(SqliteLicenseRepository l, SqliteProductInstanceRepository i, LicenseServerIdentityService identity, SqliteCachedEntitlementRepository r, TimeSource t, IdGenerator ids, @Value("${licensing.cached-entitlement-duration:PT24H}") Duration duration, CanonicalJson c, LicenseValidationService v) {
        return new CachedEntitlementService(l, i, identity, r, t, ids, duration, c, v);
    }

    @Bean
    LicenseAvailabilityPolicy availabilityPolicy() {
        return LicenseAvailabilityPolicy.pocDefaults();
    }

    @Bean
    ActiveLicenseVerifier activeLicenseVerifier(RuntimeEnvelopeVerifier v, EnvelopeJsonCodec codec, @Value("${licensing.vendor-public-key-file:}") String keyFile, LicenseServerIdentityService identity) {
        return new ActiveLicenseVerifier(new CommercialLicenseVerifier(v, codec), new ActivationVerifier(v, codec), () -> {
            if (keyFile.isBlank()) throw new IllegalStateException("Vendor public key file is not configured");
            try {
                return Ed25519Keys.decodePublic(Files.readString(Path.of(keyFile)).trim());
            } catch (Exception e) {
                throw new IllegalStateException("Cannot load vendor public key", e);
            }
        }, identity::publicIdentity);
    }

    @Bean
    AuthoritativeStateIntegrityService authoritativeStateIntegrityService(SqliteDatabase database, LicenseServerIdentityService identity, @Value("${licensing.state-integrity-file:}") String file, @Value("${licensing.identity-directory}") String identityDirectory, ObjectMapper mapper) {
        Path checkpoint = file.isBlank() ? Path.of(identityDirectory).resolve("authoritative-state.checkpoint") : Path.of(file);
        return new AuthoritativeStateIntegrityService(database, identity, checkpoint, mapper);
    }

    @Bean
    FileTrustedTimeCheckpoint fileTrustedTimeCheckpoint(SqliteTrustedTimeRepository r, LicenseServerIdentityService i, @Value("${licensing.checkpoint-file}") String f, ObjectMapper m) {
        return new FileTrustedTimeCheckpoint(r, i, Path.of(f), m);
    }

    @Bean
    TrustedTimeService trustedTimeService(FileTrustedTimeCheckpoint r, TimeSource t, @Value("${licensing.clock-rollback-tolerance:PT5M}") Duration tolerance) {
        return new TrustedTimeService(r, t, tolerance);
    }

    @Bean
    LicenseValidationService licenseValidationService(SqliteLicenseRepository l, TrustedTimeService tt, TimeSource t, ActiveLicenseVerifier verifier, AuthoritativeStateIntegrityService integrity, RecoveryService recovery) {
        return new LicenseValidationService(l, tt, t, verifier, integrity, recovery::observeValidationFailure, recovery::ready, recovery::reasonCode);
    }

    @Bean
    AuditService auditService(SqliteAuditRepository r, TimeSource t, IdGenerator ids) {
        return new AuditService(r, new AuditRedactor(), t, ids, false);
    }

    @Bean
    RecoveryService recoveryService(SqliteLicenseRepository l, LicenseServerIdentityService i, TrustedTimeService t, AuditService a) {
        return new RecoveryService(l, i, t, a, false);
    }

    @Bean
    SqliteRequestNonceRepository nonceRepository(SqliteDatabase d, SqliteImmediateTransactionRunner t) {
        return new SqliteRequestNonceRepository(d, t);
    }
}
