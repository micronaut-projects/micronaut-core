package io.micronaut.http.client.netty

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.http.client.exceptions.ResponseClosedException
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.PrematureChannelClosureException
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.LastHttpContent
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.SelfSignedCertificate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit

class ConnectionCloseResponseSpec extends Specification {
    def 'HTTP1 EOF preserves the #framing response with streaming=#streaming and tls=#tls'() {
        given:
        def group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
        def certificate = tls ? new SelfSignedCertificate() : null
        def ssl = tls ? SslContextBuilder.forServer(certificate.key(), certificate.cert()).build() : null
        def body = 'the complete response body'
        def wire = framing == 'close-delimited' ? body : "${Integer.toHexString(body.length() + 10)}\r\n${body}"
        def headers = framing == 'close-delimited' ? '' : 'Transfer-Encoding: chunked\r\n'
        def wireResponse = framing == 'incomplete-head' ? 'HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n' :
            "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n${headers}\r\n${wire}"
        def server = new ServerBootstrap().group(group).channel(NioServerSocketChannel)
            .childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    if (ssl != null) {
                        ch.pipeline().addLast(ssl.newHandler(ch.alloc()))
                    }
                    ch.pipeline().addLast(new HttpRequestDecoder(), new SimpleChannelInboundHandler<HttpObject>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
                            if (msg instanceof LastHttpContent) {
                                ctx.writeAndFlush(Unpooled.copiedBuffer(wireResponse, StandardCharsets.UTF_8))
                                    .addListener(ChannelFutureListener.CLOSE)
                            }
                        }
                    })
                }
            }).bind(InetAddress.loopbackAddress, 0).sync().channel()
        def config = new DefaultHttpClientConfiguration()
        config.sslConfiguration.insecureTrustAllCertificates = true
        def client = (StreamingHttpClient) HttpClient.create(new URI("${tls ? 'https' : 'http'}://localhost:${((InetSocketAddress) server.localAddress()).port}").toURL(), config)
        def response = streaming ? Flux.from(client.dataStream(HttpRequest.GET('/')))
            .map { it.toString(StandardCharsets.UTF_8) }.collectList().map { it.join('') } :
            Mono.from(client.retrieve(HttpRequest.GET('/'), String))

        when:
        String received = null
        Throwable failure = null
        try {
            received = response.block(Duration.ofSeconds(10))
        } catch (Throwable e) {
            failure = e
        }

        then:
        if (framing == 'close-delimited') {
            assert failure == null
            assert received == body
        } else {
            assert failure != null
            while (failure.cause != null) {
                failure = failure.cause
            }
            assert framing == 'incomplete-head' ? failure instanceof PrematureChannelClosureException : failure instanceof ResponseClosedException
        }

        cleanup:
        client?.close()
        server?.close()?.sync()
        group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync()
        certificate?.delete()

        where:
        [framing, streaming, tls] << [['close-delimited', 'incomplete-chunk', 'incomplete-head'], [false, true], [false, true]].combinations()
    }
}
