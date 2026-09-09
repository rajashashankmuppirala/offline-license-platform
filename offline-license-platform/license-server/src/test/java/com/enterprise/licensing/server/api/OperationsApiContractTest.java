package com.enterprise.licensing.server.api;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OperationsApiContractTest {
    private static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"), "license-server-http-" + UUID.randomUUID());
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("server.address", () -> "127.0.0.1");
        r.add("licensing.data-file", () -> ROOT.resolve("license.db").toString());
        r.add("licensing.identity-directory", () -> ROOT.resolve("identity").toString());
        r.add("licensing.checkpoint-file", () -> ROOT.resolve("identity/time.checkpoint").toString());
        r.add("licensing.admin-token", () -> "contract-admin-token");
    }

    @Test
    void adminStatusIsStableAuthenticatedAndUnactivated() throws Exception {
        var denied = get("/api/v1/license/admin/status", null);
        assertThat(denied.statusCode()).isEqualTo(401);
        assertThat(mapper.readTree(denied.body()).path("reasonCode").asText()).isEqualTo("UNAUTHORIZED");
        var ok = get("/api/v1/license/admin/status", "contract-admin-token");
        var json = mapper.readTree(ok.body());
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(json.path("success").asBoolean()).isTrue();
        assertThat(json.path("reasonCode").asText()).isEqualTo("LICENSE_VALID");
        assertThat(json.path("data").path("activationState").asText()).isEqualTo("UNACTIVATED");
        assertThat(json.path("data").path("serverId").asText()).isNotBlank();
    }

    @Test
    void livenessDiagnosticsStayAvailableBeforeActivation() throws Exception {
        var response = get("/actuator/health", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).path("status").asText()).isEqualTo("UP");
    }

    @Test
    void operationsExposeUtilizationDiagnosticsAndHealthGroups() throws Exception {
        for (String path : new String[]{"/api/v1/license/admin/entitlements", "/api/v1/license/admin/utilization", "/api/v1/license/admin/diagnostics"}) {
            var response = get(path, "contract-admin-token");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(response.body()).path("success").asBoolean()).isTrue();
        }
        assertThat(mapper.readTree(get("/actuator/health/liveness", null).body()).path("status").asText()).isEqualTo("UP");
        assertThat(mapper.readTree(get("/actuator/health/readiness", null).body()).path("status").asText()).isEqualTo("UP");
    }

    @Test
    void disasterRecoveryHandoffsAreAuthenticatedAndAuditedWithExactDenial() throws Exception {
        String body = "{\"currentActiveInstanceId\":\"prod\",\"targetStandbyInstanceId\":\"dr\",\"force\":true}";
        assertThat(post("/api/v1/license/admin/instances/failover", null, body).statusCode()).isEqualTo(401);
        for (String action : new String[]{"failover", "failback"}) {
            var response = post("/api/v1/license/admin/instances/" + action, "contract-admin-token", body);
            var json = mapper.readTree(response.body());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json.path("success").asBoolean()).isFalse();
            assertThat(json.path("reasonCode").asText()).isEqualTo("LICENSE_SERVER_NOT_ACTIVATED");
        }
        var audit = mapper.readTree(get("/api/v1/license/admin/audit", "contract-admin-token").body()).path("data");
        assertThat(audit).anySatisfy(event -> {
            assertThat(event.path("operation").asText()).isEqualTo("INSTANCE_TRANSITION");
            assertThat(event.path("reasonCode").asText()).isEqualTo("LICENSE_SERVER_NOT_ACTIVATED");
            assertThat(event.path("allowed").asBoolean()).isFalse();
        });
    }

    @Test
    void onlineBackupAndRestoreValidationOperationsRequireAdminAuthentication() throws Exception {
        Path backup = ROOT.resolve("admin-backup-" + UUID.randomUUID() + ".db");
        String body = "{\"path\":\"" + backup.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        assertThat(post("/api/v1/license/admin/storage/backup", null, body).statusCode()).isEqualTo(401);
        var created = post("/api/v1/license/admin/storage/backup", "contract-admin-token", body);
        assertThat(mapper.readTree(created.body()).path("success").asBoolean()).as(created.body()).isTrue();
        assertThat(backup).isRegularFile();
        assertThat(post("/api/v1/license/admin/storage/restore/validate", null, body).statusCode()).isEqualTo(401);
        var validation = mapper.readTree(post("/api/v1/license/admin/storage/restore/validate", "contract-admin-token", body).body());
        assertThat(validation.path("success").asBoolean()).isFalse();
        assertThat(validation.path("reasonCode").asText()).isEqualTo("LICENSE_SERVER_NOT_ACTIVATED");
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
