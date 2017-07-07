/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import org.particleframework.server.EmbeddedServer;
import org.particleframework.http.server.HttpServerConfiguration;

import java.nio.charset.Charset;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class NettyHttpServer implements EmbeddedServer {
    private volatile Channel serverChannel;
    private final HttpServerConfiguration serverConfiguration;

    public NettyHttpServer(HttpServerConfiguration serverConfiguration) {
        this.serverConfiguration = serverConfiguration;
    }

    public NettyHttpServer() {
        this.serverConfiguration = new HttpServerConfiguration();
    }

    @Override
    public EmbeddedServer start() {
        // TODO: allow configuration of threads and number of groups
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();
        NioEventLoopGroup parentGroup = new NioEventLoopGroup();
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        // TODO: allow configuration of channel options
        ChannelFuture future = serverBootstrap.group(parentGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new SimpleChannelInboundHandler<HttpRequest>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, HttpRequest msg) throws Exception {
                                String uri = msg.uri();
                                DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse (HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                                httpResponse.content().writeCharSequence("hello world", Charset.defaultCharset());
                                ctx.channel().writeAndFlush(httpResponse)
                                             .addListener(ChannelFutureListener.CLOSE);;

                            }
                        });
                    }
                })
                .bind(serverConfiguration.getHost(), serverConfiguration.getPort());
        try {
            future.sync();
        } catch (InterruptedException e) {
            // TODO: exception handling
            e.printStackTrace();
        }
        this.serverChannel = future.channel();
        return this;
    }

    @Override
    public EmbeddedServer stop() {
        if(this.serverChannel != null) {
            try {
                serverChannel.close().sync();
            } catch (Throwable e) {
                // TODO: exception handling
                e.printStackTrace();
            }
        }
        return this;
    }

    @Override
    public int getPort() {
        return serverConfiguration.getPort();
    }
}
