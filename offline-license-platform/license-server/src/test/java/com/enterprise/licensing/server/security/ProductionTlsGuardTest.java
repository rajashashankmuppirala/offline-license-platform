package com.enterprise.licensing.server.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.licensing.server.config.ProductionTlsGuard;
import org.junit.jupiter.api.Test;

class ProductionTlsGuardTest {
    @Test
    void productionCannotDisableTlsWhileLocalProfilesRemainExplicitlyAvailable() {
        assertThatThrownBy(() -> new ProductionTlsGuard(new String[]{"production"}, false)).isInstanceOf(IllegalStateException.class).hasMessageContaining("requires License Server HTTPS");
        assertThatCode(() -> new ProductionTlsGuard(new String[]{"production"}, true)).doesNotThrowAnyException();
        assertThatCode(() -> new ProductionTlsGuard(new String[]{"test"}, false)).doesNotThrowAnyException();
    }
}
