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
import com.enterprise.licensing.engine.activation.ActivationService;
import com.enterprise.licensing.engine.activation.RenewalImpactAssessment;
import com.enterprise.licensing.engine.validation.ActiveLicenseVerifier;
import com.enterprise.licensing.engine.identity.LicenseServerIdentityService;
import com.enterprise.licensing.model.ActivationCertificate;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.storage.identity.SoftwareIdentityProvider;
import com.enterprise.licensing.storage.repository.SqliteLicenseRepository;
import com.enterprise.licensing.storage.sqlite.SqliteDatabase;
import com.enterprise.licensing.storage.sqlite.SqliteImmediateTransactionRunner;
import com.enterprise.licensing.testkit.LicenseFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActivationIntegrationTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final CanonicalJson canonical = new CanonicalJson(mapper);
    @TempDir
    Path temp;

    @Test
    void activationIsBoundToOneIndependentlyCreatedServer() {
        var vendor = Ed25519Keys.generate();
        var signer = new VendorEnvelopeSigner(canonical);
        var license = LicenseFixtures.aInc();
        var licenseEnvelope = new CommercialLicenseSigner(signer).sign(license, vendor);
        var identityA = identity("a");
        var identityB = identity("b");
        var certificate = certificate(identityA, license.licenseId(), license.customer().customerId());
        var activationEnvelope = new ActivationSigner(signer).sign(certificate, vendor);

        var accepted = service("a", identityA, vendor.getPublic()).importArtifacts(licenseEnvelope, activationEnvelope);
        var copied = service("b", identityB, vendor.getPublic()).importArtifacts(licenseEnvelope, activationEnvelope);

        assertThat(accepted.accepted()).isTrue();
        assertThat(copied.accepted()).isFalse();
        assertThat(copied.reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_SERVER_IDENTITY_MISMATCH);
    }

    @Test
    void modifiedActivationAndWrongCustomerAreRejected() {
        var vendor = Ed25519Keys.generate();
        var signer = new VendorEnvelopeSigner(canonical);
        var license = LicenseFixtures.aInc();
        var identity = identity("server");
        var licenseEnvelope = new CommercialLicenseSigner(signer).sign(license, vendor);
        var valid = new ActivationSigner(signer).sign(certificate(identity, license.licenseId(), license.customer().customerId()), vendor);
        String alteredSignature = (valid.signature().charAt(0) == 'A' ? "B" : "A") + valid.signature().substring(1);
        var modified = new com.enterprise.licensing.model.SignedEnvelope(valid.envelopeVersion(), valid.artifactType(), valid.payloadSchemaVersion(), valid.algorithm(), valid.keyId(), valid.payload(), alteredSignature);
        assertThat(service("modified", identity, vendor.getPublic()).importArtifacts(licenseEnvelope, modified).reasonCode())
                .isEqualTo(LicenseReasonCode.LICENSE_SIGNATURE_INVALID);

        var wrongCustomer = new ActivationSigner(signer).sign(certificate(identity, license.licenseId(), "OTHER"), vendor);
        assertThat(service("wrong-customer", identity, vendor.getPublic()).importArtifacts(licenseEnvelope, wrongCustomer).reasonCode())
                .isEqualTo(LicenseReasonCode.LICENSE_SERVER_IDENTITY_MISMATCH);
    }

    @Test
    void signedLicenseCanBeRegisteredWithoutCertificateWhenActivationIsDisabled() {
        var vendor = Ed25519Keys.generate();
        var signer = new VendorEnvelopeSigner(canonical);
        var license = LicenseFixtures.aInc();
        var licenseEnvelope = new CommercialLicenseSigner(signer).sign(license, vendor);
        var identity = identity("license-only");
        var database = new SqliteDatabase(temp.resolve("license-only/license.db"));
        var repository = new SqliteLicenseRepository(database, new SqliteImmediateTransactionRunner(database), mapper);
        var runtime = new RuntimeEnvelopeVerifier(canonical, Set.of("1.0"));
        var codec = new EnvelopeJsonCodec(mapper);
        var service = new ActivationService(new CommercialLicenseVerifier(runtime, codec),
                new ActivationVerifier(runtime, codec), () -> vendor.getPublic(), identity, repository, () -> now,
                RenewalImpactAssessment.none(), false);

        var result = service.importArtifacts(licenseEnvelope, null);

        assertThat(result.accepted()).isTrue();
        var persisted = repository.active().orElseThrow();
        assertThat(persisted.license()).isEqualTo(license);
        assertThat(persisted.activation()).isNull();
        assertThat(persisted.activationEnvelope()).isNull();
        var verifier = new ActiveLicenseVerifier(new CommercialLicenseVerifier(runtime, codec),
                new ActivationVerifier(runtime, codec), () -> vendor.getPublic(), identity::publicIdentity, false);
        assertThat(verifier.verify(persisted).allowed()).isTrue();
    }

    @Test
    void certificateRemainsMandatoryWhenActivationIsEnabled() {
        var vendor = Ed25519Keys.generate();
        var signer = new VendorEnvelopeSigner(canonical);
        var licenseEnvelope = new CommercialLicenseSigner(signer).sign(LicenseFixtures.aInc(), vendor);

        var result = service("activation-required", identity("activation-required"), vendor.getPublic())
                .importArtifacts(licenseEnvelope, null);

        assertThat(result.accepted()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_SERVER_NOT_ACTIVATED);
    }

    private LicenseServerIdentityService identity(String directory) {
        return new LicenseServerIdentityService(new SoftwareIdentityProvider(temp.resolve(directory + "/identity.json"), () -> now, Map.of()));
    }

    private ActivationCertificate certificate(LicenseServerIdentityService service, String licenseId, String customerId) {
        var identity = service.publicIdentity();
        return new ActivationCertificate("1.0", UUID.randomUUID().toString(), licenseId, customerId,
                identity.licenseServerId(), identity.publicKeyFingerprint(), LicenseFixtures.aInc().activation(), now);
    }

    private ActivationService service(String name, LicenseServerIdentityService identity, java.security.PublicKey vendorPublicKey) {
        var database = new SqliteDatabase(temp.resolve(name + "/license.db"));
        var transactions = new SqliteImmediateTransactionRunner(database);
        var repository = new SqliteLicenseRepository(database, transactions, mapper);
        var verifier = new RuntimeEnvelopeVerifier(canonical, Set.of("1.0"));
        var codec = new EnvelopeJsonCodec(mapper);
        return new ActivationService(new CommercialLicenseVerifier(verifier, codec), new ActivationVerifier(verifier, codec),
                vendorPublicKey, identity, repository, () -> now);
    }
}
