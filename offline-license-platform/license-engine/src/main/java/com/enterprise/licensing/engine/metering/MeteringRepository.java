package com.enterprise.licensing.engine.metering;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.enterprise.licensing.model.UsageCounter;

public interface MeteringRepository{UsageResult consume(String requestId,String customerId,String instanceId,String poolId,String meterId,String actionType,long amount,Instant periodStart,Instant periodEnd,long limit,Instant now,Map<String,String> dimensions);List<UsageCounter> counters();}
