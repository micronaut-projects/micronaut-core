package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.http.multipart.FileUpload
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder
import io.netty.handler.codec.http.multipart.MemoryFileUpload
import io.netty.handler.codec.http2.DefaultHttp2Connection
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener
import io.netty.handler.codec.http2.Http2SecurityUtil
import io.netty.handler.codec.http2.Http2Settings
import io.netty.handler.codec.http2.HttpConversionUtil
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder
import io.netty.handler.ssl.ApplicationProtocolConfig
import io.netty.handler.ssl.ApplicationProtocolNames
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SupportedCipherSuiteFilter
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CopyOnWriteArrayList

class MaxRequestSizeSpec extends Specification {

    static final String SPEC_NAME = 'MaxRequestSizeSpec'

    void "test max request size default processor"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.maxRequestSize': '10KB',
                'spec.name': SPEC_NAME
        ])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        byte[] kb10 = new byte[10240]
        String result = client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/text", new String(kb10)).contentType(MediaType.TEXT_PLAIN_TYPE))

        then:
        result == "OK"

        when:
        byte[] kb101 = new byte[10241]
        client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/text", new String(kb101)).contentType(MediaType.TEXT_PLAIN_TYPE))

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get()._embedded.errors[0].message == "The content length [10241] exceeds the maximum allowed content length [10240]"

        cleanup:
        client.close()
        embeddedServer.close()
    }

    void "test max request size json processor"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.maxRequestSize': '10KB',
                'spec.name': SPEC_NAME
        ])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        String json = '{"x":"' + ('y' * (10240 - 8)) + '"}'
        String result = client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/json", json).contentType(MediaType.APPLICATION_JSON_TYPE))

        then:
        result == "OK"

        when:
        json = '{"x":"' + ('y' * (10240 - 7)) + '"}'
        client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/json", new String(json)).contentType(MediaType.APPLICATION_JSON_TYPE))

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get()._embedded.errors[0].message == "The content length [10241] exceeds the maximum allowed content length [10240]"

        cleanup:
        client.close()
        embeddedServer.close()
    }

    @Ignore("Whether or not the exception is thrown is inconsistent. I don't think there is anything we can do to ensure its consistency")
    void "test max request size multipart processor"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.maxRequestSize': '10KB',
                'spec.name': SPEC_NAME
        ])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        MultipartBody body = MultipartBody.builder()
                .addPart("a", "a.pdf", new byte[1872])
                .addPart("b", "b.pdf", new byte[1872])
                .addPart("c", "c.pdf", new byte[1872])
                .addPart("d", "d.pdf", new byte[1871])
                .addPart("e", "e.pdf", new byte[1871])
                .build()

        String result = client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/multipart", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))

        then:
        result == "OK"

        when:
        body = MultipartBody.builder()
                .addPart("a", "a.pdf", new byte[1872])
                .addPart("b", "b.pdf", new byte[1872])
                .addPart("c", "c.pdf", new byte[1872])
                .addPart("d", "d.pdf", new byte[1871])
                .addPart("e", "e.pdf", new byte[1872]) //One extra byte
                .build()
        client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/multipart", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get()._embedded.errors[0].message == "The content length [10241] exceeds the maximum allowed content length [10240]"

        cleanup:
        client.close()
        embeddedServer.close()
    }

    void "test max part size multipart processor"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.multipart.maxFileSize': '1KB',
                'spec.name': SPEC_NAME
        ])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        MultipartBody body = MultipartBody.builder()
                .addPart("a", "a.pdf", new byte[1024])
                .addPart("b", "b.pdf", new byte[1024])
                .addPart("c", "c.pdf", new byte[1024])
                .addPart("d", "d.pdf", new byte[1024])
                .addPart("e", "e.pdf", new byte[1024])
                .build()

        String result = client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/multipart", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))

        then:
        result == "OK"

        when:
        body = MultipartBody.builder()
                .addPart("a", "a.pdf", new byte[1024])
                .addPart("b", "b.pdf", new byte[1024])
                .addPart("c", "c.pdf", new byte[1024])
                .addPart("d", "d.pdf", new byte[1024])
                .addPart("e", "e.pdf", new byte[1025]) //One extra byte
                .build()
        client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/multipart", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get()._embedded.errors[0].message == "The part named [e] exceeds the maximum allowed content length [1024]"

        cleanup:
        client.close()
        embeddedServer.close()
    }

    void "test max part size multipart body binder"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.multipart.maxFileSize': '1KB',
                'spec.name': SPEC_NAME
        ])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        MultipartBody body = MultipartBody.builder()
                .addPart("a", "a.pdf", new byte[1024])
                .addPart("b", "b.pdf", new byte[1024])
                .addPart("c", "c.pdf", new byte[1024])
                .addPart("d", "d.pdf", new byte[1024])
                .addPart("e", "e.pdf", new byte[1024])
                .build()

        String result = client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/multipart-body", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))

        then:
        result == "OK"

        when:
        body = MultipartBody.builder()
                .addPart("a", "a.pdf", new byte[1024])
                .addPart("b", "b.pdf", new byte[1024])
                .addPart("c", "c.pdf", new byte[1024])
                .addPart("d", "d.pdf", new byte[1024])
                .addPart("e", "e.pdf", new byte[1025]) //One extra byte
                .build()
        client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/multipart-body", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get()._embedded.errors[0].message == "The part named [e] exceeds the maximum allowed content length [1024]"

        cleanup:
        client.close()
        embeddedServer.close()
    }

    void "test content length exceeded with different disk storage"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.maxRequestSize': '10KB',
                'micronaut.server.multipart.disk': true,
                'spec.name': SPEC_NAME
        ])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        MultipartBody body = MultipartBody.builder()
                .addPart("a", "a.pdf", new byte[20240])
                .build()

        String result = Flux.from(client.retrieve(HttpRequest.POST("/test-max-size/multipart-body", body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE))).blockFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get()._embedded.errors[0].message.contains("exceeds the maximum allowed content length [10240]")

        cleanup:
        client.close()
        embeddedServer.close()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/4864')
    void 'large request should keep the keep-alive connection valid'() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.maxRequestSize': '10KB',
                'micronaut.server.port': -1,
                'spec.name': SPEC_NAME
        ])

        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
        Bootstrap bootstrap = new Bootstrap()
                .group(new NioEventLoopGroup())
                .channel(NioSocketChannel)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NonNull Channel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new HttpClientCodec())
                                .addLast(new HttpObjectAggregator(1024))
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) throws Exception {
                                        responses.add(msg)
                                    }
                                })
                    }
                })
                .remoteAddress(embeddedServer.host, embeddedServer.port)

        def request1 = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, '/test-max-size/multipart-body')
        request1.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
        def requestEncoder1 = new HttpPostRequestEncoder(request1, true)
        def upload = new MemoryFileUpload('foo', 'foo', 'text/plain', '', null, 100_000)
        upload.setContent(Unpooled.wrappedBuffer(new byte[100_000]))
        requestEncoder1.addBodyHttpData(upload)

        def request2 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, '/test-max-size/text')
        request2.headers().add(HttpHeaderNames.CONTENT_LENGTH, 0)

        when:
        def channel = (SocketChannel) bootstrap.connect().sync().channel()
        channel.writeAndFlush(requestEncoder1.finalizeRequest())
        while (true) {
            def chunk = requestEncoder1.readChunk(channel.alloc())
            if (chunk == null) break
            channel.writeAndFlush(chunk)
        }
        channel.read()

        then:
        new PollingConditions(timeout: 5).eventually {
            responses.size() == 1
        }
        responses[0].status() == HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE

        // there are two valid things for the server to do:
        // - blackhole the remaining request data and keep the connection alive for the next request
        // - kill the connection
        when:
        if (!channel.isOutputShutdown()) {
            channel.writeAndFlush(request2)
            channel.read()
        }
        then:
        if (!channel.isOutputShutdown()) {
            new PollingConditions(timeout: 5).eventually {
                responses.size() == 2
            }
            responses[1].status() == HttpResponseStatus.OK
        }

        cleanup:
        responses.forEach(r -> r.release())
        channel.close()
        embeddedServer.close()
    }

    @Ignore
    void 'large request should not affect other http2 connections'() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.maxRequestSize': '10KB',
                'micronaut.server.http-version': '2.0',
                'micronaut.server.ssl.enabled': true,
                'micronaut.server.netty.log-level': 'TRACE',
                'micronaut.server.ssl.port': -1,
                'micronaut.server.ssl.buildSelfSigned': true,
                'spec.name': SPEC_NAME
        ])

        def request1 = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, '/test-max-size/multipart-body')
        request1.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), 'https')
        def requestEncoder1 = new HttpPostRequestEncoder(request1, true)
        def upload = new MemoryFileUpload('foo', 'foo', 'text/plain', '', null, 100_000)
        upload.setContent(Unpooled.wrappedBuffer(new byte[100_000]))
        requestEncoder1.addBodyHttpData(upload)

        def request2 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, '/test-max-size/text')
        request2.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), 'https')

        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
        def sslContext = SslContextBuilder.forClient()
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2))
                .build()
        def bootstrap = new Bootstrap()
                .remoteAddress(embeddedServer.host, embeddedServer.port)
                .group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.AUTO_READ, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(@NonNull SocketChannel ch) throws Exception {
                        def connection = new DefaultHttp2Connection(false)
                        def connectionHandler = new HttpToHttp2ConnectionHandlerBuilder()
                                .initialSettings(Http2Settings.defaultSettings())
                                .frameListener(new DelegatingDecompressorFrameListener(
                                        connection,
                                        new InboundHttp2ToHttpAdapterBuilder(connection)
                                                .maxContentLength(Integer.MAX_VALUE)
                                                .propagateSettings(false)
                                                .build()
                                ))
                                .connection(connection)
                                .build()
                        ch.pipeline()
                                .addLast(sslContext.newHandler(ch.alloc(), embeddedServer.host, embeddedServer.port))
                                .addLast(new ApplicationProtocolNegotiationHandler('') {
                                    @Override
                                    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
                                        if (ApplicationProtocolNames.HTTP_2 != protocol) {
                                            throw new AssertionError((Object) protocol)
                                        }
                                        ctx.pipeline()
                                                .addLast(connectionHandler)
                                                .addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                                                    @Override
                                                    protected void channelRead0(ChannelHandlerContext ctx_, FullHttpResponse msg) throws Exception {
                                                        responses.add(msg.retain())
                                                    }
                                                })

                                        ctx.channel().writeAndFlush(requestEncoder1.finalizeRequest())
                                        while (true) {
                                            def chunk = requestEncoder1.readChunk(ctx.alloc())
                                            if (chunk == null) break
                                            ctx.channel().writeAndFlush(chunk)
                                        }
                                    }
                                })
                    }
                })

        when:
        def channel = (SocketChannel) bootstrap.connect().sync().channel()
        channel.read()

        then:
        new PollingConditions(timeout: 5).eventually {
            responses.size() == 1
        }
        responses[0].status() == HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE

        when:
        channel.writeAndFlush(request2)
        channel.read()
        then:
        new PollingConditions(timeout: 5).eventually {
            responses.size() == 2
        }
        responses[1].status() == HttpResponseStatus.OK

        cleanup:
        responses.forEach(r -> r.release())
        channel.close()
        embeddedServer.close()
    }

    void "test max buffer"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.maxRequestBufferSize': '10KB',
                'spec.name': SPEC_NAME
        ])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        byte[] kb10 = new byte[10240]
        String result = client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/text", kb10).contentType(MediaType.TEXT_PLAIN_TYPE))

        then:
        result == "OK"

        when:
        byte[] kb101 = new byte[10241]
        result = client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/ignored", kb101).contentType(MediaType.TEXT_PLAIN_TYPE))

        then:
        result == "OK"

        when:
        client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/text", kb101).contentType(MediaType.TEXT_PLAIN_TYPE))

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get()._embedded.errors[0].message.startsWith("The content length [10241] exceeds the maximum allowed bufferable length [10240]")

        when:
        byte[] mb1 = new byte[1024000]
        result = client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/ignored", mb1).contentType(MediaType.TEXT_PLAIN_TYPE))

        then:
        result == "OK"

        when:
        client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/text", mb1).contentType(MediaType.TEXT_PLAIN_TYPE))

        then:
        ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get()._embedded.errors[0].message.matches("The content length \\[\\d+] exceeds the maximum allowed bufferable length \\[10240].*")

        cleanup:
        client.close()
        embeddedServer.close()
    }

    @Controller("/test-max-size")
    @Requires(property = "spec.name", value = "MaxRequestSizeSpec")
    static class TestController {

        @Post(uri = "/text", consumes = MediaType.TEXT_PLAIN)
        String text(@Body String body) {
            "OK"
        }

        // this endpoint ignores the body so it wont trigger the max buffer size
        @Post(uri = "/ignored", consumes = MediaType.TEXT_PLAIN)
        String ignored() {
            "OK"
        }

        @Post(uri = "/json", consumes = MediaType.APPLICATION_JSON)
        String json(@Body String body) {
            "OK"
        }

        @Post(uri = "/multipart", consumes = MediaType.MULTIPART_FORM_DATA)
        String multipart(CompletedFileUpload a,
                         CompletedFileUpload b,
                         CompletedFileUpload c,
                         CompletedFileUpload d,
                         CompletedFileUpload e) {
            a.discard()
            b.discard()
            c.discard()
            d.discard()
            e.discard()
            "OK"
        }

        @Post(uri = "/multipart-body", consumes = MediaType.MULTIPART_FORM_DATA)
        @SingleResult
        Publisher<String> multipart(@Body io.micronaut.http.server.multipart.MultipartBody body) {
            return Flux.from(body).map {
                if (it instanceof FileUpload) it.discard()
                return it
            }.collectList().map({ list -> "OK" })
        }
    }
}
