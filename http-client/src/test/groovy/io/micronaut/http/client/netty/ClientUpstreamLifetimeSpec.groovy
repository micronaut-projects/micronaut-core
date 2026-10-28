package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpVersionSelection
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.sse.SseClient
import io.micronaut.http.codec.CodecException
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.buffer.UnpooledByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2Settings
import io.netty.handler.codec.http2.Http2SettingsFrame
import io.netty.util.ReferenceCountUtil
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import spock.lang.Specification
import spock.lang.Timeout
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer

/**
 * Buffers and connections of the Netty client are released when the upstream misbehaves.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class ClientUpstreamLifetimeSpec extends Specification {

    def "content that arrives before the response head is released"() {
        given:
        Throwable failure = null
        def listener = new Http1ResponseHandler.ResponseListener() {
            @Override
            void fail(ChannelHandlerContext ctx, Throwable t) {
                failure = t
            }

            @Override
            void complete(HttpResponse response, CloseableByteBody body) {
                body.close()
            }

            @Override
            void finish(ChannelHandlerContext ctx) {
            }
        }
        def channel = new EmbeddedChannel(new Http1ResponseHandler(listener))
        // e.g. an HTTP/2 DATA frame before the HEADERS frame
        def content = new DefaultHttpContent(Unpooled.directBuffer().writeBytes(new byte[1024]))

        when:
        channel.writeInbound(content)

        then:
        content.refCnt() == 0
        failure instanceof HttpClientException

        cleanup:
        if (content.refCnt() > 0) {
            content.release(content.refCnt())
        }
        channel.finishAndReleaseAll()
    }

    def "the HTTP/2 client disables server push"() {
        given:
        def group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
        def settings = new CompletableFuture<Http2Settings>()
        Channel server = new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(Http2FrameCodecBuilder.forServer().build())
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                if (msg instanceof Http2SettingsFrame) {
                                    settings.complete(msg.settings())
                                } else if (msg instanceof Http2HeadersFrame && msg.endStream) {
                                    ctx.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().status("200"), true).stream(msg.stream()))
                                }
                                ReferenceCountUtil.release(msg)
                            }
                        })
                    }
                })
                .bind(InetAddress.getLoopbackAddress(), 0).sync().channel()
        def config = new DefaultHttpClientConfiguration()
        config.plaintextMode = HttpVersionSelection.PlaintextMode.H2C_PRIOR_KNOWLEDGE
        def client = HttpClient.create(new URL("http://127.0.0.1:" + ((InetSocketAddress) server.localAddress()).port), config)

        when:
        client.toBlocking().exchange("/")
        def s = settings.get(10, TimeUnit.SECONDS)

        then:
        s.pushEnabled() == Boolean.FALSE

        cleanup:
        client.close()
        server.close()
        group.shutdownGracefully()
    }

    def "jsonStream releases the body when there is no reader for the content type"() {
        given:
        // its metric is exactly what the connection allocated and did not release
        def allocator = new UnpooledByteBufAllocator(false)
        def configuration = new DefaultHttpClientConfiguration()
        configuration.channelOptions = [allocator: allocator]
        def upstream = new RawUpstream({ Socket s, int n ->
            RawUpstream.readHead(s.inputStream)
            // in one piece, so that the body is complete when the response is
            RawUpstream.write(s, "HTTP/1.1 200 OK\r\nContent-Type: application/x-unknown\r\nContent-Length: 7\r\n\r\n" + '{"a":1}')
            Thread.sleep(20000)
        })
        def client = (StreamingHttpClient) HttpClient.create(upstream.uri.toURL(), configuration)

        when:
        Flux.from(client.jsonStream(HttpRequest.GET("/json"), Map)).collectList().toFuture().get(10, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof CodecException
        new PollingConditions(timeout: 5).eventually {
            assert allocator.metric().usedDirectMemory() + allocator.metric().usedHeapMemory() == 0
        }

        cleanup:
        client.close()
        upstream.close()
    }

    def "SSE lines of a complete response are released when the subscriber cancels"() {
        given:
        def allocator = new UnpooledByteBufAllocator(false)
        def configuration = new DefaultHttpClientConfiguration()
        configuration.channelOptions = [allocator: allocator]
        String body = (0..<100).collect { "data: line$it\n\n" }.join()
        def upstream = new RawUpstream({ Socket s, int n ->
            RawUpstream.readHead(s.inputStream)
            RawUpstream.write(s, "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${body.length()}\r\n\r\n" + body)
            Thread.sleep(5000)
        })
        def client = SseClient.create(upstream.uri.toURL(), configuration)

        when:
        def events = Flux.from(client.eventStream(HttpRequest.GET("/sse"), String)).take(1).collectList().block(Duration.ofSeconds(10))

        then:
        events.size() == 1
        new PollingConditions(timeout: 5).eventually {
            assert allocator.metric().usedDirectMemory() + allocator.metric().usedHeapMemory() == 0
        }

        cleanup:
        client.close()
        upstream.close()
    }

    def "SSE line over the limit is released exactly once"() {
        given:
        def first = Unpooled.copiedBuffer("a" * 600, StandardCharsets.UTF_8)
        def second = Unpooled.copiedBuffer("b" * 600, StandardCharsets.UTF_8)
        first.retain()
        second.retain()

        when:
        SseSplitter.split(Flux.just(first, second), new BodySizeLimits(1000, 1000)).collectList().block()

        then:
        thrown(Exception)
        // only the test's own reference is left: no double release, no leak
        second.refCnt() == 1
        first.refCnt() == 1

        cleanup:
        [first, second].each { if (it.refCnt() > 0) it.release(it.refCnt()) }
    }

    def "SSE partial line is released when the stream is cancelled"() {
        given:
        def first = Unpooled.copiedBuffer("partial line without newline", StandardCharsets.UTF_8)
        first.retain()
        def sink = Sinks.many().unicast().<ByteBuf> onBackpressureBuffer()

        when:
        def disposable = SseSplitter.split(sink.asFlux(), BodySizeLimits.UNLIMITED).subscribe()
        sink.tryEmitNext(first)
        disposable.dispose()

        then:
        first.refCnt() == 1

        cleanup:
        if (first.refCnt() > 0) first.release(first.refCnt())
    }

    def "a request timeout after the response head closes the connection"() {
        given:
        def closedAfter = new CompletableFuture<Long>()
        def upstream = new RawUpstream({ Socket s, int n ->
            RawUpstream.readHead(s.inputStream)
            RawUpstream.write(s, "HTTP/1.1 200 OK\r\nContent-Length: 100000000\r\n\r\n")
            closedAfter.complete(RawUpstream.writeUntilClosed(s, new byte[100], 100, 20_000))
        })
        def ctx = ApplicationContext.run([
                'micronaut.http.client.read-timeout'   : '30s',
                'micronaut.http.client.request-timeout': '1s',
        ])
        def client = ctx.createBean(HttpClient, upstream.uri)

        when:
        client.toBlocking().exchange("/slow", String)

        then:
        thrown(Exception)

        when:
        // -1: the connection stayed open for 20 s
        long t = closedAfter.get(30, TimeUnit.SECONDS)

        then:
        t != -1

        cleanup:
        client.close()
        ctx.close()
        upstream.close()
    }

    def "an idle pooled connection that receives unsolicited data is not reused"() {
        given:
        def upstream = new RawUpstream({ Socket s, int n ->
            int requests = 0
            while (RawUpstream.readHead(s.inputStream) != null) {
                requests++
                RawUpstream.write(s, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok")
                if (requests == 1) {
                    Thread.sleep(500)
                    // unsolicited bytes while the connection sits idle in the pool
                    RawUpstream.write(s, "AAAA\r\n")
                }
            }
        })
        def ctx = ApplicationContext.run([
                'micronaut.http.client.read-timeout': '3s',
        ])
        def client = ctx.createBean(HttpClient, upstream.uri)

        when:
        def first = client.toBlocking().retrieve("/a")
        Thread.sleep(1500)
        def second = client.toBlocking().retrieve("/b")

        then:
        first == "ok"
        second == "ok"
        upstream.connectionCount == 2

        cleanup:
        client.close()
        ctx.close()
        upstream.close()
    }

    def "an idle pooled connection that keeps receiving unsolicited data is closed"() {
        given:
        def closedAfter = new CompletableFuture<Long>()
        byte[] junk = ("A" * 8192).getBytes(StandardCharsets.US_ASCII)
        def upstream = new RawUpstream({ Socket s, int n ->
            RawUpstream.readHead(s.inputStream)
            RawUpstream.write(s, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nup")
            closedAfter.complete(RawUpstream.writeUntilClosed(s, junk, 1, 15_000))
        })
        def client = HttpClient.create(upstream.uri.toURL())

        when:
        def first = client.toBlocking().retrieve("/status")
        // -1: still read after 15 s
        long t = closedAfter.get(30, TimeUnit.SECONDS)

        then:
        first == "up"
        t != -1

        cleanup:
        client.close()
        upstream.close()
    }

    /**
     * A minimal blocking HTTP/1.1 upstream. Each accepted connection is handed to the handler on
     * its own thread.
     */
    static class RawUpstream implements Closeable {
        final ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        final List<Socket> sockets = new CopyOnWriteArrayList<>()

        RawUpstream(BiConsumer<Socket, Integer> handler) {
            Thread.start {
                int i = 0
                while (!serverSocket.isClosed()) {
                    Socket s
                    try {
                        s = serverSocket.accept()
                    } catch (IOException ignored) {
                        return
                    }
                    sockets.add(s)
                    int n = i++
                    Thread.start {
                        try {
                            handler.accept(s, n)
                        } catch (Exception ignored) {
                        } finally {
                            try {
                                s.close()
                            } catch (IOException ignored) {
                            }
                        }
                    }
                }
            }
        }

        URI getUri() {
            return URI.create("http://127.0.0.1:" + serverSocket.localPort)
        }

        int getConnectionCount() {
            return sockets.size()
        }

        static String readHead(InputStream input) {
            def sb = new StringBuilder()
            while (!sb.toString().endsWith("\r\n\r\n")) {
                int b = input.read()
                if (b == -1) {
                    return null
                }
                sb.append((char) b)
            }
            return sb.toString()
        }

        static void write(Socket s, String text) {
            s.outputStream.write(text.getBytes(StandardCharsets.ISO_8859_1))
            s.outputStream.flush()
        }

        /**
         * Keeps writing chunks until the peer closes the connection or the time runs out.
         *
         * @return the number of milliseconds until the write failed, or -1 if it never failed
         */
        static long writeUntilClosed(Socket s, byte[] chunk, long pauseMillis, long maxMillis) {
            long start = System.currentTimeMillis()
            try {
                while (System.currentTimeMillis() - start < maxMillis) {
                    s.outputStream.write(chunk)
                    s.outputStream.flush()
                    Thread.sleep(pauseMillis)
                }
                return -1
            } catch (IOException ignored) {
                return System.currentTimeMillis() - start
            }
        }

        @Override
        void close() {
            serverSocket.close()
            sockets.each {
                try {
                    it.close()
                } catch (IOException ignored) {
                }
            }
        }
    }
}
