package com.enterprise.licensing.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.engine.instance.InstanceRegistrationResult;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.repository.SqliteProductInstanceRepository;
import com.enterprise.licensing.storage.sqlite.SqliteDatabase;
import com.enterprise.licensing.storage.sqlite.SqliteImmediateTransactionRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DisasterRecoveryRaceTest {
    @TempDir Path temp;
    private final java.security.KeyPair key = Ed25519Keys.generate();
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void standbyPromotionDemotionAndFailbackPreserveSingleActiveProductionClass() {
        var repository = repository();
        assertThat(repository.register(request("prod", EnvironmentType.PRODUCTION), fingerprint(), 1, 1, now).instance().state()).isEqualTo(InstanceState.REGISTERED);
        assertThat(repository.register(request("dr", EnvironmentType.DISASTER_RECOVERY), fingerprint(), 2, 1, now).instance().state()).isEqualTo(InstanceState.STANDBY);
        assertThat(repository.transition("prod",InstanceState.REGISTERED,InstanceState.ACTIVE,1,now.plusSeconds(1),false).accepted()).isTrue();
        assertThat(repository.transition("dr", InstanceState.STANDBY, InstanceState.ACTIVE, 1, now.plusSeconds(1), false).reasonCode())
                .isEqualTo(LicenseReasonCode.ACTIVE_PRODUCTION_LIMIT_EXCEEDED);
        assertThat(repository.switchActive("prod", "dr", 1, now.plusSeconds(2), true).accepted()).isTrue();
        assertThat(repository.find("prod").orElseThrow().state()).isEqualTo(InstanceState.STANDBY);
        assertThat(repository.find("dr").orElseThrow().state()).isEqualTo(InstanceState.ACTIVE);
        assertThat(repository.switchActive("dr", "prod", 1, now.plusSeconds(3), true).accepted()).isTrue();
        assertThat(repository.find("prod").orElseThrow().state()).isEqualTo(InstanceState.ACTIVE);
        assertThat(repository.find("dr").orElseThrow().state()).isEqualTo(InstanceState.STANDBY);
    }

    @Test
    void rejectedFailoverDoesNotDemoteTheActiveInstance() {
        var repository = repository();
        repository.register(request("prod", EnvironmentType.PRODUCTION), fingerprint(), 1, 1, now);
        repository.transition("prod",InstanceState.REGISTERED,InstanceState.ACTIVE,1,now.plusSeconds(1),false);
        assertThat(repository.switchActive("prod", "missing", 1, now, false).accepted()).isFalse();
        assertThat(repository.find("prod").orElseThrow().state()).isEqualTo(InstanceState.ACTIVE);
    }

    @Test
    void forceFailoverRevokesAllPrimaryDependentsWithoutAnOverlapWindow() {
        var database = new SqliteDatabase(temp.resolve("force.db"));
        var repository = new SqliteProductInstanceRepository(database, new SqliteImmediateTransactionRunner(database), new ObjectMapper());
        repository.register(request("prod", EnvironmentType.PRODUCTION), fingerprint(), 1, 1, now);
        repository.register(request("dr", EnvironmentType.DISASTER_RECOVERY), fingerprint(), 1, 1, now);
        repository.transition("prod",InstanceState.REGISTERED,InstanceState.ACTIVE,1,now.plusSeconds(1),false);
        execute(database,"INSERT INTO commercial_license VALUES(?,?,?,?,?,?,?,?,?)","L","C","{}","hash",now.minusSeconds(1).toString(),now.plusSeconds(3600).toString(),now.plusSeconds(7200).toString(),now.toString(),1);
        execute(database,"INSERT INTO registered_agent(agent_id,display_name,instance_id,registered_at,status) VALUES(?,?,?,?,?)","agent","Agent","prod",now.toString(),"ACTIVE");
        execute(database,"INSERT INTO user_lease(lease_id,user_id,account_type,instance_id,session_id,acquired_at,heartbeat_at,expires_at,released_at,revoked_at) VALUES(?,?,?,?,?,?,?,?,?,?)","user-lease","user","HUMAN","prod","session",now.toString(),now.toString(),now.plusSeconds(300).toString(),null,null);
        execute(database,"INSERT INTO agent_lease(lease_id,agent_id,execution_id,instance_id,capability,acquired_at,heartbeat_at,expires_at,released_at,revoked_at) VALUES(?,?,?,?,?,?,?,?,?,?)","agent-lease","agent","execution","prod","AUTONOMOUS_AGENT",now.toString(),now.toString(),now.plusSeconds(300).toString(),null,null);
        execute(database,"INSERT INTO cached_entitlement_issue VALUES(?,?,?,?,?,?,?)","cache","L","prod","{}",now.toString(),now.plusSeconds(300).toString(),null);

        assertThat(repository.switchActive("prod","dr",1,now.plusSeconds(1),false).accepted()).isFalse();
        assertThat(repository.find("prod").orElseThrow().state()).isEqualTo(InstanceState.ACTIVE);
        assertThat(repository.switchActive("prod","dr",1,now.plusSeconds(2),true).accepted()).isTrue();
        assertThat(repository.list()).filteredOn(i->i.state()==InstanceState.ACTIVE).singleElement()
                .extracting(RegisteredProductInstance::instanceId).isEqualTo("dr");
        assertThat(count(database,"SELECT count(*) FROM user_lease WHERE revoked_at IS NOT NULL")).isEqualTo(1);
        assertThat(count(database,"SELECT count(*) FROM agent_lease WHERE revoked_at IS NOT NULL")).isEqualTo(1);
        assertThat(count(database,"SELECT count(*) FROM cached_entitlement_issue WHERE revoked_at IS NOT NULL")).isEqualTo(1);
    }

    @Test
    void simultaneousPromotionsAllowExactlyOneWinner() {
        var repository = repository();
        repository.register(request("dr-a", EnvironmentType.DISASTER_RECOVERY), fingerprint(), 2, 1, now);
        repository.register(request("dr-b", EnvironmentType.DISASTER_RECOVERY), fingerprint(), 2, 1, now);
        var results = ConcurrentTestSupport.race(2, new java.util.concurrent.Callable<InstanceRegistrationResult>() {
            private final java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
            @Override public InstanceRegistrationResult call() {
                String id = next.getAndIncrement() == 0 ? "dr-a" : "dr-b";
                return repository.transition(id, InstanceState.STANDBY, InstanceState.ACTIVE, 1, now.plusSeconds(1), false);
            }
        });
        assertThat(results).filteredOn(InstanceRegistrationResult::accepted).hasSize(1);
        assertThat(results).filteredOn(result -> !result.accepted()).hasSize(1);
    }

    private SqliteProductInstanceRepository repository() {
        var database = new SqliteDatabase(temp.resolve(java.util.UUID.randomUUID() + ".db"));
        return new SqliteProductInstanceRepository(database, new SqliteImmediateTransactionRunner(database), new ObjectMapper());
    }
    private InstanceRegistrationRequest request(String id, EnvironmentType type) {
        return new InstanceRegistrationRequest(id, new EncodedPublicKey("Ed25519", "X.509", Ed25519Keys.encodePublic(key.getPublic())),
                ProductId.VALIDATOR, DeploymentType.KUBERNETES, type, "1.0", Map.of());
    }
    private String fingerprint() { return Ed25519Keys.fingerprint(key.getPublic()); }
    private static void execute(SqliteDatabase database,String sql,Object...values){database.withConnection(connection->{try(var statement=connection.prepareStatement(sql)){for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);statement.executeUpdate();return null;}catch(Exception e){throw new RuntimeException(e);}});}
    private static int count(SqliteDatabase database,String sql){return database.withConnection(connection->{try(var statement=connection.prepareStatement(sql);var result=statement.executeQuery()){return result.getInt(1);}catch(Exception e){throw new RuntimeException(e);}});}
}
