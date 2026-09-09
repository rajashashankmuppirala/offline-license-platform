package com.enterprise.licensing.security;

import java.nio.file.*;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OfflineNetworkBehaviorTest {
    @Test
    void runtimeHasNoHardCodedVendorOrInternetEndpoint() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        for (String module : java.util.List.of("license-server", "license-client-java", "license-engine", "license-storage")) {
            Path main = root.resolve(module).resolve("src/main");
            if (!Files.exists(main)) continue;
            try (var files = Files.walk(main)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String text = Files.readString(file);
                    assertThat(text).as(file.toString()).doesNotContain("https://").doesNotContain("http://");
                }
            }
        }
    }
}
