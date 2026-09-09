package com.enterprise.licensing.storage.repository;

import com.enterprise.licensing.engine.ports.TransactionRunner;
import com.enterprise.licensing.storage.sqlite.*;

import java.time.Instant;

public final class SqliteRequestNonceRepository {
    private final SqliteDatabase database;
    private final TransactionRunner transactions;

    public SqliteRequestNonceRepository(SqliteDatabase d, TransactionRunner t) {
        database = d;
        transactions = t;
    }

    public boolean accept(String instance, String nonce, Instant requestTime, Instant expires) {
        return transactions.write(() -> {
            execute("DELETE FROM request_nonce WHERE expires_at<=?", requestTime.toString());
            if (count(instance, nonce) > 0) return false;
            execute("INSERT INTO request_nonce(instance_id,nonce,request_time,expires_at) VALUES(?,?,?,?)", instance, nonce, requestTime.toString(), expires.toString());
            return true;
        });
    }

    private int count(String instance, String nonce) {
        return database.withConnection(c -> {
            try (var s = c.prepareStatement("SELECT count(*) FROM request_nonce WHERE instance_id=? AND nonce=?")) {
                s.setString(1, instance);
                s.setString(2, nonce);
                try (var r = s.executeQuery()) {
                    return r.getInt(1);
                }
            } catch (Exception e) {
                throw new SqliteStorageException("Nonce lookup failed", e);
            }
        });
    }

    private void execute(String sql, Object... v) {
        database.withConnection(c -> {
            try (var s = c.prepareStatement(sql)) {
                for (int i = 0; i < v.length; i++) s.setObject(i + 1, v[i]);
                s.executeUpdate();
                return null;
            } catch (Exception e) {
                throw new SqliteStorageException("Nonce update failed", e);
            }
        });
    }
}
