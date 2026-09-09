package com.enterprise.licensing.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.engine.identity.LicenseServerIdentityService;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.storage.identity.SoftwareIdentityProvider;
import com.enterprise.licensing.storage.integrity.AuthoritativeStateIntegrityService;
import com.enterprise.licensing.storage.sqlite.SqliteDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthoritativeStateIntegrityTest {
    @TempDir Path temp;

    @Test
    void detectsAChangeInEveryProtectedSecurityStateClass() {
        try (var database = new SqliteDatabase(temp.resolve("license.db"))) {
            var identity = new LicenseServerIdentityService(new SoftwareIdentityProvider(
                    temp.resolve("identity.json"), () -> Instant.parse("2026-01-01T00:00:00Z"), Map.of()));
            var service = new AuthoritativeStateIntegrityService(database, identity,
                    temp.resolve("state.checkpoint"), new ObjectMapper().findAndRegisterModules());
            service.seal();

            List<Mutation> mutations = List.of(
                    new Mutation("license_server_identity", "INSERT INTO license_server_identity VALUES(1,'S','P','F','2026-01-01T00:00:00Z','{}',0)"),
                    new Mutation("commercial_license", "INSERT INTO commercial_license VALUES('L','C','{}','H','2026-01-01T00:00:00Z','2027-01-01T00:00:00Z','2027-01-08T00:00:00Z','2026-01-01T00:00:00Z',1)"),
                    new Mutation("activation_certificate", "INSERT INTO activation_certificate VALUES('A','L','S','F','{}','2026-01-01T00:00:00Z',1)"),
                    new Mutation("local_revocation", "INSERT INTO local_revocation VALUES('R','LICENSE','X','test','2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')"),
                    new Mutation("product_instance", "INSERT INTO product_instance VALUES('I','P','F','VALIDATOR','TOMCAT','PRODUCTION','1','{}','ACTIVE','2026-01-01T00:00:00Z','2026-01-01T00:00:00Z',NULL)"),
                    new Mutation("named_user_assignment", "INSERT INTO named_user_assignment VALUES('N','U','HUMAN','VALIDATOR',NULL,'2026-01-01T00:00:00Z',NULL,NULL)"),
                    new Mutation("user_lease", "INSERT INTO user_lease(lease_id,user_id,account_type,instance_id,session_id,acquired_at,heartbeat_at,expires_at,released_at,revoked_at) VALUES('UL','U','HUMAN','I','SESSION','2026-01-01T00:00:00Z','2026-01-01T00:00:00Z','2026-01-01T00:05:00Z',NULL,NULL)"),
                    new Mutation("registered_agent", "INSERT INTO registered_agent(agent_id,display_name,instance_id,registered_at,status) VALUES('AG','Agent','I','2026-01-01T00:00:00Z','ACTIVE')"),
                    new Mutation("agent_lease", "INSERT INTO agent_lease(lease_id,agent_id,execution_id,instance_id,capability,acquired_at,heartbeat_at,expires_at,released_at,revoked_at) VALUES('AL','AG','E','I','AUTONOMOUS_AGENT','2026-01-01T00:00:00Z','2026-01-01T00:00:00Z','2026-01-01T00:05:00Z',NULL,NULL)"),
                    new Mutation("meter_usage", "INSERT INTO meter_usage(customer_id,meter_id,period_start,period_end,used_amount,licensed_limit) VALUES('C','AI_EXECUTION_UNIT','2026-01-01T00:00:00Z','2026-02-01T00:00:00Z',1,10)"),
                    new Mutation("usage_consumption", "INSERT INTO usage_consumption(request_id,customer_id,instance_id,meter_id,period_start,amount,accepted,decided_at,dimensions_json) VALUES('UR','C','I','AI_EXECUTION_UNIT','2026-01-01T00:00:00Z',1,1,'2026-01-01T00:00:00Z','{}')"),
                    new Mutation("trusted_time_state", "INSERT INTO trusted_time_state VALUES(1,'2026-01-01T00:00:00Z',1,'H')"),
                    new Mutation("cached_entitlement_issue", "INSERT INTO cached_entitlement_issue VALUES('CE','L','I','{}','2026-01-01T00:00:00Z','2026-01-02T00:00:00Z',NULL)"),
                    new Mutation("request_nonce", "INSERT INTO request_nonce VALUES('I','NONCE','2026-01-01T00:00:00Z','2026-01-01T00:05:00Z')"),
                    new Mutation("audit_event", "INSERT INTO audit_event VALUES('00000000-0000-0000-0000-000000000001','2026-01-01T00:00:00Z','C','L','I','STATUS',NULL,1,'LICENSE_VALID','{}',NULL,'EH')")
            );

            for (Mutation mutation : mutations) {
                execute(database, mutation.sql());
                assertThat(service.verifyOrInitialize()).as(mutation.table())
                        .isEqualTo(LicenseReasonCode.STATE_INTEGRITY_FAILURE);
                service.seal();
                assertThat(service.verifyOrInitialize()).as(mutation.table())
                        .isEqualTo(LicenseReasonCode.LICENSE_VALID);
            }
        }
    }

    private static void execute(SqliteDatabase database, String sql) {
        database.withConnection(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute(sql);
                return null;
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    private record Mutation(String table, String sql) {}
}
