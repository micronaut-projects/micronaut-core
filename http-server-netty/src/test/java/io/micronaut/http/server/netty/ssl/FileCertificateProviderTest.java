package io.micronaut.http.server.netty.ssl;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.ssl.FileCertificateProvider.Format;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class FileCertificateProviderTest {
    private static void write(String fqdn, Path certFile, Format format) throws Exception {
        X509Bundle bundle = new CertificateBuilder()
            .algorithm(CertificateBuilder.Algorithm.rsa2048)
            .subject(fqdn)
            .setIsCertificateAuthority(true)
            .buildSelfSigned();
        if (format == Format.PEM) {
            Files.writeString(certFile, bundle.getPrivateKeyPEM() + bundle.getCertificatePathPEM());
        } else {
            KeyStore ks = bundle
                .toKeyStore(format.name(), "".toCharArray());
            try (OutputStream os = Files.newOutputStream(certFile)) {
                ks.store(os, "".toCharArray());
            }
        }
    }

    @ParameterizedTest
    @EnumSource
    public void simple(Format format) throws Exception {
        assumeTrue(format != Format.PKCS12); // PKCS#12 doesn't store certs for some reason

        Path certFile = Files.createTempFile(getClass().getName(), "." + format);
        write("CN=localhost", certFile, format);
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "FileCertificateProviderTest",
            "micronaut.certificate.file.cert-a.path", certFile.toString(),
            "micronaut.ssl.enabled", true,
            "micronaut.server.ssl.port", 0,
            "micronaut.server.ssl.key-name", "cert-a",
            "micronaut.server.ssl.key.password", "",
            "micronaut.http.client.ssl.trust-name", "cert-a"
        ));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURI())) {

            assertEquals("hello", client.toBlocking().retrieve("/hello"));
        } finally {
            Files.deleteIfExists(certFile);
        }
    }

    @ParameterizedTest
    @EnumSource
    public void mtlsRefresh(Format format) throws Exception {
        assumeTrue(format != Format.PKCS12); // PKCS#12 doesn't store certs for some reason

        Path certFile = Files.createTempFile(getClass().getName(), "." + format);
        write("CN=a", certFile, format);
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.ofEntries(
            Map.entry("spec.name", "FileCertificateProviderTest"),
            Map.entry("micronaut.certificate.self-signed.cert-a", ""),
            Map.entry("micronaut.certificate.file.cert-b.path", certFile.toString()),
            Map.entry("micronaut.certificate.file.cert-b.refresh-mode", "file-watcher"),
            Map.entry("micronaut.ssl.enabled", true),
            Map.entry("micronaut.server.ssl.port", 0),
            Map.entry("micronaut.server.ssl.key-name", "cert-a"),
            Map.entry("micronaut.server.ssl.trust-name", "cert-b"),
            Map.entry("micronaut.server.ssl.client-authentication", "need"),
            Map.entry("micronaut.http.client.pool.enabled", false), // to see the new cert
            Map.entry("micronaut.http.client.ssl.trust-name", "cert-a"),
            Map.entry("micronaut.http.client.ssl.key-name", "cert-b"),
            Map.entry("micronaut.http.client.ssl.key.password", "")
        ));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURI())) {

            assertEquals("CN=a", client.toBlocking().retrieve("/mtls"));

            write("CN=b", certFile, format);
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertEquals("CN=b", client.toBlocking().retrieve("/mtls")));
        } finally {
            Files.deleteIfExists(certFile);
        }
    }

    @Test
    public void separateFiles() throws Exception {
        Path keyFile = Files.createTempFile(getClass().getName(), ".pem");
        Path certFile = Files.createTempFile(getClass().getName(), ".pem");

        X509Bundle cert = new CertificateBuilder()
            .algorithm(CertificateBuilder.Algorithm.rsa2048)
            .subject("CN=localhost")
            .setIsCertificateAuthority(true)
            .buildSelfSigned();
        Files.writeString(keyFile, cert.getPrivateKeyPEM());
        Files.writeString(certFile, cert.getCertificatePathPEM());

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "FileCertificateProviderTest",
            "micronaut.certificate.file.cert-a.format", "pem",
            "micronaut.certificate.file.cert-a.path", keyFile.toString(),
            "micronaut.certificate.file.cert-a.certificate-path", certFile.toString(),
            "micronaut.ssl.enabled", true,
            "micronaut.server.ssl.port", 0,
            "micronaut.server.ssl.key-name", "cert-a",
            "micronaut.server.ssl.key.password", "",
            "micronaut.http.client.ssl.trust-name", "cert-a"
        ));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURI())) {

            assertEquals("hello", client.toBlocking().retrieve("/hello"));
        } finally {
            Files.deleteIfExists(keyFile);
            Files.deleteIfExists(certFile);
        }
    }

    @Controller
    @Requires(property = "spec.name", value = "FileCertificateProviderTest")
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
