package com.enterprise.licensing.engine.ai;

import com.enterprise.licensing.engine.metering.ConfiguredAEUPolicy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ConfiguredAeuPolicyTest {
    @Test
    void weightsArePolicyAndUnknownActionsFailClosed() {
        var policy = new ConfiguredAEUPolicy(Map.of("INTERACTIVE_AI_REQUEST", 1L, "AUTONOMOUS_AGENT_RUN", 5L, "AGENT_TOOL_INVOCATION", 1L, "SCHEDULED_AGENT_RUN", 5L));
        assertThat(policy.unitsFor("INTERACTIVE_AI_REQUEST")).isEqualTo(1);
        assertThat(policy.unitsFor("AUTONOMOUS_AGENT_RUN")).isEqualTo(5);
        assertThatThrownBy(() -> policy.unitsFor("UNKNOWN")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfiguredAEUPolicy(Map.of("BROKEN", 0L))).isInstanceOf(IllegalArgumentException.class);
    }
}
