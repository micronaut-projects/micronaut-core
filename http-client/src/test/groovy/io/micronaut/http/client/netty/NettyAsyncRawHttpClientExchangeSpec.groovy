package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.core.io.buffer.ReadBuffer
import io.micronaut.core.io.buffer.ReadBufferFactory
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpVersion
import io.micronaut.http.MediaType
import io.micronaut.http.MutableByteBodyHttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.http.body.ByteBodyFactory
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.client.AsyncRawHttpClient
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpVersionSelection
import io.micronaut.http.client.RawHttpClient
import io.micronaut.http.client.RawHttpClientRegistry
import io.micronaut.http.client.RawRequestOptions
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.ReadTimeoutException
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors
import java.util.zip.GZIPOutputStream

/**
 * Exchanges of the Netty {@link AsyncRawHttpClient}, however it is obtained: injected, created,
 * from a {@link RawHttpClient} or from the {@link RawHttpClientRegistry}.
 */
class NettyAsyncRawHttpClientExchangeSpec extends Specification {
    static final String SPEC_NAME = 'NettyAsyncRawHttpClientExchangeSpec'
    static final byte[] UNCOMPRESSED = "Hello, gzip!".getBytes(StandardCharsets.UTF_8)
    static final byte[] GZIPPED = gzip(UNCOMPRESSED)
    static final long TIMEOUT_SECONDS = 10

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': SPEC_NAME])

    PollingConditions conditions = new PollingConditions(timeout: TIMEOUT_SECONDS)

    void "an injected async raw client exchanges raw bytes"() {
        given:
        AsyncRawHttpClient client = server.applicationContext.getBean(AsyncRawHttpClient)

        expect:
        client instanceof NettyAsyncRawHttpClient
        echo(client, server.URI.toString() + "/async-raw/echo") == "hello"
    }

    void "an async raw client injected with @Client resolves relative requests against its URL"() {
        given:
        AsyncRawHttpClient client = server.applicationContext.getBean(InjectedClients).client

        expect:
        client instanceof NettyAsyncRawHttpClient
        echo(client, "/async-raw/echo") == "hello"
    }

    void "a created async raw client resolves relative requests against its URL"() {
        given:
        AsyncRawHttpClient client = AsyncRawHttpClient.create(server.URI)

        expect:
        client instanceof NettyAsyncRawHttpClient
        echo(client, "/async-raw/echo") == "hello"

        cleanup:
        client?.close()
    }

    void "a created async raw client uses its configuration"() {
        given:
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration()
        configuration.followRedirects = false
        AsyncRawHttpClient client = AsyncRawHttpClient.create(server.URI, configuration)

        when:
        ByteBodyHttpResponse<?> response = send(client, HttpRequest.GET("/async-raw/redirect"), null)

        then:
        response.code() == 303
        response.headers.get(HttpHeaders.LOCATION) == "/async-raw/target"

        cleanup:
        response?.close()
        client?.close()
    }

    void "the async view of a raw client exchanges raw bytes, and closing it closes the raw client"() {
        given:
        RawHttpClient rawClient = server.applicationContext.createBean(RawHttpClient, server.URI)
        AsyncRawHttpClient client = rawClient.toAsyncRaw()

        expect:
        client instanceof NettyAsyncRawHttpClient
        echo(client, "/async-raw/echo") == "hello"
        ((HttpClient) rawClient).isRunning()

        when:
        client.close()

        then:
        !((HttpClient) rawClient).isRunning()
    }

    void "the registry returns an async raw client for a client id"() {
        given:
        AsyncRawHttpClient client = server.applicationContext.getBean(RawHttpClientRegistry)
            .getAsyncRawClient(HttpVersionSelection.forLegacyVersion(HttpVersion.HTTP_1_1), server.URL.toString(), null)

        expect:
        client instanceof NettyAsyncRawHttpClient
        echo(client, server.URI.toString() + "/async-raw/echo") == "hello"
    }

    void "the proxy options return redirects"() {
        given:
        AsyncRawHttpClient client = server.applicationContext.getBean(AsyncRawHttpClient)

        when:
        ByteBodyHttpResponse<?> response = send(client, HttpRequest.GET(server.URI.toString() + "/async-raw/redirect"), RawRequestOptions.proxy())

        then:
        response instanceof MutableByteBodyHttpResponse
        response.code() == 303
        response.headers.get(HttpHeaders.LOCATION) == "/async-raw/target"

        cleanup:
        response?.close()
    }

    void "redirects are followed without options, and with options that follow them"() {
        given:
        AsyncRawHttpClient client = server.applicationContext.getBean(AsyncRawHttpClient)

        when:
        ByteBodyHttpResponse<?> plain = send(client, HttpRequest.GET(server.URI.toString() + "/async-raw/redirect"), null)
        ByteBodyHttpResponse<?> following = send(client, HttpRequest.GET(server.URI.toString() + "/async-raw/redirect"),
            RawRequestOptions.proxy().toBuilder().followRedirects(true).build())

        then:
        plain.code() == 200
        text(plain) == "target"
        following instanceof MutableByteBodyHttpResponse
        following.code() == 200
        text(following) == "target"

        cleanup:
        plain?.close()
        following?.close()
    }

    void "the Host header is computed from the URI unless it is retained"() {
        given:
        AsyncRawHttpClient client = server.applicationContext.getBean(AsyncRawHttpClient)
        URI uri = URI.create(server.URI.toString() + "/async-raw/host")

        when:
        ByteBodyHttpResponse<?> computed = send(client, HttpRequest.GET(uri).header(HttpHeaders.HOST, "gateway.example"), RawRequestOptions.proxy())
        ByteBodyHttpResponse<?> retained = send(client, HttpRequest.GET(uri).header(HttpHeaders.HOST, "gateway.example"),
            RawRequestOptions.proxy().toBuilder().retainHostHeader(true).build())

        then:
        text(computed) == uri.host + ":" + uri.port
        text(retained) == "gateway.example"

        cleanup:
        computed?.close()
        retained?.close()
    }

    void "an encoded response is decompressed unless the options say otherwise"() {
        given:
        AsyncRawHttpClient client = server.applicationContext.getBean(AsyncRawHttpClient)
        String uri = server.URI.toString() + "/async-raw/gzip"

        when:
        ByteBodyHttpResponse<?> decompressed = send(client, HttpRequest.GET(uri), null)
        ByteBodyHttpResponse<?> raw = send(client, HttpRequest.GET(uri), RawRequestOptions.proxy())

        then:
        decompressed.byteBody().buffer().get().toByteArray() == UNCOMPRESSED
        raw.headers.get(HttpHeaders.CONTENT_ENCODING) == "gzip"
        raw.byteBody().buffer().get().toByteArray() == GZIPPED

        cleanup:
        decompressed?.close()
        raw?.close()
    }

    void "the response timeout of the options fails a slow exchange"() {
        given:
        AsyncRawHttpClient client = server.applicationContext.getBean(AsyncRawHttpClient)
        long start = System.nanoTime()

        when:
        client.exchange(HttpRequest.GET(server.URI.toString() + "/async-raw/slow"), null,
            RawRequestOptions.proxy().toBuilder().responseTimeout(Duration.ofMillis(200)).build())
            .toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof ReadTimeoutException
        Duration.ofNanos(System.nanoTime() - start) < Duration.ofSeconds(4)
    }

    void "the options are required"() {
        given:
        AsyncRawHttpClient client = server.applicationContext.getBean(AsyncRawHttpClient)

        when:
        client.exchange(HttpRequest.GET(server.URI.toString() + "/async-raw/target"), null, (RawRequestOptions) null)

        then:
        thrown(NullPointerException)
    }

    void "the raw client sends an exchange with options through the publisher API"() {
        given:
        RawHttpClient rawClient = server.applicationContext.createBean(RawHttpClient, server.URI)

        when:
        ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) Mono.from(rawClient.exchange(
            HttpRequest.GET(server.URI.toString() + "/async-raw/redirect"), null, null, RawRequestOptions.proxy())).block()

        then:
        response instanceof MutableByteBodyHttpResponse
        response.code() == 303

        cleanup:
        response?.close()
        rawClient?.close()
    }

    void "a streaming request body is sent"() {
        given:
        AsyncRawHttpClient client = server.applicationContext.getBean(AsyncRawHttpClient)
        TrackedBody body = new TrackedBody(Flux.just("hel", "lo ", "world").delayElements(Duration.ofMillis(20)).map { it.getBytes(StandardCharsets.UTF_8) })

        when:
        ByteBodyHttpResponse<?> response = exchange(client,
            HttpRequest.POST(server.URI.toString() + "/async-raw/echo", null).contentType(MediaType.TEXT_PLAIN_TYPE), body.body, null)

        then:
        response.code() == 200
        text(response) == "hello world"
        body.completed.get()

        cleanup:
        response?.close()
    }

    void "a refused connection fails the stage with the client exception and releases the request body"() {
        given:
        AsyncRawHttpClient client = server.applicationContext.getBean(AsyncRawHttpClient)
        AtomicBoolean closed = new AtomicBoolean()
        CloseableByteBody body = closeTracked(ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt("hello".getBytes(StandardCharsets.UTF_8)), closed)
        URI unreachable = URI.create("http://127.0.0.1:" + RawSocketUpstream.unusedPort() + "/async-raw/echo")

        when:
        client.exchange(HttpRequest.POST(unreachable, null), body).toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof HttpClientException
        conditions.eventually {
            assert closed.get()
        }
    }

    void "cancelling the stage before the response aborts the request"() {
        given:
        RawSocketUpstream upstream = new RawSocketUpstream()
        AsyncRawHttpClient client = server.applicationContext.getBean(AsyncRawHttpClient)
        TrackedBody body = new TrackedBody(Flux.just("hello".getBytes(StandardCharsets.UTF_8)))

        when:
        CompletableFuture<HttpResponse<?>> future = client.exchange(HttpRequest.POST(upstream.uri("/cancel"), null), body.body).toCompletableFuture()
        RawSocketUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS)

        then:
        connection != null
        connection.awaitRequest(TIMEOUT_SECONDS)

        when:
        boolean cancelled = future.cancel(false)

        then: 'the connection is closed and the request body released'
        cancelled
        future.isCancelled()
        connection.awaitClosed(TIMEOUT_SECONDS)
        conditions.eventually {
            assert body.released()
        }

        when: 'a response that arrives anyway is not delivered'
        connection.writeQuietly("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello")
        Thread.sleep(200)

        then:
        future.isCancelled()

        cleanup:
        upstream?.close()
    }

    void "cancelling a streaming request body stops it"() {
        given:
        RawSocketUpstream upstream = new RawSocketUpstream()
        AsyncRawHttpClient client = server.applicationContext.getBean(AsyncRawHttpClient)
        TrackedBody body = new TrackedBody(Flux.interval(Duration.ofMillis(20)).map { new byte[1024] })

        when:
        CompletableFuture<HttpResponse<?>> future = client.exchange(HttpRequest.POST(upstream.uri("/streaming"), null), body.body).toCompletableFuture()
        RawSocketUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS)

        then:
        connection != null
        conditions.eventually {
            assert connection.bytesReceived.get() > 8 * 1024
        }

        when:
        future.cancel(false)

        then:
        connection.awaitClosed(TIMEOUT_SECONDS)
        conditions.eventually {
            assert body.cancelled.get() && body.discarded.get()
        }

        when:
        long received = connection.bytesReceived.get()
        Thread.sleep(200)

        then:
        connection.bytesReceived.get() == received

        cleanup:
        upstream?.close()
    }

    void "cancelling the stage after the response has no effect, and the response body is read"() {
        given:
        RawSocketUpstream upstream = new RawSocketUpstream()
        AsyncRawHttpClient client = server.applicationContext.getBean(AsyncRawHttpClient)

        when:
        CompletableFuture<HttpResponse<?>> future = client.exchange(HttpRequest.GET(upstream.uri("/after")), null).toCompletableFuture()
        RawSocketUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS)

        then:
        connection != null
        connection.awaitRequest(TIMEOUT_SECONDS)

        when:
        connection.write("HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\nhello body")
        ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        then:
        !future.cancel(false)
        !future.isCancelled()
        text(response) == "hello body"
        !connection.awaitClosed(0)

        cleanup:
        response?.close()
        upstream?.close()
    }

    void "closing an unconsumed response keeps the connection"() {
        given:
        RawSocketUpstream upstream = new RawSocketUpstream()
        AsyncRawHttpClient client = server.applicationContext.getBean(AsyncRawHttpClient)

        when:
        CompletableFuture<HttpResponse<?>> future = client.exchange(HttpRequest.GET(upstream.uri("/unconsumed")), null).toCompletableFuture()
        RawSocketUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS)

        then:
        connection != null
        connection.awaitRequest(TIMEOUT_SECONDS)

        when:
        connection.write("HTTP/1.1 200 OK\r\nContent-Length: 65536\r\n\r\n")
        connection.write(new byte[16 * 1024])
        ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        response.close()

        then: 'the Netty client drains the rest of the body instead of closing the connection'
        !connection.awaitClosed(1)

        when:
        connection.write(new byte[48 * 1024])

        then:
        !connection.awaitClosed(1)

        cleanup:
        upstream?.close()
    }

    private static String echo(AsyncRawHttpClient client, String uri) {
        ByteBodyHttpResponse<?> response = exchange(client,
            HttpRequest.POST(uri, null).contentType(MediaType.TEXT_PLAIN_TYPE),
            ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt("hello".getBytes(StandardCharsets.UTF_8)), null)
        try {
            assert response.code() == 200
            return text(response)
        } finally {
            response.close()
        }
    }

    private static ByteBodyHttpResponse<?> send(AsyncRawHttpClient client, HttpRequest<?> request, RawRequestOptions options) {
        return exchange(client, request, null, options)
    }

    private static ByteBodyHttpResponse<?> exchange(AsyncRawHttpClient client, HttpRequest<?> request, CloseableByteBody body, RawRequestOptions options) {
        HttpResponse<?> response = (options == null ? client.exchange(request, body) : client.exchange(request, body, options))
            .toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assert response instanceof ByteBodyHttpResponse
        return (ByteBodyHttpResponse<?>) response
    }

    private static String text(ByteBodyHttpResponse<?> response) {
        return response.byteBody().buffer().get().toString(StandardCharsets.UTF_8)
    }

    private static byte[] gzip(byte[] data) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        new GZIPOutputStream(bytes).withCloseable { it.write(data) }
        return bytes.toByteArray()
    }

    /**
     * @return A body that records that it was closed
     */
    private static CloseableByteBody closeTracked(CloseableByteBody delegate, AtomicBoolean closed) {
        return new CloseableByteBody() {
            @Delegate(excludes = ['close'])
            CloseableByteBody wrapped = delegate

            @Override
            void close() {
                closed.set(true)
                wrapped.close()
            }
        }
    }

    /**
     * A streaming request body that records what the client does with its source.
     */
    static class TrackedBody {
        final AtomicBoolean cancelled = new AtomicBoolean()
        final AtomicBoolean completed = new AtomicBoolean()
        final AtomicBoolean discarded = new AtomicBoolean()
        final CloseableByteBody body

        TrackedBody(Flux<byte[]> source) {
            ReadBufferFactory buffers = ReadBufferFactory.getJdkFactory()
            Flux<ReadBuffer> tracked = source
                .doOnCancel { cancelled.set(true) }
                .doOnComplete { completed.set(true) }
                .map { buffers.adapt(it) }
            body = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)
                .adapt(tracked, BodySizeLimits.UNLIMITED, null, { discarded.set(true) } as Runnable)
        }

        /**
         * @return Whether the client is done with the source: it was sent completely, or cancelled
         */
        boolean released() {
            return completed.get() || cancelled.get() && discarded.get()
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class InjectedClients {
        @Inject
        @Client("/")
        AsyncRawHttpClient client
    }

    @Controller("/async-raw")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AsyncRawController {
        @Post(value = "/echo", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String echo(@Body String body) {
            return body
        }

        @Get("/redirect")
        HttpResponse<?> redirect() {
            return HttpResponse.seeOther(URI.create("/async-raw/target"))
        }

        @Get(value = "/target", produces = MediaType.TEXT_PLAIN)
        String target() {
            return "target"
        }

        @Get(value = "/headers", produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> headers(HttpRequest<?> request) {
            String received = request.headers.names().stream()
                .map { it.toLowerCase(Locale.ROOT) }
                .filter { it.startsWith("x-") || it == "keep-alive" || it.startsWith("proxy-") || it == "te" }
                .sorted()
                .collect(Collectors.joining(","))
            return HttpResponse.ok(received)
                .header("X-End-To-End", "kept")
                .header(HttpHeaders.PROXY_AUTHENTICATE, "Basic")
                .header("Keep-Alive", "timeout=5")
        }

        @Get(value = "/host", produces = MediaType.TEXT_PLAIN)
        String host(@Header(HttpHeaders.HOST) String host) {
            return host
        }

        @Get("/gzip")
        HttpResponse<byte[]> gzip() {
            return HttpResponse.ok(GZIPPED)
                .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                .contentLength(GZIPPED.length)
        }

        @Get(value = "/slow", produces = MediaType.TEXT_PLAIN)
        Mono<String> slow() {
            return Mono.delay(Duration.ofSeconds(5)).thenReturn("slow")
        }
    }
}
