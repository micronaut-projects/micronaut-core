package io.micronaut.http.server.netty.ssl;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.security.cert.X509Certificate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SelfSignedCertificateProviderTest {
    @Test
    public void simple() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "SelfSignedCertificateProviderTest",
            "micronaut.certificate.self-signed.cert-a", "",
            "micronaut.ssl.enabled", true,
            "micronaut.server.ssl.port", 0,
            "micronaut.server.ssl.key-name", "cert-a",
            "micronaut.http.client.ssl.trust-name", "cert-a"
        ));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURI())) {

            assertEquals("hello", client.toBlocking().retrieve("/hello"));
        }
    }

    @Test
    public void mtls() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "SelfSignedCertificateProviderTest",
            "micronaut.certificate.self-signed.cert-a", "",
            "micronaut.certificate.self-signed.cert-b.subject", "CN=foo",
            "micronaut.ssl.enabled", true,
            "micronaut.server.ssl.port", 0,
            "micronaut.server.ssl.key-name", "cert-a",
            "micronaut.server.ssl.trust-name", "cert-b",
            "micronaut.server.ssl.client-authentication", "need",
            "micronaut.http.client.ssl.key-name", "cert-b",
            "micronaut.http.client.ssl.trust-name", "cert-a"
        ));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURI())) {

            assertEquals("CN=foo", client.toBlocking().retrieve("/mtls"));
        }
    }

    @Test
    public void mtlsInsecure() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "SelfSignedCertificateProviderTest",
            "micronaut.certificate.self-signed.cert-a", "",
            "micronaut.certificate.self-signed.cert-b.subject", "CN=foo",
            "micronaut.ssl.enabled", true,
            "micronaut.server.ssl.port", 0,
            "micronaut.server.ssl.key-name", "cert-a",
            "micronaut.server.ssl.trust-name", "cert-b",
            "micronaut.server.ssl.client-authentication", "need",
            "micronaut.http.client.ssl.key-name", "cert-b",
            "micronaut.http.client.ssl.insecure-trust-all-certificates", true
        ));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURI())) {

            assertEquals("CN=foo", client.toBlocking().retrieve("/mtls"));
        }
    }

    @Controller
    @Requires(property = "spec.name", value = "SelfSignedCertificateProviderTest")
    static class MyCtrl {
        @Get("/hello")
        String hello() {
            return "hello";
        }

        @Get("/mtls")
        String mtls(HttpRequest<?> request) {
            return ((X509Certificate) request.getCertificate().orElseThrow()).getSubjectX500Principal().getName();
        }
    }
}
