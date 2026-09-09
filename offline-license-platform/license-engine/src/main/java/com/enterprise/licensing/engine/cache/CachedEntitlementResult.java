package com.enterprise.licensing.engine.cache;
import com.enterprise.licensing.model.*;
public record CachedEntitlementResult(boolean accepted,LicenseReasonCode reasonCode,CachedEntitlementCertificate certificate,SignedEnvelope envelope){public static CachedEntitlementResult accepted(CachedEntitlementCertificate c,SignedEnvelope e){return new CachedEntitlementResult(true,LicenseReasonCode.LICENSE_VALID,c,e);}public static CachedEntitlementResult denied(LicenseReasonCode r){return new CachedEntitlementResult(false,r,null,null);}}
