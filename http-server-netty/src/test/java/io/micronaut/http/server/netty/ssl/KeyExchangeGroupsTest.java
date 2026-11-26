package io.micronaut.http.server.netty.ssl;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.ssl.KeyExchangeGroups;
import io.micronaut.http.ssl.SslConfigurationException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.handler.ssl.OpenSsl;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class KeyExchangeGroupsTest {
    private static final X509Bundle BUNDLE;

    static {
        try {
            BUNDLE = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.ecp256)
                .setIsCertificateAuthority(true)
                .subject("CN=localhost")
                .buildSelfSigned();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static List<Boolean> engines() {
        List<Boolean> engines = new ArrayList<>();
        engines.add(false);
        if (OpenSsl.isAvailable()) {
            engines.add(true);
        }
        return engines;
    }

    private static Map<String, Object> config(boolean openssl) {
        Map<String, Object> props = new HashMap<>();
        props.put("spec.name", "KeyExchangeGroupsTest");
        props.put("micronaut.ssl.prefer-openssl", openssl);
        props.put("micronaut.server.ssl.enabled", true);
        props.put("micronaut.server.ssl.port", -1);
        props.put("micronaut.server.ssl.protocols", "TLSv1.3");
        props.put("micronaut.server.ssl.key-store.path", "string:" + BUNDLE.getPrivateKeyPEM() + BUNDLE.getCertificatePathPEM());
        props.put("micronaut.http.client.ssl.protocols", "TLSv1.3");
        props.put("micronaut.http.client.ssl.trust-store.path", "string:" + BUNDLE.getCertificatePEM());
        return props;
    }

    private static String request(Map<String, Object> props) {
        try (ApplicationContext ctx = ApplicationContext.run(props)) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
            server.start();
            try (HttpClient client = ctx.createBean(HttpClient.class, server.getURI())) {
                return client.toBlocking().retrieve("/key-exchange/hello");
            }
        }
    }

    private static boolean postQuantumSupported(boolean openssl) {
        return openssl || KeyExchangeGroups.jdkSupport(null).test(KeyExchangeGroups.X25519_MLKEM768);
    }

    @ParameterizedTest
    @MethodSource("engines")
    public void matchingGroups(boolean openssl) {
        Map<String, Object> props = config(openssl);
        props.put("micronaut.server.ssl.key-exchange-groups", "secp384r1,x25519");
        props.put("micronaut.http.client.ssl.key-exchange-groups", "secp384r1");
        assertEquals("hello", request(props));
    }

    @ParameterizedTest
    @MethodSource("engines")
    public void disjointGroupsFailTheHandshake(boolean openssl) {
        Map<String, Object> props = config(openssl);
        props.put("micronaut.server.ssl.key-exchange-groups", "secp384r1");
        props.put("micronaut.http.client.ssl.key-exchange-groups", "x25519");
        assertThrows(Exception.class, () -> request(props));
    }

    @ParameterizedTest
    @MethodSource("engines")
    public void strictPolicyWithPostQuantumClient(boolean openssl) {
        Assumptions.assumeTrue(postQuantumSupported(openssl));
        Map<String, Object> props = config(openssl);
        props.put("micronaut.server.ssl.pqc-policy", "strict");
        props.put("micronaut.http.client.ssl.key-exchange-groups", "X25519MLKEM768,x25519");
        assertEquals("hello", request(props));
    }

    @ParameterizedTest
    @MethodSource("engines")
    public void strictPolicyRejectsClassicalClient(boolean openssl) {
        Assumptions.assumeTrue(postQuantumSupported(openssl));
        Map<String, Object> props = config(openssl);
        props.put("micronaut.server.ssl.pqc-policy", "strict");
        props.put("micronaut.http.client.ssl.key-exchange-groups", "x25519,secp256r1");
        assertThrows(Exception.class, () -> request(props));
    }

    @ParameterizedTest
    @MethodSource("engines")
    public void relaxedPolicyAcceptsClassicalClient(boolean openssl) {
        Assumptions.assumeTrue(postQuantumSupported(openssl));
        Map<String, Object> props = config(openssl);
        props.put("micronaut.server.ssl.pqc-policy", "relaxed");
        props.put("micronaut.http.client.ssl.key-exchange-groups", "x25519");
        assertEquals("hello", request(props));
    }

    @ParameterizedTest
    @MethodSource("engines")
    public void unsupportedGroupFailsAtStartup(boolean openssl) {
        Map<String, Object> props = config(openssl);
        props.put("micronaut.server.ssl.key-exchange-groups", "x25519,no-such-group");
        Exception e = assertThrows(Exception.class, () -> {
            try (ApplicationContext ctx = ApplicationContext.run(props)) {
                ctx.getBean(EmbeddedServer.class).start();
            }
        });
        SslConfigurationException cause = find(e);
        assertEquals("The " + (openssl ? "OpenSSL" : "JDK") + " TLS implementation does not support the configured key exchange groups [no-such-group]", cause.getMessage());
    }

    @ParameterizedTest
    @MethodSource("engines")
    public void strictPolicyWithClassicalGroupFailsAtStartup(boolean openssl) {
        Map<String, Object> props = config(openssl);
        props.put("micronaut.server.ssl.pqc-policy", "strict");
        props.put("micronaut.server.ssl.key-exchange-groups", "X25519MLKEM768,x25519");
        Exception e = assertThrows(Exception.class, () -> {
            try (ApplicationContext ctx = ApplicationContext.run(props)) {
                ctx.getBean(EmbeddedServer.class).start();
            }
        });
        assertEquals("The strict post-quantum policy only allows post-quantum key exchange groups, but found [x25519]", find(e).getMessage());
    }

    private static SslConfigurationException find(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SslConfigurationException sce) {
                return sce;
            }
        }
        fail("No SslConfigurationException in the cause chain", t);
        throw new AssertionError();
    }

    @Requires(property = "spec.name", value = "KeyExchangeGroupsTest")
    @Controller("/key-exchange")
    static class MyController {
        @Get("/hello")
        String hello() {
            return "hello";
        }
    }
}
