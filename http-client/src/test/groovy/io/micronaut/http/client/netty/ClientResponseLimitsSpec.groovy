package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.http.client.exceptions.ContentLengthExceededException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.sse.EventStreamDecoder
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import reactor.core.publisher.Flux
import spock.lang.Specification
import spock.lang.Timeout

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Timeout(value = 300, unit = TimeUnit.SECONDS)
class ClientResponseLimitsSpec extends Specification {

    def "buffered content before the first read completion is bounded by the size limit"() {
        given:
        def listener = new Listener(limits: new BodySizeLimits(1000, 1000))
        def channel = new EmbeddedChannel(new Http1ResponseHandler(listener))
        List<DefaultHttpContent> contents = []

        when:
        // one read round: no channelReadComplete in between, as with decompressed data from one socket read
        channel.pipeline().fireChannelRead(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, new DefaultHttpHeaders()))
        for (int i = 0; i < 100; i++) {
            def c = new DefaultHttpContent(Unpooled.buffer().writeBytes(new byte[100]))
            contents.add(c)
            channel.pipeline().fireChannelRead(c)
        }
        long retained = contents.findAll { it.refCnt() > 0 }.collect { it.content().readableBytes() as long }.sum(0L) as long

        then:
        // 10000 bytes arrived against a 1000 byte limit
        retained <= 1100

        cleanup:
        channel.pipeline().fireChannelReadComplete()
        listener.body?.close()
        contents.each { if (it.refCnt() > 0) it.release(it.refCnt()) }
        channel.finishAndReleaseAll()
    }

    def "SSE line split is linear in the number of pieces"() {
        when:
        timeSplit(50_000) // warm up
        long small = timeSplit(100_000)
        long large = timeSplit(400_000)

        then:
        // 4x the input should take about 4x the time, not 16x
        large < Math.max(small, 50) * 8
    }

    def "SSE lines split across pieces"() {
        given:
        def decoder = new EventStreamDecoder(Long.MAX_VALUE)

        when:
        def events = ["da", "ta: a\r", "\ndata: b\n", "\n", "c"]
                .collectMany { decoder.decode(it.getBytes(StandardCharsets.UTF_8)) }

        then:
        events.collect { new String(it.data, StandardCharsets.UTF_8) } == ["a\nb"]
    }

    def "the client stops reading once a buffered response exceeds max-content-length"() {
        given:
        def closedAfter = new CompletableFuture<Long>()
        byte[] chunk = ("400\r\n" + ("z" * 1024) + "\r\n").getBytes(StandardCharsets.US_ASCII)
        def serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        Thread.start {
            try (Socket s = serverSocket.accept()) {
                readHead(s.inputStream)
                s.outputStream.write("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
                long start = System.currentTimeMillis()
                try {
                    while (System.currentTimeMillis() - start < 25_000) {
                        s.outputStream.write(chunk)
                        s.outputStream.flush()
                        Thread.sleep(20)
                    }
                    closedAfter.complete(-1L)
                } catch (IOException ignored) {
                    closedAfter.complete(System.currentTimeMillis() - start)
                }
            } catch (Exception e) {
                closedAfter.completeExceptionally(e)
            }
        }
        def ctx = ApplicationContext.run([
                'micronaut.http.client.max-content-length': '10000',
                'micronaut.http.client.read-timeout'      : '60s',
        ])
        def client = ctx.createBean(HttpClient, URI.create("http://127.0.0.1:" + serverSocket.localPort))

        when:
        client.toBlocking().exchange("/big", String)

        then:
        thrown(Exception)

        when:
        long t = closedAfter.get(40, TimeUnit.SECONDS)

        then:
        t != -1
        t < 15_000

        cleanup:
        client.close()
        ctx.close()
        serverSocket.close()
    }

    def "a streaming error body buffered for the error type is bounded by max-content-length"() {
        given:
        def closedAfter = new CompletableFuture<Long>()
        byte[] chunk = ("400\r\n" + ("z" * 1024) + "\r\n").getBytes(StandardCharsets.US_ASCII)
        def serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        Thread.start {
            try (Socket s = serverSocket.accept()) {
                readHead(s.inputStream)
                s.outputStream.write("HTTP/1.1 500 Internal Server Error\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
                long start = System.currentTimeMillis()
                try {
                    while (System.currentTimeMillis() - start < 25_000) {
                        s.outputStream.write(chunk)
                        s.outputStream.flush()
                        Thread.sleep(5)
                    }
                    closedAfter.complete(-1L)
                } catch (IOException ignored) {
                    closedAfter.complete(System.currentTimeMillis() - start)
                }
            } catch (Exception e) {
                closedAfter.completeExceptionally(e)
            }
        }
        def ctx = ApplicationContext.run([
                'micronaut.http.client.max-content-length': '10000',
                'micronaut.http.client.read-timeout'      : '60s',
        ])
        def client = ctx.createBean(StreamingHttpClient, URI.create("http://127.0.0.1:" + serverSocket.localPort))

        when:
        Flux.from(client.dataStream(HttpRequest.GET("/err"), Argument.of(String))).blockLast(Duration.ofSeconds(40))

        then: 'the upstream status is kept, the limit is the cause'
        def responseException = thrown(HttpClientResponseException)
        responseException.status.code == 500
        responseException.cause instanceof ContentLengthExceededException

        when:
        long t = closedAfter.get(40, TimeUnit.SECONDS)

        then:
        t != -1
        t < 15_000

        cleanup:
        client.close()
        ctx.close()
        serverSocket.close()
    }

    private static void readHead(InputStream input) {
        def sb = new StringBuilder()
        while (!sb.toString().endsWith("\r\n\r\n")) {
            int b = input.read()
            if (b == -1) {
                return
            }
            sb.append((char) b)
        }
    }

    private static long timeSplit(int n) {
        def decoder = new EventStreamDecoder(Long.MAX_VALUE)
        byte[] a = [(byte) 'a'] as byte[]
        List events = []
        long start = System.nanoTime()
        events.addAll(decoder.decode("data: ".getBytes(StandardCharsets.US_ASCII)))
        for (int i = 0; i < n; i++) {
            events.addAll(decoder.decode(a))
        }
        events.addAll(decoder.decode("\n\n".getBytes(StandardCharsets.US_ASCII)))
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        assert events.size() == 1
        assert events[0].data.length == n
        return millis
    }

    private static class Listener implements Http1ResponseHandler.ResponseListener {
        BodySizeLimits limits = BodySizeLimits.UNLIMITED
        HttpResponse response
        CloseableByteBody body
        Throwable failure

        @Override
        BodySizeLimits sizeLimits() {
            return limits
        }

        @Override
        void complete(HttpResponse response, CloseableByteBody body) {
            this.response = response
            this.body = body
        }

        @Override
        void fail(ChannelHandlerContext ctx, Throwable t) {
            failure = t
        }

        @Override
        void finish(ChannelHandlerContext ctx) {
        }
    }
}
