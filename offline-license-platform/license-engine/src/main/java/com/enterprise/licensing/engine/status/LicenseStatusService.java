package com.enterprise.licensing.engine.status;

import com.enterprise.licensing.engine.activation.LicenseRepository;
import com.enterprise.licensing.engine.ai.AiLicenseRepository;
import com.enterprise.licensing.engine.identity.LicenseServerIdentityService;
import com.enterprise.licensing.engine.instance.ProductInstanceRepository;
import com.enterprise.licensing.engine.metering.MeteringRepository;
import com.enterprise.licensing.engine.ports.TimeSource;
import com.enterprise.licensing.engine.recovery.RecoveryService;
import com.enterprise.licensing.engine.user.UserLicenseRepository;
import com.enterprise.licensing.model.*;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LicenseStatusService {
    private final LicenseServerIdentityService identity; private final LicenseRepository licenses;
    private final ProductInstanceRepository instances; private final UserLicenseRepository users;
    private final AiLicenseRepository ai; private final MeteringRepository meters; private final TimeSource time;
    private final RecoveryService recovery;
    public LicenseStatusService(LicenseServerIdentityService identity,LicenseRepository licenses,ProductInstanceRepository instances,UserLicenseRepository users,AiLicenseRepository ai,MeteringRepository meters,TimeSource time,RecoveryService recovery){this.identity=identity;this.licenses=licenses;this.instances=instances;this.users=users;this.ai=ai;this.meters=meters;this.time=time;this.recovery=recovery;}
    public Map<String,Object> status(){recovery.assess();var result=new LinkedHashMap<String,Object>();var server=identity.publicIdentity();var active=licenses.active();result.put("serverId",server.licenseServerId());result.put("activationState",active.isPresent()?"ACTIVE":"UNACTIVATED");result.put("recoveryState",recovery.state());result.put("reasonCode",recovery.reasonCode());result.put("licenseId",active.map(v->v.license().licenseId()).orElse(""));result.put("customer",active.<Object>map(v->v.license().customer()).orElse(Map.of()));result.put("validity",active.<Object>map(v->v.license().subscription()).orElse(Map.of()));result.put("graceState",active.map(v->{var now=time.now();return now.isAfter(v.license().subscription().validUntil())&&!now.isAfter(v.license().subscription().graceUntil());}).orElse(false));result.put("warnings",recovery.reasonCode()==LicenseReasonCode.LICENSE_VALID?java.util.List.of():java.util.List.of(recovery.reasonCode().name()));return Map.copyOf(result);}
    public Map<String,Object> utilization(){var now=time.now();var result=new LinkedHashMap<String,Object>();result.put("instances",instances.list());result.put("namedUsers",users.namedAssignments());result.put("activeConcurrentUsers",users.userLeases().stream().filter(v->v.activeAt(now)).toList());result.put("registeredAgents",ai.agents());result.put("activeAgentExecutions",ai.leases().stream().filter(v->v.activeAt(now)).toList());result.put("meters",meters.counters());return Map.copyOf(result);}
}
