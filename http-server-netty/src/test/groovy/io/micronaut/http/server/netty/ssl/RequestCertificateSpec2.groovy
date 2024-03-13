package io.micronaut.http.server.netty.ssl

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.handler.ssl.util.SelfSignedCertificate
import io.vertx.core.Vertx
import io.vertx.core.net.JksOptions
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import spock.lang.Specification

import javax.net.ssl.SSLHandshakeException
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class RequestCertificateSpec2 extends Specification {
    def normal() {
        given:
        def certificate = new SelfSignedCertificate()

        def keyStorePath = Files.createTempFile("micronaut-test-key-store", "pkcs12")
        def trustStorePath = Files.createTempFile("micronaut-test-trust-store", "pkcs12")

        writeStores(certificate, keyStorePath, trustStorePath)

        def ctx = ApplicationContext.run([
                "spec.name"                           : "RequestCertificateSpec2",
                "micronaut.http.client.read-timeout"  : "15s",
                'micronaut.server.ssl.enabled'        : true,
                'micronaut.server.ssl.port'           : -1,
                'micronaut.server.ssl.buildSelfSigned': true,
                'micronaut.ssl.clientAuthentication'  : "need",
                'micronaut.ssl.trust-store.path'      : 'file://' + trustStorePath.toString(),
                'micronaut.ssl.trust-store.type'      : 'JKS',
                'micronaut.ssl.trust-store.password'  : '123456',
        ])

        def server = ctx.getBean(EmbeddedServer)
        server.start()

        def vertx = Vertx.vertx()
        def client = WebClient.create(vertx, new WebClientOptions()
                .setTrustAll(true)
                .setSsl(true)
                .setKeyCertOptions(new JksOptions().setPath(keyStorePath.toString()).setPassword(""))
                .setTrustStoreOptions(new JksOptions().setPath(trustStorePath.toString()).setPassword("123456"))
        )

        when:
        def future = new CompletableFuture<HttpResponse<?>>()
        client.get(server.port, "localhost", "/mtls").send { if (it.failed()) future.completeExceptionally(it.cause()) else future.complete(it.result()) }
        def response = future.get()
        then:
        response.bodyAsString() == 'CN=localhost'
        response.statusCode() == 200

        cleanup:
        vertx.close()
        ctx.close()
        Files.deleteIfExists(keyStorePath)
        Files.deleteIfExists(trustStorePath)
    }

    def expired() {
        // this is intended behavior: an expired client cert DOES NOT lead to a handshake failure. This is JDK behavior:
        // when a cert is directly in the trust store, expiry is not checked. expiry is only checked if the cert is
        // signed by a CA that is in the trust store.

        given:
        def certificate = new SelfSignedCertificate(Date.from(Instant.now().minus(5, ChronoUnit.HOURS)), Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))

        def keyStorePath = Files.createTempFile("micronaut-test-key-store", "pkcs12")
        def trustStorePath = Files.createTempFile("micronaut-test-trust-store", "pkcs12")

        writeStores(certificate, keyStorePath, trustStorePath)

        def ctx = ApplicationContext.run([
                "spec.name"                           : "RequestCertificateSpec2",
                "micronaut.http.client.read-timeout"  : "15s",
                'micronaut.server.ssl.enabled'        : true,
                'micronaut.server.ssl.port'           : -1,
                'micronaut.server.ssl.buildSelfSigned': true,
                'micronaut.ssl.clientAuthentication'  : "need",
                'micronaut.ssl.trust-store.path'      : 'file://' + trustStorePath.toString(),
                'micronaut.ssl.trust-store.type'      : 'JKS',
                'micronaut.ssl.trust-store.password'  : '123456',
        ])

        def server = ctx.getBean(EmbeddedServer)
        server.start()

        def vertx = Vertx.vertx()
        def client = WebClient.create(vertx, new WebClientOptions()
                .setTrustAll(true)
                .setSsl(true)
                .setKeyCertOptions(new JksOptions().setPath(keyStorePath.toString()).setPassword(""))
                .setTrustStoreOptions(new JksOptions().setPath(trustStorePath.toString()).setPassword("123456"))
        )

        when:
        def future = new CompletableFuture<HttpResponse<?>>()
        client.get(server.port, "localhost", "/mtls").send { if (it.failed()) future.completeExceptionally(it.cause()) else future.complete(it.result()) }
        def response = future.get()
        then:
        response.bodyAsString() == 'CN=localhost'
        response.statusCode() == 200

        cleanup:
        vertx.close()
        ctx.close()
        Files.deleteIfExists(keyStorePath)
        Files.deleteIfExists(trustStorePath)
    }

    def untrusted() {
        given:
        def clientCert = new SelfSignedCertificate()
        // for the client to send the cert, we still need the same CN in the trust store
        def serverExpectsCert = new SelfSignedCertificate()

        def keyStorePath = Files.createTempFile("micronaut-test-key-store", "pkcs12")
        def trustStorePath = Files.createTempFile("micronaut-test-trust-store", "pkcs12")

        writeStores(clientCert, keyStorePath, null)
        writeStores(serverExpectsCert, null, trustStorePath)

        def ctx = ApplicationContext.run([
                "spec.name"                           : "RequestCertificateSpec2",
                "micronaut.http.client.read-timeout"  : "15s",
                'micronaut.server.ssl.enabled'        : true,
                'micronaut.server.ssl.port'           : -1,
                'micronaut.server.ssl.buildSelfSigned': true,
                'micronaut.ssl.clientAuthentication'  : "need",
                'micronaut.ssl.trust-store.path'      : 'file://' + trustStorePath.toString(),
                'micronaut.ssl.trust-store.type'      : 'JKS',
                'micronaut.ssl.trust-store.password'  : '123456',
                'micronaut.ssl.prefer-openssl'        : false, // openssl impl just closes the connection, different error
        ])

        def server = ctx.getBean(EmbeddedServer)
        server.start()

        def vertx = Vertx.vertx()
        def client = WebClient.create(vertx, new WebClientOptions()
                .setTrustAll(true)
                .setSsl(true)
                .setKeyCertOptions(new JksOptions().setPath(keyStorePath.toString()).setPassword(""))
                .setTrustStoreOptions(new JksOptions().setPath(trustStorePath.toString()).setPassword("123456"))
        )

        when:
        def future = new CompletableFuture<HttpResponse<?>>()
        client.get(server.port, "localhost", "/mtls").send { if (it.failed()) future.completeExceptionally(it.cause()) else future.complete(it.result()) }
        def response = future.get()
        then:
        def e = thrown ExecutionException
        e.cause instanceof SSLHandshakeException || e.cause.cause instanceof SSLHandshakeException

        cleanup:
        vertx.close()
        ctx.close()
        Files.deleteIfExists(keyStorePath)
        Files.deleteIfExists(trustStorePath)
    }

    private void writeStores(SelfSignedCertificate certificate, Path keyStorePath, Path trustStorePath) {
        if (keyStorePath != null) {
            KeyStore ks = KeyStore.getInstance("PKCS12")
            ks.load(null, null)
            ks.setKeyEntry("key", certificate.key(), "".toCharArray(), new Certificate[]{certificate.cert()})
            try (OutputStream os = Files.newOutputStream(keyStorePath)) {
                ks.store(os, "".toCharArray())
            }
        }

        if (trustStorePath != null) {
            KeyStore ts = KeyStore.getInstance("JKS")
            ts.load(null, null)
            ts.setCertificateEntry("cert", certificate.cert())
            try (OutputStream os = Files.newOutputStream(trustStorePath)) {
                ts.store(os, "123456".toCharArray())
            }
        }
    }

    @Controller
    @Requires(property = "spec.name", value = "RequestCertificateSpec2")
    static class TestController {
        @Get('/mtls')
        String name(HttpRequest<?> request) {
            def cert = request.getCertificate().get() as X509Certificate
            cert.issuerX500Principal.name
        }
    }
}
