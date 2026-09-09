package com.enterprise.licensing.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.engine.identity.LicenseServerIdentityService;
import com.enterprise.licensing.engine.time.*;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.storage.identity.SoftwareIdentityProvider;
import com.enterprise.licensing.storage.integrity.FileTrustedTimeCheckpoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.file.*;
import java.time.*;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrustedTimeIntegrationTest {
    @TempDir
    Path temp;

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void staleRestoreIsStickyUntilExplicitReconciliation() {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var identity = identity(now);
        var checkpoint = temp.resolve("checkpoint.json");
        var live = new MemoryRepository(now, 5);
        var first = new FileTrustedTimeCheckpoint(live, identity, checkpoint, mapper());
        assertThat(first.observe(now.plusSeconds(1), Duration.ofMinutes(5)).accepted()).isTrue();

        var restored = new MemoryRepository(now, 4);
        var afterRestore = new FileTrustedTimeCheckpoint(restored, identity, checkpoint, mapper());
        assertThat(afterRestore.observe(now.plusSeconds(2), Duration.ofMinutes(5)).reasonCode()).isEqualTo(LicenseReasonCode.RECOVERY_REQUIRED);
        assertThat(afterRestore.observe(now.plusSeconds(3), Duration.ofMinutes(5)).reasonCode()).isEqualTo(LicenseReasonCode.RECOVERY_REQUIRED);
        assertThat(afterRestore.recoveryRequired()).isTrue();
        assertThat(afterRestore.reconcile(now.plusSeconds(4), 0).sequence()).isGreaterThan(6);
        assertThat(afterRestore.observe(now.plusSeconds(5), Duration.ofMinutes(5)).accepted()).isTrue();
    }

    @Test
    void tamperedCheckpointRequiresRecoveryAndCannotBeBypassed() throws Exception {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var identity = identity(now);
        var checkpoint = temp.resolve("tampered.json");
        var wrapped = new FileTrustedTimeCheckpoint(new MemoryRepository(now, 1), identity, checkpoint, mapper());
        wrapped.observe(now, Duration.ofMinutes(5));
        Files.writeString(checkpoint, Files.readString(checkpoint).replace("\"sequence\":2", "\"sequence\":3"));
        var restarted = new FileTrustedTimeCheckpoint(new MemoryRepository(now, 2), identity, checkpoint, mapper());
        assertThat(restarted.observe(now.plusSeconds(1), Duration.ofMinutes(5)).reasonCode()).isEqualTo(LicenseReasonCode.STATE_INTEGRITY_FAILURE);
        assertThat(restarted.observe(now.plusSeconds(2), Duration.ofMinutes(5)).reasonCode()).isEqualTo(LicenseReasonCode.STATE_INTEGRITY_FAILURE);
    }

    @Test
    void completePrivilegedRollbackOfDatabaseAndCheckpointIsDocumentedResidualRisk() throws Exception {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var identity = identity(now);
        var checkpoint = temp.resolve("complete-rollback.json");
        var liveStore = new MemoryRepository(now, 1);
        var live = new FileTrustedTimeCheckpoint(liveStore, identity, checkpoint, mapper());
        live.observe(now.plusSeconds(1), Duration.ofMinutes(5));
        byte[] oldCheckpoint = Files.readAllBytes(checkpoint);
        live.observe(now.plusSeconds(2), Duration.ofMinutes(5));
        Files.write(checkpoint, oldCheckpoint); // A root administrator has rolled back every trusted copy.
        var fullyRestored = new FileTrustedTimeCheckpoint(new MemoryRepository(now.plusSeconds(1), 2), identity, checkpoint, mapper());
        assertThat(fullyRestored.observe(now.plusSeconds(1), Duration.ofMinutes(5)).accepted()).isTrue();
    }

    private LicenseServerIdentityService identity(Instant now) {
        return new LicenseServerIdentityService(new SoftwareIdentityProvider(temp.resolve("identity.json"), () -> now, Map.of()));
    }

    private static final class MemoryRepository implements TrustedTimeRepository {
        private Instant highest;
        private long sequence;

        private MemoryRepository(Instant highest, long sequence) {
            this.highest = highest;
            this.sequence = sequence;
        }

        @Override
        public Optional<TrustedTimeResult> current() {
            return Optional.of(new TrustedTimeResult(true, LicenseReasonCode.LICENSE_VALID, highest, sequence));
        }

        @Override
        public TrustedTimeResult observe(Instant current, Duration tolerance) {
            if (current.isBefore(highest.minus(tolerance)))
                return new TrustedTimeResult(false, LicenseReasonCode.CLOCK_ROLLBACK_DETECTED, highest, sequence);
            if (current.isAfter(highest)) highest = current;
            return new TrustedTimeResult(true, LicenseReasonCode.LICENSE_VALID, highest, ++sequence);
        }

        @Override
        public TrustedTimeResult reconcile(Instant baseline, long minimumSequence) {
            highest = baseline.isAfter(highest) ? baseline : highest;
            sequence = Math.max(sequence, minimumSequence) + 1;
            return new TrustedTimeResult(true, LicenseReasonCode.LICENSE_VALID, highest, sequence);
        }
    }
}
