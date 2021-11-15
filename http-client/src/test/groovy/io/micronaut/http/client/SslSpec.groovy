/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.ServerChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.ssl.SslHandshakeTimeoutException
import org.jetbrains.annotations.NotNull
import reactor.core.publisher.Flux
import spock.lang.Ignore
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.TimeUnit

class SslSpec extends Specification {

    @Ignore
    // service down at the moment
    void "test that clients work with self signed certificates"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        HttpClient client = ctx.createBean(HttpClient, new URL("https://httpbin.org"))

        expect:
        client.toBlocking().retrieve('/get')

        cleanup:
        ctx.close()
        client.close()
    }

    void 'test client ssl handshakeTimeout'() {
        given:
        def group = new NioEventLoopGroup()
        ServerChannel channel = (ServerChannel) new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel)
                .childHandler(new IgnoringChannelInitializer())
                .bind(0).sync().channel()
        ApplicationContext cont = ApplicationContext.run(['micronaut.http.client.ssl.handshakeTimeout': '1s'])
        def address = (InetSocketAddress) channel.localAddress()
        when:
        Flux.from(cont.getBean(HttpClient).exchange("https://localhost:$address.port"))
                .blockFirst(Duration.ofSeconds(5))
        then:
        def e = thrown RuntimeException
        e.cause instanceof SslHandshakeTimeoutException
        cleanup:
        group.shutdownGracefully()
    }

    void 'test server ssl handshakeTimeout'() {
        given:
        def group = new NioEventLoopGroup()
        ApplicationContext context = ApplicationContext.run([
                'micronaut.ssl.port'            : -1,
                'micronaut.ssl.enabled'         : true,
                'micronaut.ssl.buildSelfSigned' : true,
                'micronaut.ssl.handshakeTimeout': '1s',
        ])
        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def channel = new Bootstrap()
                .channel(NioSocketChannel)
                .group(group)
                .handler(new IgnoringChannelInitializer())
                .connect(embeddedServer.host, embeddedServer.port).sync().channel()
        expect:
        // returns false on timeout
        channel.closeFuture().await(5, TimeUnit.SECONDS)
        cleanup:
        group.shutdownGracefully()
    }

    static class IgnoringChannelInitializer extends ChannelInitializer<Channel> {
        @Override
        protected void initChannel(@NotNull Channel ch) throws Exception {
            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                @Override
                void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) throws Exception {
                    // ignore
                }
            })
        }
    }
}
