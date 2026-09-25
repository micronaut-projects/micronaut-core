/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.exceptions.StreamResetException;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3ErrorCode;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.handler.codec.http3.Http3ServerConnectionHandler;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * An HTTP/3 server that resets the stream of every request: a rejected request was not
 * processed (RFC 9114 section 4.1.1), so it is an {@link UnprocessedRequestException}; any other
 * reset is a {@link StreamResetException} with the HTTP/3 error code.
 */
class Http3StreamResetTest {

    @Test
    void rejectedRequestIsUnprocessed() throws Exception {
        try (ResettingServer server = new ResettingServer(Http3ErrorCode.H3_REQUEST_REJECTED.code());
             ApplicationContext ctx = start();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            UnprocessedRequestException failure = Assertions.assertThrows(UnprocessedRequestException.class, () -> exchange(client, server));
            Assertions.assertEquals(UnprocessedRequestException.Reason.STREAM_REFUSED, failure.getReason());
            Assertions.assertEquals(server.uri(), failure.getUri().orElseThrow());
        }
    }

    @Test
    void otherResetIsAStreamReset() throws Exception {
        try (ResettingServer server = new ResettingServer(Http3ErrorCode.H3_REQUEST_CANCELLED.code());
             ApplicationContext ctx = start();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            StreamResetException failure = Assertions.assertThrows(StreamResetException.class, () -> exchange(client, server));
            Assertions.assertEquals(Http3ErrorCode.H3_REQUEST_CANCELLED.code(), failure.getErrorCode());
            Assertions.assertEquals(StreamResetException.Protocol.HTTP_3, failure.getProtocol());
            Assertions.assertFalse(UnprocessedRequestException.isUnprocessed(failure));
        }
    }

    private static ApplicationContext start() {
        return ApplicationContext.run(Map.of(
            "micronaut.http.client.alpn-modes", "h3",
            "micronaut.http.client.ssl.insecure-trust-all-certificates", true
        ));
    }

    private static void exchange(RawHttpClient client, ResettingServer server) {
        HttpResponse<?> response = Mono.from(client.exchange(HttpRequest.GET(server.uri()), null, null)).block(Duration.ofSeconds(10));
        if (response instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
            byteBodyResponse.close();
        }
    }

    /**
     * An HTTP/3 server that answers every request by resetting its stream, as a server that
     * rejects it does: {@code STOP_SENDING} and {@code RESET_STREAM} with the error code.
     */
    private static final class ResettingServer implements AutoCloseable {
        private final EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        private final Channel channel;

        ResettingServer(int errorCode) throws Exception {
            SelfSignedCertificate certificate = new SelfSignedCertificate();
            QuicSslContext sslContext = QuicSslContextBuilder.forServer(certificate.key(), null, certificate.cert())
                .applicationProtocols(Http3.supportedApplicationProtocols())
                .build();
            ChannelHandler codec = Http3.newQuicServerCodecBuilder()
                .sslContext(sslContext)
                .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                .initialMaxData(1_000_000)
                .initialMaxStreamDataBidirectionalLocal(100_000)
                .initialMaxStreamDataBidirectionalRemote(100_000)
                .initialMaxStreamsBidirectional(100)
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                .handler(new ChannelInitializer<QuicChannel>() {
                    @Override
                    protected void initChannel(QuicChannel connection) {
                        connection.pipeline().addLast(new Http3ServerConnectionHandler(new ChannelInitializer<QuicStreamChannel>() {
                            @Override
                            protected void initChannel(QuicStreamChannel stream) {
                                stream.pipeline().addLast(new Http3RequestStreamInboundHandler() {
                                    @Override
                                    protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) {
                                        ReferenceCountUtil.release(frame);
                                        QuicStreamChannel requestStream = (QuicStreamChannel) ctx.channel();
                                        requestStream.shutdownInput(errorCode);
                                        requestStream.shutdownOutput(errorCode);
                                    }

                                    @Override
                                    protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) {
                                        ReferenceCountUtil.release(frame);
                                    }

                                    @Override
                                    protected void channelInputClosed(ChannelHandlerContext ctx) {
                                    }
                                });
                            }
                        }));
                    }
                })
                .build();
            channel = new Bootstrap()
                .group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec)
                .bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .sync()
                .channel();
        }

        URI uri() {
            return URI.create("https://localhost:" + ((InetSocketAddress) channel.localAddress()).getPort() + "/reset");
        }

        @Override
        public void close() {
            channel.close();
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }
}
