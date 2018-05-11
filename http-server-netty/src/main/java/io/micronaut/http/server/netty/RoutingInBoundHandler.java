/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.http.server.netty;

import io.micronaut.context.BeanLocator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.StreamUtils;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.hateos.JsonError;
import io.micronaut.http.hateos.Link;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.netty.NettyHttpResponse;
import io.micronaut.http.netty.buffer.NettyByteBufferFactory;
import io.micronaut.http.netty.content.HttpContentUtil;
import io.micronaut.http.netty.reactive.HandlerPublisher;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.binding.RequestBinderRegistry;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.HttpServerException;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.http.server.netty.async.ContextCompletionAwareSubscriber;
import io.micronaut.http.server.netty.async.DefaultCloseHandler;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.multipart.NettyPartData;
import io.micronaut.http.server.netty.multipart.NettyStreamingFileUpload;
import io.micronaut.http.server.netty.types.NettyCustomizableResponseTypeHandler;
import io.micronaut.http.server.netty.types.NettyCustomizableResponseTypeHandlerRegistry;
import io.micronaut.http.server.netty.types.files.NettyStreamedFileCustomizableResponseType;
import io.micronaut.http.server.netty.types.files.NettySystemFileCustomizableResponseType;
import io.micronaut.http.server.types.files.FileCustomizableResponseType;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.http.codec.TextPlainCodec;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.web.router.BasicObjectRouteMatch;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import io.micronaut.web.router.qualifier.ConsumesMediaTypeQualifier;
import io.micronaut.web.router.resource.StaticResourceResolver;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.ReplaySubject;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Internal implementation of the {@link io.netty.channel.ChannelInboundHandler} for Micronaut.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class RoutingInBoundHandler extends SimpleChannelInboundHandler<io.micronaut.http.HttpRequest<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingInBoundHandler.class);

    private final Router router;
    private final ExecutorSelector executorSelector;
    private final StaticResourceResolver staticResourceResolver;
    private final ExecutorService ioExecutor;
    private final BeanLocator beanLocator;
    private final NettyHttpServerConfiguration serverConfiguration;
    private final RequestArgumentSatisfier requestArgumentSatisfier;
    private final MediaTypeCodecRegistry mediaTypeCodecRegistry;
    private final NettyCustomizableResponseTypeHandlerRegistry customizableResponseTypeHandlerRegistry;

    /**
     * @param beanLocator                             The bean locator
     * @param router                                  The router
     * @param mediaTypeCodecRegistry                  The media type codec registry
     * @param customizableResponseTypeHandlerRegistry The customizable response type handler registry
     * @param staticResourceResolver                  The static resource resolver
     * @param serverConfiguration                     The Netty HTTP server configuration
     * @param binderRegistry                          The Request binder registry
     * @param executorSelector                        The executor selector
     * @param ioExecutor                              The IO executor
     */
    RoutingInBoundHandler(
        BeanLocator beanLocator,
        Router router,
        MediaTypeCodecRegistry mediaTypeCodecRegistry,
        NettyCustomizableResponseTypeHandlerRegistry customizableResponseTypeHandlerRegistry,
        StaticResourceResolver staticResourceResolver,
        NettyHttpServerConfiguration serverConfiguration,
        RequestBinderRegistry binderRegistry,
        ExecutorSelector executorSelector,
        ExecutorService ioExecutor) {

        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.customizableResponseTypeHandlerRegistry = customizableResponseTypeHandlerRegistry;
        this.beanLocator = beanLocator;
        this.staticResourceResolver = staticResourceResolver;
        this.ioExecutor = ioExecutor;
        this.executorSelector = executorSelector;
        this.router = router;
        this.requestArgumentSatisfier = new RequestArgumentSatisfier(binderRegistry);
        this.serverConfiguration = serverConfiguration;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        super.channelReadComplete(ctx);
        NettyHttpRequest request = NettyHttpRequest.get(ctx);
        if (request != null) {
            request.release();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        ctx.flush();
        NettyHttpRequest request = NettyHttpRequest.get(ctx);
        if (request != null) {
            request.release();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        try {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
                IdleState state = idleStateEvent.state();
                if (state == IdleState.ALL_IDLE) {
                    ctx.close();
                }
            }
        } finally {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, io.micronaut.http.HttpRequest<?> request) throws Exception {
        ctx.channel().config().setAutoRead(false);
        io.micronaut.http.HttpMethod httpMethod = request.getMethod();
        String requestPath = request.getPath();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Matching route {} - {}", httpMethod, requestPath);
        }

        Optional<UriRouteMatch<Object>> routeMatch = Optional.empty();

        List<UriRouteMatch<Object>> uriRoutes = router
            .find(httpMethod, requestPath)
            .filter((match) -> match.test(request))
            .collect(StreamUtils.minAll(
                Comparator.comparingInt((match) -> match.getVariables().size()),
                Collectors.toList()));

        if (uriRoutes.size() > 1) {
            throw new DuplicateRouteException(requestPath, uriRoutes);
        } else if (uriRoutes.size() == 1) {
            UriRouteMatch<Object> establishedRoute = uriRoutes.get(0);
            request.setAttribute(HttpAttributes.ROUTE, establishedRoute.getRoute());
            request.setAttribute(HttpAttributes.ROUTE_MATCH, establishedRoute);
            request.setAttribute(HttpAttributes.URI_TEMPLATE, establishedRoute.getRoute().getUriMatchTemplate().toString());
            routeMatch = Optional.of(establishedRoute);
        }

        RouteMatch<?> route;

        if (!routeMatch.isPresent()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No matching route found for URI {} and method {}", request.getUri(), httpMethod);
            }

            // if there is no route present try to locate a route that matches a different HTTP method
            Set<io.micronaut.http.HttpMethod> existingRoutes = router
                .findAny(request.getUri().toString())
                .map(UriRouteMatch::getHttpMethod)
                .collect(Collectors.toSet());

            if (!existingRoutes.isEmpty()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Method not allowed for URI {} and method {}", request.getUri(), httpMethod);
                }
                Optional<RouteMatch<Object>> statusRoute = router.route(HttpStatus.METHOD_NOT_ALLOWED);
                if (statusRoute.isPresent()) {
                    route = statusRoute.get();
                } else {
                    JsonError error = newError(request, "Method [" + httpMethod + "] not allowed. Allowed methods: " + existingRoutes);

                    MutableHttpResponse<Object> defaultResponse = io.micronaut.http.HttpResponse.notAllowed(existingRoutes);

                    if (io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod())) {
                        defaultResponse.body(error);
                    }
                    emitDefaultErrorResponse(ctx, request, defaultResponse);
                    return;
                }

            } else {
                Optional<? extends FileCustomizableResponseType> optionalFile = Optional.empty();
                Optional<URL> optionalUrl = staticResourceResolver.resolve(requestPath);
                if (optionalUrl.isPresent()) {
                    URL url = optionalUrl.get();
                    File file = new File(url.getPath());
                    if (file.exists()) {
                        if (!file.isDirectory() && file.canRead()) {
                            optionalFile = Optional.of(new NettySystemFileCustomizableResponseType(file));
                        }
                    } else {
                        optionalFile = Optional.of(new NettyStreamedFileCustomizableResponseType(url));
                    }
                }

                if (optionalFile.isPresent()) {
                    route = new BasicObjectRouteMatch(optionalFile.get());
                } else {
                    Optional<RouteMatch<Object>> statusRoute = router.route(HttpStatus.NOT_FOUND);
                    if (statusRoute.isPresent()) {
                        route = statusRoute.get();
                    } else {
                        emitDefaultNotFoundResponse(ctx, request);
                        return;
                    }
                }
            }
        } else {
            route = routeMatch.get();
        }
        // Check that the route is an accepted content type
        MediaType contentType = request.getContentType().orElse(null);
        if (!route.accept(contentType)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Matched route is not a supported media type: {}", contentType);
            }

            // we matched a route and it says we consume JSON and we sent in XML
            // if route is a methodbased route, then get the declaringType and call router.route with it.
            Optional<RouteMatch<Object>> statusRoute = router.route(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            if (statusRoute.isPresent()) {
                route = statusRoute.get();
            } else {
                JsonError error = newError(request, "Unsupported Media Type: " + contentType);
                MutableHttpResponse<Object> res = io.micronaut.http.HttpResponse
                    .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(error);
                emitDefaultErrorResponse(ctx, request, res);
                return;
            }
        }
        if (LOG.isDebugEnabled()) {
            if (route instanceof MethodBasedRouteMatch) {
                LOG.debug("Matched route {} - {} to controller {}", httpMethod, requestPath, ((MethodBasedRouteMatch) route).getDeclaringType().getName());
            } else {
                LOG.debug("Matched route {} - {}", httpMethod, requestPath);
            }
        }
        // all ok proceed to try and execute the route
        handleRouteMatch(route, (NettyHttpRequest) request, ctx);
    }

    private void emitDefaultNotFoundResponse(ChannelHandlerContext ctx, io.micronaut.http.HttpRequest<?> request) {
        JsonError error = newError(request, "Page Not Found");
        MutableHttpResponse<Object> res = io.micronaut.http.HttpResponse.notFound()
            .body(error);
        emitDefaultErrorResponse(ctx, request, res);
    }

    private JsonError newError(io.micronaut.http.HttpRequest<?> request, String message) {
        URI uri = request.getUri();
        return new JsonError(message)
            .link(Link.SELF, Link.of(uri));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        NettyHttpRequest nettyHttpRequest = NettyHttpRequest.get(ctx);
        RouteMatch<?> errorRoute = null;
        if (nettyHttpRequest == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Micronaut Server Error - No request state present. Cause: " + cause.getMessage(), cause);
            }
            ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
            return;
        }

        // find the origination of of the route
        RouteMatch<?> originalRoute = nettyHttpRequest.getMatchedRoute();
        Class declaringType = null;
        if (originalRoute instanceof MethodExecutionHandle) {
            declaringType = ((MethodExecutionHandle) originalRoute).getDeclaringType();
        }

        // when arguments do not match, then there is UnsatisfiedRouteException, we can handle this with a routed bad request
        if (cause instanceof UnsatisfiedRouteException) {
            if (declaringType != null) {
                // handle error with a method that is non global with bad request
                errorRoute = router.route(declaringType, HttpStatus.BAD_REQUEST).orElse(null);
            }
            if (errorRoute == null) {
                // handle error with a method that is global with bad request
                errorRoute = router.route(HttpStatus.BAD_REQUEST).orElse(null);
            }
        }

        // any another other exception may arise. handle these with non global exception marked method or a global exception marked method.
        if (errorRoute == null) {
            if (declaringType != null) {
                errorRoute = router.route(declaringType, cause).orElse(null);
            }
            if (errorRoute == null) {
                errorRoute = router.route(cause).orElse(null);
            }
        }

        if (errorRoute != null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Unexpected error occurred: " + cause.getMessage(), cause);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found matching exception handler for exception [{}]: {}", cause.getMessage(), errorRoute);
            }
            errorRoute = requestArgumentSatisfier.fulfillArgumentRequirements(errorRoute, nettyHttpRequest, false);
            MediaType defaultResponseMediaType = errorRoute.getProduces().stream().findFirst().orElse(MediaType.APPLICATION_JSON_TYPE);
            try {
                Object result = errorRoute.execute();
                io.micronaut.http.HttpResponse response = errorResultToResponse(result);

                processResponse(ctx, nettyHttpRequest, (NettyHttpResponse<?>) response, defaultResponseMediaType, errorRoute);
            } catch (Throwable e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Exception occurred executing error handler. Falling back to default error handling: " + e.getMessage(), e);
                }
                writeDefaultErrorResponse(ctx, nettyHttpRequest, e);
            }
        } else {
            Optional<ExceptionHandler> exceptionHandler = beanLocator
                .findBean(ExceptionHandler.class, Qualifiers.byTypeArguments(cause.getClass(), Object.class));

            if (exceptionHandler.isPresent()) {
                ExceptionHandler handler = exceptionHandler.get();
                MediaType defaultResponseMediaType = MediaType.fromType(exceptionHandler.getClass()).orElse(MediaType.APPLICATION_JSON_TYPE);
                try {
                    Object result = handler.handle(nettyHttpRequest, cause);
                    io.micronaut.http.HttpResponse response = errorResultToResponse(result);
                    processResponse(ctx, nettyHttpRequest, (NettyHttpResponse<?>) response, defaultResponseMediaType, null);
                } catch (Throwable e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Exception occurred executing error handler. Falling back to default error handling.");
                    }
                    writeDefaultErrorResponse(ctx, nettyHttpRequest, e);
                }
            } else {
                writeDefaultErrorResponse(ctx, nettyHttpRequest, cause);
            }
        }
    }

    private MutableHttpResponse errorResultToResponse(Object result) {
        MutableHttpResponse<?> response;
        if (result == null) {
            response = io.micronaut.http.HttpResponse.serverError();
        } else if (result instanceof io.micronaut.http.HttpResponse) {
            response = (MutableHttpResponse) result;
        } else {
            response = io.micronaut.http.HttpResponse.serverError()
                .body(result);
            MediaType.fromType(result.getClass()).ifPresent(response::contentType);
        }
        return response;
    }

    private void handleRouteMatch(
        RouteMatch<?> route,
        NettyHttpRequest<?> request,
        ChannelHandlerContext context) {
        // Set the matched route on the request
        request.setMatchedRoute(route);

        // try to fulfill the argument requirements of the route
        route = requestArgumentSatisfier.fulfillArgumentRequirements(route, request, false);

        // If it is not executable and the body is not required send back 400 - BAD REQUEST

        // decorate the execution of the route so that it runs an async executor
        request.setMatchedRoute(route);

        // The request body is required, so at this point we must have a StreamedHttpRequest
        io.netty.handler.codec.http.HttpRequest nativeRequest = request.getNativeRequest();
        if (!route.isExecutable() && io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod()) && nativeRequest instanceof StreamedHttpRequest) {
            Optional<MediaType> contentType = request.getContentType();
            HttpContentProcessor<?> processor = contentType
                .flatMap(type ->
                    beanLocator.findBean(HttpContentSubscriberFactory.class,
                        new ConsumesMediaTypeQualifier<>(type))
                ).map(factory ->
                    factory.build(request)
                ).orElse(new DefaultHttpContentProcessor(request, serverConfiguration));

            processor.subscribe(buildSubscriber(request, context, route));
        } else {
            context.read();
            route = prepareRouteForExecution(route, request);
            route.execute();
        }
    }

    private Subscriber<Object> buildSubscriber(NettyHttpRequest request,
                                               ChannelHandlerContext context,
                                               RouteMatch<?> finalRoute) {
        return new CompletionAwareSubscriber<Object>() {
            RouteMatch<?> routeMatch = finalRoute;
            AtomicBoolean executed = new AtomicBoolean(false);
            ConcurrentHashMap<Integer, LongAdder> partPositions = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, ReplaySubject> subjects = new ConcurrentHashMap<>();
            ConcurrentHashMap<Integer, ReplaySubject> childSubjects = new ConcurrentHashMap<>();
            ConcurrentHashMap<Integer, StreamingFileUpload> streamingUploads = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, HttpData> messages = new ConcurrentHashMap<>();
            ConversionService conversionService = ConversionService.SHARED;

            @Override
            protected void doOnSubscribe(Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            protected void doOnNext(Object message) {
                boolean executed = this.executed.get();
                if (message instanceof ByteBufHolder) {
                    if (message instanceof HttpData) {
                        HttpData data = (HttpData) message;
                        String name = data.getName();
                        Optional<Argument<?>> requiredInput = routeMatch.getRequiredInput(name);

                        if (requiredInput.isPresent()) {
                            Integer dataKey = System.identityHashCode(data);
                            Argument<?> argument = requiredInput.get();

                            Supplier<Object> value;

                            if (Publishers.isConvertibleToPublisher(argument.getType())) {
                                Argument typeVariable = argument.getFirstTypeVariable().orElse(argument);
                                Class typeVariableType = typeVariable.getType();

                                subjects.computeIfAbsent(name, (key) -> ReplaySubject.create());
                                ReplaySubject namedSubject = subjects.get(name);

                                if (Publishers.isConvertibleToPublisher(typeVariableType)) {
                                    childSubjects.computeIfAbsent(dataKey, (key) -> {
                                        ReplaySubject childSubject = ReplaySubject.create();
                                        Flowable flowable = childSubject.toFlowable(BackpressureStrategy.BUFFER);
                                        if (StreamingFileUpload.class.isAssignableFrom(typeVariableType) && data instanceof FileUpload) {
                                            namedSubject.onNext(new NettyStreamingFileUpload(
                                                (FileUpload) data,
                                                serverConfiguration.getMultipart(),
                                                ioExecutor,
                                                flowable));
                                        } else {
                                            namedSubject.onNext(flowable);
                                        }

                                        return childSubject;
                                    });
                                }

                                ReplaySubject subject = childSubjects.getOrDefault(dataKey, namedSubject);

                                if (data.refCnt() <= 1) {
                                    data.retain();
                                }

                                boolean partialUpload = true;

                                if (Publishers.isConvertibleToPublisher(typeVariableType)) {
                                    typeVariable = typeVariable.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                                } else if (StreamingFileUpload.class.isAssignableFrom(typeVariableType)) {
                                    typeVariable = Argument.of(PartData.class);
                                } else if (!ClassUtils.isJavaLangType(typeVariableType)) {
                                    partialUpload = false;
                                }

                                Object part = data;

                                if (data instanceof FileUpload) {
                                    FileUpload fileUpload = (FileUpload) data;

                                    if (partialUpload) {
                                        partPositions.putIfAbsent(dataKey, new LongAdder());
                                        LongAdder position = partPositions.get(dataKey);
                                        position.add(fileUpload.length());
                                        part = new NettyPartData(fileUpload, position.longValue());
                                    }

                                    if (StreamingFileUpload.class.isAssignableFrom(argument.getType())) {
                                        streamingUploads.computeIfAbsent(dataKey, (key) ->
                                            new NettyStreamingFileUpload(
                                                fileUpload,
                                                serverConfiguration.getMultipart(),
                                                ioExecutor,
                                                subject.toFlowable(BackpressureStrategy.BUFFER)));
                                    }
                                }

                                Optional<?> converted = conversionService.convert(part, typeVariable);

                                if (converted.isPresent()) {
                                    subject.onNext(converted.get());
                                }

                                if (data.isCompleted() && partialUpload) {
                                    subject.onComplete();
                                }

                                value = () -> {
                                    if (streamingUploads.containsKey(dataKey)) {
                                        return streamingUploads.get(dataKey);
                                    } else {
                                        return namedSubject.toFlowable(BackpressureStrategy.BUFFER);
                                    }
                                };

                            } else {
                                messages.putIfAbsent(name, data);
                                value = () -> {
                                    HttpData httpData = messages.get(name);
                                    if (httpData.refCnt() > 0) {
                                        return httpData;
                                    } else {
                                        return null;
                                    }
                                };
                            }

                            if (!executed) {
                                routeMatch = routeMatch.fulfill(Collections.singletonMap(argument.getName(), value.get()));
                            }

                        } else {
                            request.addContent(data);
                        }
                    } else {
                        request.addContent((ByteBufHolder) message);
                        if (!routeMatch.isExecutable() && message instanceof LastHttpContent) {
                            Optional<Argument<?>> bodyArgument = routeMatch.getBodyArgument();
                            if (bodyArgument.isPresent()) {
                                Argument<?> argument = bodyArgument.get();
                                String bodyArgumentName = argument.getName();
                                if (routeMatch.isRequiredInput(bodyArgumentName)) {
                                    Optional body = request.getBody();
                                    if (body.isPresent()) {
                                        routeMatch = routeMatch.fulfill(
                                            Collections.singletonMap(
                                                bodyArgumentName,
                                                body.get()
                                            )
                                        );
                                    }
                                }
                            }
                        }
                    }
                } else {
                    request.setBody(message);
                }

                if (!executed) {
                    if (routeMatch.isExecutable() || message instanceof LastHttpContent) {
                        // we have enough data to satisfy the route, continue
                        executeRoute();
                    }
                }
            }

            @Override
            protected void doOnError(Throwable t) {
                try {
                    exceptionCaught(context, t);
                } catch (Exception e) {
                    // should never happen
                    writeDefaultErrorResponse(context, request, e);
                }
            }

            @Override
            protected void doOnComplete() {
                subjects.forEachValue(0, (subject) -> {
                    if (!subject.hasComplete()) {
                        subject.onComplete();
                    }
                });
                executeRoute();
            }

            private void executeRoute() {
                if (executed.compareAndSet(false, true)) {
                    try {
                        routeMatch = prepareRouteForExecution(routeMatch, request);
                        routeMatch.execute();
                    } catch (Exception e) {
                        context.pipeline().fireExceptionCaught(e);
                    }
                }
            }
        };
    }

    private RouteMatch<?> prepareRouteForExecution(RouteMatch<?> route, NettyHttpRequest<?> request) {
        ChannelHandlerContext context = request.getChannelHandlerContext();
        // Select the most appropriate Executor
        ExecutorService executor;
        if (route instanceof MethodBasedRouteMatch) {
            executor = executorSelector.select((MethodBasedRouteMatch) route).orElse(context.channel().eventLoop());
        } else {
            executor = context.channel().eventLoop();
        }

        route = route.decorate(finalRoute -> {
            MediaType defaultResponseMediaType = finalRoute
                .getProduces()
                .stream()
                .findFirst()
                .orElse(MediaType.APPLICATION_JSON_TYPE);

            Publisher<? extends io.micronaut.http.HttpResponse<?>> finalPublisher;
            AtomicReference<io.micronaut.http.HttpRequest<?>> requestReference = new AtomicReference<>(request);
            Flowable<MutableHttpResponse<?>> routePublisher = Flowable.create((emitter) -> {
                MutableHttpResponse<?> response;
                io.micronaut.http.HttpRequest<?> httpRequest = requestReference.get();

                try {
                    RouteMatch<?> routeMatch = finalRoute;
                    if (!routeMatch.isExecutable()) {
                        routeMatch = requestArgumentSatisfier.fulfillArgumentRequirements(routeMatch, httpRequest, true);
                    }
                    Object result = routeMatch.execute();
                    ReturnType<?> genericReturnType = routeMatch.getReturnType();
                    Class<?> javaReturnType = genericReturnType.getType();

                    if (result != null && isResponsePublisher(genericReturnType, javaReturnType)) {
                        // special case, handle an emitted http response
                        @SuppressWarnings("unchecked")
                        Flowable<HttpResponse<?>> responseFlowable = ConversionService.SHARED.convert(result, Flowable.class)
                            .orElseThrow(() -> new IllegalStateException("Unsupported Reactive type: " + javaReturnType));

                        responseFlowable.subscribeOn(Schedulers.from(executor))
                            .subscribe(new Subscriber<HttpResponse<?>>() {
                                boolean emitted = false;

                                @Override
                                public void onSubscribe(Subscription s) {
                                    s.request(1);
                                }

                                @Override
                                public void onNext(HttpResponse<?> httpResponse) {
                                    emitted = true;
                                    if (!(httpResponse instanceof MutableHttpResponse)) {
                                        Optional<NettyHttpResponse> converted = ConversionService.SHARED.convert(httpResponse, NettyHttpResponse.class);
                                        if (converted.isPresent()) {
                                            emitter.onNext(converted.get());
                                        } else {
                                            emitter.onError(new InternalServerException("Emitted response is not mutable"));
                                        }
                                    } else {
                                        emitter.onNext((MutableHttpResponse<?>) httpResponse);
                                    }
                                }

                                @Override
                                public void onError(Throwable t) {
                                    emitted = true;
                                    emitter.onError(t);
                                }

                                @Override
                                public void onComplete() {
                                    if (!emitted) {
                                        emitter.onNext(io.micronaut.http.HttpResponse.status(HttpStatus.NOT_FOUND));
                                    }
                                    emitter.onComplete();
                                }
                            });

                        return;
                    } else if (result == null) {
                        Class declaringType = ((MethodBasedRouteMatch) routeMatch).getDeclaringType();
                        if (javaReturnType != void.class) {
                            // handle re-mapping of errors
                            Optional<RouteMatch<Object>> statusRoute = Optional.empty();
                            // if declaringType is not null, this means its a locally marked method handler
                            if (declaringType != null) {
                                statusRoute = router.route(declaringType, HttpStatus.NOT_FOUND);
                            }
                            if (!statusRoute.isPresent()){
                                statusRoute = router.route(HttpStatus.NOT_FOUND);
                            }
                            result = statusRoute
                                .map((match) -> requestArgumentSatisfier.fulfillArgumentRequirements(match, httpRequest, true))
                                .filter(RouteMatch::isExecutable)
                                .map(RouteMatch::execute)
                                .map(Object.class::cast)
                                .orElse(NettyHttpResponseFactory.getOr(request, io.micronaut.http.HttpResponse.notFound()));

                            if (result instanceof MutableHttpResponse) {
                                response = (MutableHttpResponse<?>) result;
                            } else {
                                response = io.micronaut.http.HttpResponse.status(HttpStatus.NOT_FOUND)
                                    .body(result);
                            }
                        } else {
                            response = NettyHttpResponseFactory.getOr(request, io.micronaut.http.HttpResponse.ok());
                        }
                    } else if (result instanceof io.micronaut.http.HttpResponse) {
                        Class declaringType = ((MethodBasedRouteMatch) routeMatch).getDeclaringType();
                        HttpResponse returnedResponse = (HttpResponse) result;
                        HttpStatus status = returnedResponse.getStatus();
                        if (status.getCode() >= HttpStatus.BAD_REQUEST.getCode()) {

                            // handle re-mapping of errors
                            Optional<RouteMatch<Object>> statusRoute = Optional.empty();
                            // if declaringType is not null, this means its a locally marked method handler
                            if (declaringType != null) {
                                statusRoute = router.route(declaringType, status);
                            }
                            if (!statusRoute.isPresent()){
                                statusRoute = router.route(status);
                            }
                            result = statusRoute
                                    .map((match) -> requestArgumentSatisfier.fulfillArgumentRequirements(match, httpRequest, true))
                                    .filter(RouteMatch::isExecutable)
                                    .map(RouteMatch::execute)
                                    .map(Object.class::cast)
                                    .orElse(returnedResponse);
                        }
                        if (result instanceof MutableHttpResponse) {
                            response = (MutableHttpResponse<?>) result;
                        } else {
                            response = io.micronaut.http.HttpResponse.status(status).body(result);
                        }
                    } else if (result instanceof HttpStatus) {
                        response = io.micronaut.http.HttpResponse.status((HttpStatus) result);
                    } else {
                        HttpStatus status = HttpStatus.OK;

                        if (routeMatch instanceof MethodBasedRouteMatch) {
                            final MethodBasedRouteMatch rm = (MethodBasedRouteMatch) routeMatch;
                            if (rm.hasAnnotation(Status.class)) {
                                status = rm.getAnnotation(Status.class).value();
                            }
                        }
                        response = io.micronaut.http.HttpResponse.status(status).body(result);
                    }

                    emitter.onNext(response);
                    emitter.onComplete();

                } catch (Throwable e) {
                    emitter.onError(e);
                }
            }, BackpressureStrategy.ERROR);

            finalPublisher = filterPublisher(request, requestReference, routePublisher);
            if (finalPublisher instanceof Flowable) {
                finalPublisher = ((Flowable<MutableHttpResponse<?>>) finalPublisher)
                    .subscribeOn(Schedulers.from(executor));
            } else {
                finalPublisher = Flowable.fromPublisher(finalPublisher)
                    .subscribeOn(Schedulers.from(executor));
            }

            finalPublisher.subscribe(new CompletionAwareSubscriber<io.micronaut.http.HttpResponse<?>>() {
                @Override
                protected void doOnSubscribe(Subscription subscription) {
                    subscription.request(1);
                }

                @Override
                protected void doOnNext(io.micronaut.http.HttpResponse<?> message) {
                    processResponse(
                        context,
                        request,
                        (NettyHttpResponse<?>) message,
                        defaultResponseMediaType,
                        finalRoute
                    );
                }

                @Override
                protected void doOnError(Throwable t) {
                    request.setAttribute(HttpAttributes.ERROR, t);
                    context.pipeline().fireExceptionCaught(t);
                }

                @Override
                protected void doOnComplete() {
                    // no-op
                }
            });

            return null;
        });
        return route;
    }

    private boolean isResponsePublisher(ReturnType<?> genericReturnType, Class<?> javaReturnType) {
        return Publishers.isConvertibleToPublisher(javaReturnType) && genericReturnType.getFirstTypeVariable().map(arg -> HttpResponse.class.isAssignableFrom(arg.getType())).orElse(false);
    }

    private Publisher<? extends io.micronaut.http.HttpResponse<?>> filterPublisher(
        io.micronaut.http.HttpRequest<?> request,
        AtomicReference<io.micronaut.http.HttpRequest<?>> requestReference,
        Publisher<MutableHttpResponse<?>> routePublisher) {
        Publisher<? extends io.micronaut.http.HttpResponse<?>> finalPublisher;
        List<HttpFilter> filters = new ArrayList<>(router.findFilters(request));
        if (!filters.isEmpty()) {
            // make the action executor the last filter in the chain
            filters.add((HttpServerFilter) (req, chain) -> routePublisher);

            AtomicInteger integer = new AtomicInteger();
            int len = filters.size();
            ServerFilterChain filterChain = new ServerFilterChain() {
                @SuppressWarnings("unchecked")
                @Override
                public Publisher<MutableHttpResponse<?>> proceed(io.micronaut.http.HttpRequest<?> request) {
                    int pos = integer.incrementAndGet();
                    if (pos > len) {
                        throw new IllegalStateException("The FilterChain.proceed(..) method should be invoked exactly once per filter execution. The method has instead been invoked multiple times by an erroneous filter definition.");
                    }
                    HttpFilter httpFilter = filters.get(pos);
                    return (Publisher<MutableHttpResponse<?>>) httpFilter.doFilter(requestReference.getAndSet(request), this);
                }
            };
            HttpFilter httpFilter = filters.get(0);
            finalPublisher = httpFilter.doFilter(request, filterChain);
        } else {
            finalPublisher = routePublisher;
        }
        return finalPublisher;
    }

    private void processResponse(
        ChannelHandlerContext context,
        NettyHttpRequest<?> request,
        NettyHttpResponse<?> defaultResponse,
        MediaType defaultResponseMediaType,
        RouteMatch<?> route) {
        Optional<?> optionalBody = defaultResponse.getBody();
        FullHttpResponse nativeResponse = defaultResponse.getNativeResponse();
        boolean isChunked = HttpUtil.isTransferEncodingChunked(nativeResponse);
        if (optionalBody.isPresent()) {
            // a response body is present so we need to process it
            Object body = optionalBody.get();
            Class<?> bodyType = body.getClass();

            Optional<MediaType> responseMediaType = defaultResponse.getContentType();

            Publisher<Object> publisher;
            if (Publishers.isConvertibleToPublisher(bodyType)) {
                // if the return type is a reactive type we need to subscribe to Publisher in order to emit
                // an appropriate response
                bodyType = resolveBodyType(route, bodyType);
                publisher = convertPublisher(body, body.getClass());
            } else {
                // the return result is not a reactive type so build a publisher for the result that runs on the I/O scheduler
                if (body instanceof CompletableFuture) {
                    bodyType = resolveBodyType(route, bodyType);
                    publisher = Publishers.fromCompletableFuture(() -> (CompletableFuture<Object>) body);
                } else {
                    publisher = Publishers.just(body);
                }
            }

            if (isChunked) {
                // if the transfer encoding is chunked then create a io.micronaut.http.netty.stream.StreamedHttpResponse
                // that will send the encoded data chunk by chunk

                // adapt the publisher to produce HTTP content
                writeHttpContentChunkByChunk(
                    context,
                    request,
                    nativeResponse,
                    responseMediaType.orElse(defaultResponseMediaType),
                    publisher);
            } else {
                // if the transfer encoding is not chunked then we must send a content length header so subscribe the
                // publisher, encode the result as a io.netty.handler.codec.http.FullHttpResponse
                boolean isPublisher = Publishers.isConvertibleToPublisher(body.getClass());
                boolean isSingle = !isPublisher || Publishers.isSingle(body.getClass()) || io.micronaut.http.HttpResponse.class.isAssignableFrom(bodyType);
                if (isSingle) {
                    publisher.subscribe(new ContextCompletionAwareSubscriber<Object>(context) {

                        @Override
                        protected void doOnError(Throwable t) {
                            super.doOnError(t);
                        }

                        @Override
                        protected void onComplete(Object message) {
                            try {
                                boolean isOpen = context.channel().isOpen();
                                if (!isOpen) {
                                    subscription.cancel();
                                } else if (message != null) {

                                    Object body;
                                    FullHttpResponse fullHttpResponse;
                                    NettyHttpResponse response;

                                    if (message instanceof io.micronaut.http.HttpResponse) {
                                        Optional<NettyHttpResponse> optionalResponse = ConversionService.SHARED.convert(message, NettyHttpResponse.class);
                                        response = optionalResponse.orElseThrow(() -> {
                                            String errorMessage = String.format("Could not convert the response type [%s] to the required type necessary to process the response", message.getClass().getName());
                                            return new HttpServerException(errorMessage);
                                        });
                                        body = response.getBody().orElse(message);
                                        fullHttpResponse = response.getNativeResponse();
                                    } else {
                                        response = defaultResponse;
                                        body = message;
                                        fullHttpResponse = nativeResponse;
                                    }

                                    if (responseMediaType.isPresent()) {
                                        Optional<MediaTypeCodec> codec = mediaTypeCodecRegistry.findCodec(responseMediaType.get(), body.getClass());
                                        if (codec.isPresent()) {
                                            writeSingleMessage(context, request, fullHttpResponse, body, codec.get(), responseMediaType.get());
                                            return;
                                        }
                                    }

                                    Optional<NettyCustomizableResponseTypeHandler> typeHandler = customizableResponseTypeHandlerRegistry.findTypeHandler(body.getClass());
                                    if (typeHandler.isPresent()) {
                                        typeHandler.get().handle(body, request, response, context);
                                        return;
                                    }

                                    Optional<MediaTypeCodec> codec = mediaTypeCodecRegistry.findCodec(defaultResponseMediaType, body.getClass());
                                    if (codec.isPresent()) {
                                        writeSingleMessage(context, request, fullHttpResponse, body, codec.get(), defaultResponseMediaType);
                                        return;
                                    }

                                    MediaTypeCodec defaultCodec = new TextPlainCodec(serverConfiguration.getDefaultCharset());

                                    writeSingleMessage(context, request, fullHttpResponse, body, defaultCodec, defaultResponseMediaType);

                                } else {
                                    // no body emitted so return a 404
                                    emitDefaultNotFoundResponse(context, request);
                                }
                            } finally {
                                // final read required to complete request
                                if (context.channel().isOpen()) {
                                    context.read();
                                }
                            }
                        }
                    });
                } else {
                    // unbound publisher, we cannot know the content length, so we write chunk by chunk
                    writeHttpContentChunkByChunk(
                        context,
                        request,
                        nativeResponse,
                        responseMediaType.orElse(defaultResponseMediaType),
                        publisher);
                }
            }
        } else {
            // no body returned so just write the Netty response as is
            writeNettyResponseAndCloseChannel(context, request, nativeResponse);
        }
    }

    private void writeHttpResponse(
        ChannelHandlerContext context,
        NettyHttpRequest<?> request,
        NettyHttpResponse<?> response,
        MediaType responseType) {
        Object body = response.getBody().orElse(null);
        if (body != null) {
            MediaTypeCodec codec = mediaTypeCodecRegistry.findCodec(
                responseType,
                body.getClass()
            ).orElse(new TextPlainCodec(serverConfiguration.getDefaultCharset()));
            writeSingleMessage(context, request, response.getNativeResponse(), body, codec, responseType);
        } else {
            writeNettyResponseAndCloseChannel(
                context, request, response.getNativeResponse()
            );
        }
    }

    @SuppressWarnings("unchecked")
    private Publisher<Object> convertPublisher(Object body, Class<?> bodyType) {
        return ConversionService.SHARED.convert(body, Publisher.class)
            .orElseThrow(() -> new IllegalArgumentException("Unsupported Reactive type: " + bodyType));
    }

    /**
     * @param ctx              The channel handler context
     * @param nettyHttpRequest The Netty HTTP request
     * @param cause            The throwable
     */
    void writeDefaultErrorResponse(ChannelHandlerContext ctx, NettyHttpRequest nettyHttpRequest, Throwable cause) {
        if (LOG.isErrorEnabled()) {
            LOG.error("Unexpected error occurred: " + cause.getMessage(), cause);
        }

        NettyHttpResponse error = (NettyHttpResponse) io.micronaut.http.HttpResponse.serverError()
            .body(new JsonError("Internal Server Error: " + cause.getMessage()));
        emitDefaultErrorResponse(ctx, nettyHttpRequest, error);
    }

    private Class<?> resolveBodyType(RouteMatch<?> route, Class<?> bodyType) {
        if (route != null) {
            bodyType = route.getReturnType().getFirstTypeVariable().map(Argument::getType).orElse(null);
        }
        if (bodyType == null) {
            bodyType = Object.class;
        }
        return bodyType;
    }

    private void emitDefaultErrorResponse(
        ChannelHandlerContext ctx,
        io.micronaut.http.HttpRequest<?> request,
        MutableHttpResponse<Object> defaultResponse) {
        Publisher<MutableHttpResponse<?>> notAllowedResponse = Publishers.just(defaultResponse);
        AtomicReference<io.micronaut.http.HttpRequest<?>> reference = new AtomicReference<>(request);
        notAllowedResponse = (Publisher<MutableHttpResponse<?>>) filterPublisher(request, reference, notAllowedResponse);
        notAllowedResponse.subscribe(new CompletionAwareSubscriber<MutableHttpResponse<?>>() {
            @Override
            protected void doOnSubscribe(Subscription subscription) {
                subscription.request(1);
            }

            @Override
            protected void doOnNext(MutableHttpResponse<?> message) {
                writeHttpResponse(
                    ctx,
                    (NettyHttpRequest) reference.get(),
                    (NettyHttpResponse) message,
                    MediaType.APPLICATION_VND_ERROR_TYPE);
            }

            @Override
            protected void doOnError(Throwable t) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Unexpected error occurred: " + t.getMessage(), t);
                }
                writeDefaultErrorResponse(ctx, (NettyHttpRequest) request, t);
            }

            @Override
            protected void doOnComplete() {

            }
        });
    }

    private void writeHttpContentChunkByChunk(
        ChannelHandlerContext context,
        NettyHttpRequest<?> request,
        FullHttpResponse nativeResponse,
        MediaType mediaType,
        Publisher<Object> publisher) {

        NettyByteBufferFactory byteBufferFactory = new NettyByteBufferFactory(context.alloc());
        boolean isJson = mediaType.getExtension().equals(MediaType.EXTENSION_JSON);

        Publisher<HttpContent> httpContentPublisher = Publishers.map(publisher, new Function<Object, HttpContent>() {
            boolean first = true;

            @Override
            public HttpContent apply(Object message) {
                HttpContent httpContent;
                if (message instanceof ByteBuf) {
                    httpContent = new DefaultHttpContent((ByteBuf) message);
                } else if (message instanceof ByteBuffer) {
                    ByteBuffer byteBuffer = (ByteBuffer) message;
                    Object nativeBuffer = byteBuffer.asNativeBuffer();
                    if (nativeBuffer instanceof ByteBuf) {
                        httpContent = new DefaultHttpContent((ByteBuf) nativeBuffer);
                    } else {
                        httpContent = new DefaultHttpContent(Unpooled.copiedBuffer(byteBuffer.asNioBuffer()));
                    }
                } else if (message instanceof byte[]) {
                    httpContent = new DefaultHttpContent(Unpooled.copiedBuffer((byte[]) message));
                } else if (message instanceof HttpContent) {
                    httpContent = (HttpContent) message;
                } else {

                    MediaTypeCodec codec = mediaTypeCodecRegistry.findCodec(mediaType, message.getClass()).orElse(
                        new TextPlainCodec(serverConfiguration.getDefaultCharset()));

                    ByteBuffer encoded = codec.encode(message, byteBufferFactory);
                    httpContent = new DefaultHttpContent((ByteBuf) encoded.asNativeBuffer());
                }
                if (!isJson || first) {
                    first = false;
                    return httpContent;
                } else {
                    return HttpContentUtil.prefixComma(httpContent);
                }
            }
        });

        if (isJson && !Publishers.isSingle(publisher.getClass())) {
            // if the Publisher is returning JSON then in order for it to be valid JSON for each emitted element
            // we must wrap the JSON in array and delimit the emitted items
            httpContentPublisher = Flowable.concat(
                Flowable.fromCallable(HttpContentUtil::openBracket),
                httpContentPublisher,
                Flowable.fromCallable(HttpContentUtil::closeBracket)
            );
        }

        if (mediaType.equals(MediaType.TEXT_EVENT_STREAM_TYPE)) {
            httpContentPublisher = Publishers.onComplete(httpContentPublisher, () -> {
                CompletableFuture<Void> future = new CompletableFuture<>();
                if (request == null || !request.getHeaders().isKeepAlive()) {
                    if (context.channel().isOpen()) {
                        context.pipeline()
                            .writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT))
                            .addListener(f -> {
                                    if (f.isSuccess()) {
                                        future.complete(null);
                                    } else {
                                        future.completeExceptionally(f.cause());
                                    }
                                }
                            );
                    }
                }
                return future;
            });
        }

        DelegateStreamedHttpResponse streamedResponse = new DelegateStreamedHttpResponse(nativeResponse, httpContentPublisher);
        HttpHeaders headers = streamedResponse.headers();
        headers.add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        headers.add(HttpHeaderNames.CONTENT_TYPE, mediaType);
        context.writeAndFlush(streamedResponse)
            .addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    ChannelPipeline pipeline = context.pipeline();
                    HandlerPublisher handlerPublisher = pipeline.get(HandlerPublisher.class);
                    if (handlerPublisher != null) {
                        pipeline.remove(handlerPublisher);
                    }
                    pipeline.fireExceptionCaught(future.cause());
                } else if (!request.getHeaders().isKeepAlive()) {
                    future.channel().close();
                }
            });
    }

    private void writeSingleMessage(
        ChannelHandlerContext context,
        io.micronaut.http.HttpRequest<?> request,
        FullHttpResponse nativeResponse,
        Object message,
        MediaTypeCodec codec,
        MediaType mediaType) {
        if (message == null) {
            throw new IllegalStateException("Response publisher emitted null result");
        } else {
            FullHttpResponse newResponse;
            if (!(message instanceof io.micronaut.http.HttpResponse)) {
                newResponse = encodeFullResponseBody(context, nativeResponse, message, codec, mediaType);
            } else {
                newResponse = nativeResponse;
            }
            writeNettyResponseAndCloseChannel(context, request, newResponse);
        }
    }

    private FullHttpResponse encodeFullResponseBody(ChannelHandlerContext context, FullHttpResponse nativeResponse, Object message, MediaTypeCodec codec, MediaType mediaType) {
        ByteBuf byteBuf;
        if (message instanceof ByteBuf) {
            byteBuf = (ByteBuf) message;
        } else if (message instanceof ByteBuffer) {
            ByteBuffer byteBuffer = (ByteBuffer) message;
            Object nativeBuffer = byteBuffer.asNativeBuffer();
            if (nativeBuffer instanceof ByteBuf) {
                byteBuf = (ByteBuf) nativeBuffer;
            } else {
                byteBuf = Unpooled.copiedBuffer(byteBuffer.asNioBuffer());
            }
        } else if (message instanceof byte[]) {
            byteBuf = Unpooled.copiedBuffer((byte[]) message);
        } else {
            byteBuf = (ByteBuf) codec.encode(message, new NettyByteBufferFactory(context.alloc())).asNativeBuffer();
        }
        int len = byteBuf.readableBytes();
        FullHttpResponse newResponse = nativeResponse.replace(byteBuf);
        HttpHeaders headers = newResponse.headers();
        headers.add(HttpHeaderNames.CONTENT_TYPE, mediaType);
        headers.add(HttpHeaderNames.CONTENT_LENGTH, len);
        return newResponse;
    }

    private void writeNettyResponseAndCloseChannel(
        ChannelHandlerContext context,
        io.micronaut.http.HttpRequest<?> request,
        io.netty.handler.codec.http.HttpResponse nettyResponse) {

        context.writeAndFlush(nettyResponse)
            .addListener(new DefaultCloseHandler(context, request, nettyResponse.status().code()));
    }
}
