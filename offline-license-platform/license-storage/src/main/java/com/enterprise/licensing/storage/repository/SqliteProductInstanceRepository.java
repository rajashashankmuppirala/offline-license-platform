package com.enterprise.licensing.storage.repository;

import com.enterprise.licensing.engine.instance.InstanceRegistrationResult;
import com.enterprise.licensing.engine.instance.ProductInstanceRepository;
import com.enterprise.licensing.engine.ports.TransactionRunner;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.sqlite.SqliteDatabase;
import com.enterprise.licensing.storage.sqlite.SqliteStorageException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SqliteProductInstanceRepository implements ProductInstanceRepository {
    private final SqliteDatabase database;
    private final TransactionRunner transactions;
    private final ObjectMapper mapper;

    public SqliteProductInstanceRepository(SqliteDatabase database, TransactionRunner transactions, ObjectMapper mapper) {
        this.database = database;
        this.transactions = transactions;
        this.mapper = mapper;
    }

    private static boolean productionClass(EnvironmentType type) {
        return type == EnvironmentType.PRODUCTION || type == EnvironmentType.DISASTER_RECOVERY;
    }

    @Override
    public InstanceRegistrationResult register(InstanceRegistrationRequest request, String fingerprint,
                                               int environmentLimit, int activeProductionClassLimit, Instant now) {
        return transactions.write(() -> {
            var existing = findInternal(request.instanceId());
            if (existing.isPresent()) {
                var value = existing.get();
                boolean same = value.publicKeyFingerprint().equals(fingerprint) && value.productId().equals(request.productId())
                        && value.environmentType() == request.environmentType() && value.deploymentType() == request.deploymentType();
                if (!same) return InstanceRegistrationResult.denied(LicenseReasonCode.INSTANCE_AUTHENTICATION_FAILED);
                if (value.state() == InstanceState.DECOMMISSIONED)
                    return InstanceRegistrationResult.denied(LicenseReasonCode.INVALID_REQUEST);
                return InstanceRegistrationResult.accepted(value);
            }
            if (count("SELECT count(*) FROM product_instance WHERE environment_type=? AND state<>'DECOMMISSIONED'", request.environmentType().name()) >= environmentLimit)
                return InstanceRegistrationResult.denied(LicenseReasonCode.ENVIRONMENT_LIMIT_EXCEEDED);
            InstanceState initial = request.environmentType() == EnvironmentType.DISASTER_RECOVERY ? InstanceState.STANDBY : InstanceState.REGISTERED;
            if (initial == InstanceState.ACTIVE && productionClass(request.environmentType()) &&
                    activeProductionCount() >= activeProductionClassLimit)
                return InstanceRegistrationResult.denied(LicenseReasonCode.ACTIVE_PRODUCTION_LIMIT_EXCEEDED);
            execute("INSERT INTO product_instance(instance_id,public_key,public_key_fingerprint,product_id,deployment_type,environment_type,application_version,metadata_json,state,registered_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    request.instanceId(), request.installationPublicKey().encoded(), fingerprint, request.productId().value(), request.deploymentType().name(),
                    request.environmentType().name(), request.applicationVersion(), json(request.metadata()), initial.name(), now.toString(), now.toString());
            return InstanceRegistrationResult.accepted(findInternal(request.instanceId()).orElseThrow());
        });
    }

    @Override
    public Optional<RegisteredProductInstance> find(String instanceId) {
        return transactions.read(() -> findInternal(instanceId));
    }

    @Override
    public List<RegisteredProductInstance> list() {
        return transactions.read(() -> database.withConnection(connection -> {
            try (var statement = connection.prepareStatement("SELECT * FROM product_instance ORDER BY registered_at,instance_id"); var result = statement.executeQuery()) {
                var values = new ArrayList<RegisteredProductInstance>();
                while (result.next()) values.add(map(result));
                return List.copyOf(values);
            } catch (Exception e) {
                throw new SqliteStorageException("Unable to list product instances", e);
            }
        }));
    }

    @Override
    public InstanceRegistrationResult transition(String instanceId, InstanceState expected, InstanceState target,
                                                 int activeProductionClassLimit, Instant now, boolean force) {
        return transactions.write(() -> {
            var current = findInternal(instanceId).orElse(null);
            if (current == null) return InstanceRegistrationResult.denied(LicenseReasonCode.INSTANCE_NOT_REGISTERED);
            if (current.state() != expected || current.state() == InstanceState.DECOMMISSIONED)
                return InstanceRegistrationResult.denied(LicenseReasonCode.INVALID_REQUEST);
            if (target == InstanceState.ACTIVE && current.state() != InstanceState.ACTIVE && productionClass(current.environmentType())
                    && activeProductionCount() >= activeProductionClassLimit)
                return InstanceRegistrationResult.denied(LicenseReasonCode.ACTIVE_PRODUCTION_LIMIT_EXCEEDED);
            if (target == InstanceState.DECOMMISSIONED && !force && hasLiveDependents(instanceId, now))
                return InstanceRegistrationResult.denied(LicenseReasonCode.INVALID_REQUEST);
            if (target == InstanceState.DECOMMISSIONED && force) {
                execute("UPDATE user_lease SET revoked_at=? WHERE instance_id=? AND released_at IS NULL AND revoked_at IS NULL AND expires_at>?", now.toString(), instanceId, now.toString());
                execute("UPDATE agent_lease SET revoked_at=? WHERE instance_id=? AND released_at IS NULL AND revoked_at IS NULL AND expires_at>?", now.toString(), instanceId, now.toString());
                execute("UPDATE cached_entitlement_issue SET revoked_at=? WHERE instance_id=? AND revoked_at IS NULL", now.toString(), instanceId);
            }
            execute("UPDATE product_instance SET state=?,updated_at=?,decommissioned_at=? WHERE instance_id=? AND state=?",
                    target.name(), now.toString(), target == InstanceState.DECOMMISSIONED ? now.toString() : null, instanceId, expected.name());
            return InstanceRegistrationResult.accepted(findInternal(instanceId).orElseThrow());
        });
    }

    @Override
    public InstanceRegistrationResult switchActive(String activeInstanceId, String standbyInstanceId,
                                                   int activeProductionClassLimit, Instant now, boolean force) {
        return transactions.write(() -> {
            if (activeInstanceId == null || standbyInstanceId == null || activeInstanceId.equals(standbyInstanceId)) {
                return InstanceRegistrationResult.denied(LicenseReasonCode.INVALID_REQUEST);
            }
            var active = findInternal(activeInstanceId).orElse(null);
            var target = findInternal(standbyInstanceId).orElse(null);
            if (active == null || target == null) {
                return InstanceRegistrationResult.denied(LicenseReasonCode.INSTANCE_NOT_REGISTERED);
            }
            if (active.state() != InstanceState.ACTIVE || target.state() != InstanceState.STANDBY
                    || !productionClass(active.environmentType()) || !productionClass(target.environmentType())) {
                return InstanceRegistrationResult.denied(LicenseReasonCode.INVALID_REQUEST);
            }
            if (!force && hasLiveDependents(activeInstanceId, now)) {
                return InstanceRegistrationResult.denied(LicenseReasonCode.INVALID_REQUEST);
            }
            if (force) {
                revokeDependents(activeInstanceId, now);
            }
            execute("UPDATE product_instance SET state='STANDBY',updated_at=? WHERE instance_id=? AND state='ACTIVE'",
                    now.toString(), activeInstanceId);
            if (activeProductionCount() >= activeProductionClassLimit) {
                throw new IllegalStateException("Production-class limit remained exhausted during failover");
            }
            execute("UPDATE product_instance SET state='ACTIVE',updated_at=? WHERE instance_id=? AND state='STANDBY'",
                    now.toString(), standbyInstanceId);
            return InstanceRegistrationResult.accepted(findInternal(standbyInstanceId).orElseThrow());
        });
    }

    private Optional<RegisteredProductInstance> findInternal(String instanceId) {
        return database.withConnection(connection -> {
            try (var statement = connection.prepareStatement("SELECT * FROM product_instance WHERE instance_id=?")) {
                statement.setString(1, instanceId);
                try (var result = statement.executeQuery()) {
                    return result.next() ? Optional.of(map(result)) : Optional.empty();
                }
            } catch (Exception e) {
                throw new SqliteStorageException("Unable to read product instance", e);
            }
        });
    }

    private RegisteredProductInstance map(ResultSet result) throws Exception {
        String decommissioned = result.getString("decommissioned_at");
        return new RegisteredProductInstance(result.getString("instance_id"), new EncodedPublicKey("Ed25519", "X.509", result.getString("public_key")),
                result.getString("public_key_fingerprint"), new ProductId(result.getString("product_id")), DeploymentType.valueOf(result.getString("deployment_type")),
                EnvironmentType.valueOf(result.getString("environment_type")), result.getString("application_version"), InstanceState.valueOf(result.getString("state")),
                Instant.parse(result.getString("registered_at")), Instant.parse(result.getString("updated_at")),
                decommissioned == null ? null : Instant.parse(decommissioned), mapper.readValue(result.getString("metadata_json"), new TypeReference<Map<String, String>>() {
        }));
    }

    private int activeProductionCount() {
        return count("SELECT count(*) FROM product_instance WHERE environment_type IN ('PRODUCTION','DISASTER_RECOVERY') AND state='ACTIVE'");
    }

    private boolean hasLiveDependents(String id, Instant now) {
        return count("SELECT (SELECT count(*) FROM user_lease WHERE instance_id=? AND released_at IS NULL AND revoked_at IS NULL AND expires_at>?) + (SELECT count(*) FROM agent_lease WHERE instance_id=? AND released_at IS NULL AND revoked_at IS NULL AND expires_at>?)",
                id, now.toString(), id, now.toString()) > 0;
    }

    private void revokeDependents(String instanceId, Instant now) {
        execute("UPDATE user_lease SET revoked_at=? WHERE instance_id=? AND released_at IS NULL AND revoked_at IS NULL AND expires_at>?",
                now.toString(), instanceId, now.toString());
        execute("UPDATE agent_lease SET revoked_at=? WHERE instance_id=? AND released_at IS NULL AND revoked_at IS NULL AND expires_at>?",
                now.toString(), instanceId, now.toString());
        execute("UPDATE cached_entitlement_issue SET revoked_at=? WHERE instance_id=? AND revoked_at IS NULL",
                now.toString(), instanceId);
    }

    private int count(String sql, Object... values) {
        return database.withConnection(connection -> {
            try (var statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
                try (var result = statement.executeQuery()) {
                    return result.getInt(1);
                }
            } catch (Exception e) {
                throw new SqliteStorageException("Unable to count instances", e);
            }
        });
    }

    private void execute(String sql, Object... values) {
        database.withConnection(connection -> {
            try (var statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
                statement.executeUpdate();
                return null;
            } catch (Exception e) {
                throw new SqliteStorageException("Unable to update product instance", e);
            }
        });
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new SqliteStorageException("Unable to encode metadata", e);
        }
    }
}
