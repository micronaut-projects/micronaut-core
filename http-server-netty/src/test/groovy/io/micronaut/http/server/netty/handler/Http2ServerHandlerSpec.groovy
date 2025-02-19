package io.micronaut.http.server.netty.handler


import io.micronaut.core.annotation.NonNull
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.InternalByteBody
import io.micronaut.http.body.stream.InputStreamByteBody
import io.micronaut.http.netty.body.AvailableNettyByteBody
import io.micronaut.http.netty.body.NettyBodyAdapter
import io.micronaut.http.netty.body.NettyByteBody
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.server.netty.EmbeddedTestUtil
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.CompositeByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
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
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2PingFrame
import io.netty.handler.codec.http2.Http2ResetFrame
import io.netty.handler.codec.http2.Http2SettingsAckFrame
import io.netty.handler.codec.http2.Http2SettingsFrame
import io.netty.handler.codec.http2.Http2StreamFrame
import io.netty.util.AsciiString
import org.junit.jupiter.api.Assertions
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
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
                received.addComponent(true, msg.content())
            } else {
                ctx.fireChannelRead(msg)
            }
        }
    }

    private static Tuple3<EmbeddedChannel, EmbeddedChannel, DuplexHandler> configure(RequestHandler requestHandler) {
        EmbeddedChannel server = new EmbeddedChannel()
        EmbeddedChannel client = new EmbeddedChannel()
        EmbeddedTestUtil.connect(server, client)
        // adding to the pipeline writes the http2 preface, so do it after connecting the server and client
        server.pipeline().addLast(new Http2ServerHandler.ConnectionHandlerBuilder(requestHandler)
                .build())
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

                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), AvailableNettyByteBody.empty())
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

    def "upload backpressure"() {
        given:
        Subscription serverSubscription = null
        CompositeByteBuf received = ByteBufAllocator.DEFAULT.compositeBuffer()
        int requested = 0
        boolean complete = false
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                NettyByteBody.toByteBufs(body).subscribe(new Subscriber<ByteBuf>() {
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

    def "download backpressure"() {
        given:
        Subscriber<? super ByteBuf> subscriber = null
        long demand = 0
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyBodyAdapter.adapt(new Publisher<ByteBuf>() {
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
                }, ctx.channel().eventLoop()))
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
        def (server, client, duplexHandler) = configure(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                NettyByteBody.toByteBufs(body).subscribe(new Subscriber<ByteBuf>() {
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
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyBodyAdapter.adapt(new Publisher<ByteBuf>() {
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
                }, ctx.channel().eventLoop()))
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
        exception                             | expectedCode
        new Exception()                       | Http2Error.INTERNAL_ERROR
        new Http2Exception(Http2Error.CANCEL) | Http2Error.CANCEL
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
        oa.write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), AvailableNettyByteBody.empty())
        EmbeddedTestUtil.advance(server, client)
        then:"we get the response but then a reset"
        Http2HeadersFrame response = client.readInbound()
        AsciiString.contentEquals(response.headers().status(), HttpResponseStatus.OK.codeAsText())
        Http2ResetFrame rst = client.readInbound()
        rst.stream() == stream1
        rst.errorCode() == Http2Error.CANCEL.code()

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
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyBodyAdapter.adapt(new Publisher<ByteBuf>() {
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
                }, ctx.channel().eventLoop()))
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

                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), AvailableNettyByteBody.empty())
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
                    def bb = AvailableNettyByteBody.toByteBuf(imm)
                    outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), new AvailableNettyByteBody(bb))
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
}
