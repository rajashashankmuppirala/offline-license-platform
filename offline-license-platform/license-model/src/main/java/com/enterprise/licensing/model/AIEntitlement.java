package com.enterprise.licensing.model;

import java.util.List;
import java.util.Set;

public record AIEntitlement(boolean enabled, int maxRegisteredAgents, int maxConcurrentAgentExecutions,
                            Set<AiCapability> capabilities, List<MeterEntitlement> meters) {
    public AIEntitlement {
        Validation.nonNegative(maxRegisteredAgents, "maxRegisteredAgents");
        Validation.nonNegative(maxConcurrentAgentExecutions, "maxConcurrentAgentExecutions");
        capabilities = Set.copyOf(Validation.noNulls(capabilities == null ? Set.of() : capabilities, "capabilities"));
        meters = List.copyOf(Validation.noNulls(meters == null ? List.of() : meters, "meters"));
    }
}
