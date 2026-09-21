package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.netty.channel.EventLoopGroupRegistry
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class NettyStopSpec extends Specification {

    /**
     * @return a port that is free right now
     */
    private static int freePort() {
        def socket = new ServerSocket(0)
        try {
            return socket.localPort
        } finally {
            socket.close()
        }
    }

    /**
     * @return {@code true} if a plain server socket can be bound to the given port right now
     */
    private static boolean canBind(int port) {
        new ServerSocket(port).close()
        return true
    }

    def 'can shutdown netty and application context'() {
        NettyEmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'NettyStopSpec'])
        def ctx = server.applicationContext

        when:
        server.stop()

        then:
        !ctx.running
    }

    def 'can shutdown netty and keep application context running'() {
        NettyEmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'NettyStopSpec'])
        def ctx = server.applicationContext

        when:
        server.stopServerOnly()

        then:
        ctx.running

        cleanup:
        ctx.stop()
    }

    def 'the port is free once stop returns'() {
        given:
        int port = freePort()
        NettyEmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'            : 'NettyStopSpec',
                'micronaut.server.port': port
        ])

        when:
        server.stop()

        then:
        canBind(port)
    }

    def 'the port is free once stop returns with a shared acceptor event loop group'() {
        given:
        int port = freePort()
        NettyHttpServer server = (NettyHttpServer) ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                    : 'NettyStopSpec',
                'micronaut.server.port'                        : port,
                'micronaut.netty.event-loops.parent.num-threads': 1
        ])
        def registry = server.applicationContext.getBean(EventLoopGroupRegistry)
        // the acceptor group comes from the registry, so it is not owned (and not shut down) by the server
        assert registry.getEventLoopGroup('parent').get().is(server.parentGroup)

        when:
        server.stop()

        then:
        canBind(port)
    }

    def 'the port is free once stopServerOnly returns with a shared acceptor event loop group'() {
        given:
        int port = freePort()
        NettyEmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                    : 'NettyStopSpec',
                'micronaut.server.port'                        : port,
                'micronaut.netty.event-loops.parent.num-threads': 1
        ])
        def ctx = server.applicationContext

        when:
        server.stopServerOnly()

        then:
        ctx.running
        canBind(port)

        cleanup:
        ctx.stop()
    }

    def 'a keep-alive connection cannot be reused once #description returns'(String description, Closure<?> stopMethod) {
        given:
        NettyEmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'NettyStopSpec'])
        def ctx = server.applicationContext
        def socket = new Socket(server.host, server.port)
        socket.soTimeout = 30_000
        def out = new OutputStreamWriter(socket.outputStream, StandardCharsets.US_ASCII)
        def input = new BufferedReader(new InputStreamReader(socket.inputStream, StandardCharsets.US_ASCII))

        when: 'the connection is used for a first request'
        def firstResponse = request(out, input)

        then: 'it is served'
        firstResponse == 'HTTP/1.1 200 OK|pong'

        when: 'the server is stopped and the very same connection is used again'
        stopMethod.call(server)
        def secondResponse = request(out, input)

        then: 'no further request is served on it'
        secondResponse == null

        cleanup:
        socket.close()
        ctx.stop()

        where:
        description       | stopMethod
        'stopServerOnly'  | { NettyEmbeddedServer s -> s.stopServerOnly() }
        'stop'            | { NettyEmbeddedServer s -> s.stop() }
    }

    /**
     * Send a keep-alive HTTP/1.1 request on an already connected socket and read the response.
     *
     * @return the status line and body separated by {@code |}, or {@code null} if the connection
     * did not (or no longer could) carry a response
     */
    private static String request(Writer out, BufferedReader input) {
        try {
            out.write("GET /netty-stop/ping HTTP/1.1\r\nHost: localhost\r\nConnection: keep-alive\r\n\r\n")
            out.flush()
            return readResponse(input)
        } catch (IOException ignored) {
            // the connection was closed by the server, which is what we assert for after the stop
            return null
        }
    }

    private static String readResponse(BufferedReader input) {
        String statusLine = input.readLine()
        if (statusLine == null) {
            return null
        }
        int contentLength = 0
        String header
        while ((header = input.readLine()) != null && !header.isEmpty()) {
            if (header.toLowerCase(Locale.ENGLISH).startsWith('content-length:')) {
                contentLength = Integer.parseInt(header.substring(header.indexOf(':') + 1).trim())
            }
        }
        char[] body = new char[contentLength]
        int read = 0
        while (read < contentLength) {
            int n = input.read(body, read, contentLength - read)
            if (n < 0) {
                break
            }
            read += n
        }
        return statusLine + '|' + new String(body, 0, read)
    }

    @Requires(property = 'spec.name', value = 'NettyStopSpec')
    @Controller('/netty-stop')
    static class PingController {
        @Get('/ping')
        String ping() {
            return 'pong'
        }
    }
}
