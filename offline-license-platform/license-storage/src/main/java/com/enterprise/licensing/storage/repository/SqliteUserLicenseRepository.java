package com.enterprise.licensing.storage.repository;

import com.enterprise.licensing.engine.ports.TransactionRunner;
import com.enterprise.licensing.engine.user.*;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.sqlite.SqliteDatabase;
import com.enterprise.licensing.storage.sqlite.SqliteStorageException;

import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SqliteUserLicenseRepository implements UserLicenseRepository {
    private final SqliteDatabase database;
    private final TransactionRunner transactions;

    public SqliteUserLicenseRepository(SqliteDatabase database, TransactionRunner transactions) {
        this.database = database;
        this.transactions = transactions;
    }

    private static NamedUserAssignment mapAssignment(ResultSet r) throws Exception {
        return new NamedUserAssignment(r.getString("assignment_id"), r.getString("user_id"), AccountType.valueOf(r.getString("account_type")), product(r.getString("product_id")), category(r.getString("category")), Instant.parse(r.getString("assigned_at")), instant(r.getString("released_at")), instant(r.getString("cooldown_until")));
    }

    private static ConcurrentUserLease mapLease(ResultSet r) throws Exception {
        return new ConcurrentUserLease(r.getString("lease_id"), r.getString("user_id"), AccountType.valueOf(r.getString("account_type")), r.getString("instance_id"), r.getString("pool_id"), r.getString("session_id"), Instant.parse(r.getString("acquired_at")), Instant.parse(r.getString("heartbeat_at")), Instant.parse(r.getString("expires_at")), instant(r.getString("released_at")), instant(r.getString("revoked_at")));
    }

    private static Object value(ProductId id) {
        return id == null ? null : id.value();
    }

    private static Object value(UserCategory c) {
        return c == null ? null : c.name();
    }

    private static ProductId product(String value) {
        return value == null ? null : new ProductId(value);
    }

    private static UserCategory category(String value) {
        return value == null ? null : UserCategory.valueOf(value);
    }

    private static Instant instant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    @Override
    public NamedUserResult assign(String assignmentId, String userId, AccountType accountType, ProductId productId,
                                  UserCategory category, int globalLimit, int categoryLimit, int productLimit, Instant now) {
        return transactions.write(() -> {
            var existing = findAssignment(userId, productId, category);
            if (existing != null && existing.releasedAt() == null) return NamedUserResult.accepted(existing);
            if (existing != null && existing.cooldownUntil() != null && existing.cooldownUntil().isAfter(now))
                return NamedUserResult.denied(LicenseReasonCode.NAMED_USER_REASSIGNMENT_COOLDOWN);
            int counted = count("SELECT count(*) FROM named_user_assignment WHERE released_at IS NULL OR cooldown_until>?", now.toString());
            int active = count("SELECT count(*) FROM named_user_assignment WHERE released_at IS NULL");
            if (counted >= globalLimit)
                return NamedUserResult.denied(active < globalLimit ? LicenseReasonCode.NAMED_USER_REASSIGNMENT_COOLDOWN : LicenseReasonCode.NAMED_USER_LIMIT_EXCEEDED);
            if (category != null && count("SELECT count(*) FROM named_user_assignment WHERE category=? AND (released_at IS NULL OR cooldown_until>?)", category.name(), now.toString()) >= categoryLimit)
                return NamedUserResult.denied(LicenseReasonCode.NAMED_USER_LIMIT_EXCEEDED);
            if (productId != null && count("SELECT count(*) FROM named_user_assignment WHERE product_id=? AND (released_at IS NULL OR cooldown_until>?)", productId.value(), now.toString()) >= productLimit)
                return NamedUserResult.denied(LicenseReasonCode.PRODUCT_USER_LIMIT_EXCEEDED);
            if (existing != null) {
                execute("UPDATE named_user_assignment SET assignment_id=?,account_type=?,assigned_at=?,released_at=NULL,cooldown_until=NULL WHERE user_id=? AND product_id IS ? AND category IS ?",
                        assignmentId, accountType.name(), now.toString(), userId, value(productId), value(category));
            } else {
                execute("INSERT INTO named_user_assignment(assignment_id,user_id,account_type,product_id,category,assigned_at) VALUES(?,?,?,?,?,?)",
                        assignmentId, userId, accountType.name(), value(productId), value(category), now.toString());
            }
            return NamedUserResult.accepted(findAssignment(userId, productId, category));
        });
    }

    @Override
    public NamedUserResult release(String userId, ProductId productId, UserCategory category, Instant now,
                                   Duration cooldown, boolean overrideCooldown, String overrideReason) {
        return transactions.write(() -> {
            var existing = findAssignment(userId, productId, category);
            if (existing == null || existing.releasedAt() != null)
                return NamedUserResult.denied(LicenseReasonCode.INVALID_REQUEST);
            if (overrideCooldown && !("EMPLOYEE_DEPARTURE".equals(overrideReason) || "ASSIGNMENT_CORRECTION".equals(overrideReason)))
                return NamedUserResult.denied(LicenseReasonCode.INVALID_REQUEST);
            Instant until = overrideCooldown ? now : now.plus(cooldown);
            execute("UPDATE named_user_assignment SET released_at=?,cooldown_until=? WHERE assignment_id=?", now.toString(), until.toString(), existing.assignmentId());
            return NamedUserResult.accepted(findAssignment(userId, productId, category));
        });
    }

    @Override
    public List<NamedUserAssignment> namedAssignments() {
        return transactions.read(() -> database.withConnection(connection -> {
            try (var statement = connection.prepareStatement("SELECT * FROM named_user_assignment ORDER BY assigned_at,assignment_id"); var result = statement.executeQuery()) {
                var values = new ArrayList<NamedUserAssignment>();
                while (result.next()) values.add(mapAssignment(result));
                return List.copyOf(values);
            } catch (Exception e) {
                throw new SqliteStorageException("Unable to list named users", e);
            }
        }));
    }

    @Override
    public UserLeaseResult acquire(String leaseId, String userId, AccountType accountType, String instanceId, String sessionId, String poolId, int globalLimit, Instant now, Duration duration) {
        return transactions.write(() -> {
            var existing = findLeaseBySession(instanceId, sessionId);
            if (existing != null && existing.activeAt(now)) return UserLeaseResult.accepted(existing);
            if (count("SELECT count(*) FROM product_instance WHERE instance_id=? AND state='ACTIVE'", instanceId) == 0)
                return UserLeaseResult.denied(LicenseReasonCode.INSTANCE_NOT_REGISTERED);
            if (count("SELECT count(*) FROM user_lease WHERE pool_id=? AND released_at IS NULL AND revoked_at IS NULL AND expires_at>?", poolId, now.toString()) >= globalLimit)
                return UserLeaseResult.denied(LicenseReasonCode.CONCURRENT_USER_LIMIT_EXCEEDED);
            if (existing != null) return UserLeaseResult.denied(LicenseReasonCode.USER_LEASE_INVALID);
            execute("INSERT INTO user_lease(lease_id,user_id,account_type,instance_id,session_id,acquired_at,heartbeat_at,expires_at,pool_id) VALUES(?,?,?,?,?,?,?,?,?)",
                    leaseId, userId, accountType.name(), instanceId, sessionId, now.toString(), now.toString(), now.plus(duration).toString(), poolId);
            return UserLeaseResult.accepted(findLease(leaseId));
        });
    }

    @Override
    public UserLeaseResult heartbeat(String leaseId, Instant now, Duration duration) {
        return transactions.write(() -> {
            var lease = findLease(leaseId);
            if (lease == null || !lease.activeAt(now))
                return UserLeaseResult.denied(LicenseReasonCode.USER_LEASE_INVALID);
            execute("UPDATE user_lease SET heartbeat_at=?,expires_at=? WHERE lease_id=?", now.toString(), now.plus(duration).toString(), leaseId);
            return UserLeaseResult.accepted(findLease(leaseId));
        });
    }

    @Override
    public UserLeaseResult releaseLease(String leaseId, Instant now) {
        return transactions.write(() -> {
            var lease = findLease(leaseId);
            if (lease == null) return UserLeaseResult.denied(LicenseReasonCode.USER_LEASE_INVALID);
            if (lease.releasedAt() == null)
                execute("UPDATE user_lease SET released_at=? WHERE lease_id=?", now.toString(), leaseId);
            return UserLeaseResult.accepted(findLease(leaseId));
        });
    }

    @Override
    public List<ConcurrentUserLease> userLeases() {
        return transactions.read(() -> database.withConnection(connection -> {
            try (var statement = connection.prepareStatement("SELECT * FROM user_lease ORDER BY acquired_at,lease_id"); var result = statement.executeQuery()) {
                var values = new ArrayList<ConcurrentUserLease>();
                while (result.next()) values.add(mapLease(result));
                return List.copyOf(values);
            } catch (Exception e) {
                throw new SqliteStorageException("Unable to list user leases", e);
            }
        }));
    }

    private NamedUserAssignment findAssignment(String userId, ProductId productId, UserCategory category) {
        return database.withConnection(connection -> {
            try (var statement = connection.prepareStatement("SELECT * FROM named_user_assignment WHERE user_id=? AND product_id IS ? AND category IS ?")) {
                statement.setString(1, userId);
                statement.setObject(2, value(productId));
                statement.setObject(3, value(category));
                try (var result = statement.executeQuery()) {
                    return result.next() ? mapAssignment(result) : null;
                }
            } catch (Exception e) {
                throw new SqliteStorageException("Unable to read named assignment", e);
            }
        });
    }

    private ConcurrentUserLease findLease(String id) {
        return database.withConnection(connection -> {
            try (var statement = connection.prepareStatement("SELECT * FROM user_lease WHERE lease_id=?")) {
                statement.setString(1, id);
                try (var result = statement.executeQuery()) {
                    return result.next() ? mapLease(result) : null;
                }
            } catch (Exception e) {
                throw new SqliteStorageException("Unable to read user lease", e);
            }
        });
    }

    private ConcurrentUserLease findLeaseBySession(String instance, String session) {
        return database.withConnection(connection -> {
            try (var statement = connection.prepareStatement("SELECT * FROM user_lease WHERE instance_id=? AND session_id=?")) {
                statement.setString(1, instance);
                statement.setString(2, session);
                try (var result = statement.executeQuery()) {
                    return result.next() ? mapLease(result) : null;
                }
            } catch (Exception e) {
                throw new SqliteStorageException("Unable to read session lease", e);
            }
        });
    }

    private int count(String sql, Object... values) {
        return database.withConnection(c -> {
            try (var s = c.prepareStatement(sql)) {
                for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
                try (var r = s.executeQuery()) {
                    return r.getInt(1);
                }
            } catch (Exception e) {
                throw new SqliteStorageException("Unable to count user state", e);
            }
        });
    }

    private void execute(String sql, Object... values) {
        database.withConnection(c -> {
            try (var s = c.prepareStatement(sql)) {
                for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
                s.executeUpdate();
                return null;
            } catch (Exception e) {
                throw new SqliteStorageException("Unable to update user state", e);
            }
        });
    }
}
