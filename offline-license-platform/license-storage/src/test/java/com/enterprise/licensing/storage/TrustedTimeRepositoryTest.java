package com.enterprise.licensing.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.storage.repository.SqliteTrustedTimeRepository;
import com.enterprise.licensing.storage.sqlite.*;

import java.nio.file.Path;
import java.time.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrustedTimeRepositoryTest {
    @TempDir
    Path temp;

    @Test
    void toleratesFiveMinutesButRejectsLargerRollbackAndCorruption() throws Exception {
        var d = new SqliteDatabase(temp.resolve("time.db"));
        var t = new SqliteImmediateTransactionRunner(d);
        var r = new SqliteTrustedTimeRepository(d, t);
        var high = Instant.parse("2026-01-01T12:00:00Z");
        assertThat(r.observe(high, Duration.ofMinutes(5)).accepted()).isTrue();
        assertThat(r.observe(high.minusSeconds(300), Duration.ofMinutes(5)).accepted()).isTrue();
        assertThat(r.observe(high.minusSeconds(301), Duration.ofMinutes(5)).reasonCode()).isEqualTo(LicenseReasonCode.CLOCK_ROLLBACK_DETECTED);
        d.withConnection(c -> {
            try (var s = c.prepareStatement("UPDATE trusted_time_state SET checkpoint_hash='bad'")) {
                s.executeUpdate();
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertThat(r.observe(high.plusSeconds(1), Duration.ofMinutes(5)).reasonCode()).isEqualTo(LicenseReasonCode.STATE_INTEGRITY_FAILURE);
    }
}
