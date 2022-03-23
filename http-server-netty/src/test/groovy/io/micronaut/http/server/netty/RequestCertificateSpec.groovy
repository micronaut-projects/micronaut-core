package io.micronaut.http.server.netty

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.netty.AbstractMicronautSpec
import reactor.core.publisher.Flux

import java.security.cert.X509Certificate

class RequestCertificateSpec extends AbstractMicronautSpec {

    void "test certificate extraction"() {
        when:
        def response = Flux.from(rxClient
                .exchange('/ssl', String))
                .blockFirst()
        then:
        response.code() == HttpStatus.OK.code
        response.body() == "O=Test CA,ST=Some-State,C=US"
    }

    @Override
    Map<String, Object> getConfiguration() {
        super.getConfiguration() << [
                'micronaut.server.ssl.enabled': true,
                'micronaut.server.ssl.port': -1,
                // Cannot be true!
                'micronaut.server.ssl.buildSelfSigned': false,
                'micronaut.ssl.clientAuthentication': "need",
                'micronaut.ssl.key-store.path': 'classpath:KeyStore.pkcs12',
                'micronaut.ssl.key-store.type': 'PKCS12',
                'micronaut.ssl.key-store.password': '',
                'micronaut.ssl.trust-store.path': 'classpath:TrustStore.jks',
                'micronaut.ssl.trust-store.type': 'JKS',
                'micronaut.ssl.trust-store.password': '123456',
        ]
    }

    @Controller
    static class TestController {

        @Get('/ssl')
        String html(HttpRequest<?> request) {
            def cert = request.getCertificate().get() as X509Certificate
            cert.issuerX500Principal.name
        }
    }
}
