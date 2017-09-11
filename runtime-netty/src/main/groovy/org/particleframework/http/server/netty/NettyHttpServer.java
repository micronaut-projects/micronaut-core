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

import com.fasterxml.jackson.core.JsonParseException;
import com.typesafe.netty.http.HttpStreamsServerHandler;
import com.typesafe.netty.http.StreamedHttpRequest;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import org.particleframework.bind.ArgumentBinder;
import org.particleframework.context.env.Environment;
import org.particleframework.core.order.OrderUtil;
import org.particleframework.core.reflect.GenericTypeUtils;
import org.particleframework.core.reflect.ReflectionUtils;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.HttpStatus;
import org.particleframework.http.MediaType;
import org.particleframework.http.binding.RequestBinderRegistry;
import org.particleframework.http.binding.binders.request.BodyArgumentBinder;
import org.particleframework.http.binding.binders.request.NonBlockingBodyArgumentBinder;
import org.particleframework.http.exceptions.ContentLengthExceededException;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.inject.Argument;
import org.particleframework.runtime.server.EmbeddedServer;
import org.particleframework.web.router.RouteMatch;
import org.particleframework.web.router.Router;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class NettyHttpServer implements EmbeddedServer {
    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);
    public static final String HTTP_STREAMS_CODEC = "http-streams-codec";
    public static final String HTTP_CODEC = "http-codec";
    private final Provider<Router> routerProvider;
    private final Provider<RequestBinderRegistry> requestBinderRegistryProvider;
    private volatile Channel serverChannel;
    private final HttpServerConfiguration serverConfiguration;
    private final ChannelOutboundHandlerAdapter[] outboundHandlerAdapters;
    private final Environment environment;

    @Inject
    public NettyHttpServer(
            HttpServerConfiguration serverConfiguration,
            Environment environment,
            Provider<Router> routerProvider,
            Provider<RequestBinderRegistry> requestBinderRegistryProvider,
            ChannelOutboundHandlerAdapter[] outboundHandlerAdapters
    ) {
        this.environment = environment;
        this.serverConfiguration = serverConfiguration;
        this.routerProvider = routerProvider;
        this.requestBinderRegistryProvider = requestBinderRegistryProvider;
        this.outboundHandlerAdapters = outboundHandlerAdapters;
    }

    @Override
    public EmbeddedServer start() {
        // TODO: allow configuration of threads and number of groups
        NioEventLoopGroup workerGroup = createWorkerEventLoopGroup();
        NioEventLoopGroup parentGroup = createParentEventLoopGroup();
        ServerBootstrap serverBootstrap = createServerBootstrap();


//      TODO: Restore once ConfigurationPropertiesInheritanceSpec passing for Java
//        processOptions(serverConfiguration.getOptions(), serverBootstrap::option);
//        processOptions(serverConfiguration.getChildOptions(), serverBootstrap::childOption);


        ChannelFuture future = serverBootstrap.group(parentGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        Optional<Router> routerBean = resolveRouter();
                        RequestBinderRegistry binderRegistry = requestBinderRegistryProvider.get();

                        pipeline.addLast(HTTP_CODEC, new HttpServerCodec());
                        List<ChannelOutboundHandlerAdapter> channelOutboundHandlerAdapters = Arrays.asList(outboundHandlerAdapters);
                        OrderUtil.reverseSort(channelOutboundHandlerAdapters);
                        for (ChannelOutboundHandlerAdapter outboundHandlerAdapter : outboundHandlerAdapters) {
                            pipeline.addLast(outboundHandlerAdapter);
                        }

                        pipeline.addLast(HTTP_STREAMS_CODEC, new HttpStreamsServerHandler());
                        pipeline.addLast("particle-handler", new SimpleChannelInboundHandler<HttpRequest>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, HttpRequest msg) throws Exception {
                                Channel channel = ctx.channel();

                                NettyHttpRequest nettyHttpRequest = new NettyHttpRequest(msg, ctx, environment, serverConfiguration);

                                // set the request on the channel
                                channel.attr(NettyHttpRequest.KEY)
                                        .set(nettyHttpRequest);

                                if (LOG.isDebugEnabled()) {
                                    LOG.debug("Matching route {} - {}", nettyHttpRequest.getMethod(), nettyHttpRequest.getPath());
                                }
                                Optional<RouteMatch<Object>> routeMatch = routerBean.flatMap((router) ->
                                        router.find(nettyHttpRequest.getMethod(), nettyHttpRequest.getPath())
                                                .filter((match) -> match.test(nettyHttpRequest))
                                                .findFirst()
                                );

                                routeMatch.ifPresent((route -> {

                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("Matched route {} - {} to controller {}", nettyHttpRequest.getMethod(), nettyHttpRequest.getPath(), route.getDeclaringType().getName());
                                    }
                                    // TODO: check the media type vs the route and return 415 if invalid
                                    nettyHttpRequest.setMatchedRoute(route);

                                    boolean requiresBody = false;
                                    Collection<Argument> requiredArguments = route.getRequiredArguments();
                                    Map<String, Object> argumentValues;

                                    if (requiredArguments.isEmpty()) {
                                        // no required arguments so just execute
                                        argumentValues = Collections.emptyMap();
                                    } else {
                                        argumentValues = new LinkedHashMap<>();
                                        // Begin try fulfilling the argument requirements
                                        for (Argument argument : requiredArguments) {
                                            Optional<ArgumentBinder> registeredBinder =
                                                    binderRegistry.findArgumentBinder(argument, nettyHttpRequest);
                                            if (registeredBinder.isPresent()) {
                                                ArgumentBinder argumentBinder = registeredBinder.get();
                                                if (argumentBinder instanceof BodyArgumentBinder) {
                                                    if (argumentBinder instanceof NonBlockingBodyArgumentBinder) {
                                                        Optional bindingResult = argumentBinder
                                                                .bind(argument, nettyHttpRequest);

                                                        if (bindingResult.isPresent()) {
                                                            argumentValues.put(argument.getName(), bindingResult.get());
                                                        }

                                                    } else {
                                                        argumentValues.put(argument.getName(), (Supplier<Optional>) () ->
                                                                argumentBinder.bind(argument, nettyHttpRequest)
                                                        );
                                                        requiresBody = true;
                                                    }
                                                } else {

                                                    Optional bindingResult = argumentBinder
                                                            .bind(argument, nettyHttpRequest);
                                                    if (argument.getType() == Optional.class) {
                                                        argumentValues.put(argument.getName(), bindingResult);
                                                    } else if (bindingResult.isPresent()) {
                                                        argumentValues.put(argument.getName(), bindingResult.get());
                                                    }
                                                }
                                            }
                                        }

                                    }

                                    route = route.fulfill(argumentValues);

                                    if(!route.isExecutable()) {
                                            // if we arrived here the request is not processable
                                            if (LOG.isErrorEnabled()) {
                                                LOG.error("Non-bindable arguments for route: " + route);
                                            }
                                            ctx.writeAndFlush(HttpResponse.badRequest())
                                                    .addListener(ChannelFutureListener.CLOSE);
                                    }
                                    else {

                                        // decorate the execution of the route so that it runs an async executor
                                        // TODO: Allow customization of thread pool to execute actions
                                        Executor executor = ctx.channel().eventLoop();
                                        route = route.decorate(finalRoute -> {
                                            executor.execute(() -> {

                                                Object result;
                                                try {
                                                    result = finalRoute.execute();
                                                    ChannelFuture channelFuture;
                                                    if (result != null) {
                                                        channelFuture = ctx.writeAndFlush(result);
                                                    } else {
                                                        HttpResponse res = ctx.channel().attr(NettyHttpResponse.KEY).get();
                                                        if (res == null) {
                                                            res = HttpResponse.ok();
                                                        }
                                                        channelFuture = ctx.writeAndFlush(res);
                                                    }

                                                    channelFuture.addListener(future -> {
                                                        if (!future.isSuccess()) {
                                                            Throwable cause = future.cause();
                                                            if (LOG.isErrorEnabled()) {
                                                                LOG.error("Error encoding response: " + cause.getMessage(), cause);
                                                            }
                                                            if (ctx.channel().isWritable()) {
                                                                ctx.pipeline().fireExceptionCaught(cause);
                                                            }
                                                        }
                                                    });
                                                } catch (Throwable e) {
                                                    ctx.pipeline().fireExceptionCaught(e);
                                                }
                                            });
                                            return null;
                                        });
                                        nettyHttpRequest.setMatchedRoute(route);

                                        if (!requiresBody) {
                                            route.execute();
                                        } else if (msg instanceof StreamedHttpRequest) {
                                            MediaType contentType = nettyHttpRequest.getContentType();
                                            StreamedHttpRequest streamedHttpRequest = (StreamedHttpRequest) msg;

                                            if (contentType != null && MediaType.APPLICATION_JSON_TYPE.getExtension().equals(contentType.getExtension())) {
                                                JsonContentSubscriber contentSubscriber = new JsonContentSubscriber(nettyHttpRequest);
                                                streamedHttpRequest.subscribe(contentSubscriber);
                                            } else {
                                                Subscriber<HttpContent> contentSubscriber = new HttpContentSubscriber(nettyHttpRequest);
                                                streamedHttpRequest.subscribe(contentSubscriber);
                                            }

                                        } else {
                                            if (LOG.isDebugEnabled()) {
                                                LOG.debug("Request body expected, but was empty.");
                                            }
                                            ctx.writeAndFlush(HttpResponse.badRequest())
                                                    .addListener(ChannelFutureListener.CLOSE);

                                        }

                                    }
                                }));

                                if (!routeMatch.isPresent()) {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("No matching route found for URI {} and method {}", nettyHttpRequest.getUri(), nettyHttpRequest.getMethod());
                                    }
                                    Set<HttpMethod> existingRoutes = routerBean
                                            .map(router ->
                                                    router.findAny(nettyHttpRequest.getUri().toString())
                                            ).orElse(Stream.empty())
                                            .map(RouteMatch::getHttpMethod)
                                            .collect(Collectors.toSet());
                                    if (!existingRoutes.isEmpty()) {
                                        ctx.writeAndFlush(HttpResponse.notAllowed(
                                                existingRoutes
                                        )).addListener(ChannelFutureListener.CLOSE);
                                    } else {
                                        // TODO: Here we need to add routing for 404 handlers
                                        ctx.writeAndFlush(HttpResponse.notFound())
                                           .addListener(ChannelFutureListener.CLOSE);
                                    }
                                }
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

                                if (LOG.isErrorEnabled()) {
                                    LOG.error("Unexpected error occurred: " + cause.getMessage(), cause);
                                }
                                // TODO: Remove this dependency on Jackson and add custom exception handling
                                if (cause instanceof JsonParseException) {
                                    ctx.writeAndFlush(HttpResponse.badRequest())
                                            .addListener(ChannelFutureListener.CLOSE);
                                }
                                else if(cause instanceof ContentLengthExceededException) {
                                    ctx.writeAndFlush(HttpResponse.status(HttpStatus.REQUEST_ENTITY_TOO_LARGE))
                                            .addListener(ChannelFutureListener.CLOSE);
                                }
                                else {
                                    // TODO: Here we need to add routing to error handlers
                                    DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(
                                            HttpVersion.HTTP_1_1,
                                            HttpResponseStatus.INTERNAL_SERVER_ERROR);
                                    ctx.channel()
                                            .writeAndFlush(httpResponse)
                                            .addListener(ChannelFutureListener.CLOSE);

                                }


                            }
                        });

                    }
                })
                // TODO: handle random port binding
                .bind(serverConfiguration.getHost(), serverConfiguration.getPort());

        future.addListener(op -> {
            if (future.isSuccess()) {
                serverChannel = future.channel();
            } else {
                Throwable cause = op.cause();
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error starting Particle server: " + cause.getMessage(), cause);
                }
            }
        });
        return this;
    }

    @Override
    public EmbeddedServer stop() {
        if (this.serverChannel != null) {
            try {
                serverChannel.close().sync();
            } catch (Throwable e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error stopping Particle server: " + e.getMessage(), e);
                }
            }
        }
        return this;
    }

    @Override
    public int getPort() {
        return serverConfiguration.getPort();
    }

    protected NioEventLoopGroup createParentEventLoopGroup() {
        return new NioEventLoopGroup();
    }

    protected NioEventLoopGroup createWorkerEventLoopGroup() {
        return new NioEventLoopGroup();
    }

    protected ServerBootstrap createServerBootstrap() {
        return new ServerBootstrap();
    }

    private Optional<Router> resolveRouter() {
        try {
            Router router = routerProvider.get();
            return Optional.of(router);
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("No router found for Particle server: " + e.getMessage(), e);
            }
            return Optional.empty();
        }
    }

    private void processOptions(Map<ChannelOption, Object> options, BiConsumer<ChannelOption, Object> biConsumer) {
        for (ChannelOption channelOption : options.keySet()) {
            String name = channelOption.name();
            Object value = options.get(channelOption);
            Optional<Field> declaredField = ReflectionUtils.findDeclaredField(ChannelOption.class, name);
            declaredField.ifPresent((field) -> {
                Optional<Class> typeArg = GenericTypeUtils.resolveGenericTypeArgument(field);
                typeArg.ifPresent((arg) -> {
                    Optional converted = environment.convert(value, arg);
                    converted.ifPresent((convertedValue) ->
                            biConsumer.accept(channelOption, convertedValue)
                    );
                });

            });
            if (!declaredField.isPresent()) {
                biConsumer.accept(channelOption, value);
            }
        }
    }

}
