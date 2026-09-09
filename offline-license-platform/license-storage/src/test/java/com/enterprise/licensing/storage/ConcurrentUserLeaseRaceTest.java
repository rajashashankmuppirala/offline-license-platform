package com.enterprise.licensing.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.engine.user.UserLeaseResult;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.repository.SqliteProductInstanceRepository;
import com.enterprise.licensing.storage.repository.SqliteUserLicenseRepository;
import com.enterprise.licensing.storage.sqlite.SqliteDatabase;
import com.enterprise.licensing.storage.sqlite.SqliteImmediateTransactionRunner;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConcurrentUserLeaseRaceTest {
    @TempDir Path temp;

    @Test void oneThousandContestedFinalSeatTrialsNeverOverAllocate() {
        var database=new SqliteDatabase(temp.resolve("leases.db"));var transactions=new SqliteImmediateTransactionRunner(database);
        registerInstance(database,transactions);var users=new SqliteUserLicenseRepository(database,transactions);
        var sequence=new AtomicInteger();var now=Instant.parse("2026-01-01T00:00:00Z");
        for(int trial=0;trial<1_000;trial++){
            var results=ConcurrentTestSupport.race(2,()->{int id=sequence.incrementAndGet();return users.acquire("l-"+id,"u-"+id,AccountType.HUMAN,"prod","s-"+id,EntitlementPool.CUSTOMER_WIDE,1,now,Duration.ofMinutes(5));});
            assertThat(results).filteredOn(UserLeaseResult::accepted).hasSize(1);
            assertThat(results).filteredOn(result->!result.accepted()).singleElement().extracting(UserLeaseResult::reasonCode)
                    .isEqualTo(LicenseReasonCode.CONCURRENT_USER_LIMIT_EXCEEDED);
            users.releaseLease(results.stream().filter(UserLeaseResult::accepted).findFirst().orElseThrow().lease().leaseId(),now);
        }
    }

    @Test void expiryAndRestartIndependentCountingReturnCapacity() {
        var database=new SqliteDatabase(temp.resolve("expiry.db"));var transactions=new SqliteImmediateTransactionRunner(database);registerInstance(database,transactions);
        var users=new SqliteUserLicenseRepository(database,transactions);var now=Instant.parse("2026-01-01T00:00:00Z");
        assertThat(users.acquire("l1","u1",AccountType.HUMAN,"prod","s1",EntitlementPool.CUSTOMER_WIDE,1,now,Duration.ofMinutes(5)).accepted()).isTrue();
        var reopened=new SqliteDatabase(temp.resolve("expiry.db"));var afterRestart=new SqliteUserLicenseRepository(reopened,new SqliteImmediateTransactionRunner(reopened));
        assertThat(afterRestart.acquire("l2","u2",AccountType.HUMAN,"prod","s2",EntitlementPool.CUSTOMER_WIDE,1,now.plusSeconds(301),Duration.ofMinutes(5)).accepted()).isTrue();
    }

    private static void registerInstance(SqliteDatabase database,SqliteImmediateTransactionRunner transactions){var key=Ed25519Keys.generate();
        var request=new InstanceRegistrationRequest("prod",new EncodedPublicKey("Ed25519","X.509",Ed25519Keys.encodePublic(key.getPublic())),ProductId.VALIDATOR,DeploymentType.TOMCAT,EnvironmentType.PRODUCTION,"1",Map.of());
        var instances=new SqliteProductInstanceRepository(database,transactions,new com.fasterxml.jackson.databind.ObjectMapper());instances.register(request,Ed25519Keys.fingerprint(key.getPublic()),1,1,Instant.parse("2026-01-01T00:00:00Z"));instances.transition("prod",InstanceState.REGISTERED,InstanceState.ACTIVE,1,Instant.parse("2026-01-01T00:00:01Z"),false);}
}
