package io.micronaut.http.server.netty.ssl;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.ssl.FileCertificateProvider;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ResourceCertificateProviderTest {
    private static String resource(FileCertificateProvider.Format format) throws Exception {
        X509Bundle bundle = new CertificateBuilder()
            .algorithm(CertificateBuilder.Algorithm.rsa2048)
            .subject("CN=localhost")
            .setIsCertificateAuthority(true)
            .buildSelfSigned();
        if (format == FileCertificateProvider.Format.PEM) {
            return "string:" + bundle.getPrivateKeyPEM() + bundle.getCertificatePathPEM();
        } else {
            KeyStore ks = bundle
                .toKeyStore(format.name(), "".toCharArray());
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                ks.store(os, "".toCharArray());
                return "base64:" + Base64.getEncoder().encodeToString(os.toByteArray());
            }
        }
    }

    @ParameterizedTest
    @EnumSource
    public void simple(FileCertificateProvider.Format format) throws Exception {
        assumeTrue(format != FileCertificateProvider.Format.PKCS12); // PKCS#12 doesn't store certs for some reason

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "FileCertificateProviderTest",
            "micronaut.certificate.resource.cert-a.resource", resource(format),
            "micronaut.ssl.enabled", true,
            "micronaut.server.ssl.port", 0,
            "micronaut.server.ssl.key-name", "cert-a",
            "micronaut.server.ssl.key.password", "",
            "micronaut.http.client.ssl.trust-name", "cert-a"
        ));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURI())) {

            assertEquals("hello", client.toBlocking().retrieve("/hello"));
        }
    }

    @Test
    public void trustStoreWithCertsOnly() throws Exception {
        X509Bundle bundle = new CertificateBuilder()
            .algorithm(CertificateBuilder.Algorithm.rsa2048)
            .subject("CN=localhost")
            .setIsCertificateAuthority(true)
            .buildSelfSigned();

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "FileCertificateProviderTest",
            "micronaut.certificate.resource.cert-a.resource", "string:" + bundle.getPrivateKeyPEM() + bundle.getCertificatePathPEM(),
            "micronaut.certificate.resource.trust-a.resource", "string:" + bundle.getCertificatePathPEM(),
            "micronaut.ssl.enabled", true,
            "micronaut.server.ssl.port", 0,
            "micronaut.server.ssl.key-name", "cert-a",
            "micronaut.server.ssl.key.password", "",
            "micronaut.http.client.ssl.trust-name", "trust-a"
        ));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURI())) {

            assertEquals("hello", client.toBlocking().retrieve("/hello"));
        }
    }
}
