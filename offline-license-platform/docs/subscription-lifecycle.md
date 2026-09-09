# Subscription Lifecycle

Before `validFrom`, decisions return LICENSE_NOT_YET_VALID. Through `validUntil`, normal policy applies. During the
default seven-day restricted grace, existing registered instances, prior assigned/active users, and previously
registered agents may continue within limits; new registrations and assignments are denied and allowed decisions return
LICENSE_GRACE_PERIOD. After grace, capabilities are denied as LICENSE_EXPIRED.

Trusted time records the highest accepted wall clock. Moving backward by more than five minutes returns
CLOCK_ROLLBACK_DETECTED and denies allocations, usage, and new cached certificates. This detects ordinary rollback but
cannot fully resist a root administrator controlling every state copy.

Renewal for the same customer/server may retain activation. Current-month AEU usage never resets. If new limits are
below current utilization, existing state remains visible, new allocation is denied until below the new limit, leases
expire normally, and removed entitlements fail on the next check.
