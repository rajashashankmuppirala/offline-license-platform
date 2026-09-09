package com.enterprise.licensing.engine.activation;

import com.enterprise.licensing.engine.ai.AiLicenseRepository;
import com.enterprise.licensing.engine.instance.ProductInstanceRepository;
import com.enterprise.licensing.engine.metering.MeteringRepository;
import com.enterprise.licensing.engine.user.UserLicenseRepository;
import com.enterprise.licensing.model.AgentStatus;
import com.enterprise.licensing.model.EnvironmentEntitlement;
import com.enterprise.licensing.model.InstanceState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class RepositoryRenewalImpactAssessment implements RenewalImpactAssessment {
    private final ProductInstanceRepository instances;
    private final UserLicenseRepository users;
    private final AiLicenseRepository agents;
    private final MeteringRepository meters;

    public RepositoryRenewalImpactAssessment(ProductInstanceRepository instances, UserLicenseRepository users,
                                             AiLicenseRepository agents, MeteringRepository meters) {
        this.instances = instances;
        this.users = users;
        this.agents = agents;
        this.meters = meters;
    }

    @Override
    public List<String> warnings(com.enterprise.licensing.model.CommercialLicense license, Instant now) {
        var warnings = new ArrayList<String>();
        for (EnvironmentEntitlement entitlement : license.environments()) {
            long used = instances.list().stream()
                    .filter(instance -> instance.environmentType() == entitlement.type())
                    .filter(instance -> instance.state() != InstanceState.DECOMMISSIONED).count();
            if (used > entitlement.maxInstances()) warnings.add("ENVIRONMENT_OVER_ALLOCATED:" + entitlement.type());
        }
        long productionActive = instances.list().stream()
                .filter(instance -> instance.state() == InstanceState.ACTIVE)
                .filter(instance -> instance.environmentType() == com.enterprise.licensing.model.EnvironmentType.PRODUCTION
                        || instance.environmentType() == com.enterprise.licensing.model.EnvironmentType.DISASTER_RECOVERY)
                .count();
        if (productionActive > license.activeProductionClassLimit()) warnings.add("PRODUCTION_OVER_ALLOCATED");
        if (users.namedAssignments().stream().filter(assignment -> assignment.releasedAt() == null).count()
                > license.users().maxNamedUsers()) warnings.add("NAMED_USERS_OVER_ALLOCATED");
        if (users.userLeases().stream().filter(lease -> lease.activeAt(now)).count()
                > license.users().maxConcurrentUsers()) warnings.add("USER_LEASES_OVER_ALLOCATED");
        if (agents.agents().stream().filter(agent -> agent.status() == AgentStatus.ACTIVE).count()
                > license.ai().maxRegisteredAgents()) warnings.add("AGENTS_OVER_ALLOCATED");
        if (agents.leases().stream().filter(lease -> lease.activeAt(now)).count()
                > license.ai().maxConcurrentAgentExecutions()) warnings.add("AGENT_LEASES_OVER_ALLOCATED");
        for (var counter : meters.counters()) {
            var entitlement = license.ai().meters().stream()
                    .filter(meter -> meter.id().equals(counter.meterId())).findFirst();
            if (entitlement.isEmpty() || counter.usedAmount() > entitlement.get().limit()) {
                warnings.add("METER_OVER_ALLOCATED:" + counter.meterId());
            }
        }
        return List.copyOf(warnings);
    }
}
