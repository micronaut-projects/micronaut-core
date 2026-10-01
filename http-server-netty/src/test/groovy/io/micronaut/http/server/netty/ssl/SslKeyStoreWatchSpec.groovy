package io.micronaut.http.server.netty.ssl

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.context.scope.refresh.ConfigurationRefresher
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.pkitesting.CertificateBuilder
import io.netty.pkitesting.X509Bundle
import spock.lang.Specification

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
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
