package com.enterprise.licensing.security;

import static org.assertj.core.api.Assertions.assertThat;
import com.enterprise.licensing.crypto.*;
import com.enterprise.licensing.crypto.runtime.RuntimeEnvelopeVerifier;
import com.enterprise.licensing.engine.activation.*;
import com.enterprise.licensing.engine.audit.*;
import com.enterprise.licensing.engine.identity.LicenseServerIdentityService;
import com.enterprise.licensing.engine.recovery.RecoveryService;
import com.enterprise.licensing.engine.time.*;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.identity.SoftwareIdentityProvider;
import com.enterprise.licensing.storage.repository.*;
import com.enterprise.licensing.storage.sqlite.*;
import com.enterprise.licensing.testkit.LicenseFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicensePlatformSecuritySuite {
    @TempDir Path temp;

    @Test void rejectsArtifactTamperingSchemaAttacksAndWrongArtifactType(){
        var keys=Ed25519Keys.generate();var canonical=new CanonicalJson(new ObjectMapper());byte[] payload=canonical.canonicalize(Map.of("licenseId","L"));String keyId=Ed25519Keys.fingerprint(keys.getPublic());byte[] signature=Ed25519Keys.sign(keys.getPrivate(),EnvelopeSupport.signatureInput(ArtifactType.COMMERCIAL_LICENSE,"1.0",EnvelopeSupport.ALGORITHM,keyId,payload));var valid=new SignedEnvelope(EnvelopeSupport.ENVELOPE_VERSION,ArtifactType.COMMERCIAL_LICENSE,"1.0",EnvelopeSupport.ALGORITHM,keyId,Base64.getUrlEncoder().withoutPadding().encodeToString(payload),Base64.getUrlEncoder().withoutPadding().encodeToString(signature));var verifier=new RuntimeEnvelopeVerifier(canonical,Set.of("1.0"));assertThat(verifier.verify(valid,ArtifactType.COMMERCIAL_LICENSE,keys.getPublic()).valid()).isTrue();var tampered=new SignedEnvelope(valid.envelopeVersion(),valid.artifactType(),valid.payloadSchemaVersion(),valid.algorithm(),valid.keyId(),Base64.getUrlEncoder().withoutPadding().encodeToString(canonical.canonicalize(Map.of("licenseId","OTHER"))),valid.signature());assertThat(verifier.verify(tampered,ArtifactType.COMMERCIAL_LICENSE,keys.getPublic()).reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_SIGNATURE_INVALID);var unsupported=new SignedEnvelope(valid.envelopeVersion(),valid.artifactType(),"999",valid.algorithm(),valid.keyId(),valid.payload(),valid.signature());assertThat(verifier.verify(unsupported,ArtifactType.COMMERCIAL_LICENSE,keys.getPublic()).reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_SCHEMA_UNSUPPORTED);assertThat(verifier.verify(valid,ArtifactType.ACTIVATION_CERTIFICATE,keys.getPublic()).reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_FORMAT_INVALID);}

    @Test void activationCannotMoveToAnotherInstallation(){var at=Instant.parse("2026-01-01T00:00:00Z");var original=identity(temp.resolve("one.json"),at);var other=identity(temp.resolve("two.json"),at);var license=LicenseFixtures.aInc();var server=original.publicIdentity();var activation=new ActivationCertificate("1.0","A",license.licenseId(),license.customer().customerId(),server.licenseServerId(),server.publicKeyFingerprint(),license.activation(),at);var state=new ActiveLicenseState(license,null,activation,null,at);var recovery=new RecoveryService(new StateRepository(state),other,new TrustedTimeService((now,tolerance)->new TrustedTimeResult(true,LicenseReasonCode.LICENSE_VALID,now,1),()->at,Duration.ofMinutes(5)),audit());assertThat(recovery.state()).isEqualTo(RecoveryState.RECOVERY_REQUIRED);assertThat(recovery.reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_SERVER_IDENTITY_MISMATCH);}

    @Test void nonceReplayIsRejectedAtomically(){var database=new SqliteDatabase(temp.resolve("nonce.db"));var now=Instant.parse("2026-01-01T00:00:00Z");database.withConnection(c->{try(var statement=c.prepareStatement("INSERT INTO product_instance(instance_id,public_key,public_key_fingerprint,product_id,deployment_type,environment_type,application_version,metadata_json,state,registered_at,updated_at)VALUES('I','key','fingerprint','VALIDATOR','LOCAL_JAVA','DEVELOPMENT','1','{}','REGISTERED',?,?)")){statement.setString(1,now.toString());statement.setString(2,now.toString());statement.executeUpdate();return null;}catch(Exception e){throw new RuntimeException(e);}});var nonces=new SqliteRequestNonceRepository(database,new SqliteImmediateTransactionRunner(database));assertThat(nonces.accept("I","N",now,now.plusSeconds(60))).isTrue();assertThat(nonces.accept("I","N",now.plusSeconds(1),now.plusSeconds(60))).isFalse();database.close();}

    @Test void corruptTrustedStateAndClockRollbackFailClosed(){var database=new SqliteDatabase(temp.resolve("time.db"));var trusted=new SqliteTrustedTimeRepository(database,new SqliteImmediateTransactionRunner(database));var high=Instant.parse("2026-01-01T12:00:00Z");trusted.observe(high,Duration.ofMinutes(5));assertThat(trusted.observe(high.minusSeconds(301),Duration.ofMinutes(5)).reasonCode()).isEqualTo(LicenseReasonCode.CLOCK_ROLLBACK_DETECTED);database.withConnection(c->{try(var statement=c.prepareStatement("UPDATE trusted_time_state SET checkpoint_hash='corrupt'")){statement.executeUpdate();return null;}catch(Exception e){throw new RuntimeException(e);}});assertThat(trusted.observe(high.plusSeconds(1),Duration.ofMinutes(5)).reasonCode()).isEqualTo(LicenseReasonCode.STATE_INTEGRITY_FAILURE);database.close();}

    private LicenseServerIdentityService identity(Path path,Instant at){return new LicenseServerIdentityService(new SoftwareIdentityProvider(path,()->at,Map.of()));}
    private static AuditService audit(){AuditRepository repository=new AuditRepository(){public AuditEvent append(UUID id,Instant at,String customer,String license,String instance,LicenseOperation operation,String entitlement,boolean allowed,LicenseReasonCode reason,Map<String,String>context){return new AuditEvent(id,at,customer,license,instance,operation,entitlement,allowed,reason,context,null,"hash");}public List<AuditEvent>page(int offset,int limit){return List.of();}public boolean verifyChain(){return true;}};return new AuditService(repository,new AuditRedactor(),()->Instant.EPOCH,UUID::randomUUID,false);}
    private record StateRepository(ActiveLicenseState state)implements LicenseRepository{public Optional<ActiveLicenseState>active(){return Optional.ofNullable(state);}public void activate(LicenseServerIdentity i,CommercialLicense l,SignedEnvelope le,ActivationCertificate a,SignedEnvelope ae,Instant at){}public boolean isRevoked(String type,String id,Instant at){return false;}}
}
