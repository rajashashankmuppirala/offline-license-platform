package com.enterprise.licensing.engine.user;

import com.enterprise.licensing.model.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface UserLicenseRepository {
    NamedUserResult assign(String assignmentId, String userId, AccountType accountType, ProductId productId,
                           UserCategory category, int globalLimit, int categoryLimit, int productLimit, Instant now);

    NamedUserResult release(String userId, ProductId productId, UserCategory category, Instant now,
                            Duration cooldown, boolean overrideCooldown, String overrideReason);

    List<NamedUserAssignment> namedAssignments();

    UserLeaseResult acquire(String leaseId, String userId, AccountType accountType, String instanceId,
                            String sessionId, String poolId, int globalLimit, Instant now, Duration duration);

    UserLeaseResult heartbeat(String leaseId, Instant now, Duration duration);

    UserLeaseResult releaseLease(String leaseId, Instant now);

    List<ConcurrentUserLease> userLeases();
}
