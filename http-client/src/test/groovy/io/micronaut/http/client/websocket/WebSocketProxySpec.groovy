package io.micronaut.http.client.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import reactor.core.publisher.Mono
import spock.lang.Issue
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * The websocket client connects through the proxy configured for the HTTP client.
 */
@Issue('https://github.com/micronaut-projects/micronaut-core/issues/14446')
class WebSocketProxySpec extends Specification {

    void 'websocket client connects through an http proxy over #scheme'() {
        given:
        EmbeddedServer server = startServer(scheme == 'wss')
        TestProxy proxy = new TestProxy(TestProxy.Mode.HTTP)
        ApplicationContext ctx = ApplicationContext.run(clientConfig + [
                'micronaut.http.client.proxy-type'   : 'http',
                'micronaut.http.client.proxy-address': "127.0.0.1:${proxy.port}",
        ])
        WebSocketClient client = ctx.createBean(WebSocketClient, uri(server, scheme))

        when:
        EchoClient endpoint = connect(client)

        then: 'the connection went through the proxy'
        proxy.targets == ["${server.URI.host}:${server.URI.port}".toString()]

        and: 'messages are exchanged through the tunnel'
        echoes(endpoint, 'hello through the proxy')

        cleanup:
        endpoint?.close()
        client?.close()
        ctx?.close()
        proxy?.close()
        server?.close()

        where:
        scheme << ['ws', 'wss']
    }

    void 'websocket client sends the configured credentials to an http proxy'() {
        given:
        EmbeddedServer server = startServer(true)
        TestProxy proxy = new TestProxy(TestProxy.Mode.HTTP)
        ApplicationContext ctx = ApplicationContext.run(clientConfig + [
                'micronaut.http.client.proxy-type'    : 'http',
                'micronaut.http.client.proxy-address' : "127.0.0.1:${proxy.port}",
                'micronaut.http.client.proxy-username': 'user',
                'micronaut.http.client.proxy-password': 'secret',
        ])
        WebSocketClient client = ctx.createBean(WebSocketClient, uri(server, 'wss'))

        when:
        EchoClient endpoint = connect(client)

        then:
        proxy.targets.size() == 1
        proxy.authorizations == ['Basic ' + Base64.encoder.encodeToString('user:secret'.getBytes(StandardCharsets.US_ASCII))]
        echoes(endpoint, 'hello with credentials')

        cleanup:
        endpoint?.close()
        client?.close()
        ctx?.close()
        proxy?.close()
        server?.close()
    }

    void 'websocket client connects through a socks proxy over #scheme'() {
        given:
        EmbeddedServer server = startServer(scheme == 'wss')
        TestProxy proxy = new TestProxy(TestProxy.Mode.SOCKS5)
        ApplicationContext ctx = ApplicationContext.run(clientConfig + [
                'micronaut.http.client.proxy-type'   : 'socks',
                'micronaut.http.client.proxy-address': "127.0.0.1:${proxy.port}",
        ])
        WebSocketClient client = ctx.createBean(WebSocketClient, uri(server, scheme))

        when:
        EchoClient endpoint = connect(client)

        then:
        proxy.targets == ["${server.URI.host}:${server.URI.port}".toString()]
        echoes(endpoint, 'hello through socks')

        cleanup:
        endpoint?.close()
        client?.close()
        ctx?.close()
        proxy?.close()
        server?.close()

        where:
        scheme << ['ws', 'wss']
    }

    void 'websocket client does not connect directly when the proxy is unreachable'() {
        given:
        EmbeddedServer server = startServer(false)
        int closedPort = new ServerSocket(0, 50, InetAddress.loopbackAddress).withCloseable { it.localPort }
        ApplicationContext ctx = ApplicationContext.run(clientConfig + [
                'micronaut.http.client.proxy-type'   : 'http',
                'micronaut.http.client.proxy-address': "127.0.0.1:${closedPort}",
        ])
        WebSocketClient client = ctx.createBean(WebSocketClient, uri(server, 'ws'))

        when:
        Mono.from(client.connect(EchoClient, '/proxied-echo')).toFuture().get(10, TimeUnit.SECONDS)

        then:
        thrown(ExecutionException)

        cleanup:
        client?.close()
        ctx?.close()
        server?.close()
    }

    private static Map<String, Object> getClientConfig() {
        [
                'spec.name'                                                : 'WebSocketProxySpec',
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ]
    }

    private static EmbeddedServer startServer(boolean secure) {
        Map<String, Object> options = ['spec.name': 'WebSocketProxySpec']
        if (secure) {
            options += [
                    'micronaut.server.ssl.enabled'          : true,
                    'micronaut.server.ssl.port'             : -1,
                    'micronaut.server.ssl.build-self-signed': true,
            ]
        }
        ApplicationContext.run(EmbeddedServer, options)
    }

    private static URI uri(EmbeddedServer server, String scheme) {
        new URI(scheme, server.URI.schemeSpecificPart, null)
    }

    private static EchoClient connect(WebSocketClient client) {
        Mono.from(client.connect(EchoClient, '/proxied-echo')).block(Duration.ofSeconds(10))
    }

    private static boolean echoes(EchoClient endpoint, String message) {
        endpoint.session.sendSync(message)
        new PollingConditions(timeout: 10).eventually {
            assert endpoint.messages.poll() == message
        }
        true
    }

    @Requires(property = 'spec.name', value = 'WebSocketProxySpec')
    @ServerWebSocket('/proxied-echo')
    static class EchoServer {
        @OnMessage
        void onMessage(String message, WebSocketSession session) {
            session.sendSync(message)
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketProxySpec')
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
     * A minimal proxy: an HTTP proxy that accepts {@code CONNECT}, or a SOCKS5 proxy without
     * authentication. It records the requested targets, opens the tunnel and relays the bytes in
     * both directions.
     */
    static class TestProxy implements AutoCloseable {
        enum Mode { HTTP, SOCKS5 }

        final List<String> targets = new CopyOnWriteArrayList<>()
        final List<String> authorizations = new CopyOnWriteArrayList<>()

        private final Mode mode
        private final ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.loopbackAddress)
        private final List<Socket> sockets = new CopyOnWriteArrayList<>()

        TestProxy(Mode mode) {
            this.mode = mode
            Thread.startDaemon('websocket-test-proxy') {
                while (!serverSocket.closed) {
                    try {
                        Socket client = serverSocket.accept()
                        sockets << client
                        Thread.startDaemon('websocket-test-proxy-connection') { handle(client) }
                    } catch (IOException ignored) {
                        // the proxy was closed
                    }
                }
            }
        }

        int getPort() {
            serverSocket.localPort
        }

        private void handle(Socket client) {
            try {
                DataInputStream input = new DataInputStream(client.inputStream)
                OutputStream output = client.outputStream
                String target = mode == Mode.HTTP ? readConnect(input) : readSocks(input, output)
                if (target == null) {
                    client.close()
                    return
                }
                targets << target
                int colon = target.lastIndexOf(':')
                String host = target.substring(0, colon).replace('[', '').replace(']', '')
                Socket upstream = new Socket(host, target.substring(colon + 1) as int)
                sockets << upstream
                if (mode == Mode.HTTP) {
                    output.write('HTTP/1.1 200 Connection established\r\n\r\n'.getBytes(StandardCharsets.US_ASCII))
                } else {
                    output.write([5, 0, 0, 1, 0, 0, 0, 0, 0, 0] as byte[])
                }
                output.flush()
                Thread.startDaemon('websocket-test-proxy-upstream') { relay(input, upstream.outputStream) }
                relay(upstream.inputStream, output)
            } catch (IOException ignored) {
                // a side closed the connection
            }
        }

        /**
         * Reads an HTTP proxy request and returns its {@code CONNECT} target.
         */
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

        /**
         * Reads a SOCKS5 greeting and connect request (RFC 1928) and returns the target.
         */
        private static String readSocks(DataInputStream input, OutputStream output) {
            if (input.readUnsignedByte() != 5) {
                return null
            }
            input.skipNBytes(input.readUnsignedByte()) // offered authentication methods
            output.write([5, 0] as byte[]) // no authentication
            output.flush()
            input.readUnsignedByte() // version
            int command = input.readUnsignedByte()
            input.readUnsignedByte() // reserved
            String host
            switch (input.readUnsignedByte()) {
                case 1:
                    byte[] ipv4 = new byte[4]
                    input.readFully(ipv4)
                    host = InetAddress.getByAddress(ipv4).hostAddress
                    break
                case 3:
                    byte[] name = new byte[input.readUnsignedByte()]
                    input.readFully(name)
                    host = new String(name, StandardCharsets.US_ASCII)
                    break
                case 4:
                    byte[] ipv6 = new byte[16]
                    input.readFully(ipv6)
                    host = '[' + InetAddress.getByAddress(ipv6).hostAddress + ']'
                    break
                default:
                    return null
            }
            int port = input.readUnsignedShort()
            command == 1 ? "${host}:${port}".toString() : null
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
