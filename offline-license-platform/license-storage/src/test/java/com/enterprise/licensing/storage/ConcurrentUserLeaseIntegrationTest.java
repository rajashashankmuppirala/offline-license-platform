package com.enterprise.licensing.storage;

import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.repository.*;
import com.enterprise.licensing.storage.sqlite.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.*;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentUserLeaseIntegrationTest {
    @TempDir
    Path temp;

    private static void register(SqliteProductInstanceRepository instances, String id, EnvironmentType environment) {
        var key = Ed25519Keys.generate();
        instances.register(new InstanceRegistrationRequest(id, new EncodedPublicKey("Ed25519", "X.509", Ed25519Keys.encodePublic(key.getPublic())), ProductId.VALIDATOR, DeploymentType.TOMCAT, environment, "1", Map.of()), Ed25519Keys.fingerprint(key.getPublic()), 10, 10, Instant.parse("2026-01-01T00:00:00Z"));
        instances.transition(id, environment == EnvironmentType.DISASTER_RECOVERY ? InstanceState.STANDBY : InstanceState.REGISTERED, InstanceState.ACTIVE, 10, Instant.parse("2026-01-01T00:00:01Z"), false);
    }

    @Test
    void prodUatAndDevSharePoolAndCrashExpirySurvivesRestart() {
        var database = new SqliteDatabase(temp.resolve("users.db"));
        var transactions = new SqliteImmediateTransactionRunner(database);
        var instances = new SqliteProductInstanceRepository(database, transactions, new ObjectMapper());
        register(instances, "prod", EnvironmentType.PRODUCTION);
        register(instances, "uat", EnvironmentType.UAT);
        register(instances, "dev", EnvironmentType.DEVELOPMENT);
        var users = new SqliteUserLicenseRepository(database, transactions);
        var now = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 12; i++)
            assertThat(users.acquire("p" + i, "p" + i, AccountType.HUMAN, "prod", "ps" + i, EntitlementPool.CUSTOMER_WIDE, 20, now, Duration.ofMinutes(5)).accepted()).isTrue();
        for (int i = 0; i < 5; i++)
            assertThat(users.acquire("u" + i, "u" + i, AccountType.HUMAN, "uat", "us" + i, EntitlementPool.CUSTOMER_WIDE, 20, now, Duration.ofMinutes(5)).accepted()).isTrue();
        for (int i = 0; i < 3; i++)
            assertThat(users.acquire("d" + i, "d" + i, AccountType.HUMAN, "dev", "ds" + i, EntitlementPool.CUSTOMER_WIDE, 20, now, Duration.ofMinutes(5)).accepted()).isTrue();
        assertThat(users.acquire("over", "over", AccountType.HUMAN, "uat", "over", EntitlementPool.CUSTOMER_WIDE, 20, now, Duration.ofMinutes(5)).reasonCode()).isEqualTo(LicenseReasonCode.CONCURRENT_USER_LIMIT_EXCEEDED);
        assertThat(users.heartbeat("p0", now.plusSeconds(60), Duration.ofMinutes(5)).lease().expiresAt()).isEqualTo(now.plusSeconds(360));
        var reopenedDatabase = new SqliteDatabase(temp.resolve("users.db"));
        var reopened = new SqliteUserLicenseRepository(reopenedDatabase, new SqliteImmediateTransactionRunner(reopenedDatabase));
        assertThat(reopened.acquire("replacement", "replacement", AccountType.HUMAN, "uat", "replacement", EntitlementPool.CUSTOMER_WIDE, 20, now.plusSeconds(301), Duration.ofMinutes(5)).accepted()).isTrue();
    }
}
