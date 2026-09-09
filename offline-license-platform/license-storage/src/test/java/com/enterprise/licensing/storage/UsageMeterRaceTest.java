package com.enterprise.licensing.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.engine.metering.UsageResult;
import com.enterprise.licensing.model.EntitlementPool;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.storage.repository.SqliteMeteringRepository;
import com.enterprise.licensing.storage.sqlite.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UsageMeterRaceTest {
    @TempDir
    Path temp;

    private static void assertMismatch(UsageResult result) {
        assertThat(result.accepted()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(LicenseReasonCode.INVALID_REQUEST);
        assertThat(result.counter()).isNull();
    }

    @Test
    void oneThousandContestedAeuBoundariesNeverExceedLimit() {
        var d = new SqliteDatabase(temp.resolve("usage.db"));
        var r = new SqliteMeteringRepository(d, new SqliteImmediateTransactionRunner(d), new ObjectMapper());
        var n = new AtomicInteger();
        var start = Instant.parse("2026-01-01T00:00:00Z");
        var end = Instant.parse("2026-02-01T00:00:00Z");
        for (int trial = 0; trial < 1_000; trial++) {
            String customer = "c-" + trial;
            var results = ConcurrentTestSupport.race(2, () -> {
                int i = n.incrementAndGet();
                return r.consume("req-" + i, customer, "instance-" + i, EntitlementPool.CUSTOMER_WIDE, "AI_EXECUTION_UNIT", "TEST_ACTION", 2, start, end, 2, start, Map.of());
            });
            assertThat(results).filteredOn(UsageResult::accepted).hasSize(1);
            assertThat(results).filteredOn(x -> !x.accepted()).singleElement().satisfies(x -> {
                assertThat(x.reasonCode()).isEqualTo(LicenseReasonCode.AI_USAGE_LIMIT_EXCEEDED);
                assertThat(x.counter().usedAmount()).isEqualTo(2);
            });
        }
    }

    @Test
    void nextUtcMonthStartsFreshWithoutRolloverAndRetriesAreIdempotent() {
        var d = new SqliteDatabase(temp.resolve("monthly.db"));
        var r = new SqliteMeteringRepository(d, new SqliteImmediateTransactionRunner(d), new ObjectMapper());
        var jan = Instant.parse("2026-01-01T00:00:00Z");
        var feb = Instant.parse("2026-02-01T00:00:00Z");
        var mar = Instant.parse("2026-03-01T00:00:00Z");
        assertThat(r.consume("same", "c", "i", EntitlementPool.CUSTOMER_WIDE, "AI_EXECUTION_UNIT", "TEST_ACTION", 2, jan, feb, 2, jan, Map.of()).counter().usedAmount()).isEqualTo(2);
        assertThat(r.consume("same", "c", "i", EntitlementPool.CUSTOMER_WIDE, "AI_EXECUTION_UNIT", "TEST_ACTION", 2, jan, feb, 2, jan, Map.of()).counter().usedAmount()).isEqualTo(2);
        assertThat(r.consume("feb", "c", "i", EntitlementPool.CUSTOMER_WIDE, "AI_EXECUTION_UNIT", "TEST_ACTION", 1, feb, mar, 2, feb, Map.of()).counter().usedAmount()).isEqualTo(1);
    }

    @Test
    void idempotencyKeyIsBoundToTheEntireRequestAndReplaysTheDecisionSnapshot() {
        var d = new SqliteDatabase(temp.resolve("idempotency.db"));
        var r = new SqliteMeteringRepository(d, new SqliteImmediateTransactionRunner(d), new ObjectMapper());
        var jan = Instant.parse("2026-01-01T00:00:00Z");
        var feb = Instant.parse("2026-02-01T00:00:00Z");
        var mar = Instant.parse("2026-03-01T00:00:00Z");
        var original = r.consume("key", "c", "i", EntitlementPool.CUSTOMER_WIDE, "AEU", "INTERACTIVE_AI_REQUEST", 1, jan, feb, 10, jan, Map.of("model", "small", "region", "us"));
        assertThat(original.accepted()).isTrue();
        assertThat(original.counter().usedAmount()).isEqualTo(1);
        assertThat(r.consume("later", "c", "i", EntitlementPool.CUSTOMER_WIDE, "AEU", "AUTONOMOUS_AGENT_RUN", 5, jan, feb, 10, jan, Map.of()).counter().usedAmount()).isEqualTo(6);
        var replay = r.consume("key", "c", "i", EntitlementPool.CUSTOMER_WIDE, "AEU", "INTERACTIVE_AI_REQUEST", 1, jan, feb, 10, jan, Map.of("region", "us", "model", "small"));
        assertThat(replay).isEqualTo(original);
        assertMismatch(r.consume("key", "other", "i", EntitlementPool.CUSTOMER_WIDE, "AEU", "INTERACTIVE_AI_REQUEST", 1, jan, feb, 10, jan, Map.of("model", "small", "region", "us")));
        assertMismatch(r.consume("key", "c", "other", EntitlementPool.CUSTOMER_WIDE, "AEU", "INTERACTIVE_AI_REQUEST", 1, jan, feb, 10, jan, Map.of("model", "small", "region", "us")));
        assertMismatch(r.consume("key", "c", "i", "OTHER_POOL", "AEU", "INTERACTIVE_AI_REQUEST", 1, jan, feb, 10, jan, Map.of("model", "small", "region", "us")));
        assertMismatch(r.consume("key", "c", "i", EntitlementPool.CUSTOMER_WIDE, "OTHER", "INTERACTIVE_AI_REQUEST", 1, jan, feb, 10, jan, Map.of("model", "small", "region", "us")));
        assertMismatch(r.consume("key", "c", "i", EntitlementPool.CUSTOMER_WIDE, "AEU", "OTHER_ACTION", 1, jan, feb, 10, jan, Map.of("model", "small", "region", "us")));
        assertMismatch(r.consume("key", "c", "i", EntitlementPool.CUSTOMER_WIDE, "AEU", "INTERACTIVE_AI_REQUEST", 2, jan, feb, 10, jan, Map.of("model", "small", "region", "us")));
        assertMismatch(r.consume("key", "c", "i", EntitlementPool.CUSTOMER_WIDE, "AEU", "INTERACTIVE_AI_REQUEST", 1, feb, mar, 10, feb, Map.of("model", "small", "region", "us")));
        assertMismatch(r.consume("key", "c", "i", EntitlementPool.CUSTOMER_WIDE, "AEU", "INTERACTIVE_AI_REQUEST", 1, jan, feb, 10, jan, Map.of("model", "large", "region", "us")));
        assertThat(r.counters()).singleElement().satisfies(counter -> assertThat(counter.usedAmount()).isEqualTo(6));
    }
}
