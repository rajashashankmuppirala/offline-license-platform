package com.enterprise.licensing.engine.user;

import com.enterprise.licensing.engine.activation.LicenseRepository;
import com.enterprise.licensing.engine.ports.IdGenerator;
import com.enterprise.licensing.engine.ports.TimeSource;
import com.enterprise.licensing.model.*;
import java.time.Duration;
import com.enterprise.licensing.engine.validation.OperationAuthorizer;
import com.enterprise.licensing.engine.instance.ProductInstanceRepository;

public final class UserLicenseService {
    private final LicenseRepository licenses; private final UserLicenseRepository users; private final TimeSource time;
    private final IdGenerator ids; private final Duration leaseDuration; private final Duration reassignmentCooldown;
    private final OperationAuthorizer authorizer;
    private final ProductInstanceRepository instances;
    public UserLicenseService(LicenseRepository licenses, UserLicenseRepository users, TimeSource time, IdGenerator ids,
                              Duration leaseDuration, Duration reassignmentCooldown,OperationAuthorizer authorizer,
                              ProductInstanceRepository instances) {
        this.licenses=licenses; this.users=users; this.time=time; this.ids=ids; this.leaseDuration=leaseDuration; this.reassignmentCooldown=reassignmentCooldown;this.authorizer=authorizer;this.instances=instances;
    }
    public NamedUserResult assignNamed(String userId, AccountType accountType, ProductId productId, UserCategory category) {
        var authorization=authorizer.evaluate(LicenseOperation.NAMED_USER_ASSIGN);if(!authorization.allowed())return NamedUserResult.denied(authorization.reasonCode());
        if (accountType != AccountType.HUMAN) return NamedUserResult.denied(LicenseReasonCode.ACCOUNT_NOT_HUMAN);
        var state=licenses.active().orElse(null); if(state==null) return NamedUserResult.denied(LicenseReasonCode.LICENSE_SERVER_NOT_ACTIVATED);
        int categoryLimit = category == null ? Integer.MAX_VALUE : state.license().users().categories().stream()
                .filter(c -> c.category()==category).mapToInt(UserCategoryEntitlement::limit).findFirst().orElse(0);
        int productLimit = productId == null ? Integer.MAX_VALUE : state.license().products().stream()
                .filter(p -> p.id().equals(productId) && p.enabled()).mapToInt(ProductEntitlement::maxUsers).findFirst().orElse(0);
        return users.assign(ids.next().toString(), userId, accountType, productId, category,
                state.license().users().maxNamedUsers(), categoryLimit, productLimit, time.now());
    }
    public NamedUserResult releaseNamed(String userId, ProductId productId, UserCategory category, boolean override, String reason) {
        if (override) return NamedUserResult.denied(LicenseReasonCode.INVALID_REQUEST);
        return users.release(userId, productId, category, time.now(), reassignmentCooldown, false, reason);
    }
    public NamedUserResult releaseNamed(String userId, ProductId productId, UserCategory category, NamedSeatOverride override) {
        if (override == null || !override.completeFor(userId)) return NamedUserResult.denied(LicenseReasonCode.INVALID_REQUEST);
        return users.release(userId, productId, category, time.now(), reassignmentCooldown, true, override.type());
    }
    public UserLeaseResult acquireLease(String userId, AccountType accountType, String instanceId, String sessionId) {
        var authorization=authorizer.evaluate(LicenseOperation.USER_LEASE_ACQUIRE);if(!authorization.allowed())return UserLeaseResult.denied(authorization.reasonCode());
        var instance=instances.find(instanceId).orElse(null);if(instance==null||instance.state()!=InstanceState.ACTIVE)return UserLeaseResult.denied(LicenseReasonCode.INSTANCE_NOT_REGISTERED);
        if(accountType!=AccountType.HUMAN) return UserLeaseResult.denied(LicenseReasonCode.ACCOUNT_NOT_HUMAN);
        var state=licenses.active().orElse(null); if(state==null) return UserLeaseResult.denied(LicenseReasonCode.LICENSE_SERVER_NOT_ACTIVATED);
        var now=time.now();boolean grace=now.isAfter(state.license().subscription().validUntil());
        if(grace&&users.userLeases().stream().noneMatch(l->l.userId().equals(userId)&&l.productInstanceId().equals(instanceId)&&!l.acquiredAt().isAfter(state.license().subscription().validUntil())))return UserLeaseResult.denied(LicenseReasonCode.LICENSE_EXPIRED);
        String poolId=state.license().environments().stream().filter(e->e.type()==instance.environmentType()).findFirst().map(EnvironmentEntitlement::poolId).orElse(EntitlementPool.CUSTOMER_WIDE);
        var result=users.acquire(ids.next().toString(),userId,accountType,instanceId,sessionId,poolId,state.license().users().maxConcurrentUsers(),now,leaseDuration);
        return grace&&result.accepted()?UserLeaseResult.accepted(result.lease(),LicenseReasonCode.LICENSE_GRACE_PERIOD):result;
    }
    public UserLeaseResult heartbeat(String instanceId,String leaseId){var authorization=authorizer.evaluate(LicenseOperation.USER_LEASE_HEARTBEAT);if(!authorization.allowed())return UserLeaseResult.denied(authorization.reasonCode());if(users.userLeases().stream().noneMatch(l->l.leaseId().equals(leaseId)&&l.productInstanceId().equals(instanceId)))return UserLeaseResult.denied(LicenseReasonCode.USER_LEASE_INVALID);return users.heartbeat(leaseId,time.now(),leaseDuration); }
    public UserLeaseResult releaseLease(String instanceId,String leaseId){if(users.userLeases().stream().noneMatch(l->l.leaseId().equals(leaseId)&&l.productInstanceId().equals(instanceId)))return UserLeaseResult.denied(LicenseReasonCode.USER_LEASE_INVALID);return users.releaseLease(leaseId,time.now()); }
}
