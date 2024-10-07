package io.micronaut.http.server.netty

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.netty.handler.ssl.util.SelfSignedCertificate
import reactor.core.publisher.Flux
import spock.lang.Shared

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.X509Certificate

class RequestCertificateSpec extends AbstractMicronautSpec {
    @Shared Path keyStorePath
    @Shared Path trustStorePath

    void "test certificate extraction"() {
        when:
        def response = Flux.from(httpClient
                .exchange('/ssl', String))
                .blockFirst()
        then:
        response.code() == HttpStatus.OK.code
        response.body() == "CN=localhost"
    }

    @Override
    void cleanupSpec() {
        Files.deleteIfExists(keyStorePath)
        Files.deleteIfExists(trustStorePath)
    }

    @Override
    Map<String, Object> getConfiguration() {
        def certificate = new SelfSignedCertificate()

        keyStorePath = Files.createTempFile("micronaut-test-key-store", "pkcs12")
        trustStorePath = Files.createTempFile("micronaut-test-trust-store", "pkcs12")

        KeyStore ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry("key", certificate.key(), "".toCharArray(), new Certificate[]{certificate.cert()})
        try (OutputStream os = Files.newOutputStream(keyStorePath)) {
            ks.store(os, "".toCharArray())
        }

        KeyStore ts = KeyStore.getInstance("JKS")
        ts.load(null, null)
        ts.setCertificateEntry("cert", certificate.cert())
        try (OutputStream os = Files.newOutputStream(trustStorePath)) {
            ts.store(os, "123456".toCharArray())
        }

        super.getConfiguration() << [
                "micronaut.http.client.read-timeout": "15s",
                'micronaut.server.ssl.enabled': true,
                'micronaut.server.ssl.port': -1,
                // Cannot be true!
                'micronaut.server.ssl.buildSelfSigned': false,
                'micronaut.ssl.clientAuthentication': "need",
                'micronaut.ssl.key-store.path': 'file://' + keyStorePath.toString(),
                'micronaut.ssl.key-store.type': 'PKCS12',
                'micronaut.ssl.key-store.password': '',
                'micronaut.ssl.trust-store.path': 'file://' + trustStorePath.toString(),
                'micronaut.ssl.trust-store.type': 'JKS',
                'micronaut.ssl.trust-store.password': '123456',
        ]
    }

    @Requires(property = 'spec.name', value = 'RequestCertificateSpec')
    @Controller
    static class TestController {

        @Get('/ssl')
        String html(HttpRequest<?> request) {
            def cert = request.getCertificate().get() as X509Certificate
            cert.issuerX500Principal.name
        }
    }
}
