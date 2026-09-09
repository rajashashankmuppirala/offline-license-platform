package com.enterprise.licensing.server.e2e;

import static org.assertj.core.api.Assertions.*;

import com.enterprise.licensing.client.*;
import com.enterprise.licensing.client.identity.ProductInstanceIdentityStore;
import com.enterprise.licensing.client.transport.JdkLicenseTransport;
import com.enterprise.licensing.crypto.*;
import com.enterprise.licensing.crypto.vendor.*;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.testkit.LicenseFixtures;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseOutageRecoveryProcessTest {
    @TempDir Path temp;
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final String adminToken="outage-admin-token";

    @Test void connectedCacheOutageExpiryAndRecoveryFollowTheOperationMatrix() throws Exception {
        int port=freePort();
        var vendor=Ed25519Keys.generate();
        Path vendorPublic=temp.resolve("vendor-public.key");
        Files.writeString(vendorPublic,Ed25519Keys.encodePublic(vendor.getPublic()));
        Process server=start(port,vendorPublic);
        DefaultLicenseClient client;
        ConcurrentUserLease originalUserLease;
        try {
            awaitHealthy(port);
            var activationRequest=activationRequest(port);
            activate(port,activationRequest,vendor);

            var identity=new ProductInstanceIdentityStore(temp.resolve("product/identity.json"),mapper).loadOrCreate();
            client=new DefaultLicenseClient(URI.create("http://127.0.0.1:"+port),identity,
                    activationRequest.licenseServerId().toString(),Ed25519Keys.decodePublic(activationRequest.installationPublicKey().encoded()),adminToken,
                    mapper,JdkLicenseTransport.localLoopbackTest(),temp.resolve("product/cache.bin"),Clock.systemUTC(),
                    Duration.ofSeconds(2),TransportSecurityMode.LOCAL_LOOPBACK_TEST);
            var instance=client.register(DeploymentType.KUBERNETES,EnvironmentType.PRODUCTION,ProductId.VALIDATOR,"1.0",Map.of());
            assertThat(instance.state()).isEqualTo(InstanceState.REGISTERED);
            assertSuccess(postAdminNoBody(port,"/api/v1/license/admin/instances/"+instance.instanceId()+"/activate"));
            assertSuccess(postAdmin(port,"/api/v1/license/admin/agents","{\"agentId\":\"agent-1\",\"displayName\":\"Agent\",\"instanceId\":\""+instance.instanceId()+"\"}"));

            originalUserLease=client.acquireUser("user-1",AccountType.HUMAN,"session-1");
            assertThat(client.acquireAgentExecution("agent-1","execution-1",AiCapability.AUTONOMOUS_AGENT)).isNotNull();
            assertThat(client.consumeAction("AI_EXECUTION_UNIT","INTERACTIVE_AI_REQUEST",Map.of()).usedAmount()).isEqualTo(1);
            client.refreshCachedEntitlement();
            assertThat(client.product(ProductId.VALIDATOR).allowed()).isTrue();
        } finally {
            stop(server);
        }

        assertThat(client.product(ProductId.VALIDATOR).allowed()).isTrue();
        assertThat(client.feature(ProductId.VALIDATOR,new FeatureId("TABLEAU")).allowed()).isTrue();
        assertUnavailable(()->client.register(DeploymentType.KUBERNETES,EnvironmentType.UAT,ProductId.VALIDATOR,"1.0",Map.of()));
        assertUnavailable(()->client.acquireUser("user-2",AccountType.HUMAN,"session-2"));
        assertUnavailable(()->client.acquireAgentExecution("agent-1","execution-2",AiCapability.AUTONOMOUS_AGENT));
        assertUnavailable(()->client.consumeAction("AI_EXECUTION_UNIT","INTERACTIVE_AI_REQUEST",Map.of()));

        var expiredClient=copyWithClock(client,port,Clock.offset(Clock.systemUTC(),Duration.ofHours(25)));
        assertThat(expiredClient.product(ProductId.VALIDATOR).reasonCode()).isEqualTo(LicenseReasonCode.CACHED_ENTITLEMENT_EXPIRED);

        Process restarted=start(port,vendorPublic);
        try {
            awaitHealthy(port);
            var renewed=client.heartbeatUser(originalUserLease.leaseId());
            assertThat(renewed.expiresAt()).isAfter(originalUserLease.expiresAt());
            assertThat(client.consumeAction("AI_EXECUTION_UNIT","INTERACTIVE_AI_REQUEST",Map.of()).usedAmount())
                    .as("the denied outage call was not buffered").isEqualTo(2);
        } finally {
            stop(restarted);
        }
    }

    private DefaultLicenseClient copyWithClock(DefaultLicenseClient ignored,int port,Clock clock) {
        var identity=new ProductInstanceIdentityStore(temp.resolve("product/identity.json"),mapper).loadOrCreate();
        try {
            var request=mapper.readValue(Files.readString(temp.resolve("activation-request.json")),ActivationRequest.class);
            return new DefaultLicenseClient(URI.create("http://127.0.0.1:"+port),identity,request.licenseServerId().toString(),
                    Ed25519Keys.decodePublic(request.installationPublicKey().encoded()),adminToken,mapper,JdkLicenseTransport.localLoopbackTest(),
                    temp.resolve("product/cache.bin"),clock,Duration.ofMillis(300),TransportSecurityMode.LOCAL_LOOPBACK_TEST);
        } catch(Exception exception) { throw new IllegalStateException(exception); }
    }

    private ActivationRequest activationRequest(int port)throws Exception {
        String body="{\"licenseId\":\"LIC-AINC-2026-001\",\"customerId\":\"AINC\",\"environmentEvidence\":{}}";
        var response=postAdmin(port,"/api/v1/license/admin/activation/request",body);
        var value=mapper.treeToValue(mapper.readTree(response.body()).path("data"),ActivationRequest.class);
        Files.writeString(temp.resolve("activation-request.json"),mapper.writeValueAsString(value));
        return value;
    }

    private void activate(int port,ActivationRequest request,java.security.KeyPair vendor)throws Exception {
        var base=LicenseFixtures.aInc();var now=Instant.now();
        var subscription=new SubscriptionEntitlement(base.subscription().type(),base.subscription().tier(),
                now.minus(Duration.ofDays(1)),now.plus(Duration.ofDays(30)),7);
        var license=new CommercialLicense(base.schemaVersion(),base.licenseId(),base.customer(),subscription,base.activation(),
                base.environments(),base.users(),base.products(),base.ai(),now,1,base.activeProductionClassLimit(),base.revocationIds());
        var signer=new VendorEnvelopeSigner(new CanonicalJson(mapper));
        var licenseEnvelope=new CommercialLicenseSigner(signer).sign(license,vendor);
        var certificate=new ActivationCertificate("1.0",UUID.randomUUID().toString(),license.licenseId(),
                license.customer().customerId(),request.licenseServerId(),request.publicKeyFingerprint(),license.activation(),now);
        var activationEnvelope=new ActivationSigner(signer).sign(certificate,vendor);
        var response=postAdmin(port,"/api/v1/license/admin/activation/import",mapper.writeValueAsString(Map.of(
                "commercialLicense",licenseEnvelope,"activationCertificate",activationEnvelope)));
        assertThat(mapper.readTree(response.body()).path("success").asBoolean()).as(response.body()).isTrue();
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
    private void assertSuccess(HttpResponse<String> response)throws Exception {assertThat(mapper.readTree(response.body()).path("success").asBoolean()).as(response.body()).isTrue();}

    private Process start(int port,Path vendorPublic)throws Exception {
        String java=Path.of(System.getProperty("java.home"),"bin","java").toString();
        String classpath=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        return new ProcessBuilder(java,"-cp",classpath,"com.enterprise.licensing.server.LicenseServerApplication",
                "--server.address=127.0.0.1","--server.port="+port,"--licensing.data-file="+temp.resolve("server/license.db"),
                "--licensing.identity-directory="+temp.resolve("server/identity"),
                "--licensing.checkpoint-file="+temp.resolve("server/identity/time.checkpoint"),
                "--licensing.vendor-public-key-file="+vendorPublic,"--licensing.admin-token="+adminToken)
                .redirectErrorStream(true).redirectOutput(temp.resolve("server-"+System.nanoTime()+".log").toFile()).start();
    }

    private void awaitHealthy(int port)throws Exception {long deadline=System.nanoTime()+Duration.ofSeconds(20).toNanos();while(System.nanoTime()<deadline){try{var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/actuator/health/liveness")).timeout(Duration.ofSeconds(1)).GET().build();if(HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.discarding()).statusCode()==200)return;}catch(Exception ignored){}Thread.sleep(100);}throw new AssertionError("server did not become healthy");}
    private static int freePort()throws Exception{try(var socket=new java.net.ServerSocket(0,1,InetAddress.getLoopbackAddress())){return socket.getLocalPort();}}
    private static void stop(Process process)throws Exception{process.destroy();if(!process.waitFor(10,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}}
    private static void assertUnavailable(ThrowingCallable call){assertThatThrownBy(call).isInstanceOfSatisfying(LicenseClientDeniedException.class,e->assertThat(e.reasonCode()).isEqualTo(LicenseReasonCode.LICENSE_SERVER_UNAVAILABLE));}
}
