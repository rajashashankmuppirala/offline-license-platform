package com.enterprise.licensing.engine.entitlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.engine.activation.ActiveLicenseState;
import com.enterprise.licensing.engine.activation.LicenseRepository;
import com.enterprise.licensing.engine.instance.InstanceRegistrationResult;
import com.enterprise.licensing.engine.instance.ProductInstanceRepository;
import com.enterprise.licensing.engine.ports.IdGenerator;
import com.enterprise.licensing.engine.user.*;
import com.enterprise.licensing.engine.validation.LicenseValidationResult;
import com.enterprise.licensing.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductEntitlementServiceTest {
    static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void enabledProductRequiresAMatchingRegisteredInstance() {
        var service = service(ProductId.VALIDATOR);
        assertThat(service.checkProduct("instance", ProductId.VALIDATOR, "c").allowed()).isTrue();
        assertThat(service.checkProduct("missing", ProductId.VALIDATOR, "c").reasonCode())
                .isEqualTo(LicenseReasonCode.INSTANCE_NOT_REGISTERED);
        assertThat(service(ProductId.DATA_MANAGER).checkProduct("instance", ProductId.VALIDATOR, "c").reasonCode())
                .isEqualTo(LicenseReasonCode.PRODUCT_NOT_ENTITLED);
    }

    @Test
    void unknownAndDisabledProductsAreDenied() {
        assertThat(service(ProductId.VALIDATOR).checkProduct("instance", new ProductId("UNKNOWN"), "c").reasonCode())
                .isEqualTo(LicenseReasonCode.PRODUCT_NOT_ENTITLED);
        assertThat(service(new ProductId("DATA_FLOW")).checkProduct("instance", new ProductId("DATA_FLOW"), "c").reasonCode())
                .isEqualTo(LicenseReasonCode.PRODUCT_NOT_ENTITLED);
    }

    @Test
    void registeredAndStandbyInstancesCannotServeEntitlements() {
        assertThat(service(ProductId.VALIDATOR, InstanceState.REGISTERED).checkProduct("instance", ProductId.VALIDATOR, "c").reasonCode())
                .isEqualTo(LicenseReasonCode.INSTANCE_NOT_REGISTERED);
        assertThat(service(ProductId.VALIDATOR, InstanceState.STANDBY).checkFeature("instance", ProductId.VALIDATOR, new FeatureId("TABLEAU"), "c").reasonCode())
                .isEqualTo(LicenseReasonCode.INSTANCE_NOT_REGISTERED);
    }

    @Test void productUserCapacityIsStableRegisteredInstanceDecisionWithExactContext(){
        var assignment=new NamedUserAssignment("a","existing",AccountType.HUMAN,ProductId.VALIDATOR,null,NOW,null,null);
        UserLicenseRepository users=new UserLicenseRepository(){
            public List<NamedUserAssignment> namedAssignments(){return List.of(assignment);}
            public NamedUserResult assign(String a,String b,AccountType c,ProductId d,UserCategory e,int f,int g,int h,Instant i){throw new UnsupportedOperationException();}
            public NamedUserResult release(String a,ProductId b,UserCategory c,Instant d,java.time.Duration e,boolean f,String g){throw new UnsupportedOperationException();}
            public UserLeaseResult acquire(String a,String b,AccountType c,String d,String e,String f,int g,Instant h,java.time.Duration i){throw new UnsupportedOperationException();}
            public UserLeaseResult heartbeat(String a,Instant b,java.time.Duration c){throw new UnsupportedOperationException();}
            public UserLeaseResult releaseLease(String a,Instant b){throw new UnsupportedOperationException();}
            public List<ConcurrentUserLease> userLeases(){return List.of();}
        };
        var service=new EntitlementService(licenses(),instances(ProductId.VALIDATOR),users,()->NOW,IdGenerator.random(),ignored->LicenseValidationResult.allowed(false));
        var denied=service.checkProductUserCapacity("instance",ProductId.VALIDATOR,"next","correlation");
        assertThat(denied.allowed()).isFalse();assertThat(denied.reasonCode()).isEqualTo(LicenseReasonCode.PRODUCT_USER_LIMIT_EXCEEDED);
        assertThat(denied.operation()).isEqualTo(LicenseOperation.PRODUCT_USER_CAPACITY_CHECK);
        assertThat(denied.diagnosticContext()).containsEntry("usedAmount","1").containsEntry("licensedLimit","1").containsEntry("productId","VALIDATOR");
        assertThat(service.checkProductUserCapacity("instance",ProductId.VALIDATOR,"existing","c").allowed()).isTrue();
        assertThat(service.checkProductUserCapacity("missing",ProductId.VALIDATOR,"next","c").reasonCode()).isEqualTo(LicenseReasonCode.INSTANCE_NOT_REGISTERED);
    }

    static EntitlementService service(ProductId instanceProduct) {
        return service(instanceProduct, InstanceState.ACTIVE);
    }

    static EntitlementService service(ProductId instanceProduct, InstanceState state) {
        return new EntitlementService(licenses(), instances(instanceProduct, state), () -> NOW, IdGenerator.random(),
                ignored -> LicenseValidationResult.allowed(false));
    }

    static LicenseRepository licenses() {
        var state = new ActiveLicenseState(license(), null, null, null, NOW);
        return new LicenseRepository() {
            @Override public Optional<ActiveLicenseState> active() { return Optional.of(state); }
            @Override public void activate(LicenseServerIdentity i, CommercialLicense l, SignedEnvelope le,
                                           ActivationCertificate a, SignedEnvelope ae, Instant at) { }
            @Override public boolean isRevoked(String type, String id, Instant at) { return false; }
        };
    }

    static ProductInstanceRepository instances(ProductId productId) {
        return instances(productId, InstanceState.ACTIVE);
    }

    static ProductInstanceRepository instances(ProductId productId, InstanceState state) {
        var instance = new RegisteredProductInstance("instance", new EncodedPublicKey("Ed25519", "X.509", "AA"),
                "fingerprint", productId, DeploymentType.TOMCAT, EnvironmentType.PRODUCTION, "1",
                state, NOW, NOW, null, Map.of());
        return new ProductInstanceRepository() {
            @Override public Optional<RegisteredProductInstance> find(String id) {
                return "instance".equals(id) ? Optional.of(instance) : Optional.empty();
            }
            @Override public List<RegisteredProductInstance> list() { return List.of(instance); }
            @Override public InstanceRegistrationResult register(InstanceRegistrationRequest request, String fingerprint,
                                                                  int environmentLimit, int activeLimit, Instant now) {
                throw new UnsupportedOperationException();
            }
            @Override public InstanceRegistrationResult transition(String id, InstanceState expected, InstanceState target,
                                                                    int activeLimit, Instant now, boolean force) {
                throw new UnsupportedOperationException();
            }
        };
    }

    static CommercialLicense license() {
        return new CommercialLicense("1.0", "L", new CustomerIdentity("C", "Customer", "c@example.test"),
                new SubscriptionEntitlement("SUBSCRIPTION", "TEAMS", NOW.minusSeconds(60), NOW.plusSeconds(3600), 7),
                new ActivationPolicy(true, 1, true), List.of(new EnvironmentEntitlement(EnvironmentType.PRODUCTION, 2, false)),
                new UserEntitlement(5, 5, List.of()),
                List.of(new ProductEntitlement(ProductId.VALIDATOR, true, 1,
                                Set.of(new FeatureId("TABLEAU"), new FeatureId("ORACLE_ANALYTICS"), new FeatureId("COGNOS"))),
                        new ProductEntitlement(new ProductId("DATA_FLOW"), false, 0, Set.of())),
                new AIEntitlement(false, 0, 0, Set.of(), List.of()), 1, Set.of());
    }
}
