package com.enterprise.licensing.engine.entitlement;

import com.enterprise.licensing.engine.activation.LicenseRepository;
import com.enterprise.licensing.engine.instance.ProductInstanceRepository;
import com.enterprise.licensing.engine.ports.*;
import com.enterprise.licensing.engine.user.UserLicenseRepository;
import com.enterprise.licensing.engine.validation.*;
import com.enterprise.licensing.model.*;

import java.util.Map;

public final class EntitlementService {
    private final LicenseRepository licenses;
    private final ProductInstanceRepository instances;
    private final UserLicenseRepository users;
    private final TimeSource time;
    private final IdGenerator ids;
    private final OperationAuthorizer validation;

    public EntitlementService(LicenseRepository l, ProductInstanceRepository i, TimeSource t, IdGenerator ids, OperationAuthorizer v) {
        this(l, i, null, t, ids, v);
    }

    public EntitlementService(LicenseRepository l, ProductInstanceRepository i, UserLicenseRepository users, TimeSource t, IdGenerator ids, OperationAuthorizer v) {
        licenses = l;
        instances = i;
        this.users = users;
        time = t;
        this.ids = ids;
        validation = v;
    }

    public LicenseDecision checkProduct(String instanceId, ProductId productId, String correlationId) {
        var status = validation.evaluate(LicenseOperation.PRODUCT_CHECK);
        if (!status.allowed())
            return decision(false, status.reasonCode(), LicenseOperation.PRODUCT_CHECK, correlationId, Map.of());
        var base = validateInstance(instanceId, productId, LicenseOperation.PRODUCT_CHECK, correlationId);
        if (base != null) return base;
        var state = licenses.active().orElseThrow();
        boolean allowed = state.license().products().stream().anyMatch(p -> p.id().equals(productId) && p.enabled());
        return decision(allowed, allowed ? status.reasonCode() : LicenseReasonCode.PRODUCT_NOT_ENTITLED, LicenseOperation.PRODUCT_CHECK, correlationId, Map.of("productId", productId.value()));
    }

    public LicenseDecision checkFeature(String instanceId, ProductId productId, FeatureId featureId, String correlationId) {
        var status = validation.evaluate(LicenseOperation.FEATURE_CHECK);
        if (!status.allowed())
            return decision(false, status.reasonCode(), LicenseOperation.FEATURE_CHECK, correlationId, Map.of());
        var product = checkProduct(instanceId, productId, correlationId);
        if (!product.allowed())
            return new LicenseDecision(false, product.reasonCode(), LicenseOperation.FEATURE_CHECK, product.metadata(), product.diagnosticContext());
        var normalized = LegacyFeatureCatalog.normalize(productId, featureId);
        var state = licenses.active().orElseThrow();
        boolean allowed = state.license().products().stream().filter(p -> p.id().equals(productId) && p.enabled()).anyMatch(p -> p.features().contains(normalized));
        return decision(allowed, allowed ? status.reasonCode() : LicenseReasonCode.FEATURE_NOT_ENTITLED, LicenseOperation.FEATURE_CHECK, correlationId, Map.of("productId", productId.value(), "featureId", normalized.value()));
    }

    public LicenseDecision checkProductUserCapacity(String instanceId, ProductId productId, String userId, String correlationId) {
        var operation = LicenseOperation.PRODUCT_USER_CAPACITY_CHECK;
        var status = validation.evaluate(operation);
        if (!status.allowed()) return decision(false, status.reasonCode(), operation, correlationId, Map.of());
        var base = validateInstance(instanceId, productId, operation, correlationId);
        if (base != null) return base;
        var product = licenses.active().orElseThrow().license().products().stream().filter(p -> p.id().equals(productId) && p.enabled()).findFirst().orElse(null);
        if (product == null)
            return decision(false, LicenseReasonCode.PRODUCT_NOT_ENTITLED, operation, correlationId, Map.of("productId", productId.value()));
        if (users == null)
            return decision(false, LicenseReasonCode.INTERNAL_ERROR, operation, correlationId, Map.of("productId", productId.value()));
        var assignments = users.namedAssignments();
        boolean existing = assignments.stream().anyMatch(a -> a.userId().equals(userId) && productId.equals(a.productId()) && a.countsAt(time.now()));
        long used = assignments.stream().filter(a -> productId.equals(a.productId()) && a.countsAt(time.now())).count();
        boolean allowed = existing || used < product.maxUsers();
        return decision(allowed, allowed ? status.reasonCode() : LicenseReasonCode.PRODUCT_USER_LIMIT_EXCEEDED, operation, correlationId, Map.of("productId", productId.value(), "userId", userId, "usedAmount", Long.toString(used), "licensedLimit", Integer.toString(product.maxUsers())));
    }

    public void requireProduct(String instanceId, ProductId productId, String correlationId) {
        var d = checkProduct(instanceId, productId, correlationId);
        if (!d.allowed()) throw new LicenseDeniedException(d);
    }

    public void requireFeature(String instanceId, ProductId productId, FeatureId featureId, String correlationId) {
        var d = checkFeature(instanceId, productId, featureId, correlationId);
        if (!d.allowed()) throw new LicenseDeniedException(d);
    }

    private LicenseDecision validateInstance(String id, ProductId productId, LicenseOperation op, String correlation) {
        if (licenses.active().isEmpty())
            return decision(false, LicenseReasonCode.LICENSE_SERVER_NOT_ACTIVATED, op, correlation, Map.of());
        var instance = instances.find(id).orElse(null);
        if (instance == null || instance.state() != InstanceState.ACTIVE)
            return decision(false, LicenseReasonCode.INSTANCE_NOT_REGISTERED, op, correlation, Map.of("instanceId", id));
        if (!instance.productId().equals(productId))
            return decision(false, LicenseReasonCode.PRODUCT_NOT_ENTITLED, op, correlation, Map.of("instanceId", id, "productId", productId.value()));
        return null;
    }

    private LicenseDecision decision(boolean allowed, LicenseReasonCode reason, LicenseOperation op, String correlation, Map<String, String> context) {
        return new LicenseDecision(allowed, reason, op, new DecisionMetadata(ids.next(), time.now(), correlation), context);
    }
}
