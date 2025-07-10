package io.micronaut.http.server.netty.ssl;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.handler.ssl.OpenSsl;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
