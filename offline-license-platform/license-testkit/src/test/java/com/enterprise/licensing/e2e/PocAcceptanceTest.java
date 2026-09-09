package com.enterprise.licensing.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class PocAcceptanceTest {
    @Test
    void demoDefinesFortyExecutableFailFastAcceptanceStepsInOrder() throws Exception {
        Path script = Path.of("../scripts/poc-demo-local.sh").toAbsolutePath().normalize();
        assertThat(script).isRegularFile();
        var syntax = new ProcessBuilder("sh", "-n", script.toString()).redirectErrorStream(true).start();
        assertThat(syntax.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(syntax.exitValue()).as(new String(syntax.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isZero();
        var process = new ProcessBuilder("sh", script.toString(), "--list").redirectErrorStream(true).start();
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(output).isZero();
        var lines = output.lines().toList();
        assertThat(lines).hasSize(40);
        for (int i = 1; i <= 40; i++) assertThat(lines.get(i - 1)).startsWith(String.format("%02d. ", i));
        assertThat(output).contains("LICENSE_SIGNATURE_INVALID", "LICENSE_SERVER_IDENTITY_MISMATCH",
                "ENVIRONMENT_INSTANCE_LIMIT_EXCEEDED", "CONCURRENT_USER_LIMIT_EXCEEDED",
                "NAMED_USER_LIMIT_EXCEEDED", "FEATURE_NOT_ENTITLED", "AGENT_REGISTRATION_LIMIT_EXCEEDED",
                "AGENT_CONCURRENCY_LIMIT_EXCEEDED", "AI_USAGE_LIMIT_EXCEEDED", "LICENSE_SERVER_UNAVAILABLE",
                "LICENSE_EXPIRED", "CLOCK_ROLLBACK_DETECTED", "valid bound cache", "authoritative state survived restart");

        List<Integer> meaningfulDecisions = List.of(8, 10, 12, 13, 14, 16, 21, 23, 25, 26, 28, 30, 33, 39, 40);
        meaningfulDecisions.forEach(number -> assertThat(lines.get(number - 1))
                .as("step %s must demand a one-to-one audit assertion", number)
                .contains("exactly one decision audit"));

        String source = Files.readString(script);
        assertThat(source).contains("set -eu", "surefire.skipAfterFailureCount=1", "DomainDecisionAuditTest");
        assertThat(source.lines().filter(line -> line.startsWith("run_group '")).count()).isEqualTo(8);
    }
}
