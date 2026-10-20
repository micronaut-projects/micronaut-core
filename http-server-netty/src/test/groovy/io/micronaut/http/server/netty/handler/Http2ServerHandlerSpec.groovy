package io.micronaut.http.server.netty.handler

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import io.micronaut.http.body.ByteBody
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.ConcatenatingSubscriber
import io.micronaut.http.body.InternalByteBody
import io.micronaut.http.body.stream.InputStreamByteBody
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.server.netty.EmbeddedTestUtil
import io.micronaut.http.server.netty.handler.accesslog.Http2AccessLogManager
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.CompositeByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.DefaultHttp2PingFrame
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame
import io.netty.handler.codec.http2.DefaultHttp2WindowUpdateFrame
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler
import io.netty.handler.codec.http2.Http2CodecUtil
import io.netty.handler.codec.http2.Http2DataFrame
import io.netty.handler.codec.http2.Http2Error
import io.netty.handler.codec.http2.Http2Exception
import io.netty.handler.codec.http2.Http2Frame
import io.netty.handler.codec.http2.Http2FrameCodec
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2GoAwayFrame
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2PingFrame
import io.netty.handler.codec.http2.Http2ResetFrame
import io.netty.handler.codec.http2.Http2SettingsAckFrame
import io.netty.handler.codec.http2.Http2SettingsFrame
import io.netty.handler.codec.http2.Http2StreamFrame
import io.netty.handler.timeout.IdleStateEvent
import io.netty.util.AsciiString
import io.netty.util.ReferenceCountUtil
import org.jspecify.annotations.NonNull
import org.junit.jupiter.api.Assertions
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.channels.ClosedChannelException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadLocalRandom


class Http2ServerHandlerSpec extends Specification {
    private static class DuplexHandler extends Http2ChannelDuplexHandler {
        Http2FrameCodec frameCodec
        CompositeByteBuf received
        /** Each data frame as [content, endStream], in order. */
        final List<List<Object>> dataFrames = []

        @Override
        protected void handlerAdded0(ChannelHandlerContext ctx) throws Exception {
            this.frameCodec = (Http2FrameCodec) ctx.pipeline().context(Http2FrameCodec.class).handler()
            this.received = ctx.alloc().compositeBuffer()
        }

        @Override
        protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
            received.release()
        }

        @Override
        void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) throws Exception {
            if (msg instanceof Http2DataFrame) {
                dataFrames.add([msg.content().toString(StandardCharsets.UTF_8), msg.isEndStream()])
                received.addComponent(true, msg.content())
            } else {
                ctx.fireChannelRead(msg)
            }
        }
    }

    private static Tuple3<EmbeddedChannel, EmbeddedChannel, DuplexHandler> configure(RequestHandler requestHandler, boolean accessLog = false) {
        EmbeddedChannel server = new EmbeddedChannel()
        EmbeddedChannel client = new EmbeddedChannel()
        EmbeddedTestUtil.connect(server, client)
        // adding to the pipeline writes the http2 preface, so do it after connecting the server and client
        def builder = new Http2ServerHandler.ConnectionHandlerBuilder(requestHandler)
        if (accessLog) {
            def logger = (Logger) LoggerFactory.getLogger(Http2ServerHandlerSpec.name + ".accessLog")
            logger.setLevel(Level.INFO)
            builder.accessLogManagerFactory(new Http2AccessLogManager.Factory(logger, null, null))
        }
        server.pipeline().addLast(builder.build())
        def duplexHandler = new DuplexHandler()
        client.pipeline().addLast(Http2FrameCodecBuilder.forClient().build(), duplexHandler)

        return new Tuple(server, client, duplexHandler)
    }

    private static ByteBuf randomData(int size) {
        def buf = ByteBufAllocator.DEFAULT.buffer(size)
        def bytes = new byte[size]
        ThreadLocalRandom.current().nextBytes(bytes)
        buf.writeBytes(bytes)
        return buf
    }

    def simple() {
        given:
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                Assertions.assertEquals(HttpMethod.GET, request.method())
                Assertions.assertEquals("/", request.uri())
                Assertions.assertEquals("yawk.at", request.headers().getAsString(HttpHeaderNames.HOST))

                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when:
        def stream1 = duplexHandler.newStream()
        def req1 = new DefaultHttp2Headers()
        req1.method(HttpMethod.GET.asciiName())
        req1.scheme("http")
        req1.authority("yawk.at")
        req1.path("/")
        client.writeOutbound(new DefaultHttp2HeadersFrame(req1, true).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        then:
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        def response = (Http2HeadersFrame) client.readInbound()
        "200".contentEquals(response.headers().status())
        "0".contentEquals(response.headers().get(HttpHeaderNames.CONTENT_LENGTH))

        cleanup:
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)
    }

    def "a response that fails after its headers were handed over is reset, not followed by other headers"() {
        given:
        def content = Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)
        Throwable failure = null
        int responsesWritten = 0
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                def headers = new DefaultHttpHeaders() {
                    @Override
                    Iterator<Map.Entry<CharSequence, CharSequence>> iteratorCharSequence() {
                        throw new OutOfMemoryError("Simulated failure")
                    }
                }
                try {
                    outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, headers), new NettyByteBodyFactory(ctx.channel()).adapt(content))
                } catch (Throwable t) {
                    failure = t
                    outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR), NettyByteBodyFactory.empty())
                }
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }

            @Override
            void responseWritten(Object attachment) {
                responsesWritten++
            }
        })

        when:
        def stream1 = duplexHandler.newStream()
        def req1 = new DefaultHttp2Headers()
        req1.method(HttpMethod.GET.asciiName())
        req1.scheme("http")
        req1.authority("yawk.at")
        req1.path("/")
        client.writeOutbound(new DefaultHttp2HeadersFrame(req1, true).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        then:
        failure instanceof OutOfMemoryError
        content.refCnt() == 0
        responsesWritten == 1
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        def reset = (Http2ResetFrame) client.readInbound()
        reset.errorCode() == Http2Error.INTERNAL_ERROR.code()
        client.readInbound() == null

        cleanup:
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)
    }

    def "abort resets a stream whose response is not complete"() {
        given:
        OutboundAccess access = null
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                access = outboundAccess
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when:
        def stream1 = duplexHandler.newStream()
        def req1 = new DefaultHttp2Headers()
        req1.method(HttpMethod.GET.asciiName())
        req1.scheme("http")
        req1.authority("yawk.at")
        req1.path("/")
        client.writeOutbound(new DefaultHttp2HeadersFrame(req1, true).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        access.abort()
        EmbeddedTestUtil.advance(server, client)
        then:
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        ((Http2ResetFrame) client.readInbound()).errorCode() == Http2Error.INTERNAL_ERROR.code()

        when:
        // the stream is finished: a late response is dropped
        access.abort()
        access.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty())
        EmbeddedTestUtil.advance(server, client)
        then:
        client.readInbound() == null

        cleanup:
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)
    }

    def "upload backpressure"() {
        given:
        Subscription serverSubscription = null
        CompositeByteBuf received = ByteBufAllocator.DEFAULT.compositeBuffer()
        int requested = 0
        boolean complete = false
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                NettyByteBodyFactory.toByteBufs(body).subscribe(new Subscriber<ByteBuf>() {
                    @Override
                    void onSubscribe(Subscription s) {
                        serverSubscription = s
                    }

                    @Override
                    void onNext(ByteBuf byteBuf) {
                        requested -= byteBuf.readableBytes()
                        received.addComponent(true, byteBuf)
                        if (requested > 0) {
                            serverSubscription.request(1)
                        }
                    }

                    @Override
                    void onError(Throwable t) {
                        t.printStackTrace()
                    }

                    @Override
                    void onComplete() {
                        complete = true
                    }
                })
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when: "send the request headers"
        def stream1 = duplexHandler.newStream()
        def req1 = new DefaultHttp2Headers()
        req1.method(HttpMethod.POST.asciiName())
        req1.scheme("http")
        req1.authority("yawk.at")
        req1.path("/")
        client.writeOutbound(new DefaultHttp2HeadersFrame(req1, false).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        then:
        def serverSettings = (Http2SettingsFrame) client.readInbound()
        client.readInbound() instanceof Http2SettingsAckFrame

        when: "enqueue two data packets"
        def windowSize = serverSettings.settings().initialWindowSize() ?: Http2CodecUtil.DEFAULT_WINDOW_SIZE
        def data1 = randomData(windowSize)
        def future1 = client.writeOneOutbound(new DefaultHttp2DataFrame(data1.retainedSlice(), false).stream(stream1))
        def data2 = randomData(60000)
        def future2 = client.writeOneOutbound(new DefaultHttp2DataFrame(data2.retainedSlice(), false).stream(stream1))
        client.flushOutbound()
        EmbeddedTestUtil.advance(server, client)
        then: "first data packet is written because it fits the initial window, second is not"
        client.readInbound() == null
        future1.isDone()
        !future2.isDone()

        when: "we request all the data from the first packet"
        requested += data1.readableBytes()
        serverSubscription.request(1)
        EmbeddedTestUtil.advance(server, client)
        then: "first packet is fully transferred, second is still not done (window has not reached its end)"
        received.readSlice(received.readableBytes()) == data1
        !future2.isDone()

        when: "we request all data from the second packet"
        requested += data2.readableBytes()
        serverSubscription.request(1)
        EmbeddedTestUtil.advance(server, client)
        then: "second packet is fully transferred"
        future2.isDone()
        received.readSlice(received.readableBytes()) == data2

        cleanup:
        received.release()
        data1.release()
        data2.release()
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)
    }

    /**
     * HTTP/2 needs no counterpart to the HTTP/1 handler's combined add-and-complete: netty's
     * encoder merges DATA frames queued in the same event loop tick, including the empty
     * END_STREAM frame that follows the trailing bytes, so the stream already ends with the frame
     * that carries them.
     */
    def "a concatenated response ends the stream with the frame carrying its trailing bytes"() {
        given:
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                def bbf = new NettyByteBodyFactory(ctx.channel())
                def elements = Flux.just(bbf.adapt(Unpooled.copiedBuffer("1", StandardCharsets.UTF_8)), bbf.adapt(Unpooled.copiedBuffer("2", StandardCharsets.UTF_8)))
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), ConcatenatingSubscriber.concatenate(bbf, elements, ConcatenatingSubscriber.Separators.JDK_JSON))
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when:
        def stream1 = duplexHandler.newStream()
        def req1 = new DefaultHttp2Headers()
        req1.method(HttpMethod.GET.asciiName())
        req1.scheme("http")
        req1.authority("yawk.at")
        req1.path("/")
        client.writeOutbound(new DefaultHttp2HeadersFrame(req1, true).stream(stream1))
        EmbeddedTestUtil.advance(server, client)

        then: "the closing bracket travels in the end-of-stream frame, there is no empty terminating frame"
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        client.readInbound() instanceof Http2HeadersFrame
        duplexHandler.dataFrames == [['[1', false], [',2]', true]]

        cleanup:
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)
    }

    def "download backpressure"() {
        given:
        Subscriber<? super ByteBuf> subscriber = null
        long demand = 0
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), new NettyByteBodyFactory(ctx.channel()).adaptNetty(new Publisher<ByteBuf>() {
                    @Override
                    void subscribe(Subscriber<? super ByteBuf> s) {
                        subscriber = s
                        s.onSubscribe(new Subscription() {
                            @Override
                            void request(long n) {
                                demand += n
                            }

                            @Override
                            void cancel() {

                            }
                        })
                    }
                }))
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when: "send request"
        def stream1 = duplexHandler.newStream()
        def req1 = new DefaultHttp2Headers()
        req1.method(HttpMethod.POST.asciiName())
        req1.scheme("http")
        req1.authority("yawk.at")
        req1.path("/")
        client.writeOutbound(new DefaultHttp2HeadersFrame(req1, false).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        then: "we get the response headers, and a demand for body data"
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        client.readInbound() instanceof Http2HeadersFrame
        demand > 0

        when: "we satisfy the demand for body data"
        def windowSize = duplexHandler.frameCodec.connection().remote().flowController().windowSize(duplexHandler.frameCodec.connection().stream(stream1.id()))
        def data1 = randomData(windowSize)
        subscriber.onNext(data1.retainedSlice())
        demand--
        EmbeddedTestUtil.advance(server, client)
        then: "first data is fully written & received, so there is new demand. window is now empty"
        demand > 0
        duplexHandler.received.readSlice(duplexHandler.received.readableBytes()) == data1

        when: "we satisfy the demand for body data"
        def data2 = randomData(100)
        subscriber.onNext(data2.retainedSlice())
        demand--
        EmbeddedTestUtil.advance(server, client)
        then: "because window is empty, data2 is not written -> no new demand"
        demand == 0
        !duplexHandler.received.isReadable()

        when: "we expand the window from the client to ack the data1 read"
        client.writeOutbound(new DefaultHttp2WindowUpdateFrame(data1.readableBytes()).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        then: "window now includes data2, it is sent and there is new demand"
        demand > 0
        duplexHandler.received.readSlice(duplexHandler.received.readableBytes()) == data2

        when: "we satisfy the demand for more data"
        def data3 = randomData(50000)
        subscriber.onNext(data3.retainedSlice())
        demand--
        EmbeddedTestUtil.advance(server, client)
        then: "we receive data3 fully, because it is smaller than window size"
        demand > 0
        duplexHandler.received.readSlice(duplexHandler.received.readableBytes()) == data3

        cleanup:
        data1.release()
        data2.release()
        data3.release()
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)
    }

    def "download inputstream backpressure"() {
        given:
        ExecutorService service = Executors.newSingleThreadExecutor()
        long read = 0
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), InputStreamByteBody.create(new InputStream() {
                    @Override
                    int read() throws IOException {
                        read++
                        return 1
                    }
                }, OptionalLong.empty(), service, new NettyByteBodyFactory(ctx.channel())))
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when: "send request"
        def stream1 = duplexHandler.newStream()
        def req1 = new DefaultHttp2Headers()
        req1.method(HttpMethod.POST.asciiName())
        req1.scheme("http")
        req1.authority("yawk.at")
        req1.path("/")
        client.writeOutbound(new DefaultHttp2HeadersFrame(req1, false).stream(stream1))
        def windowSize = duplexHandler.frameCodec.connection().local().flowController().windowSize(duplexHandler.frameCodec.connection().stream(stream1.id()))
        EmbeddedTestUtil.advance(client, server)
        then:"read and buffered some bytes"
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        client.readInbound() instanceof Http2HeadersFrame
        new PollingConditions(timeout: 5).eventually {
            EmbeddedTestUtil.advance(client, server)
            // 8192 is ExtendedInputStream.CHUNK_SIZE
            read == (windowSize.intdiv(8192) + 1) * 8192
            duplexHandler.received.readableBytes() == windowSize
        }

        when:"consume some of the bytes"
        read = 0
        // have to munch a number of bytes that is:
        // - not too close to a multiple of windowSize so that we aren't below the window update threshold
        // - not too small to be satisfied by the existing buffered chunks
        int toConsume = (int) (8192 * 5.5)
        while (toConsume > 0) {
            def n = Math.min(duplexHandler.received.readableBytes(), toConsume)
            duplexHandler.received.skipBytes(n)
            client.writeOutbound(new DefaultHttp2WindowUpdateFrame(n).stream(stream1))
            EmbeddedTestUtil.advance(client, server)
            toConsume -= n
        }
        then:"more chunks read from the input stream"
        new PollingConditions(timeout: 5).eventually {
            EmbeddedTestUtil.advance(client, server)
            read == 6 * 8192
            duplexHandler.received.readableBytes() == windowSize
        }

        cleanup:
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)
        service.shutdownNow()
    }

    def "upload stream reset"(Http2Frame frame) {
        given:
        Throwable err = null
        CompositeByteBuf received = ByteBufAllocator.DEFAULT.compositeBuffer()
        boolean complete = false
        boolean responseWritten = false
        OutboundAccess outboundAccessR = null
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                outboundAccessR = outboundAccess
                NettyByteBodyFactory.toByteBufs(body).subscribe(new Subscriber<ByteBuf>() {
                    @Override
                    void onSubscribe(Subscription s) {
                        s.request(Long.MAX_VALUE)
                    }

                    @Override
                    void onNext(ByteBuf byteBuf) {
                        received.addComponent(true, byteBuf)
                    }

                    @Override
                    void onError(Throwable t) {
                        err = t
                    }

                    @Override
                    void onComplete() {
                        complete = true
                    }
                })
            }

            @Override
            void responseWritten(Object attachment) {
                responseWritten = true
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when: "send the request headers"
        def stream1 = duplexHandler.newStream()
        def req1 = new DefaultHttp2Headers()
        req1.method(HttpMethod.POST.asciiName())
        req1.scheme("http")
        req1.authority("yawk.at")
        req1.path("/")
        client.writeOutbound(new DefaultHttp2HeadersFrame(req1, false).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        then:
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame

        when: "send some data"
        def data1 = randomData(500)
        client.writeOutbound(new DefaultHttp2DataFrame(data1.retainedSlice(), false).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        then: "data is received by server"
        received.readSlice(received.readableBytes()) == data1

        when:
        if (frame instanceof Http2StreamFrame) frame.stream(stream1)
        client.writeOutbound(frame)
        EmbeddedTestUtil.advance(server, client)
        then:
        err instanceof ClosedChannelException
        !responseWritten

        when:
        outboundAccessR.write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty())
        then:
        responseWritten

        cleanup:
        received.release()
        data1.release()
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)

        where:
        frame << [new DefaultHttp2ResetFrame(Http2Error.CANCEL), new DefaultHttp2GoAwayFrame(Http2Error.CANCEL)]
    }

    def "download exception"(Exception exception, Http2Error expectedCode) {
        given:
        Subscriber<? super ByteBuf> subscriber = null
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), new NettyByteBodyFactory(ctx.channel()).adaptNetty(new Publisher<ByteBuf>() {
                    @Override
                    void subscribe(Subscriber<? super ByteBuf> s) {
                        subscriber = s
                        s.onSubscribe(new Subscription() {
                            @Override
                            void request(long n) {
                            }

                            @Override
                            void cancel() {
                            }
                        })
                    }
                }))
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when: "send request"
        def stream1 = duplexHandler.newStream()
        def req1 = new DefaultHttp2Headers()
        req1.method(HttpMethod.POST.asciiName())
        req1.scheme("http")
        req1.authority("yawk.at")
        req1.path("/")
        client.writeOutbound(new DefaultHttp2HeadersFrame(req1, false).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        then: "we get the response headers, and a demand for body data"
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        client.readInbound() instanceof Http2HeadersFrame

        when: "send some data"
        def data1 = randomData(100)
        subscriber.onNext(data1.retainedSlice())
        EmbeddedTestUtil.advance(server, client)
        then: "data is received"
        duplexHandler.received.readSlice(duplexHandler.received.readableBytes()) == data1

        when: "send error"
        subscriber.onError(exception)
        EmbeddedTestUtil.advance(server, client)
        then: "because window is empty, data2 is not written -> no new demand"
        Http2ResetFrame rst = client.readInbound()
        rst.errorCode() == expectedCode.code()

        cleanup:
        data1.release()
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)

        where:
        exception                              | expectedCode
        new Exception()                        | Http2Error.INTERNAL_ERROR
        new Http2Exception(Http2Error.CANCEL)  | Http2Error.CANCEL
        ByteBody.BodyDiscardedException.create() | Http2Error.CANCEL
    }

    def "closeIfNoSubscriber"() {
        given:
        CloseableByteBody b = null
        OutboundAccess oa = null
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                b = body
                oa = outboundAccess
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when: "send the request headers"
        def stream1 = duplexHandler.newStream()
        def req1 = new DefaultHttp2Headers()
        req1.method(HttpMethod.POST.asciiName())
        req1.scheme("http")
        req1.authority("yawk.at")
        req1.path("/")
        client.writeOutbound(new DefaultHttp2HeadersFrame(req1, false).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        then:
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame

        when:"send some data, then close the request"
        def data1 = randomData(500)
        client.writeOutbound(new DefaultHttp2DataFrame(data1.retainedSlice(), false).stream(stream1))
        b.close()
        EmbeddedTestUtil.advance(server, client)
        then:"no reset yet"
        client.readInbound() == null

        when:"response can still be sent"
        oa.write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty())
        EmbeddedTestUtil.advance(server, client)
        then:"we get the response but then a reset"
        Http2HeadersFrame response = client.readInbound()
        AsciiString.contentEquals(response.headers().status(), HttpResponseStatus.OK.codeAsText())
        Http2ResetFrame rst = client.readInbound()
        rst.stream() == stream1
        // the response was delivered in full, so this is not a failure for the client
        rst.errorCode() == Http2Error.NO_ERROR.code()

        cleanup:
        data1.release()
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)
    }

    def "complete response before request body is finished resets with NO_ERROR"() {
        given: "a handler that answers without reading the request body"
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED), NettyByteBodyFactory.empty())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when: "the client starts an upload that it has not finished"
        def stream1 = duplexHandler.newStream()
        def req1 = new DefaultHttp2Headers()
        req1.method(HttpMethod.POST.asciiName())
        req1.scheme("http")
        req1.authority("yawk.at")
        req1.path("/")
        client.writeOutbound(new DefaultHttp2HeadersFrame(req1, false).stream(stream1))
        def data1 = randomData(500)
        client.writeOutbound(new DefaultHttp2DataFrame(data1.retainedSlice(), false).stream(stream1))
        EmbeddedTestUtil.advance(server, client)

        then: "the complete response is followed by a reset that does not signal an error"
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        Http2HeadersFrame response = client.readInbound()
        AsciiString.contentEquals(response.headers().status(), HttpResponseStatus.UNAUTHORIZED.codeAsText())
        Http2ResetFrame rst = client.readInbound()
        rst.stream() == stream1
        rst.errorCode() == Http2Error.NO_ERROR.code()

        cleanup:
        data1.release()
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)
    }

    def "download cancelled by client"() {
        given:
        Subscriber<? super ByteBuf> subscriber = null
        long demand = 0
        boolean cancelled = false
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), new NettyByteBodyFactory(ctx.channel()).adaptNetty(new Publisher<ByteBuf>() {
                    @Override
                    void subscribe(Subscriber<? super ByteBuf> s) {
                        subscriber = s
                        s.onSubscribe(new Subscription() {
                            @Override
                            void request(long n) {
                                demand += n
                            }

                            @Override
                            void cancel() {
                                cancelled = true
                            }
                        })
                    }
                }))
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when: "send request"
        def stream1 = duplexHandler.newStream()
        def req1 = new DefaultHttp2Headers()
        req1.method(HttpMethod.POST.asciiName())
        req1.scheme("http")
        req1.authority("yawk.at")
        req1.path("/")
        client.writeOutbound(new DefaultHttp2HeadersFrame(req1, false).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        def windowSize = duplexHandler.frameCodec.connection().remote().flowController().windowSize(duplexHandler.frameCodec.connection().stream(stream1.id()))
        def data1 = randomData(windowSize)
        subscriber.onNext(data1.retainedSlice())
        demand--
        EmbeddedTestUtil.advance(server, client)
        def data2 = randomData(100)
        subscriber.onNext(data2.retainedSlice())
        demand--
        EmbeddedTestUtil.advance(server, client)
        then: "we get the response headers, and a demand for body data"
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        client.readInbound() instanceof Http2HeadersFrame
        demand == 0
        duplexHandler.received.readSlice(duplexHandler.received.readableBytes()) == data1

        when: "cancel the stream"
        client.writeOutbound(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        then: "future cancelled"
        cancelled

        cleanup:
        data1.release()
        data2.release()
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)
    }

    /**
     * A bare server channel with the HTTP/2 connection handler and a handler behind it that
     * records the user events that make it that far.
     */
    private static Tuple2<EmbeddedChannel, List<Object>> configureWithEventRecorder() {
        def received = []
        def server = new EmbeddedChannel()
        server.pipeline().addLast(new Http2ServerHandler.ConnectionHandlerBuilder(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }).build())
        server.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                received.add(evt)
                super.userEventTriggered(ctx, evt)
            }
        })
        return new Tuple2<>(server, received)
    }

    def "unrecognized user events are forwarded down the pipeline"() {
        given: "a server pipeline with a handler downstream of the http2 connection handler"
        def (server, received) = configureWithEventRecorder()

        when: "an event the connection handler does not consume is fired"
        server.pipeline().fireUserEventTriggered(event)

        then: "it reaches the next handler and the connection stays up"
        received == [event]
        server.isOpen()

        cleanup:
        server.checkException()
        server.finishAndReleaseAll()

        where:
        event << [new Object(), IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT]
    }

    def "an all-idle event closes the connection"() {
        given:
        def (server, received) = configureWithEventRecorder()

        when: "the connection has been idle for too long"
        server.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT)

        then: "it is closed instead of the event being passed on"
        !server.isOpen()
        received == []

        cleanup:
        server.checkException()
        server.finishAndReleaseAll()
    }

    def "ping response"() {
        given:
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when: "send a ping"
        client.writeOutbound(new DefaultHttp2PingFrame(123))
        EmbeddedTestUtil.advance(server, client)
        then:
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        Http2PingFrame ack = client.readInbound()
        ack.ack()
        ack.content() == 123

        cleanup:
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)
    }

    def "100-continue: no continue"() {
        given:
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                Assertions.assertEquals(HttpMethod.POST, request.method())
                Assertions.assertEquals("/", request.uri())
                Assertions.assertEquals("yawk.at", request.headers().getAsString(HttpHeaderNames.HOST))

                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when:
        def stream1 = duplexHandler.newStream()
        def req1 = new DefaultHttp2Headers()
        req1.method(HttpMethod.POST.asciiName())
        req1.scheme("http")
        req1.authority("yawk.at")
        req1.path("/")
        req1.set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE)
        client.writeOutbound(new DefaultHttp2HeadersFrame(req1, true).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        then:
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        def response = (Http2HeadersFrame) client.readInbound()
        "200".contentEquals(response.headers().status())
        "0".contentEquals(response.headers().get(HttpHeaderNames.CONTENT_LENGTH))

        cleanup:
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)
    }

    def "100-continue: do continue"() {
        given:
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                Assertions.assertEquals(HttpMethod.POST, request.method())
                Assertions.assertEquals("/", request.uri())
                Assertions.assertEquals("yawk.at", request.headers().getAsString(HttpHeaderNames.HOST))

                InternalByteBody.bufferFlow(body).onComplete((imm, t) -> {
                    def bb = NettyByteBodyFactory.toByteBuf(imm)
                    outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), new NettyByteBodyFactory(ctx.channel()).adapt(bb))
                })
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when:
        def stream1 = duplexHandler.newStream()
        def req1 = new DefaultHttp2Headers()
        req1.method(HttpMethod.POST.asciiName())
        req1.scheme("http")
        req1.authority("yawk.at")
        req1.path("/")
        req1.set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE)
        client.writeOutbound(new DefaultHttp2HeadersFrame(req1, false).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        then:
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        def cresponse = (Http2HeadersFrame) client.readInbound()
        "100".contentEquals(cresponse.headers().status())

        when:
        client.writeOutbound(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8), true).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        then:
        def response = (Http2HeadersFrame) client.readInbound()
        "200".contentEquals(response.headers().status())
        "3".contentEquals(response.headers().get(HttpHeaderNames.CONTENT_LENGTH))
        !response.isEndStream()
        duplexHandler.received.toString(StandardCharsets.UTF_8) == "foo"

        cleanup:
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)
    }

    def "100-continue: rejecting the request without reading the body does not send 100 continue"(boolean closeFirst, boolean streamingResponse) {
        given: "a handler that answers without reading the request body"
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                if (closeFirst) {
                    body.close()
                }
                def unauthorized = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED)
                if (streamingResponse) {
                    outboundAccess.write(unauthorized, new NettyByteBodyFactory(ctx.channel()).adaptNetty(Flux.just(Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8))))
                } else {
                    outboundAccess.write(unauthorized, NettyByteBodyFactory.empty())
                }
                if (!closeFirst) {
                    body.close()
                }
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when: "the client starts an upload that waits for 100 continue"
        def stream1 = duplexHandler.newStream()
        def req1 = new DefaultHttp2Headers()
        req1.method(HttpMethod.POST.asciiName())
        req1.scheme("http")
        req1.authority("yawk.at")
        req1.path("/")
        req1.set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE)
        client.writeOutbound(new DefaultHttp2HeadersFrame(req1, false).stream(stream1))
        EmbeddedTestUtil.advance(server, client)

        then: "the final response is sent without an interim response and the connection stays up"
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        Http2HeadersFrame response = client.readInbound()
        AsciiString.contentEquals(response.headers().status(), HttpResponseStatus.UNAUTHORIZED.codeAsText())
        List<Object> rest = []
        Object msg
        while ((msg = client.readInbound()) != null) {
            rest.add(msg)
        }
        !rest.any { it instanceof Http2HeadersFrame }
        !rest.any { it instanceof Http2GoAwayFrame }
        rest.every { !(it instanceof Http2ResetFrame) || ((Http2ResetFrame) it).stream() == stream1 }
        server.isOpen()

        when: "another request is sent on the same connection"
        rest.each { ReferenceCountUtil.release(it) }
        def stream2 = duplexHandler.newStream()
        def req2 = new DefaultHttp2Headers()
        req2.method(HttpMethod.GET.asciiName())
        req2.scheme("http")
        req2.authority("yawk.at")
        req2.path("/")
        client.writeOutbound(new DefaultHttp2HeadersFrame(req2, true).stream(stream2))
        EmbeddedTestUtil.advance(server, client)

        then: "it is answered"
        Http2HeadersFrame response2 = client.readInbound()
        response2.stream() == stream2
        AsciiString.contentEquals(response2.headers().status(), HttpResponseStatus.UNAUTHORIZED.codeAsText())

        cleanup:
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)

        where:
        closeFirst | streamingResponse
        true       | false
        false      | false
        true       | true
        false      | true
    }

    def "a request without required pseudo-headers resets only its own stream"(String missing, boolean accessLog) {
        given:
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }, accessLog)

        when: "a request is sent without a required pseudo-header"
        def stream1 = duplexHandler.newStream()
        def req1 = new DefaultHttp2Headers()
        if (missing != "method") {
            req1.method(HttpMethod.GET.asciiName())
        }
        req1.scheme("http")
        req1.authority("yawk.at")
        if (missing != "path") {
            req1.path("/")
        }
        client.writeOutbound(new DefaultHttp2HeadersFrame(req1, true).stream(stream1))
        EmbeddedTestUtil.advance(server, client)

        then: "the stream is reset with PROTOCOL_ERROR"
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        Http2ResetFrame rst = client.readInbound()
        rst.stream() == stream1
        rst.errorCode() == Http2Error.PROTOCOL_ERROR.code()
        client.readInbound() == null

        when: "another request is sent on the same connection"
        def stream2 = duplexHandler.newStream()
        def req2 = new DefaultHttp2Headers()
        req2.method(HttpMethod.GET.asciiName())
        req2.scheme("http")
        req2.authority("yawk.at")
        req2.path("/")
        client.writeOutbound(new DefaultHttp2HeadersFrame(req2, true).stream(stream2))
        EmbeddedTestUtil.advance(server, client)

        then: "it is answered"
        Http2HeadersFrame response2 = client.readInbound()
        response2.stream() == stream2
        "200".contentEquals(response2.headers().status())

        cleanup:
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)

        where:
        missing  | accessLog
        "method" | false
        "path"   | false
        "method" | true
        "path"   | true
    }

    def "a response to a stream that was reset by the server's decoder does not fail the connection"(boolean streaming) {
        given: "a handler that answers later"
        OutboundAccess pending = null
        Subscriber<? super ByteBuf> responseSubscriber = null
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                if (pending == null) {
                    body.close()
                    pending = outboundAccess
                    if (streaming) {
                        // response headers go out now, the body completes after the reset
                        outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), new NettyByteBodyFactory(ctx.channel()).adaptNetty(new Publisher<ByteBuf>() {
                            @Override
                            void subscribe(Subscriber<? super ByteBuf> s) {
                                responseSubscriber = s
                                s.onSubscribe(new Subscription() {
                                    @Override
                                    void request(long n) {
                                    }

                                    @Override
                                    void cancel() {
                                    }
                                })
                            }
                        }))
                    }
                } else {
                    body.close()
                    outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty())
                }
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when: "the client sends more data than its declared content-length"
        def stream1 = duplexHandler.newStream()
        def req1 = new DefaultHttp2Headers()
        req1.method(HttpMethod.POST.asciiName())
        req1.scheme("http")
        req1.authority("yawk.at")
        req1.path("/")
        req1.set(HttpHeaderNames.CONTENT_LENGTH, "1")
        client.writeOutbound(new DefaultHttp2HeadersFrame(req1, false).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        client.writeOutbound(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("hello", StandardCharsets.UTF_8), true).stream(stream1))
        EmbeddedTestUtil.advance(server, client)

        then: "the server resets the stream"
        pending != null
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        if (streaming) {
            Http2HeadersFrame response1 = client.readInbound()
            assert response1.stream() == stream1
        }
        Http2ResetFrame rst = client.readInbound()
        rst.stream() == stream1
        rst.errorCode() == Http2Error.PROTOCOL_ERROR.code()

        when: "the response for the reset stream is written afterwards"
        if (streaming) {
            responseSubscriber.onComplete()
        } else {
            pending.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED), NettyByteBodyFactory.empty())
        }
        EmbeddedTestUtil.advance(server, client)
        def stream2 = duplexHandler.newStream()
        def req2 = new DefaultHttp2Headers()
        req2.method(HttpMethod.GET.asciiName())
        req2.scheme("http")
        req2.authority("yawk.at")
        req2.path("/")
        client.writeOutbound(new DefaultHttp2HeadersFrame(req2, true).stream(stream2))
        EmbeddedTestUtil.advance(server, client)

        then: "the connection stays up and the next request is answered"
        Http2HeadersFrame response2 = client.readInbound()
        response2.stream() == stream2
        "200".contentEquals(response2.headers().status())
        client.readInbound() == null

        cleanup:
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)

        where:
        streaming << [false, true]
    }

    /**
     * Like {@link #configure(RequestHandler)}, but the client bytes are not forwarded
     * automatically. Use {@link #transfer(EmbeddedChannel, EmbeddedChannel, boolean)} to deliver
     * everything the client has written so far to the server as a <i>single</i> read batch.
     */
    private static Tuple3<EmbeddedChannel, EmbeddedChannel, DuplexHandler> configureRaw(RequestHandler requestHandler) {
        EmbeddedChannel server = new EmbeddedChannel(new Http2ServerHandler.ConnectionHandlerBuilder(requestHandler).build())
        def duplexHandler = new DuplexHandler()
        EmbeddedChannel client = new EmbeddedChannel(Http2FrameCodecBuilder.forClient().build(), duplexHandler)
        return new Tuple(server, client, duplexHandler)
    }

    private static ByteBuf drainOutbound(EmbeddedChannel channel) {
        CompositeByteBuf composite = ByteBufAllocator.DEFAULT.compositeBuffer()
        ByteBuf buf
        while ((buf = channel.readOutbound()) != null) {
            composite.addComponent(true, buf)
        }
        return composite
    }

    /**
     * Deliver all bytes the raw client has written to the server in one channelRead, optionally
     * followed by a channelReadComplete, and then forward the server output to the client.
     */
    private static void transfer(EmbeddedChannel server, EmbeddedChannel client, boolean readComplete) {
        def packet = drainOutbound(client)
        if (readComplete) {
            server.writeInbound(packet)
        } else {
            server.writeOneInbound(packet)
        }
        server.runPendingTasks()
        def response = drainOutbound(server)
        if (response.isReadable() && client.isOpen()) {
            client.writeInbound(response)
        } else {
            response.release()
        }
        client.runPendingTasks()
    }

    private static DefaultHttp2Headers postHeaders() {
        def req = new DefaultHttp2Headers()
        req.method(HttpMethod.POST.asciiName())
        req.scheme("http")
        req.authority("yawk.at")
        req.path("/")
        return req
    }

    private static class BodyCollector implements Subscriber<ByteBuf> {
        final CompositeByteBuf received = ByteBufAllocator.DEFAULT.compositeBuffer()
        Throwable err
        boolean complete

        @Override
        void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE)
        }

        @Override
        void onNext(ByteBuf byteBuf) {
            received.addComponent(true, byteBuf)
        }

        @Override
        void onError(Throwable t) {
            err = t
        }

        @Override
        void onComplete() {
            complete = true
        }
    }

    def "stream reset before readComplete releases buffered data"() {
        given:
        boolean accepted = false
        def (server, client, duplexHandler) = configureRaw(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                accepted = true
                body.close()
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when: "headers, data and rst arrive in one read batch"
        def stream1 = duplexHandler.newStream()
        client.writeOutbound(new DefaultHttp2HeadersFrame(postHeaders(), false).stream(stream1))
        client.writeOutbound(new DefaultHttp2DataFrame(randomData(500), false).stream(stream1))
        client.writeOutbound(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream1))
        transfer(server, client, true)
        then: "the request was never dispatched, and the buffered data is released (checked by the leak detector)"
        !accepted
        server.isOpen()

        cleanup:
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
    }

    def "connection close with streaming body in progress"() {
        given:
        def collector = new BodyCollector()
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                NettyByteBodyFactory.toByteBufs(body).subscribe(collector)
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when: "send the request headers and some data"
        def stream1 = duplexHandler.newStream()
        client.writeOutbound(new DefaultHttp2HeadersFrame(postHeaders(), false).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        def data1 = randomData(500)
        client.writeOutbound(new DefaultHttp2DataFrame(data1.retainedSlice(), false).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        then: "the body is in streaming mode and the data has been received"
        collector.received.readSlice(collector.received.readableBytes()) == data1
        collector.err == null
        !collector.complete

        when: "the connection goes away (idle timeout, client disconnect)"
        server.unsafe().close(server.newPromise())
        EmbeddedTestUtil.advance(server, client)
        then: "the body subscriber is notified"
        collector.err instanceof ClosedChannelException
        !collector.complete

        cleanup:
        collector.received.release()
        data1.release()
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
    }

    def "connection close before readComplete releases buffered data"() {
        given:
        boolean accepted = false
        def (server, client, duplexHandler) = configureRaw(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                accepted = true
                body.close()
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when: "headers and data arrive in one read, and the connection closes before readComplete"
        def stream1 = duplexHandler.newStream()
        client.writeOutbound(new DefaultHttp2HeadersFrame(postHeaders(), false).stream(stream1))
        client.writeOutbound(new DefaultHttp2DataFrame(randomData(500), false).stream(stream1))
        transfer(server, client, false)
        server.unsafe().close(server.newPromise())
        server.runPendingTasks()
        then: "the request was never dispatched, and the buffered data is released (checked by the leak detector)"
        !accepted
        !server.isOpen()

        cleanup:
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
    }

    def "trailers complete the request body (readComplete between frames)"() {
        given:
        def collector = new BodyCollector()
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                NettyByteBodyFactory.toByteBufs(body).subscribe(collector)
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when: "send headers, data and trailers, each in their own read"
        def stream1 = duplexHandler.newStream()
        client.writeOutbound(new DefaultHttp2HeadersFrame(postHeaders(), false).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        def data1 = randomData(500)
        client.writeOutbound(new DefaultHttp2DataFrame(data1.retainedSlice(), false).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        then:
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        !collector.complete

        when:
        def trailers = new DefaultHttp2Headers()
        trailers.set("x-checksum", "abc")
        client.writeOutbound(new DefaultHttp2HeadersFrame(trailers, true).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        then: "the body completes with the data, and the response arrives"
        collector.complete
        collector.err == null
        collector.received.readSlice(collector.received.readableBytes()) == data1
        def response = (Http2HeadersFrame) client.readInbound()
        "200".contentEquals(response.headers().status())
        response.isEndStream()

        cleanup:
        collector.received.release()
        data1.release()
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)
    }

    def "trailers complete the request body (single read batch)"() {
        given:
        boolean accepted = false
        ByteBuf receivedBody = null
        Throwable err = null
        def (server, client, duplexHandler) = configureRaw(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                accepted = true
                InternalByteBody.bufferFlow(body).onComplete((imm, t) -> {
                    err = t
                    if (imm != null) {
                        receivedBody = NettyByteBodyFactory.toByteBuf(imm)
                    }
                })
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when: "headers, data and trailers arrive in one read batch"
        def stream1 = duplexHandler.newStream()
        client.writeOutbound(new DefaultHttp2HeadersFrame(postHeaders(), false).stream(stream1))
        def data1 = randomData(500)
        client.writeOutbound(new DefaultHttp2DataFrame(data1.retainedSlice(), false).stream(stream1))
        def trailers = new DefaultHttp2Headers()
        trailers.set("x-checksum", "abc")
        client.writeOutbound(new DefaultHttp2HeadersFrame(trailers, true).stream(stream1))
        transfer(server, client, true)
        then: "the request is dispatched with the full body, and the response arrives"
        accepted
        err == null
        receivedBody != null
        receivedBody == data1
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        def response = (Http2HeadersFrame) client.readInbound()
        "200".contentEquals(response.headers().status())
        response.isEndStream()

        cleanup:
        receivedBody?.release()
        data1.release()
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
    }

    def "graceful goaway does not cancel in-flight streams"() {
        given:
        def collector = new BodyCollector()
        OutboundAccess outboundAccessR = null
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                outboundAccessR = outboundAccess
                NettyByteBodyFactory.toByteBufs(body).subscribe(collector)
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when: "send the request headers and some data"
        def stream1 = duplexHandler.newStream()
        client.writeOutbound(new DefaultHttp2HeadersFrame(postHeaders(), false).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        def data1 = randomData(500)
        client.writeOutbound(new DefaultHttp2DataFrame(data1.retainedSlice(), false).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        then:
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        collector.received.readSlice(collector.received.readableBytes()) == data1

        when: "the client announces it will not open more streams"
        client.writeOutbound(new DefaultHttp2GoAwayFrame(Http2Error.NO_ERROR))
        EmbeddedTestUtil.advance(server, client)
        then: "the in-flight request is unaffected"
        collector.err == null
        !collector.complete

        when: "the client finishes the upload"
        def data2 = randomData(100)
        client.writeOutbound(new DefaultHttp2DataFrame(data2.retainedSlice(), true).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        then:
        collector.complete
        collector.received.readSlice(collector.received.readableBytes()) == data2

        when: "the server responds"
        def responseBody = Unpooled.copiedBuffer("bar", StandardCharsets.UTF_8)
        outboundAccessR.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), new NettyByteBodyFactory(server).adapt(responseBody))
        EmbeddedTestUtil.advance(server, client)
        then: "the response is still delivered"
        def response = (Http2HeadersFrame) client.readInbound()
        "200".contentEquals(response.headers().status())
        duplexHandler.received.toString(StandardCharsets.UTF_8) == "bar"

        cleanup:
        collector.received.release()
        data1.release()
        data2.release()
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)
    }

    def "100-continue: rejected before the body is consumed"(String responseBody) {
        given:
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                // e.g. a security filter rejecting the request. The body is closed after the response
                // has been written, which is what RoutingInBoundHandler.responseWritten does.
                def bb = Unpooled.copiedBuffer(responseBody, StandardCharsets.UTF_8)
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED), new NettyByteBodyFactory(ctx.channel()).adapt(bb))
                body.close()
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when:
        def stream1 = duplexHandler.newStream()
        def req1 = postHeaders()
        req1.set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE)
        client.writeOutbound(new DefaultHttp2HeadersFrame(req1, false).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        then: "we get the 401 and a reset for the request body, but no 100 continue and no goaway"
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        def response = (Http2HeadersFrame) client.readInbound()
        "401".contentEquals(response.headers().status())
        response.isEndStream() == responseBody.isEmpty()
        duplexHandler.received.toString(StandardCharsets.UTF_8) == responseBody
        Http2ResetFrame rst = client.readInbound()
        rst.stream() == stream1
        rst.errorCode() == Http2Error.NO_ERROR.code() // the response was delivered in full
        client.readInbound() == null
        server.isOpen()

        cleanup:
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)

        where:
        responseBody << ["", "unauthorized"]
    }

    def "response written after the stream is finished is released"() {
        given:
        OutboundAccess outboundAccessR = null
        boolean subscribed = false
        boolean cancelled = false
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccessR = outboundAccess
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })

        when: "the client resets the stream, and the first response is dropped"
        def stream1 = duplexHandler.newStream()
        client.writeOutbound(new DefaultHttp2HeadersFrame(postHeaders(), false).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        client.writeOutbound(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream1))
        EmbeddedTestUtil.advance(server, client)
        outboundAccessR.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty())
        EmbeddedTestUtil.advance(server, client)
        then:
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        client.readInbound() == null

        when: "another response with a buffered body is written"
        def responseBuf = randomData(100)
        outboundAccessR.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), new NettyByteBodyFactory(server).adapt(responseBuf))
        EmbeddedTestUtil.advance(server, client)
        then: "the body is released"
        responseBuf.refCnt() == 0
        client.readInbound() == null

        when: "a response with a streaming body is written"
        outboundAccessR.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), new NettyByteBodyFactory(server).adaptNetty(new Publisher<ByteBuf>() {
            @Override
            void subscribe(Subscriber<? super ByteBuf> s) {
                subscribed = true
                s.onSubscribe(new Subscription() {
                    @Override
                    void request(long n) {
                    }

                    @Override
                    void cancel() {
                        cancelled = true
                    }
                })
            }
        }))
        EmbeddedTestUtil.advance(server, client)
        then: "the publisher is either never subscribed to, or cancelled"
        !subscribed || cancelled
        client.readInbound() == null

        cleanup:
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)
    }
}
