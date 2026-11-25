package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.RawHttpClient
import io.micronaut.http.client.RawRequestOptions
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/**
 * A raw exchange with {@link RawRequestOptions#isRetryStaleConnection()} turned off leaves the
 * retries to the caller, e.g. a gateway: the client does not send an idempotent request again
 * when its reused connection was closed before any response, unlike an exchange with the
 * default options, see {@link StaleConnectionRetrySpec}.
 */
class StaleConnectionRawProxySpec extends Specification {

    static final RawRequestOptions NO_RETRY = RawRequestOptions.proxy().toBuilder().retryStaleConnection(false).build()

    @AutoCleanup
    StaleConnectionRetrySpec.RawServer server = new StaleConnectionRetrySpec.RawServer({ int connection, int requestOnConnection -> requestOnConnection == 0 })

    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(['micronaut.http.client.read-timeout': '10s'])

    @AutoCleanup
    RawHttpClient client = ctx.createBean(RawHttpClient)

    private int exchange(String path, RawRequestOptions options) {
        HttpResponse<?> response = Mono.from(client.exchange(HttpRequest.GET("http://127.0.0.1:${server.port}$path"), null, null, options)).block()
        try {
            return response.code()
        } finally {
            if (response instanceof AutoCloseable) {
                ((AutoCloseable) response).close()
            }
            // let the connection go back to the pool before the next request is sent
            Thread.sleep(100)
        }
    }

    void "an exchange that does not retry a closed connection is not sent again"() {
        expect:
        exchange('/first', NO_RETRY) == 200

        when:
        exchange('/second', NO_RETRY)

        then: "the exchange fails with the closed connection, and the request was sent once"
        thrown(Exception)
        server.requests*.toString() == ['0/0 GET /first', '0/1 GET /second']
        server.connections.get() == 1
    }

    void "a redirect of an exchange that does not retry a closed connection is not sent again"() {
        given: "a server that redirects to the closing server"
        ServerSocket redirecting = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        Thread.startVirtualThread {
            try {
                redirecting.accept().withCloseable { Socket socket ->
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.inputStream, StandardCharsets.US_ASCII))
                    String line
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        // skip the request head
                    }
                    socket.outputStream.write("HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:${server.port}/second\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
                    socket.outputStream.flush()
                }
            } catch (IOException ignored) {
                // closed
            }
        }
        RawRequestOptions noRetryFollowing = NO_RETRY.toBuilder().followRedirects(true).build()

        expect:
        exchange('/first', NO_RETRY) == 200

        when:
        Mono.from(client.exchange(HttpRequest.GET("http://127.0.0.1:${redirecting.localPort}/redirect"), null, null, noRetryFollowing)).block()

        then: "the redirected request fails with the closed connection, and was sent once"
        thrown(Exception)
        server.requests*.toString() == ['0/0 GET /first', '0/1 GET /second']
        server.connections.get() == 1

        cleanup:
        redirecting.close()
    }

    void "a request that a client filter replaced does not retry a closed connection either"() {
        given: "a client filter that replaces the request with a new one, without the attributes of the exchange"
        ApplicationContext filtering = ApplicationContext.run(['micronaut.http.client.read-timeout': '10s', 'spec.name': 'StaleConnectionRawProxySpec'])
        RawHttpClient filteringClient = filtering.createBean(RawHttpClient)

        when:
        HttpResponse<?> first = Mono.from(filteringClient.exchange(HttpRequest.GET("http://127.0.0.1:${server.port}/first"), null, null, NO_RETRY)).block()
        ((AutoCloseable) first).close()
        Thread.sleep(100)
        Mono.from(filteringClient.exchange(HttpRequest.GET("http://127.0.0.1:${server.port}/second"), null, null, NO_RETRY)).block()

        then: "the replacement is not sent again on a new connection"
        thrown(Exception)
        filtering.getBean(ReplacingFilter).replaced.get() == 2
        server.requests*.toString() == ['0/0 GET /first', '0/1 GET /second']
        server.connections.get() == 1

        cleanup:
        filteringClient?.close()
        filtering?.close()
    }

    @Requires(property = 'spec.name', value = 'StaleConnectionRawProxySpec')
    @ClientFilter('/**')
    static class ReplacingFilter {
        final AtomicInteger replaced = new AtomicInteger()

        @RequestFilter
        HttpRequest<?> replace(HttpRequest<?> request) {
            replaced.incrementAndGet()
            return HttpRequest.GET(request.uri.toString())
        }
    }

    void "a proxy exchange with the default options is still sent again"() {
        expect:
        exchange('/first', RawRequestOptions.proxy()) == 200

        when:
        int status = exchange('/second', RawRequestOptions.proxy())

        then:
        status == 200
        server.requests*.toString() == ['0/0 GET /first', '0/1 GET /second', '1/0 GET /second']
    }

    void "the retry of a closed connection is on by default and can be turned off"() {
        expect:
        RawRequestOptions.getDefault().isRetryStaleConnection()
        RawRequestOptions.proxy().isRetryStaleConnection()
        !NO_RETRY.isRetryStaleConnection()
        NO_RETRY != RawRequestOptions.proxy()
    }
}
