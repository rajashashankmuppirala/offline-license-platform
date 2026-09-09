package com.enterprise.licensing.example.product;

import com.enterprise.licensing.client.DefaultLicenseClient;
import com.enterprise.licensing.client.LicenseClient;
import com.enterprise.licensing.client.LicenseClientDeniedException;
import com.enterprise.licensing.client.TransportSecurityMode;
import com.enterprise.licensing.client.identity.ProductInstanceIdentityStore;
import com.enterprise.licensing.client.lease.AgentLeaseManager;
import com.enterprise.licensing.client.lease.UserLeaseManager;
import com.enterprise.licensing.client.transport.JdkLicenseTransport;
import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

/** A runnable example whose business code sees typed decisions and leases, never license JSON. */
public final class SampleProductApplication {
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofMinutes(1);
    private final LicenseClient licenses;

    public SampleProductApplication(LicenseClient licenses) {
        this.licenses = licenses;
    }

    public LicenseDecision tableau() {
        return licenses.feature(ProductId.VALIDATOR, new FeatureId("TABLEAU"));
    }

    public UserLeaseManager login(String user, String session) {
        return new UserLeaseManager(licenses, licenses.acquireUser(user, AccountType.HUMAN, session),
                HEARTBEAT_INTERVAL, Clock.systemUTC());
    }

    public AgentLeaseManager autonomousRun(String agent, String execution) {
        return new AgentLeaseManager(licenses,
                licenses.acquireAgentExecution(agent, execution, AiCapability.AUTONOMOUS_AGENT),
                HEARTBEAT_INTERVAL, Clock.systemUTC());
    }

    public UsageCounter recordInteractiveAi(String requestId) {
        return licenses.consumeAction(requestId, "AI_EXECUTION_UNIT", "INTERACTIVE_AI_REQUEST",
                Map.of("source", "sample-product"));
    }

    /** Exercises every documented integration path and throws on the first denied or invalid result. */
    public void exercise(String user, String session, String agent, String execution,
                         String requestId, PrintStream out) {
        Object activationState = licenses.status().get("activationState");
        require("ACTIVE".equals(String.valueOf(activationState)), "License Server is not ACTIVE");
        out.println("STATUS ACTIVE");

        RegisteredProductInstance instance = licenses.register(
                DeploymentType.OTHER, EnvironmentType.PRODUCTION, ProductId.VALIDATOR,
                "1.0.0", Map.of("application", "sample-product"));
        require(instance.state() == InstanceState.ACTIVE, "Sample product instance is not ACTIVE");
        out.println("INSTANCE ACTIVE " + instance.instanceId());

        requireAllowed(licenses.product(ProductId.VALIDATOR), "VALIDATOR product");
        out.println("PRODUCT VALIDATOR LICENSE_VALID");
        requireAllowed(tableau(), "TABLEAU feature");
        out.println("FEATURE TABLEAU LICENSE_VALID");
        requireAllowed(licenses.productUserCapacity(ProductId.VALIDATOR, user), "VALIDATOR user capacity");

        try (UserLeaseManager userLease = login(user, session)) {
            require(userLease.valid(), "User lease was not active after acquisition");
            out.println("USER_LEASE ACTIVE " + userLease.current().leaseId());
            try (AgentLeaseManager agentLease = autonomousRun(agent, execution)) {
                require(agentLease.valid(), "Agent execution lease was not active after acquisition");
                out.println("AI_EXECUTION_LEASE ACTIVE " + agentLease.current().leaseId());
                UsageCounter counter = recordInteractiveAi(requestId);
                require(counter.usedAmount() <= counter.licensedLimit(), "AI usage exceeded its licensed limit");
                out.println("AI_METER ACCEPTED " + counter.usedAmount() + "/" + counter.licensedLimit());
            }
        }
        out.println("SAMPLE_PRODUCT_FLOW COMPLETE");
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> environment = System.getenv();
        URI server = URI.create(required(environment, "LICENSE_SERVER_URL"));
        require(isLoopbackHttp(server), "The local sample runner only permits loopback HTTP endpoints");

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String identityFile = required(environment, "SAMPLE_PRODUCT_IDENTITY_FILE");
        ActivationRequest activation = mapper.readValue(
                Path.of(required(environment, "LICENSE_SERVER_ACTIVATION_REQUEST_FILE")).toFile(),
                ActivationRequest.class);
        var identity = new ProductInstanceIdentityStore(Path.of(identityFile), mapper).loadOrCreate();
        Path cache = Path.of(environment.getOrDefault("SAMPLE_PRODUCT_CACHE_FILE",
                Path.of(identityFile).resolveSibling("entitlement-cache.json").toString()));
        LicenseClient client = new DefaultLicenseClient(
                server, identity, activation.licenseServerId().toString(),
                Ed25519Keys.decodePublic(activation.installationPublicKey().encoded()),
                required(environment, "LICENSE_SERVER_ADMIN_TOKEN"), mapper,
                JdkLicenseTransport.localLoopbackTest(), cache, Clock.systemUTC(), Duration.ofSeconds(5),
                TransportSecurityMode.LOCAL_LOOPBACK_TEST);

        new SampleProductApplication(client).exercise(
                environment.getOrDefault("SAMPLE_PRODUCT_USER", "sample-user"),
                environment.getOrDefault("SAMPLE_PRODUCT_SESSION", "sample-session"),
                environment.getOrDefault("SAMPLE_PRODUCT_AGENT", "sample-agent"),
                environment.getOrDefault("SAMPLE_PRODUCT_EXECUTION", "sample-execution"),
                environment.getOrDefault("SAMPLE_PRODUCT_REQUEST_ID", "sample-ai-request"), System.out);
    }

    private static void requireAllowed(LicenseDecision decision, String subject) {
        if (!decision.allowed()) throw new LicenseClientDeniedException(decision.reasonCode());
        require(decision.reasonCode() == LicenseReasonCode.LICENSE_VALID,
                subject + " returned an unexpected reason code");
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Required environment variable is missing: " + name);
        return value;
    }

    private static boolean isLoopbackHttp(URI uri) {
        String host = uri.getHost();
        return "http".equalsIgnoreCase(uri.getScheme())
                && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
