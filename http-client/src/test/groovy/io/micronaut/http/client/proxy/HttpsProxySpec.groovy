package io.micronaut.http.client.proxy

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.http.client.ServiceHttpClientConfiguration
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.ssl.AbstractClientSslConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.handler.ssl.util.SelfSignedCertificate
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.lang.TempDir

import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.Certificate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * The client connects to an HTTP proxy over TLS when {@code proxy-ssl} is configured, and tunnels
 * the request with {@code CONNECT} inside that connection.
 */
class HttpsProxySpec extends Specification {

    private static final String PASSWORD = 'secret'

    @TempDir
    Path tempDir

    SelfSignedCertificate proxyCertificate = new SelfSignedCertificate('localhost')

    void cleanup() {
        proxyCertificate.delete()
    }

    void 'client sends a request to an #scheme target through an https proxy'() {
        given:
        EmbeddedServer server = startServer(scheme == 'https')
        TlsProxy proxy = new TlsProxy(proxyCertificate)
        ApplicationContext ctx = ApplicationContext.run(clientConfig + [
                'micronaut.http.client.proxy-type'                       : 'http',
                'micronaut.http.client.proxy-address'                    : "localhost:${proxy.port}",
                'micronaut.http.client.proxy-ssl.trust-store.path'       : "file:${writeTrustStore()}",
                'micronaut.http.client.proxy-ssl.trust-store.password'   : PASSWORD,
                'micronaut.http.client.proxy-ssl.trust-store.type'       : 'PKCS12',
        ])
        HttpClient client = ctx.createBean(HttpClient, server.URI)

        when:
        String body = client.toBlocking().retrieve(HttpRequest.GET('/https-proxy'))

        then: 'the request went through a TLS connection to the proxy'
        body == 'hello'
        proxy.handshakes > 0
        proxy.targets.unique() == ["${server.URI.host}:${server.URI.port}".toString()]

        cleanup:
        client?.close()
        ctx?.close()
        proxy?.close()
        server?.close()

        where:
        scheme << ['http', 'https']
    }

    void 'client sends the configured credentials inside the TLS connection to the proxy'() {
        given:
        EmbeddedServer server = startServer(true)
        TlsProxy proxy = new TlsProxy(proxyCertificate)
        ApplicationContext ctx = ApplicationContext.run(clientConfig + [
                'micronaut.http.client.proxy-type'                               : 'http',
                'micronaut.http.client.proxy-address'                            : "localhost:${proxy.port}",
                'micronaut.http.client.proxy-username'                           : 'user',
                'micronaut.http.client.proxy-password'                           : 'pass',
                'micronaut.http.client.proxy-ssl.insecure-trust-all-certificates': true,
        ])
        HttpClient client = ctx.createBean(HttpClient, server.URI)

        when:
        String body = client.toBlocking().retrieve(HttpRequest.GET('/https-proxy'))

        then:
        body == 'hello'
        proxy.authorizations.unique() == ['Basic ' + Base64.encoder.encodeToString('user:pass'.getBytes(StandardCharsets.US_ASCII))]

        cleanup:
        client?.close()
        ctx?.close()
        proxy?.close()
        server?.close()
    }

    void 'client fails when it does not trust the certificate of the proxy'() {
        given:
        EmbeddedServer server = startServer(false)
        TlsProxy proxy = new TlsProxy(proxyCertificate)
        ApplicationContext ctx = ApplicationContext.run(clientConfig + [
                'micronaut.http.client.proxy-type'       : 'http',
                'micronaut.http.client.proxy-address'    : "localhost:${proxy.port}",
                'micronaut.http.client.proxy-ssl.enabled': true,
        ])
        HttpClient client = ctx.createBean(HttpClient, server.URI)

        when:
        client.toBlocking().retrieve(HttpRequest.GET('/https-proxy'))

        then: 'no request reached the proxy, and none went directly to the target'
        thrown(HttpClientException)
        proxy.targets.isEmpty()

        cleanup:
        client?.close()
        ctx?.close()
        proxy?.close()
        server?.close()
    }

    void 'proxy-ssl can be disabled explicitly'() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.http.client.proxy-ssl.enabled'             : false,
                'micronaut.http.client.proxy-ssl.trust-store.path'    : 'classpath:certs/truststore',
                'micronaut.http.client.proxy-ssl.trust-store.password': PASSWORD,
        ])

        when:
        HttpClientConfiguration configuration = ctx.getBean(DefaultHttpClientConfiguration)

        then:
        configuration.proxySslConfiguration.isPresent()
        !configuration.proxySslConfiguration.get().enabled
        configuration.proxySslConfiguration.get().trustStore.path.get() == 'classpath:certs/truststore'

        cleanup:
        ctx.close()
    }

    void 'proxy-ssl is not configured by default'() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        expect:
        ctx.getBean(DefaultHttpClientConfiguration).proxySslConfiguration.isEmpty()
        ctx.getBean(DefaultHttpClientConfiguration).sslConfiguration.enabled

        cleanup:
        ctx.close()
    }

    void 'a service binds its own proxy-ssl, and otherwise inherits the default one'() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.http.client.proxy-ssl.insecure-trust-all-certificates': true,
                'micronaut.http.services.own.url'                                : 'http://localhost:1',
                'micronaut.http.services.own.proxy-ssl.trust-store.path'         : 'classpath:certs/truststore',
                'micronaut.http.services.own.proxy-ssl.trust-store.password'     : PASSWORD,
                'micronaut.http.services.inherited.url'                          : 'http://localhost:1',
        ])

        when:
        Collection<ServiceHttpClientConfiguration> services = ctx.getBeansOfType(ServiceHttpClientConfiguration)
        ServiceHttpClientConfiguration own = services.find { it.serviceId == 'own' }
        ServiceHttpClientConfiguration inherited = services.find { it.serviceId == 'inherited' }

        then:
        own.proxySslConfiguration.get().enabled
        own.proxySslConfiguration.get().trustStore.path.get() == 'classpath:certs/truststore'
        !((AbstractClientSslConfiguration) own.proxySslConfiguration.get()).insecureTrustAllCertificates

        inherited.proxySslConfiguration.get().enabled
        ((AbstractClientSslConfiguration) inherited.proxySslConfiguration.get()).insecureTrustAllCertificates

        cleanup:
        ctx.close()
    }

    void 'a declarative client of a service connects through its https proxy'() {
        given:
        EmbeddedServer server = startServer(true)
        TlsProxy proxy = new TlsProxy(proxyCertificate)
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name'                                                          : 'HttpsProxySpec',
                'micronaut.http.services.proxied.url'                                : server.URI.toString(),
                'micronaut.http.services.proxied.proxy-type'                         : 'http',
                'micronaut.http.services.proxied.proxy-address'                      : "localhost:${proxy.port}",
                'micronaut.http.services.proxied.ssl.insecure-trust-all-certificates': true,
                'micronaut.http.services.proxied.proxy-ssl.trust-store.path'         : "file:${writeTrustStore()}",
                'micronaut.http.services.proxied.proxy-ssl.trust-store.password'     : PASSWORD,
                'micronaut.http.services.proxied.proxy-ssl.trust-store.type'         : 'PKCS12',
        ])

        when:
        String body = ctx.getBean(ProxiedClient).hello()

        then:
        body == 'hello'
        proxy.handshakes > 0
        proxy.targets.unique() == ["${server.URI.host}:${server.URI.port}".toString()]

        cleanup:
        ctx?.close()
        proxy?.close()
        server?.close()
    }

    private Path writeTrustStore() {
        KeyStore trustStore = KeyStore.getInstance('PKCS12')
        trustStore.load(null, null)
        trustStore.setCertificateEntry('proxy', proxyCertificate.cert())
        Path path = tempDir.resolve('proxy-truststore.p12')
        Files.newOutputStream(path).withCloseable { trustStore.store(it, PASSWORD.toCharArray()) }
        path
    }

    private static Map<String, Object> getClientConfig() {
        [
                'spec.name'                                                : 'HttpsProxySpec',
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ]
    }

    private static EmbeddedServer startServer(boolean secure) {
        Map<String, Object> options = ['spec.name': 'HttpsProxySpec']
        if (secure) {
            options += [
                    'micronaut.server.ssl.enabled'          : true,
                    'micronaut.server.ssl.port'             : -1,
                    'micronaut.server.ssl.build-self-signed': true,
            ]
        }
        ApplicationContext.run(EmbeddedServer, options)
    }

    @Requires(property = 'spec.name', value = 'HttpsProxySpec')
    @Controller('/https-proxy')
    static class HelloController {
        @Get(produces = 'text/plain')
        String hello() {
            'hello'
        }
    }

    @Requires(property = 'spec.name', value = 'HttpsProxySpec')
    @Client(id = 'proxied', path = '/https-proxy')
    static interface ProxiedClient {
        @Get(consumes = 'text/plain')
        String hello()
    }

    /**
     * A minimal HTTPS proxy: it accepts TLS connections with the given certificate, reads a
     * {@code CONNECT} request inside them, records it, opens the tunnel and relays the bytes in
     * both directions.
     */
    static class TlsProxy implements AutoCloseable {
        final List<String> targets = new CopyOnWriteArrayList<>()
        final List<String> authorizations = new CopyOnWriteArrayList<>()
        final AtomicInteger handshakeCount = new AtomicInteger()

        int getHandshakes() {
            handshakeCount.get()
        }

        private final SSLServerSocket serverSocket
        private final List<Socket> sockets = new CopyOnWriteArrayList<>()

        TlsProxy(SelfSignedCertificate certificate) {
            KeyStore keyStore = KeyStore.getInstance('PKCS12')
            keyStore.load(null, null)
            keyStore.setKeyEntry('proxy', certificate.key(), PASSWORD.toCharArray(), [certificate.cert()] as Certificate[])
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.defaultAlgorithm)
            kmf.init(keyStore, PASSWORD.toCharArray())
            SSLContext sslContext = SSLContext.getInstance('TLS')
            sslContext.init(kmf.keyManagers, null, null)
            serverSocket = (SSLServerSocket) sslContext.serverSocketFactory.createServerSocket(0, 50, InetAddress.loopbackAddress)
            Thread.startDaemon('https-test-proxy') {
                while (!serverSocket.closed) {
                    try {
                        SSLSocket client = (SSLSocket) serverSocket.accept()
                        sockets << client
                        Thread.startDaemon('https-test-proxy-connection') { handle(client) }
                    } catch (IOException ignored) {
                        // the proxy was closed
                    }
                }
            }
        }

        int getPort() {
            serverSocket.localPort
        }

        private void handle(SSLSocket client) {
            try {
                client.startHandshake()
                handshakeCount.incrementAndGet()
                InputStream input = client.inputStream
                OutputStream output = client.outputStream
                String target = readConnect(input)
                if (target == null) {
                    client.close()
                    return
                }
                targets << target
                int colon = target.lastIndexOf(':')
                String host = target.substring(0, colon).replace('[', '').replace(']', '')
                Socket upstream = new Socket(host, target.substring(colon + 1) as int)
                sockets << upstream
                output.write('HTTP/1.1 200 Connection established\r\n\r\n'.getBytes(StandardCharsets.US_ASCII))
                output.flush()
                Thread.startDaemon('https-test-proxy-upstream') { relay(input, upstream.outputStream) }
                relay(upstream.inputStream, output)
            } catch (IOException ignored) {
                // a side closed the connection, or the handshake failed
            } finally {
                client.close()
            }
        }

        private String readConnect(InputStream input) {
            StringBuilder head = new StringBuilder()
            while (!head.toString().endsWith('\r\n\r\n')) {
                int b = input.read()
                if (b < 0) {
                    return null
                }
                head.append((char) b)
            }
            List<String> lines = head.readLines()
            lines.drop(1).each { line ->
                int colon = line.indexOf(':')
                if (colon > 0 && line.substring(0, colon).equalsIgnoreCase('proxy-authorization')) {
                    authorizations << line.substring(colon + 1).trim()
                }
            }
            String[] requestLine = lines[0].split(' ')
            requestLine[0] == 'CONNECT' ? requestLine[1] : null
        }

        private static void relay(InputStream from, OutputStream to) {
            try {
                byte[] buffer = new byte[8192]
                int n
                while ((n = from.read(buffer)) >= 0) {
                    to.write(buffer, 0, n)
                    to.flush()
                }
            } catch (IOException ignored) {
                // a side closed the tunnel
            }
        }

        @Override
        void close() {
            serverSocket.close()
            sockets.each { it.close() }
        }
    }
}
