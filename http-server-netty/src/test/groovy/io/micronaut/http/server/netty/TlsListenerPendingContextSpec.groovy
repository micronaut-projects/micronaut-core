package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.ssl.CertificateProvider
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.security.KeyStore

/**
 * A TLS listener whose CertificateProvider has not emitted must not serve plaintext HTTP.
 */
class TlsListenerPendingContextSpec extends Specification {

    void "a TLS listener never answers plaintext HTTP while its SSL context is pending"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                  : 'TlsListenerPendingContextSpec',
                'micronaut.server.ssl.enabled': true,
                'micronaut.server.ssl.port'  : -1,
                'micronaut.server.ssl.key-name': 'tls-pending-never',
        ])

        when:
        String response
        try {
            response = new Socket(server.host, server.port).withCloseable { s ->
                s.soTimeout = 10_000
                s.outputStream.write("GET /tls-pending HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
                s.outputStream.flush()
                new String(s.inputStream.readAllBytes(), StandardCharsets.UTF_8)
            }
        } catch (IOException e) {
            response = e.toString()
        }

        then:
        server.URI.scheme == 'https'
        !response.startsWith('HTTP/1.1 200')
        !response.contains('SECRET')

        cleanup:
        server?.close()
    }

    @Singleton
    @Named('tls-pending-never')
    @Requires(property = 'spec.name', value = 'TlsListenerPendingContextSpec')
    static class NeverEmittingCertificateProvider implements CertificateProvider {
        @Override
        String getName() {
            'tls-pending-never'
        }

        @Override
        Publisher<KeyStore> getKeyStore() {
            Flux.never()
        }
    }

    @Requires(property = 'spec.name', value = 'TlsListenerPendingContextSpec')
    @Controller
    static class SecretController {
        @Get('/tls-pending')
        String secret(HttpRequest<?> request) {
            "SECRET isSecure=${request.secure}"
        }
    }
}
