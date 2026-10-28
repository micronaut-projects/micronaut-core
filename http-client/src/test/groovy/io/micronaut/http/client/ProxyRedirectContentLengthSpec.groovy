package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * A relayed request with a body that is redirected with 307 is sent again without that body:
 * the request head the upstream receives must not claim the body it no longer has, or the
 * upstream takes the start of the next request on the pooled connection as the body.
 */
class ProxyRedirectContentLengthSpec extends Specification {

    void "a proxied #method with a body that is redirected with 307 is not sent on with a stale content-length"() {
        given:
        ServerSocket upstream = new ServerSocket(0, 50, InetAddress.loopbackAddress)
        CompletableFuture<String> redirected = new CompletableFuture<>()
        Thread.start {
            while (!upstream.closed) {
                Socket socket
                try {
                    socket = upstream.accept()
                } catch (IOException ignored) {
                    return
                }
                Thread.start {
                    try {
                        while (!redirected.done) {
                            String received = readHead(socket.inputStream)
                            if (received.startsWith("$method /old ")) {
                                socket.inputStream.readNBytes(5)
                                socket.outputStream.write("HTTP/1.1 307 Temporary Redirect\r\nLocation: /new\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
                            } else {
                                socket.outputStream.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
                                redirected.complete(received)
                            }
                        }
                    } catch (IOException ignored) {
                    } finally {
                        socket.close()
                    }
                }
            }
        }
        EmbeddedServer gateway = ApplicationContext.run(EmbeddedServer, [
            'spec.name'    : 'ProxyRedirectContentLengthSpec',
            'upstream.port': upstream.localPort,
        ])

        when:
        Socket client = new Socket(gateway.host, gateway.port)
        client.outputStream.write("$method /old HTTP/1.1\r\nHost: gateway\r\nContent-Length: 5\r\nConnection: close\r\n\r\nhello".getBytes(StandardCharsets.ISO_8859_1))
        String head = redirected.get(10, TimeUnit.SECONDS)

        then: 'the redirected request does not announce a body, nothing follows its head'
        head.startsWith("$method /new ")
        !head.toLowerCase(Locale.ROOT).contains("content-length")
        !head.toLowerCase(Locale.ROOT).contains("transfer-encoding")

        cleanup:
        client?.close()
        gateway.close()
        upstream.close()

        where:
        method << ['GET', 'HEAD']
    }

    private static String readHead(InputStream stream) {
        StringBuilder head = new StringBuilder()
        while (!head.toString().endsWith("\r\n\r\n")) {
            int b = stream.read()
            if (b < 0) {
                throw new EOFException("Connection closed after: " + head)
            }
            head.append((char) b)
        }
        return head.toString()
    }

    @Requires(property = 'spec.name', value = 'ProxyRedirectContentLengthSpec')
    @Filter('/**')
    static class GatewayFilter implements HttpServerFilter {
        private final ProxyHttpClient client
        private final int port

        GatewayFilter(ProxyHttpClient client, @Value('${upstream.port}') int port) {
            this.client = client
            this.port = port
        }

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return client.proxy(request.mutate().uri(uri -> uri.scheme('http').host('127.0.0.1').port(port)))
        }
    }
}
