package com.enterprise.licensing.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.licensing.engine.ports.TimeSource;
import com.enterprise.licensing.storage.identity.SoftwareIdentityProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SoftwareIdentityProviderTest {
    @TempDir
    Path temp;

    private static TimeSource fixedTime() {
        return () -> Instant.parse("2026-01-01T00:00:00Z");
    }

    @Test
    void createsRestrictiveStableIdentityAndReloadsIt() throws Exception {
        Path file = temp.resolve("identity/server.json");
        var provider = new SoftwareIdentityProvider(file, fixedTime(), Map.of("site", "primary"));
        var first = provider.loadOrCreate();
        var second = new SoftwareIdentityProvider(file, fixedTime(), Map.of()).loadOrCreate();

        assertThat(second.identity()).isEqualTo(first.identity());
        assertThat(second.privateKey().getEncoded()).isEqualTo(first.privateKey().getEncoded());
        try {
            assertThat(Files.getPosixFilePermissions(file)).isEqualTo(Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
        }
    }

    @Test
    void refusesMissingOrCorruptExistingMaterialInsteadOfRegenerating() throws Exception {
        Path file = temp.resolve("identity/server.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "");
        assertThatThrownBy(() -> new SoftwareIdentityProvider(file, fixedTime(), Map.of()).loadOrCreate())
                .hasMessageContaining("Unable to load");
    }
}
