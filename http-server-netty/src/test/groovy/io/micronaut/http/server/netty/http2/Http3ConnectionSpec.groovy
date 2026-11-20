package io.micronaut.http.server.netty.http2

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.server.netty.NettyHttpRequest
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.Channel
import io.netty.handler.codec.quic.QuicChannel
import io.netty.handler.ssl.util.SelfSignedCertificate
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

class Http3ConnectionSpec extends Specification {
    List<Path> tempFiles = []

    def 'each HTTP/3 connection sees its own client certificate'() {
        given:
        def serverCert = new SelfSignedCertificate("localhost")
        def certA = new SelfSignedCertificate("clienta")
        def certB = new SelfSignedCertificate("clientb")

        def serverKeyStore = keyStore(serverCert)
        def trustStore = tempFile()
        KeyStore ts = KeyStore.getInstance("JKS")
        ts.load(null, null)
        ts.setCertificateEntry("a", certA.cert())
        ts.setCertificateEntry("b", certB.cert())
        try (OutputStream os = Files.newOutputStream(trustStore)) {
            ts.store(os, "123456".toCharArray())
        }

        def serverCtx = ApplicationContext.run([
                'spec.name'                              : 'Http3ConnectionSpec',
                'micronaut.server.ssl.enabled'           : true,
                'micronaut.server.ssl.build-self-signed' : false,
                'micronaut.server.ssl.client-authentication': 'need',
                'micronaut.server.ssl.key-store.path'    : 'file://' + serverKeyStore,
                'micronaut.server.ssl.key-store.type'    : 'PKCS12',
                'micronaut.server.ssl.key-store.password': '',
                'micronaut.server.ssl.trust-store.path'  : 'file://' + trustStore,
                'micronaut.server.ssl.trust-store.type'  : 'JKS',
                'micronaut.server.ssl.trust-store.password': '123456',
                'micronaut.server.netty.listeners.a.family': 'QUIC',
                'micronaut.server.netty.listeners.a.port': -1,
        ])
        def server = serverCtx.getBean(EmbeddedServer)
        server.start()

        def clientCtxA = clientContext(certA)
        def clientCtxB = clientContext(certB)
        def clientA = clientCtxA.createBean(HttpClient, server.URI).toBlocking()
        def clientB = clientCtxB.createBean(HttpClient, server.URI).toBlocking()

        expect:
        // A handshakes first
        clientA.retrieve("/h3/cert") == "CN=clienta"
        // B handshakes afterwards
        clientB.retrieve("/h3/cert") == "CN=clientb"
        // a new request on A's existing connection must still see A's certificate, not the
        // certificate of the most recently established connection
        clientA.retrieve("/h3/cert") == "CN=clienta"
        clientB.retrieve("/h3/cert") == "CN=clientb"

        cleanup:
        clientCtxA?.close()
        clientCtxB?.close()
        server?.close()
        serverCtx?.close()
        tempFiles.each { Files.deleteIfExists(it) }
        [serverCert, certA, certB].each { it?.delete() }
    }

    def 'idle timeout applies to HTTP/3 connections'() {
        given:
        def serverCtx = ApplicationContext.run([
                'spec.name'                                : 'Http3ConnectionSpec',
                'micronaut.server.idle-timeout'            : '500ms',
                'micronaut.server.ssl.enabled'             : true,
                'micronaut.server.ssl.build-self-signed'   : true,
                'micronaut.server.netty.listeners.a.family': 'QUIC',
                'micronaut.server.netty.listeners.a.port'  : -1,

                'micronaut.http.client.alpn-modes'                         : ['h3'],
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ])
        def server = serverCtx.getBean(EmbeddedServer)
        server.start()
        def client = serverCtx.createBean(HttpClient, server.URI)

        when:
        client.toBlocking().retrieve("/h3/connection")
        def connection = serverCtx.getBean(Ctrl).connection
        then:
        connection instanceof QuicChannel
        // still open right after the response, closed once idle
        connection.isActive()
        connection.closeFuture().await(10, TimeUnit.SECONDS)

        cleanup:
        client?.close()
        server?.close()
        serverCtx?.close()
    }

    private ApplicationContext clientContext(SelfSignedCertificate cert) {
        def ks = keyStore(cert)
        return ApplicationContext.run([
                'micronaut.http.client.alpn-modes'                         : ['h3'],
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'micronaut.http.client.ssl.key-store.path'                 : 'file://' + ks,
                'micronaut.http.client.ssl.key-store.type'                 : 'PKCS12',
                'micronaut.http.client.ssl.key-store.password'             : '',
        ])
    }

    private Path keyStore(SelfSignedCertificate certificate) {
        def path = tempFile()
        KeyStore ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry("key", certificate.key(), "".toCharArray(), new Certificate[]{certificate.cert()})
        try (OutputStream os = Files.newOutputStream(path)) {
            ks.store(os, "".toCharArray())
        }
        return path
    }

    private Path tempFile() {
        def path = Files.createTempFile("micronaut-h3-cert-test", ".store")
        tempFiles.add(path)
        return path
    }

    @Controller
    @Requires(property = "spec.name", value = "Http3ConnectionSpec")
    static class Ctrl {
        volatile Channel connection

        @Get('/h3/connection')
        String connection(HttpRequest<?> request) {
            connection = ((NettyHttpRequest<?>) request).channelHandlerContext.channel().parent()
            return "ok"
        }

        @Get('/h3/cert')
        String cert(HttpRequest<?> request) {
            return (request.certificate.get() as X509Certificate).subjectX500Principal.name
        }
    }
}
