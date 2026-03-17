package io.micronaut.http.server.netty.ssl;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.ssl.SslConfigurationException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.handler.ssl.OpenSsl;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PemSslConfigTest {
    private static final Set<CertificateBuilder.Algorithm> PQC = Set.of(
        CertificateBuilder.Algorithm.mlKem512,
        CertificateBuilder.Algorithm.mlKem768,
        CertificateBuilder.Algorithm.mlKem1024,
        CertificateBuilder.Algorithm.mlDsa44,
        CertificateBuilder.Algorithm.mlDsa65,
        CertificateBuilder.Algorithm.mlDsa87
    );

    static List<Arguments> algorithms() {
        List<Arguments> out = new ArrayList<>();
        for (boolean openssl : new boolean[]{false, true}) {
            if (openssl && !OpenSsl.isAvailable()) {
                continue;
            }
            for (boolean separateProperties : new boolean[]{false, true}) {
                List<List<CertificateBuilder.Algorithm>> algs = new ArrayList<>();

                for (CertificateBuilder.Algorithm simple : List.of(
                    CertificateBuilder.Algorithm.ecp256,
                    CertificateBuilder.Algorithm.ecp384,
                    CertificateBuilder.Algorithm.rsa2048,
                    CertificateBuilder.Algorithm.rsa4096
                )) {
                    algs.add(List.of(simple));
                }
                if (!openssl) { // TODO: currently broken: https://github.com/netty/netty/pull/15467
                    algs.add(List.of(CertificateBuilder.Algorithm.ed25519));
                    algs.add(List.of(CertificateBuilder.Algorithm.ed448));
                }

                algs.add(List.of(CertificateBuilder.Algorithm.rsa2048, CertificateBuilder.Algorithm.rsa2048));

                /* TODO: currently broken: https://github.com/netty/netty/pull/15467
                if (PlatformDependent.javaVersion() >= 24 && openssl) {
                    for (CertificateBuilder.Algorithm pqc : PQC) {
                        if (pqc.supportSigning()) {
                            algs.add(List.of(pqc));
                        }
                        algs.add(List.of(CertificateBuilder.Algorithm.rsa2048, pqc));
                    }
                }
                 */

                for (List<CertificateBuilder.Algorithm> alg : algs) {
                    out.add(Arguments.of(alg, openssl, separateProperties));
                }
            }
        }
        return out;
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    public void test(List<CertificateBuilder.Algorithm> algorithms, boolean openssl, boolean separateProperties) throws Exception {
        X509Bundle root = null;
        X509Bundle prev = null;
        X509Bundle leaf = null;
        for (int i = 0; i < algorithms.size(); i++) {
            boolean isLeaf = i == algorithms.size() - 1;
            CertificateBuilder builder = new CertificateBuilder()
                .subject("CN=" + (isLeaf ? "localhost" : "ca" + i))
                .setIsCertificateAuthority(!isLeaf || algorithms.size() == 1)
                .algorithm(algorithms.get(i));
            if (prev == null) {
                root = builder.buildSelfSigned();
                prev = root;
            } else {
                prev = builder.buildIssuedBy(prev);
            }
            if (isLeaf) {
                leaf = prev;
            }
        }
        assert leaf != null;

        Map<String, Object> props = new HashMap<>();
        props.put("spec.name", "PemSslConfigTest");
        props.put("micronaut.ssl.prefer-openssl", openssl);
        props.put("micronaut.ssl.protocols", "TLSv1.3");
        props.put("micronaut.http.client.ssl.protocols", "TLSv1.3");
        props.put("micronaut.server.ssl.port", -1);
        props.put("micronaut.server.ssl.enabled", true);
        if (separateProperties) {
            props.put("micronaut.server.ssl.key-store.key-path", "string:" + leaf.getPrivateKeyPEM());
            props.put("micronaut.server.ssl.key-store.certificate-path", "string:" + leaf.getCertificatePathPEM());
        } else {
            props.put("micronaut.server.ssl.key-store.path", "string:" + leaf.getPrivateKeyPEM() + leaf.getCertificatePathPEM());
        }
        props.put("micronaut.http.client.ssl.trust-store.path", "string:" + root.getCertificatePEM());
        try (ApplicationContext ctx = ApplicationContext.run(props)) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
            server.start();

            try (HttpClient client = ctx.createBean(HttpClient.class, server.getURI())) {
                assertEquals("hello", client.toBlocking().retrieve("/pem-ssl/hello"));
            }
        }
    }

    @Requires(property = "spec.name", value = "PemSslConfigTest")
    @Controller("/pem-ssl")
    static class MyController {
        @Get("/hello")
        String hello() {
            return "hello";
        }
    }

    private static final X509Bundle BUNDLE;
    private static final String KEY_STORE_RESOURCE;
    private static final String KEY_STORE_RESOURCE_PASSWORD = "password";

    static {
        try {
            BUNDLE = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.ecp256)
                .setIsCertificateAuthority(true)
                .subject("CN=localhost")
                .buildSelfSigned();

            KeyStore ks = BUNDLE.toKeyStore(null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ks.store(baos, KEY_STORE_RESOURCE_PASSWORD.toCharArray());
            KEY_STORE_RESOURCE = "base64:" + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static List<Arguments> simpleErrorConditions() {
        String doesNotExist = "file:/tmp/" + UUID.randomUUID();
        return List.of(
            Arguments.of(Map.of(), "No key store configured"),
            Arguments.of(
                Map.of("micronaut.ssl.key-store.path", "string:ks1", "micronaut.ssl.key-store.key-path", "string:k1"),
                "Cannot specify key store path and key-path or certificate-path at the same time"),
            Arguments.of(
                Map.of("micronaut.ssl.key-store.path", "string:ks1", "micronaut.ssl.key-store.certificate-path", "string:k1"),
                "Cannot specify key store path and key-path or certificate-path at the same time"),
            Arguments.of(
                Map.of("micronaut.ssl.key-store.certificate-path", "string:c1"),
                "Must also specify key-path"),
            Arguments.of(
                Map.of("micronaut.ssl.key-store.key-path", "string:k1"),
                "Must also specify certificate-path"),
            Arguments.of(
                Map.of("micronaut.ssl.key-store.path", doesNotExist),
                "The resource " + doesNotExist + " could not be found"),
            Arguments.of(
                Map.of("micronaut.ssl.key-store.key-path", doesNotExist, "micronaut.ssl.key-store.certificate-path", "string:c1"),
                "The resource " + doesNotExist + " could not be found"),
            Arguments.of(
                Map.of("micronaut.ssl.key-store.key-path", "string:" + BUNDLE.getPrivateKeyPEM(), "micronaut.ssl.key-store.certificate-path", doesNotExist),
                "The resource " + doesNotExist + " could not be found"),
            Arguments.of(
                Map.of("micronaut.ssl.key-store.key-path", "string:" + BUNDLE.getPrivateKeyPEM() + BUNDLE.getPrivateKeyPEM(), "micronaut.ssl.key-store.certificate-path", "string:" + BUNDLE.getCertificatePathPEM()),
                "key-path contained more than one PEM object. It should only contain the private key."),
            Arguments.of(
                Map.of("micronaut.ssl.key-store.key-path", "string:" + BUNDLE.getCertificatePathPEM(), "micronaut.ssl.key-store.certificate-path", "string:" + BUNDLE.getCertificatePathPEM()),
                "key-path contained a certificate instead of a private key."),
            Arguments.of(
                Map.of("micronaut.ssl.key-store.path", "string:" + BUNDLE.getPrivateKeyPEM() + BUNDLE.getPrivateKeyPEM() + BUNDLE.getCertificatePathPEM()),
                "PEM must only contain the private key and a certificate chain")
        );
    }

    @ParameterizedTest
    @MethodSource("simpleErrorConditions")
    public void simpleErrorConditions(Map<String, Object> cfg, String message) {
        Map<String, Object> combined = new HashMap<>(cfg);
        combined.put("micronaut.ssl.enabled", true);
        try (ApplicationContext ctx = ApplicationContext.run(combined)) {
            SslConfigurationException ex = assertThrows(
                SslConfigurationException.class,
                () -> ctx.getBean(CertificateProvidedSslBuilder.class).build()
            );
            try {
                assertEquals(message, ex.getMessage());
            } catch (Throwable e) {
                e.addSuppressed(ex);
                throw e;
            }
        }
    }

    static List<Arguments> extendedErrorConditions() {
        return List.of(
            Arguments.of(
                Map.of("micronaut.ssl.key-store.path", KEY_STORE_RESOURCE, "micronaut.ssl.key-store.password", "wrong-password"),
                (Consumer<SslConfigurationException>) e -> {
                    assertEquals("keystore password was incorrect", e.getCause().getMessage());
                    assertEquals(0, e.getSuppressed().length);
                    assertEquals(0, e.getCause().getSuppressed().length);
                }),
            Arguments.of(
                Map.of("micronaut.ssl.key-store.path", "base64:ABCDEFG"),
                (Consumer<SslConfigurationException>) e -> {
                    Throwable cause = e.getCause();
                    assertInstanceOf(IOException.class, cause);
                    assertEquals(1, cause.getSuppressed().length);
                    assertEquals("Also tried and failed to load the input as PEM", cause.getSuppressed()[0].getMessage());
                    assertEquals("Missing start tag", cause.getSuppressed()[0].getCause().getMessage());
                }),
            Arguments.of(
                Map.of("micronaut.ssl.key-store.path", "string:-----BEGIN PRIVATE KEY-----\nabcdef\n-----END PRIVATE KEY-----\n"),
                (Consumer<SslConfigurationException>) e -> {
                    Throwable cause = e.getCause();
                    assertInstanceOf(IllegalArgumentException.class, cause);
                    assertEquals(1, cause.getSuppressed().length);
                    assertEquals("Also tried and failed to load the input as a key store", cause.getSuppressed()[0].getMessage());
                    assertEquals("Invalid keystore format", cause.getSuppressed()[0].getCause().getMessage());
                })
        );
    }

    @ParameterizedTest
    @MethodSource("extendedErrorConditions")
    public void extendedErrorConditions(Map<String, Object> cfg, Consumer<SslConfigurationException> check) {
        Map<String, Object> combined = new HashMap<>(cfg);
        combined.put("micronaut.ssl.enabled", true);
        try (ApplicationContext ctx = ApplicationContext.run(combined)) {
            SslConfigurationException ex = assertThrows(
                SslConfigurationException.class,
                () -> ctx.getBean(CertificateProvidedSslBuilder.class).build()
            );
            try {
                check.accept(ex);
            } catch (Throwable e) {
                e.addSuppressed(ex);
                throw e;
            }
        }
    }
}
