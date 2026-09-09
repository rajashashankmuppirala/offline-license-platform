package com.enterprise.licensing.client.lease;

import com.enterprise.licensing.client.LicenseClient;
import com.enterprise.licensing.model.AgentExecutionLease;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Maintains one agent execution lease and never extends it locally during an outage. */
public final class AgentLeaseManager implements AutoCloseable {
    private final LicenseClient client; private final Clock clock;
    private final ScheduledExecutorService scheduler; private final AtomicReference<AgentExecutionLease> lease;
    public AgentLeaseManager(LicenseClient client,AgentExecutionLease initial,Duration heartbeatEvery,Clock clock){this.client=Objects.requireNonNull(client);this.clock=Objects.requireNonNull(clock);lease=new AtomicReference<>(Objects.requireNonNull(initial));if(heartbeatEvery.isZero()||heartbeatEvery.isNegative())throw new IllegalArgumentException("heartbeatEvery must be positive");scheduler=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("license-agent-heartbeat").factory());scheduler.scheduleWithFixedDelay(this::heartbeat,heartbeatEvery.toMillis(),heartbeatEvery.toMillis(),TimeUnit.MILLISECONDS);}
    public boolean valid(){var current=lease.get();return current!=null&&current.activeAt(clock.instant());}
    public AgentExecutionLease current(){if(!valid())throw new IllegalStateException("agent execution lease is no longer valid");return lease.get();}
    private void heartbeat(){var current=lease.get();if(current==null||!current.activeAt(clock.instant()))return;try{lease.set(client.heartbeatAgentExecution(current.leaseId()));}catch(RuntimeException ignored){}}
    @Override public void close(){scheduler.shutdownNow();var current=lease.getAndSet(null);if(current!=null)try{client.releaseAgentExecution(current.leaseId());}catch(RuntimeException ignored){}}
}
