package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.UnprocessedRequestException
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A keep-alive connection that the server closed while it sat idle in the pool fails the next
 * request that is written to it, although the server never processed that request. Idempotent
 * requests with a replayable body are retried once on another connection.
 */
class StaleConnectionRetrySpec extends Specification {

    @AutoCleanup
    RawServer server

    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run([
            'micronaut.http.client.read-timeout': '10s',
    ])

    HttpClient client

    def cleanup() {
        client?.close()
    }

    private void start(Closure<Boolean> respond) {
        start(ctx, respond)
    }

    private void start(ApplicationContext context, Closure<Boolean> respond) {
        server = new RawServer(respond)
        client = context.createBean(HttpClient, new URI("http://127.0.0.1:${server.port}"))
    }

    private static void waitFor(Closure<Boolean> condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition.call()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError('Condition not met in time')
            }
            Thread.sleep(10)
        }
    }

    private String exchange(HttpRequest<?> request) {
        String body = client.toBlocking().retrieve(request, String)
        // let the connection go back to the pool before the next request is sent
        Thread.sleep(100)
        return body
    }

    void "an idempotent GET on a reused connection that the server closed is retried on a new connection"() {
        given: "a server that closes the connection instead of answering its second request"
        start { int connection, int requestOnConnection -> requestOnConnection == 0 }

        expect:
        exchange(HttpRequest.GET('/first')) == 'ok /first'

        when:
        String response = exchange(HttpRequest.GET('/second'))

        then: "the request is sent again on a new connection"
        response == 'ok /second'
        server.requests*.toString() == ['0/0 GET /first', '0/1 GET /second', '1/0 GET /second']
        server.connections.get() == 2
    }

    void "a PUT with a body is retried with the same body"() {
        given:
        start { int connection, int requestOnConnection -> requestOnConnection == 0 }

        expect:
        exchange(HttpRequest.GET('/first')) == 'ok /first'

        when:
        String response = exchange(HttpRequest.PUT('/put', 'hello').contentType(MediaType.TEXT_PLAIN_TYPE))

        then:
        response == 'ok /put'
        server.requests*.toString() == ['0/0 GET /first', '0/1 PUT /put hello', '1/0 PUT /put hello']
    }

    void "the retried request carries the same client-generated headers and leaves the caller's request unchanged"() {
        given:
        start { int connection, int requestOnConnection -> requestOnConnection == 0 }
        def request = HttpRequest.PUT('/put', 'hello')

        expect:
        exchange(HttpRequest.GET('/first')) == 'ok /first'

        when:
        String response = exchange(request)

        then:
        response == 'ok /put'
        server.requests*.toString() == ['0/0 GET /first', '0/1 PUT /put hello', '1/0 PUT /put hello']
        def attempts = server.requests.findAll { it.method == 'PUT' }
        attempts.size() == 2
        attempts.every { it.headers['host'] == "127.0.0.1:${server.port}".toString() }
        attempts.every { it.headers['content-length'] == '5' }
        attempts.every { it.headers['content-type'] == MediaType.APPLICATION_JSON }
        attempts[0].headers == attempts[1].headers

        and: "the client-generated headers were not written into the caller's request"
        !request.headers.contains('Host')
        !request.headers.contains('Content-Length')
        !request.headers.contains('Content-Type')
        !request.headers.contains('Connection')
    }

    void "a POST on a reused connection that the server closed is not retried"() {
        given:
        start { int connection, int requestOnConnection -> requestOnConnection == 0 }

        expect:
        exchange(HttpRequest.GET('/first')) == 'ok /first'

        when:
        exchange(HttpRequest.POST('/post', 'hello').contentType(MediaType.TEXT_PLAIN_TYPE))

        then:
        def e = thrown(HttpClientException)
        e.message.contains('Connection closed before response was received')
        server.requests*.toString() == ['0/0 GET /first', '0/1 POST /post hello']
        server.connections.get() == 1
    }

    void "a PUT with a streaming body is not retried"() {
        given:
        start { int connection, int requestOnConnection -> requestOnConnection == 0 }

        expect:
        exchange(HttpRequest.GET('/first')) == 'ok /first'

        when:
        exchange(HttpRequest.PUT('/put', Flux.just('hel', 'lo')).contentType(MediaType.TEXT_PLAIN_TYPE))

        then:
        def e = thrown(HttpClientException)
        e.message.contains('Connection closed before response was received')
        server.requests.size() == 2
        server.connections.get() == 1
    }

    void "a request on a new connection that the server closed is not retried"() {
        given: "a server that never answers"
        start { int connection, int requestOnConnection -> false }

        when:
        exchange(HttpRequest.GET('/first'))

        then:
        def e = thrown(HttpClientException)
        e.message.contains('Connection closed before response was received')
        server.requests*.toString() == ['0/0 GET /first']
        server.connections.get() == 1
    }

    void "a request is retried at most once"() {
        given: "two pooled connections, and a server that closes a connection instead of answering any later request"
        CountDownLatch bothArrived = new CountDownLatch(2)
        start { int connection, int requestOnConnection ->
            if (connection < 2 && requestOnConnection == 0) {
                bothArrived.countDown()
                // hold the answer so that the client opens a second connection
                bothArrived.await(10, TimeUnit.SECONDS)
                return true
            }
            return false
        }
        assert Flux.merge(
                Flux.from(client.retrieve(HttpRequest.GET('/a'), String)),
                Flux.from(client.retrieve(HttpRequest.GET('/b'), String))
        ).collectList().block().toSorted() == ['ok /a', 'ok /b']
        Thread.sleep(100)

        when:
        exchange(HttpRequest.GET('/second'))

        then: "the request was sent twice, and the second failure is not retried"
        def e = thrown(HttpClientException)
        e.message.contains('Connection closed before response was received')
        server.requests.findAll { it.path == '/second' }.size() == 2
    }

    void "a request whose reused connection is found closed when it is written is retried on a new connection"() {
        given: "a connection that closes right before the next request on it is written, as when the client only sees the close of the idle connection by then"
        AtomicBoolean closeBeforeWrite = new AtomicBoolean()
        ctx.getBean(NettyClientCustomizer.Registry).register(new NettyClientCustomizer() {
            @Override
            NettyClientCustomizer specializeForChannel(Channel channel, NettyClientCustomizer.ChannelRole role) {
                return new NettyClientCustomizer() {
                    @Override
                    void onRequestPipelineBuilt() {
                        if (closeBeforeWrite.compareAndSet(true, false)) {
                            channel.close()
                        }
                    }
                }
            }
        })
        start { int connection, int requestOnConnection -> true }
        ByteBuf body = Unpooled.copiedBuffer('hello', StandardCharsets.UTF_8)

        expect:
        exchange(HttpRequest.GET('/first')) == 'ok /first'

        when:
        closeBeforeWrite.set(true)
        String response = exchange(HttpRequest.PUT('/put', body).contentType(MediaType.TEXT_PLAIN_TYPE))

        then: "the request never reached the closed connection, and is sent once on a new connection with its body"
        response == 'ok /put'
        !closeBeforeWrite.get()
        server.requests*.toString() == ['0/0 GET /first', '1/0 PUT /put hello']
        server.connections.get() == 2
        waitFor { body.refCnt() == 0 }
    }

    void "a retry whose new connection cannot be opened fails with the connect error"() {
        given: "a server that stops listening, then closes the connection instead of answering its second request"
        start { int connection, int requestOnConnection ->
            if (requestOnConnection == 0) {
                return true
            }
            server.serverSocket.close()
            return false
        }

        expect:
        exchange(HttpRequest.GET('/first')) == 'ok /first'

        when:
        exchange(HttpRequest.GET('/second'))

        then: "the retry reports that no connection could be opened"
        def e = thrown(UnprocessedRequestException)
        e.reason == UnprocessedRequestException.Reason.CONNECT
        server.requests*.toString() == ['0/0 GET /first', '0/1 GET /second']
        server.connections.get() == 1
    }

    void "a retry is sent on another idle pooled connection of the same event loop"() {
        given: "a single event loop, two pooled connections, and a server that closes the first connection that gets a second request"
        ApplicationContext singleLoopCtx = ApplicationContext.run([
                'micronaut.http.client.read-timeout': '10s',
                'micronaut.netty.event-loops.default.num-threads': 1,
        ])
        CountDownLatch bothArrived = new CountDownLatch(2)
        AtomicBoolean closedOne = new AtomicBoolean()
        start(singleLoopCtx) { int connection, int requestOnConnection ->
            if (requestOnConnection == 0) {
                bothArrived.countDown()
                // hold the answer so that the client opens a second connection
                bothArrived.await(10, TimeUnit.SECONDS)
                return true
            }
            return !closedOne.compareAndSet(false, true)
        }
        assert Flux.merge(
                Flux.from(client.retrieve(HttpRequest.GET('/a'), String)),
                Flux.from(client.retrieve(HttpRequest.GET('/b'), String))
        ).collectList().block().toSorted() == ['ok /a', 'ok /b']
        Thread.sleep(100)

        when:
        String response = exchange(HttpRequest.PUT('/put', 'hello').contentType(MediaType.TEXT_PLAIN_TYPE))

        then: "the request is sent again on the other pooled connection, without opening a new one"
        response == 'ok /put'
        def attempts = server.requests.findAll { it.path == '/put' }
        attempts*.index == [1, 1]
        attempts*.connection.toSet().size() == 2
        attempts*.body == ['hello', 'hello']
        server.connections.get() == 2

        cleanup:
        client?.close()
        client = null
        singleLoopCtx.close()
    }

    void "cancelling a retried request while its new connection is pending releases the request body"() {
        given: "a pool of a single connection, and a server that closes the connection instead of answering the PUT once another request waits for the connection"
        ApplicationContext singleConnectionCtx = ApplicationContext.run([
                'micronaut.http.client.read-timeout': '10s',
                'micronaut.http.client.pool.max-concurrent-http1-connections': 1,
        ])
        CountDownLatch otherQueued = new CountDownLatch(1)
        CountDownLatch releaseOther = new CountDownLatch(1)
        start(singleConnectionCtx) { int connection, int requestOnConnection ->
            if (connection == 0 && requestOnConnection == 1) {
                otherQueued.await(10, TimeUnit.SECONDS)
                return false
            }
            if (connection == 1) {
                // hold the only connection, so that the retry has to wait for it
                releaseOther.await(10, TimeUnit.SECONDS)
            }
            return true
        }
        exchange(HttpRequest.GET('/first'))
        ByteBuf body = Unpooled.copiedBuffer('hello', StandardCharsets.UTF_8)

        when: "the PUT fails on the stale connection, and its retry waits behind another request"
        Disposable put = Flux.from(client.exchange(HttpRequest.PUT('/put', body).contentType(MediaType.TEXT_PLAIN_TYPE)))
                .subscribe({}, {})
        waitFor { server.requests.any { it.path == '/put' } }
        CompletableFuture<String> other = Mono.from(client.retrieve(HttpRequest.GET('/other'), String)).toFuture()
        Thread.sleep(200)
        otherQueued.countDown()
        waitFor { server.requests.any { it.path == '/other' } }
        Thread.sleep(200)

        then: "the body is kept for the retry"
        body.refCnt() == 1

        when: "the PUT is cancelled while the retry waits for a connection"
        put.dispose()
        releaseOther.countDown()

        then: "the body is released, and the PUT is not sent again"
        other.get(10, TimeUnit.SECONDS) == 'ok /other'
        waitFor { body.refCnt() == 0 }
        server.requests.findAll { it.path == '/put' }.size() == 1

        cleanup:
        client?.close()
        client = null
        singleConnectionCtx.close()
    }

    static final class RawServer implements AutoCloseable {
        final ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        final AtomicInteger connections = new AtomicInteger()
        final List<Req> requests = new CopyOnWriteArrayList<>()
        final Closure<Boolean> respond
        final Thread acceptor

        RawServer(Closure<Boolean> respond) {
            this.respond = respond
            acceptor = Thread.startVirtualThread { acceptLoop() }
        }

        private void acceptLoop() {
            try {
                while (true) {
                    Socket socket = serverSocket.accept()
                    int connection = connections.getAndIncrement()
                    Thread.startVirtualThread { serveAndClose(socket, connection) }
                }
            } catch (IOException ignored) {
                // closed
            }
        }

        int getPort() {
            serverSocket.localPort
        }

        private void serveAndClose(Socket socket, int connection) {
            try {
                serve(socket, connection)
            } catch (IOException ignored) {
                // the client closed the connection
            } finally {
                socket.close()
            }
        }

        private static String readChunkedBody(InputStream input) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream()
            int size
            while ((size = Integer.parseInt(readLine(input).trim(), 16)) != 0) {
                bytes.write(input.readNBytes(size))
                readLine(input)
            }
            readLine(input)
            return bytes.toString(StandardCharsets.UTF_8)
        }

        private void serve(Socket socket, int connection) throws IOException {
            InputStream input = socket.inputStream
            OutputStream out = socket.outputStream
            for (int i = 0; ; i++) {
                String requestLine = null
                int contentLength = 0
                boolean chunked = false
                Map<String, String> headers = [:]
                String line
                while ((line = readLine(input)) != null && !line.isEmpty()) {
                    if (requestLine == null) {
                        requestLine = line
                        continue
                    }
                    int colon = line.indexOf(':')
                    if (colon > 0) {
                        headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim())
                    }
                    if (line.toLowerCase(Locale.ROOT).startsWith('content-length:')) {
                        contentLength = line.substring('content-length:'.length()).trim() as int
                    } else if (line.toLowerCase(Locale.ROOT).startsWith('transfer-encoding:') && line.toLowerCase(Locale.ROOT).contains('chunked')) {
                        chunked = true
                    }
                }
                if (requestLine == null) {
                    return
                }
                String body
                if (chunked) {
                    body = readChunkedBody(input)
                } else {
                    body = new String(input.readNBytes(contentLength), StandardCharsets.UTF_8)
                }
                String[] parts = requestLine.split(' ')
                requests.add(new Req(connection, i, parts[0], parts[1], body, headers))
                if (!respond.call(connection, i)) {
                    // close without answering, as if the idle connection had already been closed
                    return
                }
                byte[] responseBody = "ok ${parts[1]}".getBytes(StandardCharsets.US_ASCII)
                out.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${responseBody.length}\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
                out.write(responseBody)
                out.flush()
            }
        }

        private static String readLine(InputStream input) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream()
            int b
            while ((b = input.read()) != -1) {
                if (b == '\n') {
                    break
                }
                if (b != '\r') {
                    line.write(b)
                }
            }
            if (b == -1 && line.size() == 0) {
                return null
            }
            return line.toString(StandardCharsets.US_ASCII)
        }

        @Override
        void close() {
            serverSocket.close()
            acceptor.join(5000)
        }
    }

    static final class Req {
        final int connection
        final int index
        final String method
        final String path
        final String body
        final Map<String, String> headers

        Req(int connection, int index, String method, String path, String body, Map<String, String> headers) {
            this.connection = connection
            this.index = index
            this.method = method
            this.path = path
            this.body = body
            this.headers = headers
        }

        @Override
        String toString() {
            "$connection/$index $method $path${body ? ' ' + body : ''}"
        }
    }
}
