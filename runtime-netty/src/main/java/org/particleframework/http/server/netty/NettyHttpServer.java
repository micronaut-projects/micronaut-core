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

import com.typesafe.netty.http.HttpStreamsServerHandler;
import com.typesafe.netty.http.StreamedHttpRequest;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import org.particleframework.core.bind.ArgumentBinder;
import org.particleframework.context.BeanLocator;
import org.particleframework.context.env.Environment;
import org.particleframework.core.io.socket.SocketUtils;
import org.particleframework.core.order.OrderUtil;
import org.particleframework.core.reflect.GenericTypeUtils;
import org.particleframework.core.reflect.ReflectionUtils;
import org.particleframework.core.type.Argument;
import org.particleframework.http.*;
import org.particleframework.http.binding.RequestBinderRegistry;
import org.particleframework.http.binding.binders.request.BodyArgumentBinder;
import org.particleframework.http.binding.binders.request.NonBlockingBodyArgumentBinder;
import org.particleframework.http.cors.CorsHandler;
import org.particleframework.http.exceptions.InternalServerException;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.http.server.exceptions.ExceptionHandler;
import org.particleframework.http.server.netty.configuration.NettyHttpServerConfiguration;
import org.particleframework.http.server.netty.handler.ChannelHandlerFactory;
import org.particleframework.http.util.HttpUtil;
import org.particleframework.inject.qualifiers.Qualifiers;
import org.particleframework.runtime.server.EmbeddedServer;
import org.particleframework.web.router.RouteMatch;
import org.particleframework.web.router.Router;
import org.particleframework.web.router.UriRouteMatch;
import org.particleframework.web.router.exceptions.RoutingException;
import org.particleframework.web.router.qualifier.ConsumesMediaTypeQualifier;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
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
    public static final String HTTP_STREAMS_CODEC = "http-streams-codec";
    public static final String HTTP_CODEC = "http-codec";
    public static final String PARTICLE_HANDLER = "particle-handler";
    public static final String CORS_HANDLER = "cors-handler";
    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);

    private volatile Channel serverChannel;
    private final NettyHttpServerConfiguration serverConfiguration;
    private final ChannelHandlerFactory[] channelHandlerFactories;
    private final Environment environment;
    private final boolean corsEnabled;
    private final CorsHandler corsHandler;
    private final Optional<Router> router;
    private final RequestBinderRegistry binderRegistry;
    private final BeanLocator beanLocator;

    @Inject
    public NettyHttpServer(
            NettyHttpServerConfiguration serverConfiguration,
            Environment environment,
            Optional<Router> router,
            RequestBinderRegistry binderRegistry,
            BeanLocator beanLocator,
            ChannelHandlerFactory[] channelHandlerFactories
    ) {
        Optional<File> location = serverConfiguration.getMultipart().getLocation();
        location.ifPresent(dir ->
                DiskFileUpload.baseDirectory = dir.getAbsolutePath()
        );
        this.beanLocator = beanLocator;
        this.environment = environment;
        this.serverConfiguration = serverConfiguration;
        this.router = router;
        this.channelHandlerFactories = channelHandlerFactories;
        this.binderRegistry = binderRegistry;
        HttpServerConfiguration.CorsConfiguration corsConfiguration = serverConfiguration.getCors();
        this.corsEnabled = corsConfiguration.isEnabled();
        this.corsHandler = this.corsEnabled ? new CorsHandler(corsConfiguration) : null;
    }

    @Override
    public EmbeddedServer start() {
        // TODO: allow configuration of threads and number of groups
        NioEventLoopGroup workerGroup = createWorkerEventLoopGroup();
        NioEventLoopGroup parentGroup = createParentEventLoopGroup();
        ServerBootstrap serverBootstrap = createServerBootstrap();

        processOptions(serverConfiguration.getOptions(), serverBootstrap::option);
        processOptions(serverConfiguration.getChildOptions(), serverBootstrap::childOption);


        int port = serverConfiguration.getPort();
        ChannelFuture future = serverBootstrap.group(parentGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        Optional<Router> routerBean = NettyHttpServer.this.router;
                        RequestBinderRegistry binderRegistry = NettyHttpServer.this.binderRegistry;

                        pipeline.addLast(HTTP_CODEC, new HttpServerCodec());

                        List<ChannelHandler> channelHandlers = new ArrayList<>();
                        for (ChannelHandlerFactory channelHandlerFactory : channelHandlerFactories) {
                            ChannelHandler channelHandler = channelHandlerFactory.build(ch);
                            channelHandlers.add(channelHandler);
                        }
                        OrderUtil.reverseSort(channelHandlers);

                        for (ChannelHandler outboundHandlerAdapter : channelHandlers) {
                            pipeline.addLast(outboundHandlerAdapter);
                        }

                        pipeline.addLast(HTTP_STREAMS_CODEC, new HttpStreamsServerHandler());
                        pipeline.addLast(PARTICLE_HANDLER, new SimpleChannelInboundHandler<HttpRequest>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, HttpRequest msg) throws Exception {
                                Channel channel = ctx.channel();

                                NettyHttpRequest nettyHttpRequest = new NettyHttpRequest(msg, ctx, environment, serverConfiguration);

                                // set the request on the channel
                                channel.attr(NettyHttpRequest.KEY)
                                        .set(nettyHttpRequest);

                                if(corsEnabled && nettyHttpRequest.getHeaders().getOrigin().isPresent()) {
                                    Optional<MutableHttpResponse<?>> corsResponse = corsHandler.handleRequest(nettyHttpRequest);
                                    if(corsResponse.isPresent()) {
                                        ChannelFuture channelFuture = ctx.writeAndFlush(corsResponse.get());
                                        if(!io.netty.handler.codec.http.HttpUtil.isKeepAlive(msg)) {
                                            channelFuture.addListener(ChannelFutureListener.CLOSE);
                                        }
                                        return;
                                    }
                                    else {
                                        pipeline.addBefore(PARTICLE_HANDLER, CORS_HANDLER, new ChannelOutboundHandlerAdapter() {
                                            @Override
                                            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                                                if(msg instanceof MutableHttpResponse) {
                                                    corsHandler.handleResponse(nettyHttpRequest, (MutableHttpResponse<?>) msg);
                                                }
                                                pipeline.remove(this);
                                                super.write(ctx, msg, promise);
                                            }
                                        });
                                    }
                                }

                                if (LOG.isDebugEnabled()) {
                                    LOG.debug("Matching route {} - {}", nettyHttpRequest.getMethod(), nettyHttpRequest.getPath());
                                }

                                // find a matching route
                                Optional<UriRouteMatch<Object>> routeMatch = routerBean.flatMap((router) ->
                                        router.find(nettyHttpRequest.getMethod(), nettyHttpRequest.getPath())
                                                .filter((match) -> match.test(nettyHttpRequest))
                                                .findFirst()
                                );


                                routeMatch.ifPresent((route -> {
                                    // Check that the route is an accepted content type
                                    if (!route.accept(nettyHttpRequest.getContentType())) {
                                        if (LOG.isDebugEnabled()) {
                                            LOG.debug("Matched route is not a supported media type: {}", nettyHttpRequest.getContentType());
                                        }

                                        // if the content type is not accepted send by 415 - UNSUPPORTED MEDIA TYPE
                                        Object unsupportedResult =
                                                findStatusRoute(HttpStatus.UNSUPPORTED_MEDIA_TYPE, nettyHttpRequest, binderRegistry)
                                                        .map(RouteMatch::execute)
                                                        .orElse(HttpResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE));

                                        ctx.writeAndFlush(unsupportedResult)
                                                .addListener(ChannelFutureListener.CLOSE);
                                    } else {
                                        if (LOG.isDebugEnabled()) {
                                            LOG.debug("Matched route {} - {} to controller {}", nettyHttpRequest.getMethod(), nettyHttpRequest.getPath(), route.getDeclaringType().getName());
                                        }
                                        // all ok proceed to try and execute the route
                                        handleRouteMatch(route, nettyHttpRequest, binderRegistry, ctx);
                                    }
                                }));

                                if (!routeMatch.isPresent()) {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("No matching route found for URI {} and method {}", nettyHttpRequest.getUri(), nettyHttpRequest.getMethod());
                                    }

                                    // if there is no route present try to locate a route that matches a different HTTP method
                                    Set<HttpMethod> existingRoutes = routerBean
                                            .map(router ->
                                                    router.findAny(nettyHttpRequest.getUri().toString())
                                            ).orElse(Stream.empty())
                                            .map(UriRouteMatch::getHttpMethod)
                                            .collect(Collectors.toSet());

                                    if (!existingRoutes.isEmpty()) {
                                        // if there are other routes that match send back 405 - METHOD_NOT_ALLOWED
                                        Object notAllowedResponse =
                                                findStatusRoute(HttpStatus.METHOD_NOT_ALLOWED, nettyHttpRequest, binderRegistry)
                                                        .map(RouteMatch::execute)
                                                        .orElse(HttpResponse.notAllowed(
                                                                existingRoutes
                                                        ));

                                        ctx.writeAndFlush(notAllowedResponse)
                                                .addListener(ChannelFutureListener.CLOSE);
                                    } else {
                                        // if no alternative route was found send back 404 - NOT_FOUND
                                        Object notFoundResponse =
                                                findStatusRoute(HttpStatus.NOT_FOUND, nettyHttpRequest, binderRegistry)
                                                        .map(RouteMatch::execute)
                                                        .orElse(HttpResponse.notFound());

                                        ctx.writeAndFlush(notFoundResponse)
                                                .addListener(ChannelFutureListener.CLOSE);
                                    }
                                }
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

                                if (LOG.isErrorEnabled()) {
                                    LOG.error("Unexpected error occurred: " + cause.getMessage(), cause);
                                }

                                NettyHttpRequest nettyHttpRequest = ctx.channel().attr(NettyHttpRequest.KEY).get();

                                if (nettyHttpRequest != null) {

                                    RouteMatch matchedRoute = nettyHttpRequest.getMatchedRoute();
                                    Class declaringType = matchedRoute.getDeclaringType();
                                    try {
                                        if (declaringType != null) {

                                            Optional<RouteMatch<Object>> match = routerBean
                                                    .flatMap(router -> router.route(declaringType, cause))
                                                    .map(route -> fulfillArgumentRequirements(route, nettyHttpRequest, binderRegistry))
                                                    .filter(RouteMatch::isExecutable);


                                            if (match.isPresent()) {
                                                RouteMatch finalRoute = match.get();
                                                Object result = finalRoute.execute();
                                                ctx.writeAndFlush(result)
                                                        .addListener(ChannelFutureListener.CLOSE);
                                                return;
                                            }
                                        }

                                        Optional<ExceptionHandler> exceptionHandler = beanLocator
                                                .findBean(ExceptionHandler.class, Qualifiers.byTypeArguments(cause.getClass(), Object.class));

                                        if (exceptionHandler.isPresent()) {
                                            Object result = exceptionHandler
                                                    .get()
                                                    .handle(nettyHttpRequest, cause);
                                            ctx.writeAndFlush(result)
                                                    .addListener(ChannelFutureListener.CLOSE);
                                            return;
                                        }
                                    } catch (Throwable e) {
                                        if (LOG.isErrorEnabled()) {
                                            LOG.error("Exception occurred executing error handler. Falling back to default error handling: " + e.getMessage(), e);
                                        }
                                    }
                                }

                                Object errorResponse;
                                try {
                                    errorResponse = findStatusRoute(HttpStatus.INTERNAL_SERVER_ERROR, nettyHttpRequest, binderRegistry)
                                            .map(RouteMatch::execute)
                                            .orElse(HttpResponse.serverError());
                                } catch (Throwable e) {
                                    if (LOG.isErrorEnabled()) {
                                        LOG.error("Exception occurred executing error handler. Falling back to default error handling: " + e.getMessage(), e);
                                    }
                                    errorResponse = HttpResponse.serverError();
                                }

                                ctx.channel()
                                        .writeAndFlush(errorResponse)
                                        .addListener(ChannelFutureListener.CLOSE);


                            }
                        });

                    }
                })
                .bind(serverConfiguration.getHost(), port == -1 ? SocketUtils.findAvailableTcpPort() : port);

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
        return newEventLoopGroup(serverConfiguration.getParent());
    }

    protected NioEventLoopGroup createWorkerEventLoopGroup() {
        return newEventLoopGroup(serverConfiguration.getWorker());
    }

    protected Object handleBadRequest(NettyHttpRequest request, RequestBinderRegistry binderRegistry) {
        try {
            return this.router.flatMap(router ->
                    router.route(HttpStatus.BAD_REQUEST)
                            .map(match -> fulfillArgumentRequirements(match, request, binderRegistry))
                            .filter(RouteMatch::isExecutable)
                            .map(RouteMatch::execute)
            ).orElse(HttpResponse.badRequest());
        } catch (Exception e) {
            throw new InternalServerException("Error executing status code 400 handler: " + e.getMessage(), e);
        }
    }

    protected ServerBootstrap createServerBootstrap() {
        return new ServerBootstrap();
    }

    private NioEventLoopGroup newEventLoopGroup(NettyHttpServerConfiguration.EventLoopConfig config) {
        if (config != null) {
            return new NioEventLoopGroup(config.getNumOfThreads());
        } else {
            return new NioEventLoopGroup();
        }
    }

    private void handleRouteMatch(RouteMatch<Object> route, NettyHttpRequest request, RequestBinderRegistry binderRegistry, ChannelHandlerContext context) {
        // Set the matched route on the request
        request.setMatchedRoute(route);

        // try to fulfill the argument requirements of the route
        route = fulfillArgumentRequirements(route, request, binderRegistry);

        // If it is not executable and the body is not required send back 400 - BAD REQUEST
        if (!route.isExecutable() && !request.isBodyRequired()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Bad request: Unbindable arguments for route: " + route);
            }
            context.writeAndFlush(handleBadRequest(request, binderRegistry))
                    .addListener(ChannelFutureListener.CLOSE);
        } else {

            // decorate the execution of the route so that it runs an async executor
            // TODO: Allow customization of thread pool to execute actions
            route = prepareRouteForExecution(route, request, binderRegistry);

            request.setMatchedRoute(route);

            if (!request.isBodyRequired()) {
                // The request body is not required so simply execute the route
                route.execute();
            } else {

                // The request body is required, so at this point we must have a StreamedHttpRequest
                HttpRequest nativeRequest = request.getNativeRequest();
                if (nativeRequest instanceof StreamedHttpRequest) {
                    MediaType contentType = request.getContentType();
                    StreamedHttpRequest streamedHttpRequest = (StreamedHttpRequest) nativeRequest;
                    Optional<HttpContentSubscriberFactory> subscriberBean = beanLocator.findBean(HttpContentSubscriberFactory.class,
                            new ConsumesMediaTypeQualifier<>(contentType));

                    if (subscriberBean.isPresent()) {
                        HttpContentSubscriberFactory factory = subscriberBean.get();
                        HttpContentSubscriber subscriber = factory.build(request);
                        if(subscriber.isEnabled()) {
                            streamedHttpRequest.subscribe(subscriber);
                        }
                        else {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Request body parsing not enabled for subscriber: " + subscriber.getClass().getSimpleName());
                            }
                            context.writeAndFlush(handleBadRequest(request, binderRegistry))
                                    .addListener(ChannelFutureListener.CLOSE);

                        }
                    } else {
                        Subscriber<HttpContent> contentSubscriber = new DefaultHttpContentSubscriber(request);
                        streamedHttpRequest.subscribe(contentSubscriber);
                    }

                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Request body expected, but was empty.");
                    }
                    context.writeAndFlush(handleBadRequest(request, binderRegistry))
                            .addListener(ChannelFutureListener.CLOSE);

                }
            }

        }
    }

    private RouteMatch<Object> prepareRouteForExecution(RouteMatch<Object> route, NettyHttpRequest request, RequestBinderRegistry binderRegistry) {
        ChannelHandlerContext context = request.getChannelHandlerContext();
        Executor executor = context.channel().eventLoop();

        route = route.decorate(finalRoute -> {
            executor.execute(() -> {

                Object result;
                try {
                    try {
                        result = finalRoute.execute();
                    } catch (RoutingException e) {
                        result = HttpResponse.badRequest();
                    }

                    ChannelFuture channelFuture;
                    if (result == null) {
                        result = context.channel().attr(NettyHttpResponse.KEY).get();
                        if (result == null) {
                            result = HttpResponse.ok();
                        }
                    }
                    if (result instanceof HttpResponse) {
                        HttpStatus status = ((HttpResponse) result).getStatus();
                        if (status.getCode() >= 300) {
                            // handle re-mapping of errors
                            result = findStatusRoute(status, request, binderRegistry)
                                    .map(RouteMatch::execute)
                                    .orElse(result);
                        }
                    }
                    channelFuture = context.writeAndFlush(result);

                    channelFuture.addListener(future -> {
                        if (!future.isSuccess()) {
                            Throwable cause = future.cause();
                            if (LOG.isErrorEnabled()) {
                                LOG.error("Error encoding response: " + cause.getMessage(), cause);
                            }
                            if (context.channel().isWritable()) {
                                context.pipeline().fireExceptionCaught(cause);
                            }
                        }
                    });
                } catch (Throwable e) {
                    context.pipeline().fireExceptionCaught(e);
                }
            });
            return null;
        });
        return route;
    }

    private Optional<RouteMatch<Object>> findStatusRoute(HttpStatus status, NettyHttpRequest request, RequestBinderRegistry binderRegistry) {
        return this.router.flatMap(router ->
                router.route(status)
        ).map(match -> fulfillArgumentRequirements(match, request, binderRegistry))
                .filter(RouteMatch::isExecutable);
    }

    private RouteMatch<Object> fulfillArgumentRequirements(RouteMatch<Object> route, NettyHttpRequest request, RequestBinderRegistry binderRegistry) {
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
                        binderRegistry.findArgumentBinder(argument, request);
                if (registeredBinder.isPresent()) {
                    ArgumentBinder argumentBinder = registeredBinder.get();
                    String argumentName = argument.getName();
                    if (argumentBinder instanceof BodyArgumentBinder) {
                        if (argumentBinder instanceof NonBlockingBodyArgumentBinder) {
                            Optional bindingResult = argumentBinder
                                    .bind(argument, request);

                            if (bindingResult.isPresent()) {
                                argumentValues.put(argumentName, bindingResult.get());
                            }

                        } else {
                            argumentValues.put(argumentName, (Supplier<Optional>) () ->
                                    argumentBinder.bind(argument, request)
                            );
                            request.setBodyRequired(true);
                        }
                    } else {

                        Optional bindingResult = argumentBinder
                                .bind(argument, request);
                        if (argument.getType() == Optional.class) {
                            argumentValues.put(argumentName, bindingResult);
                        } else if (bindingResult.isPresent()) {
                            argumentValues.put(argumentName, bindingResult.get());
                        } else if (HttpUtil.isFormData(request)) {
                            argumentValues.put(argumentName, (Supplier<Optional>) () ->
                                    argumentBinder.bind(argument, request)
                            );
                        }
                    }
                }
            }
        }

        route = route.fulfill(argumentValues);
        return route;
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
