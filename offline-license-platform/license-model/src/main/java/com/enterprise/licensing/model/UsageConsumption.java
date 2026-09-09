package com.enterprise.licensing.model;

import java.time.Instant;
import java.util.Map;

public record UsageConsumption(String requestId,String customerId,String instanceId,String meterId,long amount,
                               Instant periodStart,Instant decidedAt,boolean accepted,Map<String,String> dimensions){
    public UsageConsumption{requestId=Validation.required(requestId,"requestId");customerId=Validation.required(customerId,"customerId");instanceId=Validation.required(instanceId,"instanceId");meterId=Validation.required(meterId,"meterId");if(amount<=0||periodStart==null||decidedAt==null)throw new IllegalArgumentException("invalid usage consumption");dimensions=dimensions==null?Map.of():Map.copyOf(dimensions);}
}
