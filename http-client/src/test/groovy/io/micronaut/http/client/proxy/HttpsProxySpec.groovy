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
import io.micronaut.http.ssl.ClientSslConfiguration
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import reactor.core.publisher.Mono
import spock.util.concurrent.PollingConditions
import io.netty.handler.ssl.util.SelfSignedCertificate
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.lang.TempDir

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.Certificate
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
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

    void 'client negotiates #version with the target through an https proxy'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, serverOptions + [
                'micronaut.server.http-version': '2.0',
        ])
        TlsProxy proxy = new TlsProxy(proxyCertificate)
        ApplicationContext ctx = ApplicationContext.run(clientConfig + [
                'micronaut.http.client.proxy-type'                               : 'http',
                'micronaut.http.client.proxy-address'                            : "localhost:${proxy.port}",
                'micronaut.http.client.proxy-ssl.insecure-trust-all-certificates': true,
        ] + versionConfig)
        HttpClient client = ctx.createBean(HttpClient, server.URI)

        when:
        String body = client.toBlocking().retrieve(HttpRequest.GET('/https-proxy/version'))

        then: 'the protocol is the one the target negotiated, not the one of the proxy connection'
        body == expected
        proxy.handshakes > 0
        proxy.targets.unique() == ["${server.URI.host}:${server.URI.port}".toString()]

        cleanup:
        client?.close()
        ctx?.close()
        proxy?.close()
        server?.close()

        where:
        version                | serverOptions                     | versionConfig                                                | expected
        'h2 over ALPN'         | secureServerOptions               | ['micronaut.http.client.alpn-modes': ['h2', 'http/1.1']]    | 'HTTP_2_0'
        'http/1.1 over ALPN'   | secureServerOptions               | ['micronaut.http.client.alpn-modes': ['http/1.1']]          | 'HTTP_1_1'
        'h2c'                  | ['spec.name': 'HttpsProxySpec']   | ['micronaut.http.client.plaintext-mode': 'h2c']             | 'HTTP_2_0'
    }

    void 'the host name verifier of the target only checks the target'() {
        given:
        EmbeddedServer server = startServer(true)
        TlsProxy proxy = new TlsProxy(proxyCertificate)
        List<String> hosts = new CopyOnWriteArrayList<>()
        List<Certificate> certificates = new CopyOnWriteArrayList<>()
        ClientSslConfiguration targetSsl = new ClientSslConfiguration()
        targetSsl.insecureTrustAllCertificates = true
        targetSsl.hostnameVerifier = { String host, SSLSession session ->
            hosts << host
            certificates << session.peerCertificates[0]
            true
        } as HostnameVerifier
        ClientSslConfiguration proxySsl = new ClientSslConfiguration()
        proxySsl.insecureTrustAllCertificates = true
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration()
        configuration.sslConfiguration = targetSsl
        configuration.proxySslConfiguration = proxySsl
        configuration.proxyType = Proxy.Type.HTTP
        configuration.proxyAddress = InetSocketAddress.createUnresolved('localhost', proxy.port)
        HttpClient client = HttpClient.create(server.URL, configuration)

        when:
        String body = client.toBlocking().retrieve(HttpRequest.GET('/https-proxy'))

        then:
        body == 'hello'
        hosts.unique() == [server.URI.host]
        certificates.every { it != proxyCertificate.cert() }

        cleanup:
        client?.close()
        proxy?.close()
        server?.close()
    }

    void 'proxy-ssl.endpoint-identification #identification the proxy certificate check for an address it is not issued for'() {
        given:
        EmbeddedServer server = startServer(false)
        TlsProxy proxy = new TlsProxy(proxyCertificate)
        ApplicationContext ctx = ApplicationContext.run(clientConfig + [
                'micronaut.http.client.proxy-type'                         : 'http',
                'micronaut.http.client.proxy-address'                      : "127.0.0.1:${proxy.port}",
                'micronaut.http.client.proxy-ssl.trust-store.path'         : "file:${writeTrustStore()}",
                'micronaut.http.client.proxy-ssl.trust-store.password'     : PASSWORD,
                'micronaut.http.client.proxy-ssl.trust-store.type'         : 'PKCS12',
                'micronaut.http.client.proxy-ssl.endpoint-identification'  : identification == 'keeps',
        ])
        HttpClient client = ctx.createBean(HttpClient, server.URI)

        when:
        Object result
        try {
            result = client.toBlocking().retrieve(HttpRequest.GET('/https-proxy'))
        } catch (HttpClientException e) {
            result = e
        }

        then: 'the certificate is issued for localhost, not 127.0.0.1'
        identification == 'keeps' ? result instanceof HttpClientException : result == 'hello'

        cleanup:
        client?.close()
        ctx?.close()
        proxy?.close()
        server?.close()

        where:
        identification << ['keeps', 'disables']
    }

    void 'proxy-ssl is rejected with HTTP/3'() {
        given:
        ClientSslConfiguration proxySsl = new ClientSslConfiguration()
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration()
        configuration.alpnModes = ['h3']
        configuration.proxySslConfiguration = proxySsl
        configuration.proxyType = Proxy.Type.HTTP
        configuration.proxyAddress = InetSocketAddress.createUnresolved('localhost', 1)

        when:
        HttpClient.create(new URL('https://localhost:1'), configuration)

        then:
        def e = thrown(ConfigurationException)
        e.message.contains('HTTP/3')
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
        HelloController.HITS.set(0)

        when:
        client.toBlocking().retrieve(HttpRequest.GET('/https-proxy'))

        then: 'no request reached the proxy, and none went directly to the target'
        thrown(HttpClientException)
        proxy.targets.isEmpty()
        HelloController.HITS.get() == 0

        cleanup:
        client?.close()
        ctx?.close()
        proxy?.close()
        server?.close()
    }

    void 'proxy-ssl applies to the proxy a proxy selector returns'() {
        given:
        EmbeddedServer server = startServer(true)
        TlsProxy proxy = new TlsProxy(proxyCertificate)
        ClientSslConfiguration targetSsl = new ClientSslConfiguration()
        targetSsl.insecureTrustAllCertificates = true
        ClientSslConfiguration proxySsl = new ClientSslConfiguration()
        proxySsl.insecureTrustAllCertificates = true
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration()
        configuration.sslConfiguration = targetSsl
        configuration.proxySslConfiguration = proxySsl
        configuration.proxySelector = new ProxySelector() {
            @Override
            List<Proxy> select(URI uri) {
                [new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved('localhost', proxy.port))]
            }

            @Override
            void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            }
        }
        HttpClient client = HttpClient.create(server.URL, configuration)

        when:
        String body = client.toBlocking().retrieve(HttpRequest.GET('/https-proxy'))

        then:
        body == 'hello'
        proxy.handshakes > 0
        proxy.targets.unique() == ["${server.URI.host}:${server.URI.port}".toString()]

        cleanup:
        client?.close()
        proxy?.close()
        server?.close()
    }

    void 'the host name verifier of proxy-ssl checks the proxy, and a rejection sends nothing to it'() {
        given:
        EmbeddedServer server = startServer(true)
        TlsProxy proxy = new TlsProxy(proxyCertificate)
        List<String> hosts = new CopyOnWriteArrayList<>()
        List<Certificate> certificates = new CopyOnWriteArrayList<>()
        ClientSslConfiguration targetSsl = new ClientSslConfiguration()
        targetSsl.insecureTrustAllCertificates = true
        ClientSslConfiguration proxySsl = new ClientSslConfiguration()
        proxySsl.insecureTrustAllCertificates = true
        proxySsl.hostnameVerifier = { String host, SSLSession session ->
            hosts << host
            certificates << session.peerCertificates[0]
            accept
        } as HostnameVerifier
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration()
        configuration.sslConfiguration = targetSsl
        configuration.proxySslConfiguration = proxySsl
        configuration.proxyType = Proxy.Type.HTTP
        configuration.proxyAddress = InetSocketAddress.createUnresolved('localhost', proxy.port)
        configuration.proxyUsername = 'user'
        configuration.proxyPassword = 'pass'
        HttpClient client = HttpClient.create(server.URL, configuration)
        HelloController.HITS.set(0)

        when:
        Object result
        try {
            result = client.toBlocking().retrieve(HttpRequest.GET('/https-proxy'))
        } catch (HttpClientException e) {
            result = e
        }

        then: 'the verifier saw the proxy, never the target'
        hosts.unique() == ['localhost']
        certificates.every { it == proxyCertificate.cert() }

        and:
        if (accept) {
            assert result == 'hello'
            assert proxy.targets.size() > 0
        } else {
            assert result instanceof HttpClientException
            assert proxy.targets.isEmpty()
            assert proxy.authorizations.isEmpty()
            assert HelloController.HITS.get() == 0
        }

        cleanup:
        client?.close()
        proxy?.close()
        server?.close()

        where:
        accept << [true, false]
    }

    void 'websocket client connects over #scheme through an https proxy'() {
        given:
        EmbeddedServer server = startServer(scheme == 'wss')
        TlsProxy proxy = new TlsProxy(proxyCertificate)
        ApplicationContext ctx = ApplicationContext.run(clientConfig + [
                'micronaut.http.client.proxy-type'                               : 'http',
                'micronaut.http.client.proxy-address'                            : "localhost:${proxy.port}",
                'micronaut.http.client.proxy-ssl.insecure-trust-all-certificates': true,
        ])
        WebSocketClient client = ctx.createBean(WebSocketClient, new URI(scheme, server.URI.schemeSpecificPart, null))

        when:
        EchoClient endpoint = Mono.from(client.connect(EchoClient, '/https-proxy-echo')).block(Duration.ofSeconds(10))
        endpoint.session.sendSync('hello through the tls proxy')

        then: 'the session is secure only when the target connection is'
        new PollingConditions(timeout: 10).eventually {
            assert endpoint.messages.poll() == 'hello through the tls proxy'
        }
        endpoint.session.secure == (scheme == 'wss')
        proxy.handshakes > 0
        proxy.targets == ["${server.URI.host}:${server.URI.port}".toString()]

        cleanup:
        endpoint?.close()
        client?.close()
        ctx?.close()
        proxy?.close()
        server?.close()

        where:
        scheme << ['ws', 'wss']
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

    private static Map<String, Object> getSecureServerOptions() {
        [
                'spec.name'                             : 'HttpsProxySpec',
                'micronaut.server.ssl.enabled'          : true,
                'micronaut.server.ssl.port'             : -1,
                'micronaut.server.ssl.build-self-signed': true,
        ]
    }

    private static EmbeddedServer startServer(boolean secure) {
        ApplicationContext.run(EmbeddedServer, secure ? secureServerOptions : ['spec.name': 'HttpsProxySpec'])
    }

    @Requires(property = 'spec.name', value = 'HttpsProxySpec')
    @Controller('/https-proxy')
    static class HelloController {
        static final AtomicInteger HITS = new AtomicInteger()

        @Get(produces = 'text/plain')
        String hello() {
            HITS.incrementAndGet()
            'hello'
        }

        @Get(uri = '/version', produces = 'text/plain')
        String version(HttpRequest<?> request) {
            request.httpVersion.name()
        }
    }

    @Requires(property = 'spec.name', value = 'HttpsProxySpec')
    @Client(id = 'proxied', path = '/https-proxy')
    static interface ProxiedClient {
        @Get(consumes = 'text/plain')
        String hello()
    }

    @Requires(property = 'spec.name', value = 'HttpsProxySpec')
    @ServerWebSocket('/https-proxy-echo')
    static class EchoServer {
        @OnMessage
        void onMessage(String message, WebSocketSession session) {
            session.sendSync(message)
        }
    }

    @Requires(property = 'spec.name', value = 'HttpsProxySpec')
    @ClientWebSocket
    static abstract class EchoClient implements AutoCloseable {
        final Queue<String> messages = new ConcurrentLinkedQueue<>()
        WebSocketSession session

        @OnOpen
        void onOpen(WebSocketSession session) { this.session = session }

        @OnMessage
        void onMessage(String message) { messages.add(message) }
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
