package com.enterprise.licensing.server.operations;

import static org.assertj.core.api.Assertions.assertThat;
import com.enterprise.licensing.engine.activation.*;
import com.enterprise.licensing.engine.audit.*;
import com.enterprise.licensing.engine.identity.LicenseServerIdentityService;
import com.enterprise.licensing.engine.recovery.RecoveryService;
import com.enterprise.licensing.engine.time.*;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.identity.SoftwareIdentityProvider;
import com.enterprise.licensing.storage.integrity.FileTrustedTimeCheckpoint;
import com.enterprise.licensing.testkit.LicenseFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecoveryIntegrationTest {
    @TempDir Path temp;
    private final Instant now=Instant.parse("2026-01-02T00:00:00Z");

    @Test void sameInstallationRestartsActiveButMigrationAndDeletionDoNotRecoverAuthority(){
        Path identityFile=temp.resolve("original/identity.json");
        var original=identity(identityFile);var active=new Repository(state(original));
        assertThat(service(active,original,new MemoryTime(now,0)).state()).isEqualTo(RecoveryState.ACTIVE);
        var restarted=identity(identityFile);
        assertThat(service(active,restarted,new MemoryTime(now,1)).state()).isEqualTo(RecoveryState.ACTIVE);
        var migrated=identity(temp.resolve("new-host/identity.json"));
        assertThat(service(active,migrated,new MemoryTime(now,1)).state()).isEqualTo(RecoveryState.RECOVERY_REQUIRED);
        assertThat(service(new Repository(null),identity(temp.resolve("deleted/identity.json")),new MemoryTime(now,0)).state()).isEqualTo(RecoveryState.UNACTIVATED);
    }

    @Test void staleRestoreStaysRecoveryRequiredUntilAuditedAdminReconciliation(){
        var identity=identity(temp.resolve("identity/identity.json"));var checkpoint=temp.resolve("identity/time.json");
        var live=new FileTrustedTimeCheckpoint(new MemoryTime(now,5),identity,checkpoint,new ObjectMapper().findAndRegisterModules());
        live.observe(now.plusSeconds(1),Duration.ofMinutes(5));
        var restored=new FileTrustedTimeCheckpoint(new MemoryTime(now,4),identity,checkpoint,new ObjectMapper().findAndRegisterModules());
        var audit=new MemoryAudit();var recovery=new RecoveryService(new Repository(state(identity)),identity,
                new TrustedTimeService(restored,()->now.plusSeconds(2),Duration.ofMinutes(5)),audit.service());
        assertThat(recovery.state()).isEqualTo(RecoveryState.RECOVERY_REQUIRED);
        assertThat(recovery.assess()).isEqualTo(RecoveryState.RECOVERY_REQUIRED);
        assertThat(recovery.reconcile("root-admin","verified same-host backup")).isEqualTo(RecoveryState.ACTIVE);
        assertThat(audit.events).singleElement().extracting(AuditEvent::operation).isEqualTo(LicenseOperation.RECOVERY_RECONCILE);
    }

    private RecoveryService service(LicenseRepository repository,LicenseServerIdentityService identity,TrustedTimeRepository time){return new RecoveryService(repository,identity,new TrustedTimeService(time,()->now,Duration.ofMinutes(5)),new MemoryAudit().service());}
    private LicenseServerIdentityService identity(Path file){return new LicenseServerIdentityService(new SoftwareIdentityProvider(file,()->now,Map.of()));}
    private ActiveLicenseState state(LicenseServerIdentityService identity){var license=LicenseFixtures.aInc();var server=identity.publicIdentity();var activation=new ActivationCertificate("1.0","ACT-1",license.licenseId(),license.customer().customerId(),server.licenseServerId(),server.publicKeyFingerprint(),license.activation(),now);return new ActiveLicenseState(license,null,activation,null,now);}

    private record Repository(ActiveLicenseState value) implements LicenseRepository {public Optional<ActiveLicenseState>active(){return Optional.ofNullable(value);}public void activate(LicenseServerIdentity i,CommercialLicense l,SignedEnvelope le,ActivationCertificate a,SignedEnvelope ae,Instant at){}public boolean isRevoked(String t,String i,Instant at){return false;}}
    private static final class MemoryTime implements TrustedTimeRepository {private Instant highest;private long sequence;MemoryTime(Instant h,long s){highest=h;sequence=s;}public Optional<TrustedTimeResult>current(){return Optional.of(new TrustedTimeResult(true,LicenseReasonCode.LICENSE_VALID,highest,sequence));}public TrustedTimeResult observe(Instant at,Duration tolerance){if(at.isAfter(highest))highest=at;return new TrustedTimeResult(true,LicenseReasonCode.LICENSE_VALID,highest,++sequence);}public TrustedTimeResult reconcile(Instant at,long minimum){if(at.isAfter(highest))highest=at;sequence=Math.max(sequence,minimum)+1;return new TrustedTimeResult(true,LicenseReasonCode.LICENSE_VALID,highest,sequence);}}
    private static final class MemoryAudit implements AuditRepository {private final List<AuditEvent>events=new ArrayList<>();AuditService service(){return new AuditService(this,new AuditRedactor(),()->Instant.EPOCH,()->UUID.randomUUID(),false);}public AuditEvent append(UUID id,Instant at,String customer,String license,String instance,LicenseOperation operation,String entitlement,boolean allowed,LicenseReasonCode reason,Map<String,String>context){var event=new AuditEvent(id,at,customer,license,instance,operation,entitlement,allowed,reason,context,null,"hash");events.add(event);return event;}public List<AuditEvent>page(int offset,int limit){return List.copyOf(events);}public boolean verifyChain(){return true;}}
}
