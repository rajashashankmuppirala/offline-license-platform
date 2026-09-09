package com.enterprise.licensing.engine.ai;

import com.enterprise.licensing.model.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface AiLicenseRepository{
 AgentResult register(String agentId,String displayName,String instanceId,String poolId,int limit,Instant now);
 AgentResult decommission(String agentId,Instant now);
 List<RegisteredAgent> agents();
 AgentLeaseResult acquire(String leaseId,String agentId,String executionId,String instanceId,String poolId,AiCapability capability,int limit,Instant now,Duration duration);
 AgentLeaseResult heartbeat(String leaseId,Instant now,Duration duration);
 AgentLeaseResult release(String leaseId,Instant now);
 List<AgentExecutionLease> leases();
}
