package com.enterprise.licensing.example.tomcat;

import com.enterprise.licensing.client.LicenseClient;
import com.enterprise.licensing.client.lease.UserLeaseManager;
import com.enterprise.licensing.model.AccountType;

import java.time.Clock;
import java.time.Duration;

/**
 * Framework-neutral lifecycle core that a ServletContextListener delegates to.
 */
public final class LicenseLifecycleListener implements AutoCloseable {
    private final LicenseClient client;
    private UserLeaseManager lease;

    public LicenseLifecycleListener(LicenseClient client) {
        this.client = client;
    }

    public void login(String userId, String sessionId) {
        if (lease != null) throw new IllegalStateException("session already licensed");
        lease = new UserLeaseManager(client, client.acquireUser(userId, AccountType.HUMAN, sessionId), Duration.ofMinutes(1), Clock.systemUTC());
    }

    public boolean sessionLicensed() {
        return lease != null && lease.valid();
    }

    @Override
    public void close() {
        if (lease != null) {
            lease.close();
            lease = null;
        }
    }
}
