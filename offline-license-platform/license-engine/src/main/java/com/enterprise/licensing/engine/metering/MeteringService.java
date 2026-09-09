package com.enterprise.licensing.engine.metering;

import com.enterprise.licensing.engine.activation.LicenseRepository;
import com.enterprise.licensing.engine.ports.IdGenerator;
import com.enterprise.licensing.engine.ports.TimeSource;
import com.enterprise.licensing.model.*;

import java.time.*;
import java.util.Map;

import com.enterprise.licensing.engine.validation.OperationAuthorizer;
import com.enterprise.licensing.engine.instance.ProductInstanceRepository;

public final class MeteringService {
    private final LicenseRepository licenses;
    private final MeteringRepository meters;
    private final TimeSource time;
    private final IdGenerator ids;
    private final OperationAuthorizer authorizer;
    private final ProductInstanceRepository instances;
    private final AEUPolicy aeuPolicy;

    public MeteringService(LicenseRepository l, MeteringRepository m, TimeSource t, IdGenerator i, OperationAuthorizer v, ProductInstanceRepository p, AEUPolicy a) {
        licenses = l;
        meters = m;
        time = t;
        ids = i;
        authorizer = v;
        instances = p;
        aeuPolicy = a;
    }

    static Window window(MeterPeriod period, Instant at) {
        if (period != MeterPeriod.MONTHLY) throw new IllegalArgumentException("Unsupported meter period");
        var date = at.atZone(ZoneOffset.UTC);
        var start = date.withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        return new Window(start, start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant());
    }

    public UsageResult consumeAction(String instanceId, String meterId, String actionType, Map<String, String> dimensions) {
        return consumeAction(ids.next().toString(), instanceId, meterId, actionType, dimensions);
    }

    public UsageResult consumeAction(String requestId, String instanceId, String meterId, String actionType, Map<String, String> dimensions) {
        var authorization = authorizer.evaluate(LicenseOperation.USAGE_CONSUME);
        if (!authorization.allowed()) return UsageResult.denied(authorization.reasonCode(), null);
        if (requestId == null || requestId.isBlank() || actionType == null || actionType.isBlank())
            return UsageResult.denied(LicenseReasonCode.INVALID_REQUEST, null);
        final long amount;
        try {
            amount = aeuPolicy.unitsFor(actionType);
        } catch (IllegalArgumentException e) {
            return UsageResult.denied(LicenseReasonCode.INVALID_REQUEST, null);
        }
        var state = licenses.active().orElse(null);
        if (state == null) return UsageResult.denied(LicenseReasonCode.LICENSE_SERVER_NOT_ACTIVATED, null);
        if (!state.license().ai().enabled()) return UsageResult.denied(LicenseReasonCode.AI_NOT_ENTITLED, null);
        var instance = instances.find(instanceId).orElse(null);
        if (instance == null || instance.state() != InstanceState.ACTIVE)
            return UsageResult.denied(LicenseReasonCode.INSTANCE_NOT_REGISTERED, null);
        var entitlement = state.license().ai().meters().stream().filter(m -> m.id().equals(meterId)).findFirst().orElse(null);
        if (entitlement == null) return UsageResult.denied(LicenseReasonCode.AI_NOT_ENTITLED, null);
        String environmentPool = state.license().environments().stream().filter(e -> e.type() == instance.environmentType()).findFirst().map(EnvironmentEntitlement::poolId).orElse(EntitlementPool.CUSTOMER_WIDE);
        String pool = EntitlementPool.CUSTOMER_WIDE.equals(entitlement.poolId()) ? environmentPool : entitlement.poolId();
        var now = time.now();
        var window = window(entitlement.period(), now);
        return meters.consume(requestId, state.license().customer().customerId(), instanceId, pool, meterId, actionType, amount, window.start, window.end, entitlement.limit(), now, dimensions == null ? Map.of() : dimensions);
    }

    record Window(Instant start, Instant end) {
    }
}
