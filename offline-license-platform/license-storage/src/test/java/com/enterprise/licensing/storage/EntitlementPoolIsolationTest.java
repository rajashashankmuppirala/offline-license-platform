package com.enterprise.licensing.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.engine.ai.AgentResult;
import com.enterprise.licensing.engine.metering.UsageResult;
import com.enterprise.licensing.engine.user.UserLeaseResult;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.repository.*;
import com.enterprise.licensing.storage.sqlite.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntitlementPoolIsolationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    @TempDir
    Path temp;

    private static void register(SqliteProductInstanceRepository instances, String id, EnvironmentType environment) {
        var key = Ed25519Keys.generate();
        instances.register(new InstanceRegistrationRequest(id, new EncodedPublicKey("Ed25519", "X.509", Ed25519Keys.encodePublic(key.getPublic())), ProductId.VALIDATOR, DeploymentType.TOMCAT, environment, "1", Map.of()), Ed25519Keys.fingerprint(key.getPublic()), 2, 2, NOW);
        instances.transition(id, InstanceState.REGISTERED, InstanceState.ACTIVE, 2, NOW.plusSeconds(1), false);
    }

    @Test
    void userCapacityIsCustomerWideByDefaultAndOnlyExplicitPoolsIsolateIt() {
        var shared = context("users-shared.db");
        var sequence = new AtomicInteger();
        var sharedResults = ConcurrentTestSupport.race(2, () -> {
            int n = sequence.incrementAndGet();
            String instance = n == 1 ? "prod" : "uat";
            return shared.users.acquire("l" + n, "u" + n, AccountType.HUMAN, instance, "s" + n, EntitlementPool.CUSTOMER_WIDE, 1, NOW, Duration.ofMinutes(5));
        });
        assertThat(sharedResults).filteredOn(UserLeaseResult::accepted).hasSize(1);
        assertThat(sharedResults).filteredOn(r -> !r.accepted()).singleElement().extracting(UserLeaseResult::reasonCode).isEqualTo(LicenseReasonCode.CONCURRENT_USER_LIMIT_EXCEEDED);

        var isolated = context("users-isolated.db");
        sequence.set(0);
        var isolatedResults = ConcurrentTestSupport.race(2, () -> {
            int n = sequence.incrementAndGet();
            String instance = n == 1 ? "prod" : "uat";
            String pool = n == 1 ? "POOL_PROD" : "POOL_UAT";
            return isolated.users.acquire("l" + n, "u" + n, AccountType.HUMAN, instance, "s" + n, pool, 1, NOW, Duration.ofMinutes(5));
        });
        assertThat(isolatedResults).allSatisfy(r -> assertThat(r.accepted()).isTrue());
        assertThat(isolated.users.userLeases()).extracting(ConcurrentUserLease::poolId).containsExactlyInAnyOrder("POOL_PROD", "POOL_UAT");
    }

    @Test
    void agentRegistrationAndExecutionPoolsShareByDefaultAndIsolateExplicitly() {
        var shared = context("agents-shared.db");
        var sequence = new AtomicInteger();
        var sharedRegistrations = ConcurrentTestSupport.race(2, () -> {
            int n = sequence.incrementAndGet();
            return shared.agents.register("a" + n, "Agent", n == 1 ? "prod" : "uat", EntitlementPool.CUSTOMER_WIDE, 1, NOW);
        });
        assertThat(sharedRegistrations).filteredOn(AgentResult::accepted).hasSize(1);

        var isolated = context("agents-isolated.db");
        assertThat(isolated.agents.register("prod-agent", "Agent", "prod", "POOL_PROD", 1, NOW).accepted()).isTrue();
        assertThat(isolated.agents.register("uat-agent", "Agent", "uat", "POOL_UAT", 1, NOW).accepted()).isTrue();
        sequence.set(0);
        var leases = ConcurrentTestSupport.race(2, () -> sequence.incrementAndGet() == 1
                ? isolated.agents.acquire("lp", "prod-agent", "ep", "prod", "POOL_PROD", AiCapability.AUTONOMOUS_AGENT, 1, NOW, Duration.ofMinutes(5))
                : isolated.agents.acquire("lu", "uat-agent", "eu", "uat", "POOL_UAT", AiCapability.AUTONOMOUS_AGENT, 1, NOW, Duration.ofMinutes(5)));
        assertThat(leases).allSatisfy(r -> assertThat(r.accepted()).isTrue());
        assertThat(isolated.agents.leases()).extracting(AgentExecutionLease::poolId).containsExactlyInAnyOrder("POOL_PROD", "POOL_UAT");
    }

    @Test
    void meterUsageAggregatesByDefaultAndSeparatesOnlyExplicitPools() {
        var database = new SqliteDatabase(temp.resolve("meters.db"));
        var meters = new SqliteMeteringRepository(database, new SqliteImmediateTransactionRunner(database), new ObjectMapper());
        var end = Instant.parse("2026-02-01T00:00:00Z");
        var shared = ConcurrentTestSupport.race(2, new java.util.concurrent.Callable<UsageResult>() {
            private final AtomicInteger sequence = new AtomicInteger();

            public UsageResult call() {
                int n = sequence.incrementAndGet();
                return meters.consume("s" + n, "C", n == 1 ? "prod" : "uat", EntitlementPool.CUSTOMER_WIDE, "AEU", "TEST_ACTION", 2, NOW, end, 2, NOW, Map.of());
            }
        });
        assertThat(shared).filteredOn(UsageResult::accepted).hasSize(1);
        assertThat(shared).filteredOn(r -> !r.accepted()).singleElement().extracting(UsageResult::reasonCode).isEqualTo(LicenseReasonCode.AI_USAGE_LIMIT_EXCEEDED);
        assertThat(meters.consume("p", "C", "prod", "POOL_PROD", "AEU", "TEST_ACTION", 2, NOW, end, 2, NOW, Map.of()).accepted()).isTrue();
        assertThat(meters.consume("u", "C", "uat", "POOL_UAT", "AEU", "TEST_ACTION", 2, NOW, end, 2, NOW, Map.of()).accepted()).isTrue();
        assertThat(meters.counters()).extracting(UsageCounter::poolId).containsExactlyInAnyOrder(EntitlementPool.CUSTOMER_WIDE, "POOL_PROD", "POOL_UAT");
    }

    private Context context(String file) {
        var database = new SqliteDatabase(temp.resolve(file));
        var transactions = new SqliteImmediateTransactionRunner(database);
        var instances = new SqliteProductInstanceRepository(database, transactions, new ObjectMapper());
        register(instances, "prod", EnvironmentType.PRODUCTION);
        register(instances, "uat", EnvironmentType.UAT);
        return new Context(new SqliteUserLicenseRepository(database, transactions), new SqliteAiRepository(database, transactions));
    }

    private record Context(SqliteUserLicenseRepository users, SqliteAiRepository agents) {
    }
}
