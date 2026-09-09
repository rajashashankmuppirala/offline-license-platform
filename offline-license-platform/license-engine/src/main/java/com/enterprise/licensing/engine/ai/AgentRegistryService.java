package com.enterprise.licensing.engine.ai;

import com.enterprise.licensing.engine.activation.LicenseRepository;
import com.enterprise.licensing.engine.ports.TimeSource;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.model.LicenseOperation;
import com.enterprise.licensing.engine.validation.OperationAuthorizer;
import com.enterprise.licensing.engine.instance.ProductInstanceRepository;
import com.enterprise.licensing.model.EnvironmentEntitlement;
import com.enterprise.licensing.model.EntitlementPool;

public final class AgentRegistryService {
    private final LicenseRepository licenses;
    private final AiLicenseRepository agents;
    private final TimeSource time;
    private final OperationAuthorizer authorizer;
    private final ProductInstanceRepository instances;

    public AgentRegistryService(LicenseRepository l, AiLicenseRepository a, TimeSource t, OperationAuthorizer v, ProductInstanceRepository p) {
        licenses = l;
        agents = a;
        time = t;
        authorizer = v;
        instances = p;
    }

    public AgentResult register(String id, String name, String instanceId) {
        var authorization = authorizer.evaluate(LicenseOperation.AGENT_REGISTER);
        if (!authorization.allowed()) return AgentResult.denied(authorization.reasonCode());
        var state = licenses.active().orElse(null);
        if (state == null) return AgentResult.denied(LicenseReasonCode.LICENSE_SERVER_NOT_ACTIVATED);
        if (!state.license().ai().enabled()) return AgentResult.denied(LicenseReasonCode.AI_NOT_ENTITLED);
        var instance = instances.find(instanceId).orElse(null);
        if (instance == null) return AgentResult.denied(LicenseReasonCode.INSTANCE_NOT_REGISTERED);
        String pool = state.license().environments().stream().filter(e -> e.type() == instance.environmentType()).findFirst().map(EnvironmentEntitlement::poolId).orElse(EntitlementPool.CUSTOMER_WIDE);
        return agents.register(id, name, instanceId, pool, state.license().ai().maxRegisteredAgents(), time.now());
    }

    public AgentResult decommission(String id) {
        return agents.decommission(id, time.now());
    }
}
