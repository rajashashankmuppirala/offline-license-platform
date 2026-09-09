package com.enterprise.licensing.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.engine.ai.AgentLeaseResult;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.repository.*;
import com.enterprise.licensing.storage.sqlite.*;

import java.nio.file.Path;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentExecutionLeaseRaceTest {
    @TempDir
    Path temp;

    @Test
    void oneThousandContestedExecutionTrialsNeverOverAllocate() {
        var d = new SqliteDatabase(temp.resolve("execution.db"));
        var t = new SqliteImmediateTransactionRunner(d);
        AgentRegistrationRaceTest.register(d, t);
        var a = new SqliteAiRepository(d, t);
        var now = Instant.parse("2026-01-01T00:00:00Z");
        a.register("agent", "Agent", "prod", EntitlementPool.CUSTOMER_WIDE, 1, now);
        var n = new AtomicInteger();
        for (int trial = 0; trial < 1_000; trial++) {
            var results = ConcurrentTestSupport.race(2, () -> {
                int i = n.incrementAndGet();
                return a.acquire("lease-" + i, "agent", "execution-" + i, "prod", EntitlementPool.CUSTOMER_WIDE, AiCapability.AUTONOMOUS_AGENT, 1, now, Duration.ofMinutes(5));
            });
            assertThat(results).filteredOn(AgentLeaseResult::accepted).hasSize(1);
            assertThat(results).filteredOn(r -> !r.accepted()).singleElement().extracting(AgentLeaseResult::reasonCode).isEqualTo(LicenseReasonCode.AGENT_CONCURRENCY_LIMIT_EXCEEDED);
            a.release(results.stream().filter(AgentLeaseResult::accepted).findFirst().orElseThrow().lease().leaseId(), now);
        }
    }

    @Test
    void crashedExecutionExpiresAndCapacitySurvivesRestart() {
        var path = temp.resolve("restart.db");
        var d = new SqliteDatabase(path);
        var t = new SqliteImmediateTransactionRunner(d);
        AgentRegistrationRaceTest.register(d, t);
        var a = new SqliteAiRepository(d, t);
        var now = Instant.parse("2026-01-01T00:00:00Z");
        a.register("agent", "Agent", "prod", EntitlementPool.CUSTOMER_WIDE, 1, now);
        assertThat(a.acquire("l1", "agent", "e1", "prod", EntitlementPool.CUSTOMER_WIDE, AiCapability.AUTONOMOUS_AGENT, 1, now, Duration.ofMinutes(5)).accepted()).isTrue();
        var d2 = new SqliteDatabase(path);
        var a2 = new SqliteAiRepository(d2, new SqliteImmediateTransactionRunner(d2));
        assertThat(a2.acquire("l2", "agent", "e2", "prod", EntitlementPool.CUSTOMER_WIDE, AiCapability.AUTONOMOUS_AGENT, 1, now.plusSeconds(301), Duration.ofMinutes(5)).accepted()).isTrue();
    }
}
