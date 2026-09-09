package com.enterprise.licensing.client.transport;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public final class JdkLicenseTransport implements LicenseTransport {
    private final HttpClient client;
    private final boolean allowLoopbackHttp;

    private JdkLicenseTransport(HttpClient client, boolean allowLoopbackHttp) {
        this.client=client;this.allowLoopbackHttp=allowLoopbackHttp;
    }

    public static JdkLicenseTransport pinned(String expectedServerPublicKeyPin) {
        try {
            TrustManagerFactory factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init((java.security.KeyStore)null);
            X509TrustManager platform=null;for(TrustManager manager:factory.getTrustManagers())if(manager instanceof X509TrustManager value){platform=value;break;}
            if(platform==null)throw new IllegalStateException("No platform X.509 trust manager is available");
            SSLContext context=SSLContext.getInstance("TLS");context.init(null,new TrustManager[]{new PinnedPublicKeyTrustManager(platform,expectedServerPublicKeyPin)},null);
            return new JdkLicenseTransport(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).sslContext(context).build(),false);
        } catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Unable to configure pinned HTTPS transport",e); }
    }

    public static JdkLicenseTransport localLoopbackTest() {
        return new JdkLicenseTransport(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),true);
    }

    public static String publicKeyPin(PublicKey key) {
        try{return "sha256/"+Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(key.getEncoded()));}
        catch(Exception e){throw new IllegalStateException("Unable to calculate public-key pin",e);}
    }

    @Override public Response exchange(String method,URI uri,Map<String,String>headers,byte[]body,Duration timeout)throws IOException,InterruptedException{
        EndpointSecurity.requireAllowed(uri,allowLoopbackHttp);
        var builder=HttpRequest.newBuilder(uri).timeout(timeout);headers.forEach(builder::header);builder.method(method,HttpRequest.BodyPublishers.ofByteArray(body==null?new byte[0]:body));var response=client.send(builder.build(),HttpResponse.BodyHandlers.ofByteArray());return new Response(response.statusCode(),response.body());
    }

    static final class PinnedPublicKeyTrustManager implements X509TrustManager {
        private final X509TrustManager delegate;private final byte[] expected;
        PinnedPublicKeyTrustManager(X509TrustManager delegate,String pin){this.delegate=delegate;this.expected=decodePin(pin);}
        public void checkClientTrusted(X509Certificate[] chain,String authType)throws CertificateException{delegate.checkClientTrusted(chain,authType);}
        public void checkServerTrusted(X509Certificate[] chain,String authType)throws CertificateException{delegate.checkServerTrusted(chain,authType);if(chain==null||chain.length==0)throw new CertificateException("Server certificate chain is empty");try{byte[] actual=MessageDigest.getInstance("SHA-256").digest(chain[0].getPublicKey().getEncoded());if(!MessageDigest.isEqual(expected,actual))throw new CertificateException("License Server public-key pin mismatch");}catch(CertificateException e){throw e;}catch(Exception e){throw new CertificateException("Unable to verify License Server public-key pin",e);}}
        public X509Certificate[] getAcceptedIssuers(){return delegate.getAcceptedIssuers();}
        private static byte[] decodePin(String pin){if(pin==null||!pin.startsWith("sha256/"))throw new IllegalArgumentException("Server public-key pin must use sha256/<base64>");try{byte[] value=Base64.getDecoder().decode(pin.substring(7));if(value.length!=32)throw new IllegalArgumentException("Server public-key pin must contain a SHA-256 digest");return value;}catch(IllegalArgumentException e){throw new IllegalArgumentException("Invalid server public-key pin",e);}}
    }
}
