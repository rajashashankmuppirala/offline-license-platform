package com.enterprise.licensing.example.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.engine.activation.*;
import com.enterprise.licensing.engine.instance.InstanceRegistrationService;
import com.enterprise.licensing.engine.validation.LicenseValidationResult;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.repository.SqliteProductInstanceRepository;
import com.enterprise.licensing.storage.sqlite.*;
import com.enterprise.licensing.testkit.LicenseFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogicalReplicaRegistrationIntegrationTest {
    @TempDir Path temp;

    @Test void replicasRolloutsReschedulingAndRestoredNamespaceRemainOneRegisteredInstallation() throws Exception {
        var database=new SqliteDatabase(temp.resolve("license-server.db"));
        var repository=new SqliteProductInstanceRepository(database,new SqliteImmediateTransactionRunner(database),new ObjectMapper());
        var service=new InstanceRegistrationService(new ActiveLicense(),repository,()->Instant.parse("2026-01-01T00:00:00Z"),
                ignored->LicenseValidationResult.allowed(false));
        var simulator=new KubernetesReplicaSimulator();
        Path identity=temp.resolve("namespace/state/instance-identity.json");
        Path backup=temp.resolve("protected-backup/instance-identity.json");

        var initial=simulator.startReplicas(identity,3,service::register);
        String registeredId=initial.getFirst().instance().instanceId();
        String registeredFingerprint=initial.getFirst().instance().publicKeyFingerprint();
        assertAcceptedAs(initial,registeredId);

        var rollout=simulator.startReplicas(identity,20,service::register);
        var rescheduled=simulator.startReplicas(identity,7,service::register);
        assertAcceptedAs(rollout,registeredId);
        assertAcceptedAs(rescheduled,registeredId);
        assertThat(repository.list()).singleElement().extracting(RegisteredProductInstance::instanceId).isEqualTo(registeredId);

        Files.createDirectories(backup.getParent());
        Files.copy(identity,backup);
        Files.delete(identity);
        Files.copy(backup,identity);
        assertAcceptedAs(simulator.startReplicas(identity,3,service::register),registeredId);
        assertThat(repository.list()).hasSize(1);

        Files.delete(identity);
        var identityLost=simulator.startReplicas(identity,3,service::register);
        assertThat(identityLost).allSatisfy(result->{
            assertThat(result.accepted()).isFalse();
            assertThat(result.reasonCode()).isEqualTo(LicenseReasonCode.ENVIRONMENT_LIMIT_EXCEEDED);
        });
        assertThat(repository.list()).singleElement().satisfies(instance->{
            assertThat(instance.instanceId()).isEqualTo(registeredId);
            assertThat(instance.publicKeyFingerprint()).isEqualTo(registeredFingerprint);
        });
    }

    private static void assertAcceptedAs(java.util.List<com.enterprise.licensing.engine.instance.InstanceRegistrationResult> results,String instanceId){
        assertThat(results).allSatisfy(result->{assertThat(result.accepted()).isTrue();assertThat(result.instance().instanceId()).isEqualTo(instanceId);});
    }

    private static final class ActiveLicense implements LicenseRepository {
        private final ActiveLicenseState active=new ActiveLicenseState(LicenseFixtures.aInc(),null,null,null,Instant.EPOCH);
        public Optional<ActiveLicenseState> active(){return Optional.of(active);}
        public void activate(LicenseServerIdentity identity,CommercialLicense license,SignedEnvelope licenseEnvelope,
                             ActivationCertificate certificate,SignedEnvelope activationEnvelope,Instant importedAt){}
        public boolean isRevoked(String artifactType,String artifactId,Instant at){return false;}
    }
}
