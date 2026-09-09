package com.enterprise.licensing.model;

import java.time.Instant;

public record UsageCounter(String customerId,String poolId,String meterId,Instant periodStart,Instant periodEnd,long usedAmount,long licensedLimit){
    public UsageCounter(String customerId,String meterId,Instant periodStart,Instant periodEnd,long usedAmount,long licensedLimit){this(customerId,EntitlementPool.CUSTOMER_WIDE,meterId,periodStart,periodEnd,usedAmount,licensedLimit);}
    public UsageCounter{customerId=Validation.required(customerId,"customerId");poolId=EntitlementPool.normalize(poolId);meterId=Validation.required(meterId,"meterId");if(periodStart==null||periodEnd==null||usedAmount<0||licensedLimit<0)throw new IllegalArgumentException("invalid usage counter");}
}
