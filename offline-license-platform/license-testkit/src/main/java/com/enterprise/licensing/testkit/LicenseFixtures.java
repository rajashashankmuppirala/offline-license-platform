package com.enterprise.licensing.testkit;

import com.enterprise.licensing.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class LicenseFixtures {
    private LicenseFixtures() {
    }

    public static CommercialLicense aInc() {
        return new CommercialLicense("1.0", "LIC-AINC-2026-001",
                new CustomerIdentity("AINC", "A Inc.", "a@d.com"),
                new SubscriptionEntitlement("SUBSCRIPTION", "TEAMS", Instant.parse("2025-08-11T00:00:00Z"), Instant.parse("2026-08-14T23:59:59Z"), 7),
                new ActivationPolicy(true, 1, true),
                List.of(new EnvironmentEntitlement(EnvironmentType.PRODUCTION, 1, false),
                        new EnvironmentEntitlement(EnvironmentType.DISASTER_RECOVERY, 1, true),
                        new EnvironmentEntitlement(EnvironmentType.UAT, 2, false),
                        new EnvironmentEntitlement(EnvironmentType.QA, 2, false),
                        new EnvironmentEntitlement(EnvironmentType.DEVELOPMENT, 5, false),
                        new EnvironmentEntitlement(EnvironmentType.OTHER, 0, false)),
                new UserEntitlement(500, 20, List.of(new UserCategoryEntitlement(UserCategory.REPORTING, 100), new UserCategoryEntitlement(UserCategory.DQ_ISSUE_MANAGER, 100))),
                List.of(new ProductEntitlement(ProductId.VALIDATOR, true, 500, Set.of(new FeatureId("TABLEAU"), new FeatureId("ORACLE_ANALYTICS"), new FeatureId("COGNOS"))),
                        new ProductEntitlement(ProductId.DATA_MANAGER, true, 500, Set.of()),
                        new ProductEntitlement(ProductId.DATA_QUALITY, true, 500, Set.of()),
                        new ProductEntitlement(ProductId.DATA_FLOW, true, 500, Set.of())),
                new AIEntitlement(true, 10, 3, Set.of(AiCapability.INTERACTIVE_ASSISTANT, AiCapability.AUTONOMOUS_AGENT,
                        AiCapability.SCHEDULED_AGENT, AiCapability.MCP_TOOL_EXECUTION),
                        List.of(new MeterEntitlement("AI_EXECUTION_UNIT", MeterPeriod.MONTHLY, 100_000))),
                Instant.parse("2025-12-01T00:00:00Z"), 1, 1, Set.of());
    }
}
