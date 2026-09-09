package com.enterprise.licensing.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeArtifactSecretScanTest {
    @Test
    void packagedRuntimeVerificationBuildsBeforeScanningArchivesAndDependencyGraphs() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path verifier = root.resolve("scripts/verify-runtime-artifacts.sh");
        assertThat(verifier).isRegularFile();
        var syntax = new ProcessBuilder("sh", "-n", verifier.toString()).redirectErrorStream(true).start();
        assertThat(syntax.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(syntax.exitValue()).as(new String(syntax.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isZero();
        String verifierSource = Files.readString(verifier);
        assertThat(verifierSource.indexOf("mvn -q -DskipTests package"))
                .isLessThan(verifierSource.indexOf("for archive in $artifacts"));
        assertThat(verifierSource).contains("dependency:tree", "jar tf", "unzip -p", "license-crypto-vendor",
                "license-server/target/", "license-client-java/target/", "license-engine/target/", "license-storage/target/",
                "examples/sample-product/target/", "examples/tomcat-example/target/", "examples/kubernetes-example/target/");

        for (String module : java.util.List.of("license-server", "license-client-java", "license-engine", "license-storage", "examples")) {
            Path source = root.resolve(module).resolve("src");
            if (!Files.exists(source)) continue;
            try (var files = Files.walk(source)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String text = Files.readString(file);
                    assertThat(text).as(file.toString()).doesNotContain("BEGIN PRIVATE KEY").doesNotContain("BEGIN ED25519 PRIVATE KEY").doesNotContain("vendor-private.key\":");
                }
            }
        }
    }
}
