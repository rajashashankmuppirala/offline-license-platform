package com.enterprise.licensing.server.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.crypto.Ed25519Keys;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagnosticSafeStartupProcessTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @TempDir
    Path temp;

    private static int freePort() throws Exception {
        try (var socket = new java.net.ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static void stop(Process process) throws Exception {
        process.destroy();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void missingIdentityBootstrapsButUnreadableIdentityStartsInDiagnosticSafeMode() throws Exception {
        Path missing = temp.resolve("missing");
        Files.createDirectories(missing);
        int bootstrapPort = freePort();
        Process bootstrap = start(bootstrapPort, missing);
        try {
            awaitLiveness(bootstrapPort);
            assertThat(get(bootstrapPort, "/actuator/health/readiness").statusCode()).isEqualTo(200);
            assertThat(missing.resolve("identity/identity.json")).isRegularFile();
        } finally {
            stop(bootstrap);
        }

        Path empty = temp.resolve("empty");
        Files.createDirectories(empty.resolve("identity"));
        Files.createFile(empty.resolve("identity/identity.json"));
        assertSafeMode(empty);
        Path malformed = temp.resolve("malformed");
        Files.createDirectories(malformed.resolve("identity"));
        Files.writeString(malformed.resolve("identity/identity.json"), "{not-json");
        assertSafeMode(malformed);

        Path mismatched = temp.resolve("mismatched");
        Files.createDirectories(mismatched);
        int createPort = freePort();
        Process creator = start(createPort, mismatched);
        try {
            awaitLiveness(createPort);
        } finally {
            stop(creator);
        }
        Path identity = mismatched.resolve("identity/identity.json");
        var json = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(identity.toFile());
        json.put("privateKey", Ed25519Keys.encodePrivate(Ed25519Keys.generate().getPrivate()));
        mapper.writeValue(identity.toFile(), json);
        assertSafeMode(mismatched);
    }

    private void assertSafeMode(Path root) throws Exception {
        int port = freePort();
        Process process = start(port, root);
        try {
            awaitLiveness(port);
            var response = get(port, "/actuator/health/readiness");
            assertThat(response.statusCode()).isEqualTo(503);
            var body = mapper.readTree(response.body());
            assertThat(body.path("status").asText()).isEqualTo("DOWN");
            String rendered = body.toString();
            assertThat(rendered).contains("STATE_INTEGRITY_FAILURE", "IDENTITY_UNREADABLE");
        } finally {
            stop(process);
        }
    }

    private Process start(int port, Path root) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        return new ProcessBuilder(java, "-cp", classpath, "com.enterprise.licensing.server.LicenseServerApplication", "--server.address=127.0.0.1", "--server.port=" + port, "--licensing.data-file=" + root.resolve("data/license.db"), "--licensing.identity-directory=" + root.resolve("identity"), "--licensing.checkpoint-file=" + root.resolve("identity/time.checkpoint"), "--licensing.admin-token=diagnostic-token").redirectErrorStream(true).redirectOutput(root.resolve("server-" + System.nanoTime() + ".log").toFile()).start();
    }

    private void awaitLiveness(int port) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (get(port, "/actuator/health/liveness").statusCode() == 200) return;
            } catch (Exception ignored) {
            }
            Thread.sleep(100);
        }
        throw new AssertionError("server did not reach diagnostic liveness");
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
