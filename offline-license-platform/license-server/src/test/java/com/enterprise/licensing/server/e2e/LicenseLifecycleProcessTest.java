package com.enterprise.licensing.server.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.client.DefaultLicenseClient;
import com.enterprise.licensing.client.TransportSecurityMode;
import com.enterprise.licensing.client.identity.ProductInstanceIdentityStore;
import com.enterprise.licensing.client.transport.JdkLicenseTransport;
import com.enterprise.licensing.crypto.CanonicalJson;
import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.crypto.vendor.ActivationSigner;
import com.enterprise.licensing.crypto.vendor.CommercialLicenseSigner;
import com.enterprise.licensing.crypto.vendor.VendorEnvelopeSigner;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.testkit.LicenseFixtures;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseLifecycleProcessTest {
    @TempDir Path temp;
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final String adminToken="process-admin-token";

    @Test void separateServerProcessPreservesCompleteAuthoritativeStateAcrossRestartWithoutVendorNetwork() throws Exception {
        int port=freePort();
        var vendor=Ed25519Keys.generate();
        Path vendorPublic=temp.resolve("vendor-public.key");
        Files.writeString(vendorPublic,Ed25519Keys.encodePublic(vendor.getPublic()));
        Process first=start(port,vendorPublic);
        String firstServerId;
        JsonNode utilizationBefore;
        List<JsonNode> decisionAuditsBefore;
        String userLeaseExpiry;
        String agentLeaseExpiry;
        DefaultLicenseClient client;
        try {
            awaitHealthy(port);
            var initial=data(get(port,"/api/v1/license/admin/status",true));
            assertThat(initial.path("activationState").asText()).isEqualTo("UNACTIVATED");
            firstServerId=initial.path("serverId").asText();

            var activationRequest=activationRequest(port);
            activate(port,activationRequest,vendor);
            var identity=new ProductInstanceIdentityStore(temp.resolve("product/identity.json"),mapper).loadOrCreate();
            client=new DefaultLicenseClient(URI.create("http://127.0.0.1:"+port),identity,
                    activationRequest.licenseServerId().toString(),
                    Ed25519Keys.decodePublic(activationRequest.installationPublicKey().encoded()),adminToken,
                    mapper,JdkLicenseTransport.localLoopbackTest(),temp.resolve("product/cache.bin"),Clock.systemUTC(),
                    Duration.ofSeconds(2),TransportSecurityMode.LOCAL_LOOPBACK_TEST);

            var instance=client.register(DeploymentType.KUBERNETES,EnvironmentType.PRODUCTION,ProductId.VALIDATOR,"1.0",Map.of());
            assertSuccess(postAdminNoBody(port,"/api/v1/license/admin/instances/"+instance.instanceId()+"/activate"));
            assertSuccess(postAdmin(port,"/api/v1/license/admin/users/named",
                    "{\"userId\":\"named-1\",\"accountType\":\"HUMAN\",\"productId\":\"VALIDATOR\",\"category\":null}"));
            assertThat(client.productUserCapacity(ProductId.VALIDATOR,"named-1").allowed()).isTrue();
            var productCapacity=client.productUserCapacity(ProductId.VALIDATOR,"named-2");
            assertThat(productCapacity.reasonCode()).isEqualTo(LicenseReasonCode.PRODUCT_USER_LIMIT_EXCEEDED);
            assertThat(productCapacity.diagnosticContext()).containsEntry("usedAmount","1").containsEntry("licensedLimit","1");
            var userLease=client.acquireUser("concurrent-1",AccountType.HUMAN,"session-1");
            assertSuccess(postAdmin(port,"/api/v1/license/admin/agents",
                    "{\"agentId\":\"agent-1\",\"displayName\":\"Agent One\",\"instanceId\":\""+instance.instanceId()+"\"}"));
            var agentLease=client.acquireAgentExecution("agent-1","execution-1",AiCapability.AUTONOMOUS_AGENT);
            assertThat(client.consumeAction("AI_EXECUTION_UNIT","INTERACTIVE_AI_REQUEST",Map.of()).usedAmount()).isEqualTo(1);
            client.refreshCachedEntitlement();

            utilizationBefore=data(get(port,"/api/v1/license/admin/utilization",true));
            var auditBefore=data(get(port,"/api/v1/license/admin/audit?limit=100",true));
            decisionAuditsBefore=decisionAudits(auditBefore);
            userLeaseExpiry=userLease.expiresAt().toString();
            agentLeaseExpiry=agentLease.expiresAt().toString();
            assertPopulated(utilizationBefore,userLeaseExpiry,agentLeaseExpiry);
            assertThat(auditBefore.size()).isGreaterThanOrEqualTo(8);
            assertThat(rowCount("cached_entitlement_issue")).isEqualTo(1);
        } finally {
            stop(first);
        }

        assertThat(Files.walk(temp).filter(Files::isRegularFile).map(path->path.getFileName().toString()))
                .noneMatch(name->name.contains("private")||name.contains("seed"));
        Process restarted=start(port,vendorPublic);
        try {
            awaitHealthy(port);
            var status=data(get(port,"/api/v1/license/admin/status",true));
            assertThat(status.path("serverId").asText()).isEqualTo(firstServerId);
            assertThat(status.path("activationState").asText()).isEqualTo("ACTIVE");
            assertThat(status.path("recoveryState").asText()).isEqualTo("ACTIVE");
            assertThat(status.path("reasonCode").asText()).isEqualTo("LICENSE_VALID");

            var utilizationAfter=data(get(port,"/api/v1/license/admin/utilization",true));
            assertPopulated(utilizationAfter,userLeaseExpiry,agentLeaseExpiry);
            assertThat(utilizationAfter).as("restart must not mutate or extend persisted leases").isEqualTo(utilizationBefore);
            assertThat(decisionAudits(data(get(port,"/api/v1/license/admin/audit?limit=100",true))))
                    .as("all pre-restart domain-decision audits must survive in exact order")
                    .startsWith(decisionAuditsBefore.toArray(JsonNode[]::new));
            assertThat(data(get(port,"/api/v1/license/admin/diagnostics",true)).path("auditChainValid").asBoolean()).isTrue();
            assertThat(rowCount("cached_entitlement_issue")).isEqualTo(1);
            assertThat(client.product(ProductId.VALIDATOR).reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_VALID);
            assertThat(get(port,"/actuator/health/liveness",false).statusCode()).isEqualTo(200);
            assertThat(get(port,"/actuator/health/readiness",false).statusCode()).isEqualTo(200);
        } finally {
            stop(restarted);
        }
    }

    private void assertPopulated(JsonNode utilization,String userExpiry,String agentExpiry) {
        assertThat(utilization.path("instances")).hasSize(1);
        assertThat(utilization.path("instances").get(0).path("state").asText()).isEqualTo("ACTIVE");
        assertThat(utilization.path("namedUsers")).hasSize(1);
        assertThat(utilization.path("activeConcurrentUsers")).hasSize(1);
        assertThat(utilization.path("activeConcurrentUsers").get(0).path("expiresAt").asText()).isEqualTo(userExpiry);
        assertThat(utilization.path("registeredAgents")).hasSize(1);
        assertThat(utilization.path("activeAgentExecutions")).hasSize(1);
        assertThat(utilization.path("activeAgentExecutions").get(0).path("expiresAt").asText()).isEqualTo(agentExpiry);
        assertThat(utilization.path("meters")).hasSize(1);
        assertThat(utilization.path("meters").get(0).path("usedAmount").asInt()).isEqualTo(1);
    }

    private ActivationRequest activationRequest(int port)throws Exception {
        var response=postAdmin(port,"/api/v1/license/admin/activation/request",
                "{\"licenseId\":\"LIC-AINC-2026-001\",\"customerId\":\"AINC\",\"environmentEvidence\":{}}");
        assertSuccess(response);
        return mapper.treeToValue(mapper.readTree(response.body()).path("data"),ActivationRequest.class);
    }

    private void activate(int port,ActivationRequest request,java.security.KeyPair vendor)throws Exception {
        var base=LicenseFixtures.aInc();var now=Instant.now();
        var subscription=new SubscriptionEntitlement(base.subscription().type(),base.subscription().tier(),
                now.minus(Duration.ofDays(1)),now.plus(Duration.ofDays(30)),7);
        var products=base.products().stream().map(product->product.id().equals(ProductId.VALIDATOR)
                ?new ProductEntitlement(product.id(),product.enabled(),1,product.features()):product).toList();
        var license=new CommercialLicense(base.schemaVersion(),base.licenseId(),base.customer(),subscription,base.activation(),
                base.environments(),base.users(),products,base.ai(),now,1,base.activeProductionClassLimit(),base.revocationIds());
        var signer=new VendorEnvelopeSigner(new CanonicalJson(mapper));
        var licenseEnvelope=new CommercialLicenseSigner(signer).sign(license,vendor);
        var certificate=new ActivationCertificate("1.0",UUID.randomUUID().toString(),license.licenseId(),
                license.customer().customerId(),request.licenseServerId(),request.publicKeyFingerprint(),license.activation(),now);
        var activationEnvelope=new ActivationSigner(signer).sign(certificate,vendor);
        assertSuccess(postAdmin(port,"/api/v1/license/admin/activation/import",mapper.writeValueAsString(Map.of(
                "commercialLicense",licenseEnvelope,"activationCertificate",activationEnvelope))));
    }

    private long rowCount(String table)throws Exception {
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+temp.resolve("data/license.db"));
            var statement=connection.createStatement();var result=statement.executeQuery("SELECT count(*) FROM "+table)){
            return result.getLong(1);
        }
    }

    private List<JsonNode> decisionAudits(JsonNode events) {
        var values=new ArrayList<JsonNode>();
        events.forEach(event->{if(!"STATUS".equals(event.path("operation").asText()))values.add(event.deepCopy());});
        return List.copyOf(values);
    }

    private JsonNode data(HttpResponse<String> response)throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var root=mapper.readTree(response.body());
        assertThat(root.path("success").asBoolean()).as(response.body()).isTrue();
        return root.path("data");
    }
    private HttpResponse<String> postAdmin(int port,String path,String body)throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(3))
                .header("Authorization","Bearer "+adminToken).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> postAdminNoBody(int port,String path)throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(3))
                .header("Authorization","Bearer "+adminToken).POST(HttpRequest.BodyPublishers.noBody()).build();
        return HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
    }
    private void assertSuccess(HttpResponse<String> response)throws Exception {
        assertThat(mapper.readTree(response.body()).path("success").asBoolean()).as(response.body()).isTrue();
    }
    private Process start(int port,Path vendorPublic)throws Exception {
        String java=Path.of(System.getProperty("java.home"),"bin","java").toString();
        String classpath=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        var builder=new ProcessBuilder(java,"-cp",classpath,"com.enterprise.licensing.server.LicenseServerApplication",
                "--server.address=127.0.0.1","--server.port="+port,"--licensing.data-file="+temp.resolve("data/license.db"),
                "--licensing.identity-directory="+temp.resolve("identity"),
                "--licensing.checkpoint-file="+temp.resolve("identity/time.checkpoint"),
                "--licensing.vendor-public-key-file="+vendorPublic,"--licensing.admin-token="+adminToken);
        builder.environment().put("http_proxy","http://127.0.0.1:1");
        builder.environment().put("https_proxy","http://127.0.0.1:1");
        return builder.redirectErrorStream(true).redirectOutput(temp.resolve("server-"+System.nanoTime()+".log").toFile()).start();
    }
    private void awaitHealthy(int port)throws Exception {long deadline=System.nanoTime()+Duration.ofSeconds(20).toNanos();while(System.nanoTime()<deadline){try{if(get(port,"/actuator/health/liveness",false).statusCode()==200)return;}catch(Exception ignored){}Thread.sleep(100);}throw new AssertionError("server process did not become healthy");}
    private HttpResponse<String>get(int port,String path,boolean admin)throws Exception{var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(3)).GET();if(admin)request.header("Authorization","Bearer "+adminToken);return HttpClient.newHttpClient().send(request.build(),HttpResponse.BodyHandlers.ofString());}
    private static int freePort()throws Exception{try(var socket=new java.net.ServerSocket(0,1,InetAddress.getLoopbackAddress())){return socket.getLocalPort();}}
    private static void stop(Process process)throws Exception{process.destroy();if(!process.waitFor(10,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}}
}
