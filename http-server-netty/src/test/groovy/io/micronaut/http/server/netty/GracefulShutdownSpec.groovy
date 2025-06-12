package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.graceful.GracefulShutdownManager
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler
import io.netty.handler.codec.http2.Http2DataFrame
import io.netty.handler.codec.http2.Http2Error
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2GoAwayFrame
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2SettingsAckFrame
import io.netty.handler.codec.http2.Http2SettingsFrame
import io.netty.handler.codec.http3.DefaultHttp3Headers
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame
import io.netty.handler.codec.http3.Http3
import io.netty.handler.codec.http3.Http3ClientConnectionHandler
import io.netty.handler.codec.http3.Http3DataFrame
import io.netty.handler.codec.http3.Http3HeadersFrame
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler
import io.netty.handler.codec.quic.QuicChannel
import io.netty.handler.codec.quic.QuicSslContextBuilder
import io.netty.handler.codec.quic.QuicStreamChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.ApplicationProtocolConfig
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.reactivestreams.Publisher
import reactor.core.publisher.Sinks
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class GracefulShutdownSpec extends Specification {
    def "http1 before request"() {
        given:
        def server = ApplicationContext.<EmbeddedServer> run(EmbeddedServer, ['spec.name': 'GracefulShutdownSpec'])
        def gracefulShutdown = server.applicationContext.getBean(GracefulShutdownManager)

        def loop = new NioEventLoopGroup(1)
        def ch = new Bootstrap()
                .group(loop)
                .channel(NioSocketChannel)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NonNull Channel c) throws Exception {

                    }
                })
                .connect(server.host, server.port).sync().channel()

        when:
        TimeUnit.SECONDS.sleep(1) // wait for connection to be set up
        def shFuture = gracefulShutdown.shutdownGracefully().toCompletableFuture()

        then:
        new PollingConditions().eventually {
            shFuture.isDone()
            !shFuture.isCompletedExceptionally()
            !ch.isOpen()
        }

        cleanup:
        loop.shutdownGracefully()
        server.close()
    }

    def "http1 after response"() {
        given:
        def server = ApplicationContext.<EmbeddedServer> run(EmbeddedServer, ['spec.name': 'GracefulShutdownSpec'])
        def gracefulShutdown = server.applicationContext.getBean(GracefulShutdownManager)

        def loop = new NioEventLoopGroup(1)
        FullHttpResponse response = null
        def ch = new Bootstrap()
                .group(loop)
                .channel(NioSocketChannel)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NonNull Channel c) throws Exception {
                        c.pipeline().addLast(new HttpClientCodec(), new HttpObjectAggregator(1024), new ChannelInboundHandlerAdapter() {
                            @Override
                            void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) throws Exception {
                                response = (FullHttpResponse) msg
                            }
                        })
                    }
                })
                .connect(server.host, server.port).sync().channel()

        when:
        ch.writeAndFlush(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/graceful-shutdown/simple", Unpooled.EMPTY_BUFFER), ch.voidPromise())
        then:
        new PollingConditions().eventually {
            response != null
            response.status() == HttpResponseStatus.OK
        }

        when:
        def shFuture = gracefulShutdown.shutdownGracefully().toCompletableFuture()

        then:
        new PollingConditions().eventually {
            shFuture.isDone()
            !shFuture.isCompletedExceptionally()
            !ch.isOpen()
        }

        cleanup:
        response.release()
        loop.shutdownGracefully()
        server.close()
    }

    def "http1 before response single"() {
        given:
        def server = ApplicationContext.<EmbeddedServer> run(EmbeddedServer, ['spec.name': 'GracefulShutdownSpec'])
        def gracefulShutdown = server.applicationContext.getBean(GracefulShutdownManager)

        def loop = new NioEventLoopGroup(1)
        FullHttpResponse response = null
        def ch = new Bootstrap()
                .group(loop)
                .channel(NioSocketChannel)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NonNull Channel c) throws Exception {
                        c.pipeline().addLast(new HttpClientCodec(), new HttpObjectAggregator(1024), new ChannelInboundHandlerAdapter() {
                            @Override
                            void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) throws Exception {
                                response = (FullHttpResponse) msg
                            }
                        })
                    }
                })
                .connect(server.host, server.port).sync().channel()

        def sink = Sinks.<String> one()
        server.applicationContext.getBean(MyCtrl).publisher = sink.asMono()

        // one simple request to wait for connection setup
        when:
        ch.writeAndFlush(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/graceful-shutdown/simple", Unpooled.EMPTY_BUFFER), ch.voidPromise())
        then:
        new PollingConditions().eventually {
            response != null
            response.status() == HttpResponseStatus.OK
        }

        when:
        response.release()
        response = null

        ch.writeAndFlush(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/graceful-shutdown/single", Unpooled.EMPTY_BUFFER), ch.voidPromise())
        TimeUnit.SECONDS.sleep(1)
        def shFuture = gracefulShutdown.shutdownGracefully().toCompletableFuture()
        TimeUnit.SECONDS.sleep(1)
        sink.tryEmitValue("foo")

        then:
        new PollingConditions().eventually {
            response != null
            response.status() == HttpResponseStatus.OK
            response.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true)

            shFuture.isDone()
            !shFuture.isCompletedExceptionally()
            !ch.isOpen()
        }

        cleanup:
        response.release()
        loop.shutdownGracefully()
        server.close()
    }

    def "http1 before response"(String endpoint) {
        given:
        def server = ApplicationContext.<EmbeddedServer> run(EmbeddedServer, ['spec.name': 'GracefulShutdownSpec'])
        def gracefulShutdown = server.applicationContext.getBean(GracefulShutdownManager)

        def loop = new NioEventLoopGroup(1)
        FullHttpResponse response = null
        def ch = new Bootstrap()
                .group(loop)
                .channel(NioSocketChannel)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NonNull Channel c) throws Exception {
                        c.pipeline().addLast(new HttpClientCodec(), new HttpObjectAggregator(1024), new ChannelInboundHandlerAdapter() {
                            @Override
                            void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) throws Exception {
                                response = (FullHttpResponse) msg
                            }
                        })
                    }
                })
                .connect(server.host, server.port).sync().channel()

        def sink = Sinks.<String> one()
        server.applicationContext.getBean(MyCtrl).publisher = sink.asMono()

        // one simple request to wait for connection setup
        when:
        ch.writeAndFlush(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/graceful-shutdown/simple", Unpooled.EMPTY_BUFFER), ch.voidPromise())
        then:
        new PollingConditions().eventually {
            response != null
            response.status() == HttpResponseStatus.OK
        }

        when:
        response.release()
        response = null

        ch.writeAndFlush(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, endpoint, Unpooled.EMPTY_BUFFER), ch.voidPromise())
        TimeUnit.SECONDS.sleep(1)
        def shFuture = gracefulShutdown.shutdownGracefully().toCompletableFuture()
        TimeUnit.SECONDS.sleep(1)
        sink.tryEmitValue("foo")

        then:
        new PollingConditions().eventually {
            response != null
            response.status() == HttpResponseStatus.OK
            response.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true)

            shFuture.isDone()
            !shFuture.isCompletedExceptionally()
            !ch.isOpen()
        }

        cleanup:
        response.release()
        loop.shutdownGracefully()
        server.close()

        where:
        endpoint << ["/graceful-shutdown/multi", "/graceful-shutdown/single"]
    }

    def "http1 during response"() {
        given:
        def server = ApplicationContext.<EmbeddedServer> run(EmbeddedServer, ['spec.name': 'GracefulShutdownSpec'])
        def gracefulShutdown = server.applicationContext.getBean(GracefulShutdownManager)

        def loop = new NioEventLoopGroup(1)
        FullHttpResponse response = null
        def ch = new Bootstrap()
                .group(loop)
                .channel(NioSocketChannel)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NonNull Channel c) throws Exception {
                        c.pipeline().addLast(new HttpClientCodec(), new HttpObjectAggregator(1024), new ChannelInboundHandlerAdapter() {
                            @Override
                            void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) throws Exception {
                                response = (FullHttpResponse) msg
                            }
                        })
                    }
                })
                .connect(server.host, server.port).sync().channel()

        def sink = Sinks.many().unicast().<String> onBackpressureBuffer()
        server.applicationContext.getBean(MyCtrl).publisher = sink.asFlux()

        // one simple request to wait for connection setup
        when:
        ch.writeAndFlush(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/graceful-shutdown/simple", Unpooled.EMPTY_BUFFER), ch.voidPromise())
        then:
        new PollingConditions().eventually {
            response != null
            response.status() == HttpResponseStatus.OK
        }

        when:
        response.release()
        response = null

        ch.writeAndFlush(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/graceful-shutdown/multi", Unpooled.EMPTY_BUFFER), ch.voidPromise())
        sink.tryEmitNext("foo")
        TimeUnit.SECONDS.sleep(1)
        def shFuture = gracefulShutdown.shutdownGracefully().toCompletableFuture()
        TimeUnit.SECONDS.sleep(1)

        then:
        gracefulShutdown.reportActiveTasks().getAsLong() == 1

        when:
        sink.tryEmitNext("bar")
        sink.tryEmitComplete()

        then:
        new PollingConditions().eventually {
            response != null
            response.status() == HttpResponseStatus.OK
            !response.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true)
            response.content().toString(StandardCharsets.UTF_8) == "[foo,bar]"

            shFuture.isDone()
            !shFuture.isCompletedExceptionally()
            !ch.isOpen()
        }

        cleanup:
        response.release()
        loop.shutdownGracefully()
        server.close()
    }

    def "http2"() {
        given:
        def server = ApplicationContext.<EmbeddedServer> run(EmbeddedServer, [
                'spec.name'                             : 'GracefulShutdownSpec',
                'micronaut.server.ssl.enabled'          : true,
                'micronaut.server.ssl.build-self-signed': true,
                'micronaut.server.http-version'         : '2.0'
        ])
        def gracefulShutdown = server.applicationContext.getBean(GracefulShutdownManager)

        def loop = new NioEventLoopGroup(1)
        BlockingQueue<Object> inbound = new LinkedBlockingQueue<>()
        def duplexHandler = new Http2ChannelDuplexHandler() {
            @Override
            void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) throws Exception {
                inbound.add(msg)
            }
        }
        def ch = new Bootstrap()
                .group(loop)
                .channel(NioSocketChannel)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NonNull Channel c) throws Exception {
                        c.pipeline().addLast(SslContextBuilder.forClient()
                                .applicationProtocolConfig(new ApplicationProtocolConfig(
                                        ApplicationProtocolConfig.Protocol.ALPN,
                                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                                        "h2"
                                ))
                                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                .build().newHandler(c.alloc()), Http2FrameCodecBuilder.forClient().build(), duplexHandler)
                    }
                })
                .connect(server.host, server.port).sync().channel()

        def sink = Sinks.<String> one()
        server.applicationContext.getBean(MyCtrl).publisher = sink.asMono()

        expect:
        inbound.take() instanceof Http2SettingsFrame
        inbound.take() instanceof Http2SettingsAckFrame

        when:
        ch.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()
                .path("/graceful-shutdown/simple")
                .method("GET")
                .authority("localhost")
                .scheme("https"), true
        ).stream(duplexHandler.newStream()), ch.newPromise().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE))
        def stream2 = duplexHandler.newStream()
        ch.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()
                .path("/graceful-shutdown/single")
                .method("GET")
                .authority("localhost")
                .scheme("https"), true
        ).stream(stream2), ch.newPromise().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE))
        TimeUnit.SECONDS.sleep(1)
        def shFuture = gracefulShutdown.shutdownGracefully().toCompletableFuture()

        then:
        Http2HeadersFrame resp1 = inbound.take()
        !resp1.isEndStream()
        resp1.headers().status().toString() == "200"
        Http2DataFrame data1 = inbound.take()
        data1.isEndStream()
        Http2GoAwayFrame goAway = inbound.take()
        goAway.errorCode() == Http2Error.NO_ERROR.code()
        goAway.lastStreamId() == stream2.id()
        !shFuture.isDone()

        new PollingConditions().eventually {
            gracefulShutdown.reportActiveTasks().getAsLong() == 1
        }

        when:
        sink.tryEmitValue("foo")
        then:
        Http2HeadersFrame resp2 = inbound.take()
        !resp2.isEndStream()
        resp2.headers().status().toString() == "200"
        Http2DataFrame data2 = inbound.take()
        data2.isEndStream()

        cleanup:
        data1.release()
        data2.release()
        goAway.release()
        loop.shutdownGracefully()
        server.close()
    }

    def "http3"() {
        given:
        def server = ApplicationContext.<EmbeddedServer> run(EmbeddedServer, [
                'spec.name'                                 : 'GracefulShutdownSpec',
                'micronaut.server.ssl.enabled'              : true,
                'micronaut.server.ssl.build-self-signed'    : true,
                'micronaut.server.netty.listeners.h3.family': 'QUIC',
        ])
        def gracefulShutdown = server.applicationContext.getBean(GracefulShutdownManager)

        def loop = new NioEventLoopGroup(1)
        def ch = new Bootstrap()
                .group(loop)
                .channel(NioDatagramChannel)
                .handler(Http3.newQuicClientCodecBuilder()
                        .initialMaxData(10000)
                        .initialMaxStreamDataBidirectionalLocal(10000)
                        .sslContext(QuicSslContextBuilder.forClient()
                                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                .applicationProtocols(Http3.supportedApplicationProtocols()).build())
                        .build())
                .bind(0).sync().channel()


        def connectionHandler = new Http3ClientConnectionHandler()
        def qc = QuicChannel.newBootstrap(ch)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NonNull Channel c) throws Exception {
                        c.pipeline().addLast(new LoggingHandler(LogLevel.INFO), connectionHandler)
                    }
                })
                .remoteAddress(new InetSocketAddress(server.host, server.port))
                .connect().get()

        Http3HeadersFrame headers1 = null
        Http3DataFrame data1 = null
        def stream1 = Http3.newRequestStream(qc, new Http3RequestStreamInboundHandler() {
            @Override
            protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
                headers1 = frame
            }

            @Override
            protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) throws Exception {
                data1 = frame
            }

            @Override
            protected void channelInputClosed(ChannelHandlerContext ctx) throws Exception {
            }
        }).get()
        Http3HeadersFrame headers2 = null
        Http3DataFrame data2 = null
        def stream2 = Http3.newRequestStream(qc, new Http3RequestStreamInboundHandler() {
            @Override
            protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
                headers2 = frame
            }

            @Override
            protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) throws Exception {
                data2 = frame
            }

            @Override
            protected void channelInputClosed(ChannelHandlerContext ctx) throws Exception {
            }
        }).get()

        def sink = Sinks.<String> one()
        server.applicationContext.getBean(MyCtrl).publisher = sink.asMono()

        when:
        stream1.writeAndFlush(new DefaultHttp3HeadersFrame(new DefaultHttp3Headers()
                .path("/graceful-shutdown/simple")
                .method("GET")
                .authority("localhost")
                .scheme("https")
        )).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync()
        stream2.writeAndFlush(new DefaultHttp3HeadersFrame(new DefaultHttp3Headers()
                .path("/graceful-shutdown/single")
                .method("GET")
                .authority("localhost")
                .scheme("https")
        )).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync()
        TimeUnit.SECONDS.sleep(1)
        def shFuture = gracefulShutdown.shutdownGracefully().toCompletableFuture()

        then:
        new PollingConditions().eventually {
            headers1 != null
            data1 != null
            headers1.headers().status().toString() == "200"
            connectionHandler.goAwayReceived
            !shFuture.isDone()

            gracefulShutdown.reportActiveTasks().getAsLong() == 1
        }

        when:
        sink.tryEmitValue("foo")
        then:
        new PollingConditions().eventually {
            headers2 != null
            data2 != null
            headers2.headers().status().toString() == "200"
        }
        when:
        qc.close()
        then:
        new PollingConditions().eventually {
            shFuture.isDone()
        }

        cleanup:
        data1.release()
        data2.release()
        loop.shutdownGracefully()
        server.close()
    }

    @Controller("/graceful-shutdown")
    @Requires(property = "spec.name", value = "GracefulShutdownSpec")
    static class MyCtrl {
        Publisher<String> publisher

        @Get("/simple")
        String simple() {
            return "foo"
        }

        @Get("/single")
        @SingleResult
        Publisher<String> single() {
            return publisher
        }

        @Get("/multi")
        Publisher<String> multi() {
            return publisher
        }
    }
}
