package com.enterprise.licensing.storage.repository;

import com.enterprise.licensing.crypto.EnvelopeJsonCodec;
import com.enterprise.licensing.engine.cache.CachedEntitlementRepository;
import com.enterprise.licensing.engine.ports.TransactionRunner;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.sqlite.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

public final class SqliteCachedEntitlementRepository implements CachedEntitlementRepository {
    private final SqliteDatabase database;
    private final TransactionRunner transactions;
    private final EnvelopeJsonCodec codec;

    public SqliteCachedEntitlementRepository(SqliteDatabase d, TransactionRunner t, ObjectMapper m) {
        database = d;
        transactions = t;
        codec = new EnvelopeJsonCodec(m);
    }

    private static SqliteStorageException failure(Exception e) {
        return new SqliteStorageException("Cached entitlement persistence failure", e);
    }

    @Override
    public void save(CachedEntitlementCertificate c, SignedEnvelope e) {
        transactions.write(() -> {
            execute("INSERT INTO cached_entitlement_issue(certificate_id,license_id,instance_id,envelope_json,issued_at,expires_at) VALUES(?,?,?,?,?,?)", c.certificateId(), c.licenseId(), c.instanceId(), codec.write(e), c.issuedAt().toString(), c.expiresAt().toString());
            return null;
        });
    }

    @Override
    public Optional<SignedEnvelope> latestFor(String instance) {
        return transactions.read(() -> database.withConnection(c -> {
            try (var s = c.prepareStatement("SELECT envelope_json FROM cached_entitlement_issue WHERE instance_id=? AND revoked_at IS NULL ORDER BY issued_at DESC LIMIT 1")) {
                s.setString(1, instance);
                try (var r = s.executeQuery()) {
                    return r.next() ? Optional.of(codec.read(r.getString(1))) : Optional.empty();
                }
            } catch (Exception e) {
                throw failure(e);
            }
        }));
    }

    @Override
    public void revokeFor(String instance, Instant at) {
        transactions.write(() -> {
            execute("UPDATE cached_entitlement_issue SET revoked_at=? WHERE instance_id=? AND revoked_at IS NULL", at.toString(), instance);
            return null;
        });
    }

    private void execute(String sql, Object... v) {
        database.withConnection(c -> {
            try (var s = c.prepareStatement(sql)) {
                for (int i = 0; i < v.length; i++) s.setObject(i + 1, v[i]);
                s.executeUpdate();
                return null;
            } catch (Exception e) {
                throw failure(e);
            }
        });
    }
}
