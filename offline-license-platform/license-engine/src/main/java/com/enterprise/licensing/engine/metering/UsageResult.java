package com.enterprise.licensing.engine.metering;

import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.model.UsageCounter;

public record UsageResult(boolean accepted,LicenseReasonCode reasonCode,UsageCounter counter){public static UsageResult accepted(UsageCounter c){return new UsageResult(true,LicenseReasonCode.LICENSE_VALID,c);}public static UsageResult denied(LicenseReasonCode r,UsageCounter c){return new UsageResult(false,r,c);}}
