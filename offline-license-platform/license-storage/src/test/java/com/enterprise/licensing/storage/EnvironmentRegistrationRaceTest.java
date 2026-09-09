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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.io.TempDir;

class EnvironmentRegistrationRaceTest {
    private final java.security.KeyPair key = Ed25519Keys.generate();
    @TempDir
    Path temp;

    static Stream<EnvironmentType> governedTypes() {
        return Stream.of(EnvironmentType.PRODUCTION, EnvironmentType.UAT, EnvironmentType.QA,
                EnvironmentType.DEVELOPMENT, EnvironmentType.OTHER);
    }

    @ParameterizedTest
    @MethodSource("governedTypes")
    void exactlyOneFinalEnvironmentRegistrationWins(EnvironmentType type) {
        var database = new SqliteDatabase(temp.resolve(type.name() + ".db"));
        var repository = new SqliteProductInstanceRepository(database, new SqliteImmediateTransactionRunner(database), new ObjectMapper());
        var sequence = new AtomicInteger();
        var results = ConcurrentTestSupport.race(2, () -> repository.register(request(type, "instance-" + sequence.incrementAndGet()),
                fingerprint(), 1, 10, Instant.parse("2026-01-01T00:00:00Z")));
        assertThat(results).filteredOn(InstanceRegistrationResult::accepted).hasSize(1);
        assertThat(results).filteredOn(result -> !result.accepted()).singleElement()
                .extracting(InstanceRegistrationResult::reasonCode).isEqualTo(LicenseReasonCode.ENVIRONMENT_LIMIT_EXCEEDED);
    }

    private InstanceRegistrationRequest request(EnvironmentType type, String id) {
        return new InstanceRegistrationRequest(id, new EncodedPublicKey("Ed25519", "X.509", Ed25519Keys.encodePublic(key.getPublic())),
                ProductId.VALIDATOR, DeploymentType.KUBERNETES, type, "1.0", Map.of());
    }

    private String fingerprint() {
        return Ed25519Keys.fingerprint(key.getPublic());
    }
}
