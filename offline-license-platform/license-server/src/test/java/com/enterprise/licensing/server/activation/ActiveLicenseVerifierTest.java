package com.enterprise.licensing.server.activation;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.crypto.CanonicalJson;
import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.crypto.EnvelopeJsonCodec;
import com.enterprise.licensing.crypto.runtime.ActivationVerifier;
import com.enterprise.licensing.crypto.runtime.CommercialLicenseVerifier;
import com.enterprise.licensing.crypto.runtime.RuntimeEnvelopeVerifier;
import com.enterprise.licensing.crypto.vendor.ActivationSigner;
import com.enterprise.licensing.crypto.vendor.CommercialLicenseSigner;
import com.enterprise.licensing.crypto.vendor.VendorEnvelopeSigner;
import com.enterprise.licensing.engine.activation.ActiveLicenseState;
import com.enterprise.licensing.engine.validation.ActiveLicenseVerifier;
import com.enterprise.licensing.model.ActivationCertificate;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.model.LicenseServerIdentity;
import com.enterprise.licensing.model.SignedEnvelope;
import com.enterprise.licensing.storage.repository.SqliteLicenseRepository;
import com.enterprise.licensing.storage.sqlite.SqliteDatabase;
import com.enterprise.licensing.storage.sqlite.SqliteImmediateTransactionRunner;
import com.enterprise.licensing.testkit.LicenseFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActiveLicenseVerifierTest {
    @TempDir Path temp;
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final CanonicalJson canonical = new CanonicalJson(mapper);

    @Test
    void requiresActivationAndRejectsAlteredPersistedProjection() {
        var fixture = fixture(identity());

        var missingActivation = new ActiveLicenseState(
                fixture.state().license(), fixture.state().licenseEnvelope(), null, null, now);
        assertThat(fixture.verifier().verify(missingActivation).reasonCode())
                .isEqualTo(LicenseReasonCode.LICENSE_SERVER_NOT_ACTIVATED);

        var original = fixture.state().license();
        var altered = new com.enterprise.licensing.model.CommercialLicense(
                original.schemaVersion(), original.licenseId(), original.customer(), original.subscription(),
                original.activation(), original.environments(),
                new com.enterprise.licensing.model.UserEntitlement(
                        original.users().maxNamedUsers(), original.users().maxConcurrentUsers() + 1,
                        original.users().categories()),
                original.products(), original.ai(), original.activeProductionClassLimit(), original.revocationIds());
        var tamperedProjection = new ActiveLicenseState(
                altered, fixture.state().licenseEnvelope(), fixture.state().activation(),
                fixture.state().activationEnvelope(), now);
        assertThat(fixture.verifier().verify(tamperedProjection).reasonCode())
                .isEqualTo(LicenseReasonCode.STATE_INTEGRITY_FAILURE);
    }

    @Test
    void rejectsActivationCopiedToAnotherInstallationIdentity() {
        var originalIdentity = identity();
        var fixture = fixture(originalIdentity);
        var copiedVerifier = verifier(fixture.vendor().getPublic(), identity());

        assertThat(copiedVerifier.verify(fixture.state()).reasonCode())
                .isEqualTo(LicenseReasonCode.LICENSE_SERVER_IDENTITY_MISMATCH);
    }

    @Test
    void rejectsACommercialEnvelopeAlteredInPersistentStorage() {
        var identity = identity();
        var fixture = fixture(identity);
        try (var database = new SqliteDatabase(temp.resolve("license.db"))) {
            var repository = new SqliteLicenseRepository(
                    database, new SqliteImmediateTransactionRunner(database), mapper);
            var state = fixture.state();
            repository.activate(identity, state.license(), state.licenseEnvelope(), state.activation(),
                    state.activationEnvelope(), now);

            var envelope = state.licenseEnvelope();
            var altered = new SignedEnvelope(envelope.envelopeVersion(), envelope.artifactType(),
                    envelope.payloadSchemaVersion(), envelope.algorithm(), envelope.keyId(), envelope.payload(),
                    (envelope.signature().charAt(0) == 'A' ? "B" : "A") + envelope.signature().substring(1));
            database.withConnection(connection -> {
                try (var statement = connection.prepareStatement(
                        "UPDATE commercial_license SET envelope_json=? WHERE active=1")) {
                    statement.setString(1, new EnvelopeJsonCodec(mapper).write(altered));
                    statement.executeUpdate();
                    return null;
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });

            assertThat(fixture.verifier().verify(repository.active().orElseThrow()).reasonCode())
                    .isEqualTo(LicenseReasonCode.LICENSE_SIGNATURE_INVALID);
        }
    }

    private Fixture fixture(LicenseServerIdentity identity) {
        var vendor = Ed25519Keys.generate();
        var signer = new VendorEnvelopeSigner(canonical);
        var license = LicenseFixtures.aInc();
        var licenseEnvelope = new CommercialLicenseSigner(signer).sign(license, vendor);
        var activation = new ActivationCertificate(
                "1.0", "ACT-1", license.licenseId(), license.customer().customerId(),
                identity.licenseServerId(), identity.publicKeyFingerprint(), license.activation(), now);
        var activationEnvelope = new ActivationSigner(signer).sign(activation, vendor);
        return new Fixture(vendor,
                new ActiveLicenseState(license, licenseEnvelope, activation, activationEnvelope, now),
                verifier(vendor.getPublic(), identity));
    }

    private ActiveLicenseVerifier verifier(java.security.PublicKey vendorKey, LicenseServerIdentity identity) {
        var runtime = new RuntimeEnvelopeVerifier(canonical, Set.of("1.0"));
        var codec = new EnvelopeJsonCodec(mapper);
        return new ActiveLicenseVerifier(
                new CommercialLicenseVerifier(runtime, codec), new ActivationVerifier(runtime, codec),
                () -> vendorKey, () -> identity);
    }

    private LicenseServerIdentity identity() {
        var keys = Ed25519Keys.generate();
        return new LicenseServerIdentity(UUID.randomUUID(),
                new com.enterprise.licensing.model.EncodedPublicKey(
                        "Ed25519", "X.509", Ed25519Keys.encodePublic(keys.getPublic())),
                Ed25519Keys.fingerprint(keys.getPublic()), now, Map.of());
    }

    private record Fixture(java.security.KeyPair vendor, ActiveLicenseState state,
                           ActiveLicenseVerifier verifier) {}
}
