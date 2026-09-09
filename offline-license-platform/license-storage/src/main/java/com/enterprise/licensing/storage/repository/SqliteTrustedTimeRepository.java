package com.enterprise.licensing.storage.repository;

import com.enterprise.licensing.engine.ports.TransactionRunner;
import com.enterprise.licensing.engine.time.*;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.storage.sqlite.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import java.util.Optional;

public final class SqliteTrustedTimeRepository implements TrustedTimeRepository {
    private final SqliteDatabase database;
    private final TransactionRunner transactions;

    public SqliteTrustedTimeRepository(SqliteDatabase d, TransactionRunner t) {
        database = d;
        transactions = t;
    }

    private static String hash(Instant i, long s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("trusted-time-v1\u0000" + i + "\u0000" + s).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public TrustedTimeResult observe(Instant current, Duration tolerance) {
        return transactions.write(() -> {
            var state = read();
            if (state == null) {
                write(current, 1, hash(current, 1));
                return new TrustedTimeResult(true, LicenseReasonCode.LICENSE_VALID, current, 1);
            }
            if (!state.hash.equals(hash(state.highest, state.sequence)))
                return new TrustedTimeResult(false, LicenseReasonCode.STATE_INTEGRITY_FAILURE, state.highest, state.sequence);
            if (current.isBefore(state.highest.minus(tolerance)))
                return new TrustedTimeResult(false, LicenseReasonCode.CLOCK_ROLLBACK_DETECTED, state.highest, state.sequence);
            Instant highest = current.isAfter(state.highest) ? current : state.highest;
            long sequence = state.sequence + 1;
            write(highest, sequence, hash(highest, sequence));
            return new TrustedTimeResult(true, LicenseReasonCode.LICENSE_VALID, highest, sequence);
        });
    }

    @Override
    public Optional<TrustedTimeResult> current() {
        return transactions.read(() -> {
            var state = read();
            if (state == null) return Optional.empty();
            boolean valid = state.hash.equals(hash(state.highest, state.sequence));
            return Optional.of(new TrustedTimeResult(valid, valid ? LicenseReasonCode.LICENSE_VALID : LicenseReasonCode.STATE_INTEGRITY_FAILURE, state.highest, state.sequence));
        });
    }

    @Override
    public TrustedTimeResult reconcile(Instant baseline, long minimumSequence) {
        return transactions.write(() -> {
            var state = read();
            long sequence = Math.max(state == null ? 0 : state.sequence, minimumSequence) + 1;
            Instant highest = state == null || baseline.isAfter(state.highest) ? baseline : state.highest;
            write(highest, sequence, hash(highest, sequence));
            return new TrustedTimeResult(true, LicenseReasonCode.LICENSE_VALID, highest, sequence);
        });
    }

    private State read() {
        return database.withConnection(c -> {
            try (var s = c.prepareStatement("SELECT highest_accepted_time,state_sequence,checkpoint_hash FROM trusted_time_state WHERE singleton_id=1"); var r = s.executeQuery()) {
                return r.next() ? new State(Instant.parse(r.getString(1)), r.getLong(2), r.getString(3)) : null;
            } catch (Exception e) {
                throw new SqliteStorageException("Unable to read trusted time", e);
            }
        });
    }

    private void write(Instant highest, long sequence, String hash) {
        database.withConnection(c -> {
            try (var s = c.prepareStatement("INSERT INTO trusted_time_state(singleton_id,highest_accepted_time,state_sequence,checkpoint_hash) VALUES(1,?,?,?) ON CONFLICT(singleton_id) DO UPDATE SET highest_accepted_time=excluded.highest_accepted_time,state_sequence=excluded.state_sequence,checkpoint_hash=excluded.checkpoint_hash")) {
                s.setString(1, highest.toString());
                s.setLong(2, sequence);
                s.setString(3, hash);
                s.executeUpdate();
                return null;
            } catch (Exception e) {
                throw new SqliteStorageException("Unable to update trusted time", e);
            }
        });
    }

    private record State(Instant highest, long sequence, String hash) {
    }
}
