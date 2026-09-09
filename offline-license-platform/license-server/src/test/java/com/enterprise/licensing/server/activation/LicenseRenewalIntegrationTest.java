package com.enterprise.licensing.server.activation;

import com.enterprise.licensing.crypto.*;
import com.enterprise.licensing.crypto.runtime.*;
import com.enterprise.licensing.crypto.vendor.*;
import com.enterprise.licensing.engine.activation.*;
import com.enterprise.licensing.engine.identity.LicenseServerIdentityService;
import com.enterprise.licensing.engine.validation.ActiveLicenseVerifier;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.identity.SoftwareIdentityProvider;
import com.enterprise.licensing.storage.repository.*;
import com.enterprise.licensing.storage.sqlite.*;
import com.enterprise.licensing.testkit.LicenseFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class LicenseRenewalIntegrationTest {
 @TempDir Path temp; private final Instant now=Instant.parse("2026-01-01T00:00:00Z");
 @Test void sameCustomerIdentityReusesActivationAndPreservesCurrentMonthUsage(){
  var mapper=new ObjectMapper().registerModule(new JavaTimeModule());var canonical=new CanonicalJson(mapper);var vendor=Ed25519Keys.generate();var signer=new VendorEnvelopeSigner(canonical);
  var database=new SqliteDatabase(temp.resolve("license.db"));var transactions=new SqliteImmediateTransactionRunner(database);var repository=new SqliteLicenseRepository(database,transactions,mapper);
  var identity=new LicenseServerIdentityService(new SoftwareIdentityProvider(temp.resolve("identity/identity.json"),()->now,Map.of()));var verifier=new RuntimeEnvelopeVerifier(canonical,Set.of("1.0"));var codec=new EnvelopeJsonCodec(mapper);
  var meter=new SqliteMeteringRepository(database,transactions,mapper);
  var impact=new RepositoryRenewalImpactAssessment(new SqliteProductInstanceRepository(database,transactions,mapper),new SqliteUserLicenseRepository(database,transactions),new SqliteAiRepository(database,transactions),meter);
  var service=new ActivationService(new CommercialLicenseVerifier(verifier,codec),new ActivationVerifier(verifier,codec),()->vendor.getPublic(),identity,repository,()->now,impact);
  var original=LicenseFixtures.aInc();var certificate=new ActivationCertificate("1.0","ACT-1",original.licenseId(),original.customer().customerId(),identity.publicIdentity().licenseServerId(),identity.publicIdentity().publicKeyFingerprint(),original.activation(),now);
  assertThat(service.importArtifacts(new CommercialLicenseSigner(signer).sign(original,vendor),new ActivationSigner(signer).sign(certificate,vendor)).accepted()).isTrue();
  meter.consume("usage-1","AINC","prod",EntitlementPool.CUSTOMER_WIDE,"AI_EXECUTION_UNIT","TEST_ACTION",20,now,Instant.parse("2026-02-01T00:00:00Z"),100,now,Map.of());
  var changedId=new CommercialLicense("1.0","LIC-AINC-2027-001",original.customer(),original.subscription(),original.activation(),original.environments(),original.users(),original.products(),original.ai(),1,Set.of());
  assertThat(service.importArtifacts(new CommercialLicenseSigner(signer).sign(changedId,vendor),null).reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_SERVER_NOT_ACTIVATED);
  var renewed=new CommercialLicense("1.0",original.licenseId(),original.customer(),original.subscription(),original.activation(),original.environments(),new UserEntitlement(100,10,original.users().categories()),original.products(),new AIEntitlement(true,10,3,original.ai().capabilities(),List.of(new MeterEntitlement("AI_EXECUTION_UNIT",MeterPeriod.MONTHLY,10))),now,2,1,Set.of());
  var renewal=service.importArtifacts(new CommercialLicenseSigner(signer).sign(renewed,vendor),null);
  assertThat(renewal.accepted()).isTrue();assertThat(renewal.warnings()).contains("METER_OVER_ALLOCATED:AI_EXECUTION_UNIT");
  assertThat(service.importArtifacts(new CommercialLicenseSigner(signer).sign(original,vendor),null).reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_FORMAT_INVALID);
  var conflictingSequence=new CommercialLicense("1.0",renewed.licenseId(),renewed.customer(),renewed.subscription(),renewed.activation(),renewed.environments(),new UserEntitlement(99,9,renewed.users().categories()),renewed.products(),renewed.ai(),renewed.issuedAt(),renewed.licenseSequence(),renewed.activeProductionClassLimit(),renewed.revocationIds());
  assertThat(service.importArtifacts(new CommercialLicenseSigner(signer).sign(conflictingSequence,vendor),null).reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_FORMAT_INVALID);
  assertThat(repository.active().orElseThrow().license().licenseId()).isEqualTo(original.licenseId());
  var activeVerifier=new ActiveLicenseVerifier(new CommercialLicenseVerifier(verifier,codec),new ActivationVerifier(verifier,codec),()->vendor.getPublic(),identity::publicIdentity);
  assertThat(activeVerifier.verify(repository.active().orElseThrow()).reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_VALID);
  assertThat(meter.counters()).singleElement().satisfies(c->{assertThat(c.usedAmount()).isEqualTo(20);assertThat(c.licensedLimit()).isEqualTo(100);});
  var after=meter.consume("usage-2","AINC","prod",EntitlementPool.CUSTOMER_WIDE,"AI_EXECUTION_UNIT","TEST_ACTION",1,now,Instant.parse("2026-02-01T00:00:00Z"),10,now,Map.of());
  assertThat(after.accepted()).isFalse();assertThat(after.counter().usedAmount()).isEqualTo(20);assertThat(after.counter().licensedLimit()).isEqualTo(10);
 }
}
