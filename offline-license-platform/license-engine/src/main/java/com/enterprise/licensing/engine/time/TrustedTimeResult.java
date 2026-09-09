package com.enterprise.licensing.engine.time;

import com.enterprise.licensing.model.LicenseReasonCode;import java.time.Instant;
public record TrustedTimeResult(boolean accepted,LicenseReasonCode reasonCode,Instant highestAcceptedTime,long sequence){}
