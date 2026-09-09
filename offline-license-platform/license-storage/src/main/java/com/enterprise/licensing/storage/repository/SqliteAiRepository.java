package com.enterprise.licensing.storage.repository;

import com.enterprise.licensing.engine.ai.*;
import com.enterprise.licensing.engine.ports.TransactionRunner;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.sqlite.SqliteDatabase;
import com.enterprise.licensing.storage.sqlite.SqliteStorageException;

import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SqliteAiRepository implements AiLicenseRepository {
    private final SqliteDatabase database;
    private final TransactionRunner transactions;

    public SqliteAiRepository(SqliteDatabase d, TransactionRunner t) {
        database = d;
        transactions = t;
    }

    private static RegisteredAgent mapAgent(ResultSet r) throws Exception {
        return new RegisteredAgent(r.getString("agent_id"), r.getString("display_name"), r.getString("instance_id"), r.getString("pool_id"), Instant.parse(r.getString("registered_at")), AgentStatus.valueOf(r.getString("status")));
    }

    private static AgentExecutionLease mapLease(ResultSet r) throws Exception {
        return new AgentExecutionLease(r.getString("lease_id"), r.getString("agent_id"), r.getString("execution_id"), r.getString("instance_id"), r.getString("pool_id"), AiCapability.valueOf(r.getString("capability")), Instant.parse(r.getString("acquired_at")), Instant.parse(r.getString("heartbeat_at")), Instant.parse(r.getString("expires_at")), instant(r.getString("released_at")), instant(r.getString("revoked_at")));
    }

    private static Instant instant(String v) {
        return v == null ? null : Instant.parse(v);
    }

    private static SqliteStorageException failure(String op, Exception e) {
        return new SqliteStorageException("Unable to " + op, e);
    }

    @Override
    public AgentResult register(String id, String name, String instance, String poolId, int limit, Instant now) {
        return transactions.write(() -> {
            var existing = findAgent(id);
            if (existing != null) {
                if (existing.productInstanceId().equals(instance) && existing.poolId().equals(poolId) && existing.status() == AgentStatus.ACTIVE)
                    return AgentResult.accepted(existing);
                return AgentResult.denied(LicenseReasonCode.INVALID_REQUEST);
            }
            if (count("SELECT count(*) FROM product_instance WHERE instance_id=? AND state='ACTIVE'", instance) == 0)
                return AgentResult.denied(LicenseReasonCode.INSTANCE_NOT_REGISTERED);
            if (count("SELECT count(*) FROM registered_agent WHERE pool_id=? AND status='ACTIVE'", poolId) >= limit)
                return AgentResult.denied(LicenseReasonCode.AGENT_REGISTRATION_LIMIT_EXCEEDED);
            execute("INSERT INTO registered_agent(agent_id,display_name,instance_id,registered_at,status,pool_id) VALUES(?,?,?,?,?,?)", id, name, instance, now.toString(), AgentStatus.ACTIVE.name(), poolId);
            return AgentResult.accepted(findAgent(id));
        });
    }

    @Override
    public AgentResult decommission(String id, Instant now) {
        return transactions.write(() -> {
            var a = findAgent(id);
            if (a == null) return AgentResult.denied(LicenseReasonCode.INVALID_REQUEST);
            if (count("SELECT count(*) FROM agent_lease WHERE agent_id=? AND released_at IS NULL AND revoked_at IS NULL AND expires_at>?", id, now.toString()) > 0)
                return AgentResult.denied(LicenseReasonCode.INVALID_REQUEST);
            execute("UPDATE registered_agent SET status='DECOMMISSIONED' WHERE agent_id=?", id);
            return AgentResult.accepted(findAgent(id));
        });
    }

    @Override
    public List<RegisteredAgent> agents() {
        return transactions.read(() -> database.withConnection(c -> {
            try (var s = c.prepareStatement("SELECT * FROM registered_agent ORDER BY registered_at,agent_id"); var r = s.executeQuery()) {
                var v = new ArrayList<RegisteredAgent>();
                while (r.next()) v.add(mapAgent(r));
                return List.copyOf(v);
            } catch (Exception e) {
                throw failure("list agents", e);
            }
        }));
    }

    @Override
    public AgentLeaseResult acquire(String leaseId, String agentId, String executionId, String instance, String poolId, AiCapability capability, int limit, Instant now, Duration duration) {
        return transactions.write(() -> {
            var existing = findLeaseByExecution(agentId, executionId);
            if (existing != null && existing.activeAt(now)) return AgentLeaseResult.accepted(existing);
            if (existing != null) return AgentLeaseResult.denied(LicenseReasonCode.INVALID_REQUEST);
            if (count("SELECT count(*) FROM registered_agent a JOIN product_instance i ON i.instance_id=a.instance_id WHERE a.agent_id=? AND a.instance_id=? AND a.pool_id=? AND a.status='ACTIVE' AND i.state='ACTIVE'", agentId, instance, poolId) == 0)
                return AgentLeaseResult.denied(LicenseReasonCode.AI_NOT_ENTITLED);
            if (count("SELECT count(*) FROM agent_lease WHERE pool_id=? AND released_at IS NULL AND revoked_at IS NULL AND expires_at>?", poolId, now.toString()) >= limit)
                return AgentLeaseResult.denied(LicenseReasonCode.AGENT_CONCURRENCY_LIMIT_EXCEEDED);
            execute("INSERT INTO agent_lease(lease_id,agent_id,execution_id,instance_id,capability,acquired_at,heartbeat_at,expires_at,pool_id) VALUES(?,?,?,?,?,?,?,?,?)", leaseId, agentId, executionId, instance, capability.name(), now.toString(), now.toString(), now.plus(duration).toString(), poolId);
            return AgentLeaseResult.accepted(findLease(leaseId));
        });
    }

    @Override
    public AgentLeaseResult heartbeat(String id, Instant now, Duration duration) {
        return transactions.write(() -> {
            var l = findLease(id);
            if (l == null || !l.activeAt(now)) return AgentLeaseResult.denied(LicenseReasonCode.INVALID_REQUEST);
            execute("UPDATE agent_lease SET heartbeat_at=?,expires_at=? WHERE lease_id=?", now.toString(), now.plus(duration).toString(), id);
            return AgentLeaseResult.accepted(findLease(id));
        });
    }

    @Override
    public AgentLeaseResult release(String id, Instant now) {
        return transactions.write(() -> {
            var l = findLease(id);
            if (l == null) return AgentLeaseResult.denied(LicenseReasonCode.INVALID_REQUEST);
            if (l.releasedAt() == null)
                execute("UPDATE agent_lease SET released_at=? WHERE lease_id=?", now.toString(), id);
            return AgentLeaseResult.accepted(findLease(id));
        });
    }

    @Override
    public List<AgentExecutionLease> leases() {
        return transactions.read(() -> database.withConnection(c -> {
            try (var s = c.prepareStatement("SELECT * FROM agent_lease ORDER BY acquired_at,lease_id"); var r = s.executeQuery()) {
                var v = new ArrayList<AgentExecutionLease>();
                while (r.next()) v.add(mapLease(r));
                return List.copyOf(v);
            } catch (Exception e) {
                throw failure("list agent leases", e);
            }
        }));
    }

    private RegisteredAgent findAgent(String id) {
        return database.withConnection(c -> {
            try (var s = c.prepareStatement("SELECT * FROM registered_agent WHERE agent_id=?")) {
                s.setString(1, id);
                try (var r = s.executeQuery()) {
                    return r.next() ? mapAgent(r) : null;
                }
            } catch (Exception e) {
                throw failure("read agent", e);
            }
        });
    }

    private AgentExecutionLease findLease(String id) {
        return database.withConnection(c -> {
            try (var s = c.prepareStatement("SELECT * FROM agent_lease WHERE lease_id=?")) {
                s.setString(1, id);
                try (var r = s.executeQuery()) {
                    return r.next() ? mapLease(r) : null;
                }
            } catch (Exception e) {
                throw failure("read agent lease", e);
            }
        });
    }

    private AgentExecutionLease findLeaseByExecution(String agent, String execution) {
        return database.withConnection(c -> {
            try (var s = c.prepareStatement("SELECT * FROM agent_lease WHERE agent_id=? AND execution_id=?")) {
                s.setString(1, agent);
                s.setString(2, execution);
                try (var r = s.executeQuery()) {
                    return r.next() ? mapLease(r) : null;
                }
            } catch (Exception e) {
                throw failure("read execution lease", e);
            }
        });
    }

    private int count(String sql, Object... v) {
        return database.withConnection(c -> {
            try (var s = c.prepareStatement(sql)) {
                for (int i = 0; i < v.length; i++) s.setObject(i + 1, v[i]);
                try (var r = s.executeQuery()) {
                    return r.getInt(1);
                }
            } catch (Exception e) {
                throw failure("count AI state", e);
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
                throw failure("update AI state", e);
            }
        });
    }
}
