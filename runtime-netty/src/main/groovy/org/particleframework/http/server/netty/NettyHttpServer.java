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
import org.particleframework.context.ApplicationContext;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.server.EmbeddedServer;
import org.particleframework.web.router.RouteMatch;
import org.particleframework.web.router.Router;

import java.util.Optional;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class NettyHttpServer implements EmbeddedServer {
    private volatile Channel serverChannel;
    private final HttpServerConfiguration serverConfiguration;
    private final ApplicationContext applicationContext;

    public NettyHttpServer(HttpServerConfiguration serverConfiguration, ApplicationContext applicationContext) {
        this.serverConfiguration = serverConfiguration;
        this.applicationContext = applicationContext;
    }

    public NettyHttpServer() {
        this.serverConfiguration = new HttpServerConfiguration();
        this.applicationContext = null;
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
                                Optional<Router> routerBean = applicationContext != null ? applicationContext.findBean(Router.class) : Optional.empty();

                                Optional<RouteMatch> routeMatch = routerBean.flatMap((router)->
                                        router.route(HttpMethod.valueOf(msg.method().name()), msg.uri())
                                );

                                routeMatch.ifPresent((rm -> {
                                    // TODO: here we need to analyze the binding requirements and if
                                    // the body is required then add an additional handler to the pipeline
                                    // right now only URL parameters are supported

                                    Object result = rm.invoke();

                                    // TODO: here we need a way to make the encoding of the result flexible
                                    // also support for GSON views etc.

                                    // TODO: Also need to handle exceptions that emerge from invoke()

                                    DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse (HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                                    httpResponse.content().writeCharSequence(
                                            result.toString(),
                                            serverConfiguration.getDefaultCharset());
                                    ctx.channel().writeAndFlush(httpResponse)
                                            .addListener(ChannelFutureListener.CLOSE);

                                }));

                                if(!routeMatch.isPresent()) {
                                    // TODO: Here we need to add routing for 404 handlers
                                    DefaultHttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
                                    ctx.channel().writeAndFlush(httpResponse)
                                            .addListener(ChannelFutureListener.CLOSE);
                                }

                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

                                // TODO: Here we need to add routing to error handlers
                                DefaultHttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                                ctx.channel().writeAndFlush(httpResponse)
                                        .addListener(ChannelFutureListener.CLOSE);

                            }
                        });


                    }
                })
                // TODO: handle random port binding
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
