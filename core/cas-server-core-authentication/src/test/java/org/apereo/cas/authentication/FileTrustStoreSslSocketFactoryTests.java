package org.apereo.cas.authentication;

import org.apereo.cas.configuration.model.core.authentication.HttpClientProperties;
import org.apereo.cas.util.http.SimpleHttpClient;
import org.apereo.cas.util.http.SimpleHttpClientFactoryBean;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import javax.net.ssl.HttpsURLConnection;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@code FileTrustStoreSslSocketFactory} class, checking for self-signed
 * and missing certificates via a local truststore.
 *
 * @author Misagh Moayyed
 * @since 4.1.0
 */
@Tag("FileSystem")
@Slf4j
class FileTrustStoreSslSocketFactoryTests {

    static {
        try {
            val url = new URL("https://self-signed.badssl.com");
            val conn = (HttpsURLConnection) url.openConnection();
            conn.setSSLSocketFactory(CasSSLContext.disabled().getSslContext().getSocketFactory());
            conn.connect();
            val certs = conn.getServerCertificates();
            for (val cert : certs) {
                if (cert instanceof final X509Certificate x509) {
                    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                    val keystoreResource = new ClassPathResource("truststore.jks");
                    try (val keyStoreData = keystoreResource.getInputStream()) {
                        keyStore.load(keyStoreData, "changeit".toCharArray());
                    }
                    if (keyStore.containsAlias("badssl")) {
                        keyStore.deleteEntry("badssl");
                    }
                    keyStore.setCertificateEntry("badssl", x509);
                    try (val fos = new FileOutputStream(keystoreResource.getFile())) {
                        keyStore.store(fos, "changeit".toCharArray());
                    }
                }
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
    
    @Nested
    class SslSocketFactoryTests {
        private static final ClassPathResource RESOURCE = new ClassPathResource("truststore.jks");

        private static final ClassPathResource RESOURCE_P12 = new ClassPathResource("truststore.p12");

        private static SSLConnectionSocketFactory sslFactory(final Resource resource, final String password,
                                                             final String trustStoreType) throws Exception {
            val sslContext = new DefaultCasSSLContext(resource, password, trustStoreType,
                new HttpClientProperties(), NoopHostnameVerifier.INSTANCE);
            return new SSLConnectionSocketFactory(sslContext.getSslContext());
        }

        private static SSLConnectionSocketFactory sslFactory() throws Exception {
            return sslFactory(RESOURCE, "changeit", "JKS");
        }

        private static SimpleHttpClient getSimpleHttpClient(final SSLConnectionSocketFactory sslConnectionSocketFactory) {
            val clientFactory = new SimpleHttpClientFactoryBean();
            clientFactory.setSslSocketFactory(sslConnectionSocketFactory);
            val client = clientFactory.getObject();
            assertNotNull(client);
            return client;
        }

        @Test
        void verifyTrustStoreLoadingSuccessfullyWithCertAvailable() throws Throwable {
            val client = getSimpleHttpClient(sslFactory());
            assertTrue(client.isValidEndPoint("https://self-signed.badssl.com"));
        }

        @Test
        void verifyTrustStoreNotFound() throws Throwable {
            assertThrows(IOException.class, () -> sslFactory(new FileSystemResource("test.jks"), "changeit", "JKS"));
        }

        @Test
        void verifyTrustStoreBadPassword() throws Throwable {
            assertThrows(IOException.class, () -> sslFactory(RESOURCE, "invalid", "JKS"));
        }

        @Test
        void verifyTrustStoreType() throws Throwable {
            val client = getSimpleHttpClient(sslFactory(RESOURCE_P12, "changeit", "PKCS12"));
            assertTrue(client.isValidEndPoint("https://www.google.com"));
        }

        @Test
        void verifyTrustStoreLoadingSuccessfullyForValidEndpointWithNoCert() throws Throwable {
            val client = getSimpleHttpClient(sslFactory());
            assertTrue(client.isValidEndPoint("https://www.google.com"));
        }

        @Test
        void verifyTrustStoreLoadingSuccessfullyWihInsecureEndpoint() throws Throwable {
            val client = getSimpleHttpClient(sslFactory());
            assertTrue(client.isValidEndPoint("http://wikipedia.org"));
        }
    }

    @Nested
    @SpringBootTest(classes = DefaultCasSSLContextTests.SharedTestConfiguration.class,
        properties = "cas.http-client.trust-store.file=classpath:truststore.jks")
    public class DefaultSslContext {
        @Autowired
        @Qualifier(CasSSLContext.BEAN_NAME)
        private CasSSLContext casSslContext;

        @Test
        void verifyOperation() throws Throwable {
            assertNotNull(casSslContext.getTrustManagerFactory());
            assertNotNull(DefaultCasSSLContextTests.SharedTestConfiguration.contactUrl("https://self-signed.badssl.com", casSslContext));
        }
    }
}
