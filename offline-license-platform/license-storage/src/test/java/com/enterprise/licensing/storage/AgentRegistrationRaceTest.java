package com.enterprise.licensing.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.engine.ai.AgentResult;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.repository.*;
import com.enterprise.licensing.storage.sqlite.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentRegistrationRaceTest {
    @TempDir
    Path temp;

    static void register(SqliteDatabase d, SqliteImmediateTransactionRunner t) {
        var k = Ed25519Keys.generate();
        var r = new InstanceRegistrationRequest("prod", new EncodedPublicKey("Ed25519", "X.509", Ed25519Keys.encodePublic(k.getPublic())), ProductId.VALIDATOR, DeploymentType.TOMCAT, EnvironmentType.PRODUCTION, "1", Map.of());
        var instances = new SqliteProductInstanceRepository(d, t, new com.fasterxml.jackson.databind.ObjectMapper());
        instances.register(r, Ed25519Keys.fingerprint(k.getPublic()), 1, 1, Instant.parse("2026-01-01T00:00:00Z"));
        instances.transition("prod", InstanceState.REGISTERED, InstanceState.ACTIVE, 1, Instant.parse("2026-01-01T00:00:01Z"), false);
    }

    @Test
    void exactlyOneAgentGetsFinalGlobalRegistration() {
        var d = new SqliteDatabase(temp.resolve("agents.db"));
        var t = new SqliteImmediateTransactionRunner(d);
        register(d, t);
        var agents = new SqliteAiRepository(d, t);
        var n = new AtomicInteger();
        var results = ConcurrentTestSupport.race(2, () -> {
            int i = n.incrementAndGet();
            return agents.register("agent-" + i, "Agent " + i, "prod", EntitlementPool.CUSTOMER_WIDE, 1, Instant.parse("2026-01-01T00:00:00Z"));
        });
        assertThat(results).filteredOn(AgentResult::accepted).hasSize(1);
        assertThat(results).filteredOn(r -> !r.accepted()).singleElement().extracting(AgentResult::reasonCode).isEqualTo(LicenseReasonCode.AGENT_REGISTRATION_LIMIT_EXCEEDED);
    }
}
