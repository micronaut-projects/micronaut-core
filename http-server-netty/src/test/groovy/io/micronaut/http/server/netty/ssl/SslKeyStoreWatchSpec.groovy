package io.micronaut.http.server.netty.ssl

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.watch.ReloadingConfigurationWatcher
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.context.scope.refresh.ConfigurationRefresher
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.pkitesting.CertificateBuilder
import io.netty.pkitesting.X509Bundle
import spock.lang.Specification

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.cert.X509Certificate

class SslKeyStoreWatchSpec extends Specification {

    void "a key store change through the refresher serves the new certificate on the next connection"() {
        given:
        X509Bundle first = new CertificateBuilder().subject("CN=first").setIsCertificateAuthority(true).buildSelfSigned()
        X509Bundle second = new CertificateBuilder().subject("CN=second").setIsCertificateAuthority(true).buildSelfSigned()
        Map<String, Object> values = [
            "micronaut.server.ssl.port": -1,
            "micronaut.server.ssl.enabled": true,
            "micronaut.server.ssl.key-store.key-path": "string:" + first.privateKeyPEM,
            "micronaut.server.ssl.key-store.certificate-path": "string:" + first.certificatePathPEM,
        ]
        def server = ApplicationContext.run(EmbeddedServer, values)
        def context = server.applicationContext

        expect:
        servedSubject(server) == "CN=first"

        when: "the certificate changes and the configuration is refreshed"
        values["micronaut.server.ssl.key-store.key-path"] = "string:" + second.privateKeyPEM
        values["micronaut.server.ssl.key-store.certificate-path"] = "string:" + second.certificatePathPEM
        def result = context.getBean(ConfigurationRefresher).refresh()

        then: "the server rebuilt its pipeline in place and a fresh connection is served the new certificate"
        !result.requiresRestart()
        servedSubject(server) == "CN=second"

        cleanup:
        server.close()
    }

    void "a key store file replaced in place is read again when the configuration is refreshed"() {
        given:
        X509Bundle first = new CertificateBuilder().subject("CN=first").setIsCertificateAuthority(true).buildSelfSigned()
        X509Bundle second = new CertificateBuilder().subject("CN=second").setIsCertificateAuthority(true).buildSelfSigned()
        Path dir = Files.createTempDirectory("ssl-watch")
        Path key = dir.resolve("key.pem")
        Path certificate = dir.resolve("cert.pem")
        Files.writeString(key, first.privateKeyPEM)
        Files.writeString(certificate, first.certificatePathPEM)
        def server = ApplicationContext.run(EmbeddedServer, [
            "micronaut.server.ssl.port": -1,
            "micronaut.server.ssl.enabled": true,
            "micronaut.server.ssl.key-store.key-path": "file:" + key,
            "micronaut.server.ssl.key-store.certificate-path": "file:" + certificate,
        ])
        def context = server.applicationContext

        expect:
        servedSubject(server) == "CN=first"

        when: "the files are rewritten under the same paths and everything is refreshed"
        Files.writeString(key, second.privateKeyPEM)
        Files.writeString(certificate, second.certificatePathPEM)
        def result = context.getBean(ConfigurationRefresher).refreshAll()

        then: "the store was read again before the pipelines were rebuilt"
        !result.requiresRestart()
        servedSubject(server) == "CN=second"

        cleanup:
        server.close()
        key.toFile().delete()
        certificate.toFile().delete()
        dir.toFile().delete()
    }

    void "an ssl change refreshed together with a key that needs a restart reaches the next connection"() {
        given:
        X509Bundle bundle = new CertificateBuilder().subject("CN=server").setIsCertificateAuthority(true).buildSelfSigned()
        Map<String, Object> values = [
            "spec.name": "SslKeyStoreWatchSpec",
            "micronaut.server.ssl.port": -1,
            "micronaut.server.ssl.enabled": true,
            "micronaut.server.dual-protocol": false,
            "micronaut.server.ssl.key-store.key-path": "string:" + bundle.privateKeyPEM,
            "micronaut.server.ssl.key-store.certificate-path": "string:" + bundle.certificatePathPEM,
        ]
        def server = ApplicationContext.run(EmbeddedServer, values)
        def context = server.applicationContext

        expect: "a client without a certificate is served"
        requestWithoutClientCertificate(server).startsWith("HTTP/1.1 200")

        when: "client authentication is required and dual-protocol changes in the same refresh"
        values["micronaut.server.ssl.client-authentication"] = "need"
        values["micronaut.server.dual-protocol"] = true
        def result = context.getBean(ConfigurationRefresher).refresh()

        then: "dual-protocol needs a restart, but the new client authentication applies to the next connection"
        result.outcomes() == [ReloadingConfigurationWatcher.Outcome.REQUIRES_RESTART]
        requestWithoutClientCertificate(server) == null

        cleanup:
        server.close()
    }

    @Requires(property = "spec.name", value = "SslKeyStoreWatchSpec")
    @Controller("/ssl-watch")
    static class SslWatchController {
        @Get
        String index() {
            "ok"
        }
    }

    /**
     * @return The status line of a request on a new connection without a client certificate, or null if the
     * TLS session was refused
     */
    private static String requestWithoutClientCertificate(EmbeddedServer server) {
        SSLContext sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, [new X509TrustManager() {
            X509Certificate[] getAcceptedIssuers() { new X509Certificate[0] }
            void checkClientTrusted(X509Certificate[] chain, String authType) {}
            void checkServerTrusted(X509Certificate[] chain, String authType) {}
        }] as TrustManager[], null)
        try (SSLSocket socket = (SSLSocket) sslContext.socketFactory.createSocket("localhost", server.port)) {
            socket.soTimeout = 10_000
            socket.startHandshake()
            socket.outputStream.write("GET /ssl-watch HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
            socket.outputStream.flush()
            String response = new String(socket.inputStream.readAllBytes(), StandardCharsets.ISO_8859_1)
            return response.isEmpty() ? null : response.substring(0, response.indexOf("\r\n"))
        } catch (SSLException ignored) {
            return null
        }
    }

    private static String servedSubject(EmbeddedServer server) {
        SSLContext sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, [new X509TrustManager() {
            X509Certificate[] getAcceptedIssuers() { new X509Certificate[0] }
            void checkClientTrusted(X509Certificate[] chain, String authType) {}
            void checkServerTrusted(X509Certificate[] chain, String authType) {}
        }] as TrustManager[], null)
        try (SSLSocket socket = (SSLSocket) sslContext.socketFactory.createSocket("localhost", server.port)) {
            socket.startHandshake()
            return ((X509Certificate) socket.session.peerCertificates[0]).subjectX500Principal.name
        }
    }
}
