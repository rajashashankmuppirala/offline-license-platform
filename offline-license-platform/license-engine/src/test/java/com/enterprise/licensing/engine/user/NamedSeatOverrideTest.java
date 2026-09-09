package com.enterprise.licensing.engine.user;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class NamedSeatOverrideTest {
    @Test void requiresApprovedTypeActorJustificationAndDistinctMatchingUsers() {
        assertThat(new NamedSeatOverride("EMPLOYEE_DEPARTURE","admin","left company","old","new").completeFor("old")).isTrue();
        assertThat(new NamedSeatOverride("ASSIGNMENT_CORRECTION","admin","wrong assignment","old","new").completeFor("old")).isTrue();
        assertThat(new NamedSeatOverride("OTHER","admin","why","old","new").completeFor("old")).isFalse();
        assertThat(new NamedSeatOverride("EMPLOYEE_DEPARTURE","","why","old","new").completeFor("old")).isFalse();
        assertThat(new NamedSeatOverride("EMPLOYEE_DEPARTURE","admin","","old","new").completeFor("old")).isFalse();
        assertThat(new NamedSeatOverride("EMPLOYEE_DEPARTURE","admin","why","different","new").completeFor("old")).isFalse();
        assertThat(new NamedSeatOverride("EMPLOYEE_DEPARTURE","admin","why","old","old").completeFor("old")).isFalse();
    }
}
