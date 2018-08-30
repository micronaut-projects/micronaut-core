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
import io.micronaut.core.io.Writable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.StreamUtils;
import io.micronaut.http.*;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
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
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.http.netty.content.HttpContentUtil;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.http.server.netty.async.ContextCompletionAwareSubscriber;
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
import io.micronaut.web.router.*;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import io.micronaut.web.router.qualifier.ConsumesMediaTypeQualifier;
import io.micronaut.web.router.resource.StaticResourceResolver;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.*;
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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
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
    private static final Pattern IGNORABLE_ERROR_MESSAGE = Pattern.compile(
            "^.*(?:connection.*(?:reset|closed|abort|broken)|broken.*pipe).*$", Pattern.CASE_INSENSITIVE);

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
     * @param requestArgumentSatisfier                The Request argument satisfier
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
        RequestArgumentSatisfier requestArgumentSatisfier,
        ExecutorSelector executorSelector,
        ExecutorService ioExecutor) {

        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.customizableResponseTypeHandlerRegistry = customizableResponseTypeHandlerRegistry;
        this.beanLocator = beanLocator;
        this.staticResourceResolver = staticResourceResolver;
        this.ioExecutor = ioExecutor;
        this.executorSelector = executorSelector;
        this.router = router;
        this.requestArgumentSatisfier = requestArgumentSatisfier;
        this.serverConfiguration = serverConfiguration;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        if (ctx.channel().isWritable()) {
            ctx.flush();
        }
        NettyHttpRequest request = NettyHttpRequest.remove(ctx);
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

    @SuppressWarnings("unchecked")
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        NettyHttpRequest nettyHttpRequest = NettyHttpRequest.remove(ctx);
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
            logException(cause);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Found matching exception handler for exception [{}]: {}", cause.getMessage(), errorRoute);
            }
            errorRoute = requestArgumentSatisfier.fulfillArgumentRequirements(errorRoute, nettyHttpRequest, false);
            MediaType defaultResponseMediaType = errorRoute.getProduces().stream().findFirst().orElse(MediaType.APPLICATION_JSON_TYPE);
            try {
                Object result = errorRoute.execute();
                io.micronaut.http.MutableHttpResponse<?> response = errorResultToResponse(result);
                MethodBasedRouteMatch<?> methodBasedRoute = (MethodBasedRouteMatch) errorRoute;
                AtomicReference<HttpRequest<?>> requestReference = new AtomicReference<>(nettyHttpRequest);
                Flowable<MutableHttpResponse<?>> routePublisher = buildRoutePublisher(
                        methodBasedRoute.getDeclaringType(),
                        methodBasedRoute.getReturnType().getType(),
                        requestReference,
                        Flowable.just(response));

                Flowable<? extends MutableHttpResponse<?>> filteredPublisher = filterPublisher(
                        requestReference,
                        routePublisher,
                        ctx.channel().eventLoop());

                subscribeToResponsePublisher(
                        ctx,
                        defaultResponseMediaType,
                        requestReference,
                        filteredPublisher
                );

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
                    AtomicReference<HttpRequest<?>> requestReference = new AtomicReference<>(nettyHttpRequest);
                    io.micronaut.http.MutableHttpResponse response = errorResultToResponse(result);
                    Flowable<MutableHttpResponse<?>> routePublisher = buildRoutePublisher(
                            handler.getClass(),
                            result != null ? result.getClass() : HttpResponse.class,
                            requestReference,
                            Flowable.just(response));

                    Flowable<? extends MutableHttpResponse<?>> filteredPublisher = filterPublisher(
                            requestReference,
                            routePublisher,
                            ctx.channel().eventLoop());

                    subscribeToResponsePublisher(
                            ctx,
                            defaultResponseMediaType,
                            requestReference,
                            filteredPublisher
                    );
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

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, io.micronaut.http.HttpRequest<?> request) {
        ctx.channel().config().setAutoRead(false);
        io.micronaut.http.HttpMethod httpMethod = request.getMethod();
        String requestPath = request.getPath();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Matching route {} - {}", httpMethod, requestPath);
        }

        NettyHttpRequest nettyHttpRequest = (NettyHttpRequest) request;
        io.netty.handler.codec.http.HttpRequest nativeRequest = nettyHttpRequest.getNativeRequest();
        // handle decoding failure
        DecoderResult decoderResult = nativeRequest.decoderResult();
        if (decoderResult.isFailure()) {
            Throwable cause = decoderResult.cause();
            HttpStatus status = cause instanceof TooLongFrameException ? HttpStatus.REQUEST_ENTITY_TOO_LARGE : HttpStatus.BAD_REQUEST;
            handleStatusError(
                    ctx,
                    request,
                    nettyHttpRequest,
                    HttpResponse.status(status),
                    status.getReason()
            );
            return;
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

                handleStatusError(
                        ctx,
                        request,
                        nettyHttpRequest,
                        HttpResponse.notAllowed(existingRoutes),
                        "Method [" + httpMethod + "] not allowed. Allowed methods: " + existingRoutes);
                return;
            } else {
                Optional<? extends FileCustomizableResponseType> optionalFile = matchFile(requestPath);

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

            handleStatusError(
                    ctx,
                    request,
                    nettyHttpRequest,
                    HttpResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
                    "Unsupported Media Type: " + contentType);
            return;
        }
        if (LOG.isDebugEnabled()) {
            if (route instanceof MethodBasedRouteMatch) {
                LOG.debug("Matched route {} - {} to controller {}", httpMethod, requestPath, route.getDeclaringType());
            } else {
                LOG.debug("Matched route {} - {}", httpMethod, requestPath);
            }
        }
        // all ok proceed to try and execute the route
        handleRouteMatch(route, nettyHttpRequest, ctx);
    }

    private void handleStatusError(
            ChannelHandlerContext ctx,
            HttpRequest<?> request,
            NettyHttpRequest nettyHttpRequest,
            MutableHttpResponse<Object> defaultResponse,
            String message) {
        Optional<RouteMatch<Object>> statusRoute = router.route(defaultResponse.status());
        if (statusRoute.isPresent()) {
            RouteMatch<Object> routeMatch = statusRoute.get();
            handleRouteMatch(routeMatch, nettyHttpRequest, ctx);
        } else {

            if (HttpMethod.permitsRequestBody(request.getMethod())) {
                JsonError error = newError(request, message);
                defaultResponse.body(error);
            }


            AtomicReference<HttpRequest<?>> requestReference = new AtomicReference<>(request);
            Flowable<? extends MutableHttpResponse<?>> responsePublisher = filterPublisher(
                    requestReference,
                    Flowable.just(defaultResponse),
                    ctx.channel().eventLoop()
            );
            subscribeToResponsePublisher(
                    ctx,
                    MediaType.APPLICATION_JSON_TYPE,
                    requestReference,
                    responsePublisher
            );
        }
    }

    private Optional<? extends FileCustomizableResponseType> matchFile(String path) {
        Optional<URL> optionalUrl = staticResourceResolver.resolve(path);

        if (optionalUrl.isPresent()) {
            try {
                URL url = optionalUrl.get();
                if (url.getProtocol().equals("file")) {
                    File file = Paths.get(url.toURI()).toFile();
                    if (file.exists() && !file.isDirectory() && file.canRead()) {
                        return Optional.of(new NettySystemFileCustomizableResponseType(file));
                    }
                }

                return Optional.of(new NettyStreamedFileCustomizableResponseType(url));
            } catch (URISyntaxException e) {
                //no-op
            }
        }

        return Optional.empty();
    }

    private void emitDefaultNotFoundResponse(ChannelHandlerContext ctx, io.micronaut.http.HttpRequest<?> request) {
        MutableHttpResponse<Object> res = newNotFoundError(request);
        AtomicReference<HttpRequest<?>> requestReference = new AtomicReference<>(request);
        Flowable<? extends MutableHttpResponse<?>> responsePublisher = filterPublisher(
                requestReference,
                Flowable.just(res),
                ctx.channel().eventLoop()
        );
        subscribeToResponsePublisher(
                ctx,
                MediaType.APPLICATION_JSON_TYPE,
                requestReference,
                responsePublisher
        );
    }

    private MutableHttpResponse<Object> newNotFoundError(HttpRequest<?> request) {
        JsonError error = newError(request, "Page Not Found");
        return HttpResponse.notFound()
                .body(error);
    }

    private JsonError newError(io.micronaut.http.HttpRequest<?> request, String message) {
        URI uri = request.getUri();
        return new JsonError(message)
                .link(Link.SELF, Link.of(uri));
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
            ConversionService conversionService = ConversionService.SHARED;

            Subscription s;

            @Override
            protected void doOnSubscribe(Subscription subscription) {
                this.s = subscription;
                subscription.request(1);
            }

            @Override
            protected void doOnNext(Object message) {

                boolean executed = this.executed.get();
                if (message instanceof ByteBufHolder) {
                    if (message instanceof HttpData) {
                        HttpData data = (HttpData) message;

                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Received HTTP Data for request [{}]: {}", request, message);
                        }

                        String name = data.getName();
                        Optional<Argument<?>> requiredInput = routeMatch.getRequiredInput(name);

                        if (requiredInput.isPresent()) {
                            Argument<?> argument = requiredInput.get();
                            Supplier<Object> value;

                            if (Publishers.isConvertibleToPublisher(argument.getType())) {
                                Integer dataKey = System.identityHashCode(data);
                                Argument typeVariable = argument.getFirstTypeVariable().orElse(argument);
                                Class typeVariableType = typeVariable.getType();

                                ReplaySubject namedSubject = subjects.computeIfAbsent(name, (key) -> ReplaySubject.create());

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
                                value = () -> {
                                    if (data.refCnt() > 0) {
                                        return data;
                                    } else {
                                        return null;
                                    }
                                };
                            }

                            if (!executed) {

                                String argumentName = argument.getName();
                                if (!routeMatch.isSatisfied(argumentName)) {
                                    routeMatch = routeMatch.fulfill(Collections.singletonMap(argumentName, value.get()));
                                }
                            }

                        } else {
                            request.addContent(data);
                            s.request(1);
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
                    if ((routeMatch.isExecutable() && subjects.isEmpty() && childSubjects.isEmpty()) || message instanceof LastHttpContent) {
                        // we have enough data to satisfy the route, continue
                        executeRoute();
                    } else {
                        s.request(1);
                    }
                }
            }

            @Override
            protected void doOnError(Throwable t) {
                try {
                    s.cancel();
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


            ReturnType<?> genericReturnType = finalRoute.getReturnType();
            Class<?> javaReturnType = genericReturnType.getType();

            AtomicReference<io.micronaut.http.HttpRequest<?>> requestReference = new AtomicReference<>(request);
            boolean isFuture = CompletableFuture.class.isAssignableFrom(javaReturnType);
            boolean isReactiveReturnType = Publishers.isConvertibleToPublisher(javaReturnType) || isFuture;
            boolean isSingle =
                    isReactiveReturnType && Publishers.isSingle(javaReturnType) ||
                            isResponsePublisher(genericReturnType, javaReturnType) ||
                                isFuture ||
                                    finalRoute.getAnnotationMetadata().getValue(Produces.class, "single", Boolean.class).orElse(false);

            // build the result emitter. This result emitter emits the response from a controller action
            Flowable<?> resultEmitter = buildResultEmitter(
                    context,
                    finalRoute,
                    requestReference,
                    isReactiveReturnType,
                    isSingle
            );


            // here we transform the result of the controller action into a MutableHttpResponse
            Flowable<MutableHttpResponse<?>> routePublisher = resultEmitter.map((message) -> {
                HttpResponse<?> response = messageToResponse(finalRoute, message);
                MutableHttpResponse<?> finalResponse = (MutableHttpResponse<?>) response;
                HttpStatus status = finalResponse.getStatus();
                if (status.getCode() >= HttpStatus.BAD_REQUEST.getCode()) {
                    Class declaringType = ((MethodBasedRouteMatch) finalRoute).getDeclaringType();
                    // handle re-mapping of errors
                    Optional<RouteMatch<Object>> statusRoute = Optional.empty();
                    // if declaringType is not null, this means its a locally marked method handler
                    if (declaringType != null) {
                        statusRoute = router.route(declaringType, status);
                    }
                    if (!statusRoute.isPresent()) {
                        statusRoute = router.route(status);
                    }
                    io.micronaut.http.HttpRequest<?> httpRequest = requestReference.get();

                    if (statusRoute.isPresent()) {
                        RouteMatch<Object> newRoute = statusRoute.get();
                        requestArgumentSatisfier.fulfillArgumentRequirements(newRoute, httpRequest, true);

                        if (newRoute.isExecutable()) {
                            Object result;
                            try {
                                result = newRoute.execute();
                                finalResponse = messageToResponse(newRoute, result);
                            } catch (Throwable e) {
                                throw new InternalServerException("Error executing status route [" + newRoute + "]: " + e.getMessage(), e);
                            }
                        }
                    }

                }
                return finalResponse;
            });

            routePublisher = buildRoutePublisher(
                    finalRoute.getDeclaringType(),
                    javaReturnType,
                    requestReference,
                    routePublisher
            );

            // process the publisher through the available filters
            Flowable<? extends MutableHttpResponse<?>> filteredPublisher = filterPublisher(
                    requestReference,
                    routePublisher,
                    executor
            );



            boolean isStreaming = isReactiveReturnType && !isSingle;

            filteredPublisher  = filteredPublisher.switchMap((response) -> {
                Optional<?> responseBody = response.getBody();
                if (responseBody.isPresent()) {
                    Object body = responseBody.get();
                    if (isStreaming) {
                        // handled downstream
                        return Flowable.just(response);
                    } else if (Publishers.isConvertibleToPublisher(body)) {
                        Flowable<?> bodyFlowable = Publishers.convertPublisher(body, Flowable.class);
                        Flowable<MutableHttpResponse<?>> bodyToResponse = bodyFlowable.map((bodyContent) ->
                                setBodyContent(response, bodyContent)
                        );
                        return bodyToResponse.switchIfEmpty(Flowable.just(response));
                    }
                }

                return Flowable.just(response);
            });

            if (!isStreaming) {
                subscribeToResponsePublisher(context, defaultResponseMediaType, requestReference, filteredPublisher);
            } else {
                filteredPublisher.subscribe(new ContextCompletionAwareSubscriber<MutableHttpResponse<?>>(context) {
                    @Override
                    protected void onComplete(MutableHttpResponse<?> response) {
                        Optional<?> responseBody = response.getBody();
                        @SuppressWarnings("unchecked")
                        Flowable<Object> bodyFlowable = responseBody.map(o -> Publishers.convertPublisher(o, Flowable.class)).orElse(Flowable.empty());

                        NettyMutableHttpResponse nettyHttpResponse = (NettyMutableHttpResponse) response;
                        FullHttpResponse nettyResponse = nettyHttpResponse.getNativeResponse();
                        Optional<MediaType> specifiedMediaType = response.getContentType();
                        MediaType responseMediaType = specifiedMediaType.orElse(defaultResponseMediaType);

                        applyConfiguredHeaders(response.getHeaders());

                        streamHttpContentChunkByChunk(
                                context,
                                request,
                                nettyResponse,
                                responseMediaType,
                                bodyFlowable);
                    }
                });
            }

            return null;
        });
        return route;
    }

    private Flowable<MutableHttpResponse<?>> buildRoutePublisher(
            Class<?> declaringType,
            Class<?> javaReturnType,
            AtomicReference<HttpRequest<?>> requestReference,
            Flowable<MutableHttpResponse<?>> routePublisher) {
        // In the case of an empty reactive type we switch handling so that
        // a 404 NOT_FOUND is returned
        routePublisher = routePublisher.switchIfEmpty(Flowable.create((emitter) -> {
            HttpRequest<?> httpRequest = requestReference.get();
            MutableHttpResponse<?> response;
            if (javaReturnType != void.class) {

                // handle re-mapping of errors
                Optional<RouteMatch<Object>> statusRoute = Optional.empty();
                // if declaringType is not null, this means its a locally marked method handler
                if (declaringType != null) {
                    statusRoute = router.route(declaringType, HttpStatus.NOT_FOUND);
                }
                if (!statusRoute.isPresent()) {
                    statusRoute = router.route(HttpStatus.NOT_FOUND);
                }

                if (statusRoute.isPresent()) {
                    RouteMatch<Object> newRoute = statusRoute.get();
                    requestArgumentSatisfier.fulfillArgumentRequirements(newRoute, httpRequest, true);

                    if (newRoute.isExecutable()) {
                        try {
                            Object result = newRoute.execute();
                            response = messageToResponse(newRoute, result);
                        } catch (Throwable e) {
                            emitter.onError(new InternalServerException("Error executing status route [" + newRoute + "]: " + e.getMessage(), e));
                            return;
                        }

                    } else {
                        response = newNotFoundError(httpRequest);
                    }
                } else {
                    response = newNotFoundError(httpRequest);
                }
            } else {
                // void return type with no response, nothing else to do
                response = HttpResponse.ok();
            }
            try {
                emitter.onNext(response);
                emitter.onComplete();
            } catch (Throwable e) {
                emitter.onError(new InternalServerException("Error executing Error route [" + response.getStatus() + "]: " + e.getMessage(), e));
            }
        }, BackpressureStrategy.ERROR));
        return routePublisher;
    }

    private void subscribeToResponsePublisher(
            ChannelHandlerContext context,
            MediaType defaultResponseMediaType,
            AtomicReference<HttpRequest<?>> requestReference,
            Flowable<? extends MutableHttpResponse<?>> finalPublisher) {
        finalPublisher =  finalPublisher.map((response) -> {
            Optional<MediaType> specifiedMediaType = response.getContentType();
            MediaType responseMediaType = specifiedMediaType.orElse(defaultResponseMediaType);

            applyConfiguredHeaders(response.getHeaders());

            Optional<?> responseBody = response.getBody();
            if (responseBody.isPresent()) {

                Object body = responseBody.get();

                Optional<NettyCustomizableResponseTypeHandler> typeHandler = customizableResponseTypeHandlerRegistry
                        .findTypeHandler(body.getClass());
                if (typeHandler.isPresent()) {
                    NettyCustomizableResponseTypeHandler th = typeHandler.get();
                    setBodyContent(response, new NettyCustomizableResponseTypeHandlerInvoker(th, body));
                    return response;
                }

                if (specifiedMediaType.isPresent())  {

                    Optional<MediaTypeCodec> registeredCodec = mediaTypeCodecRegistry.findCodec(responseMediaType, body.getClass());
                    if (registeredCodec.isPresent()) {
                        MediaTypeCodec codec = registeredCodec.get();
                        return encodeBodyWithCodec(response, body, codec, responseMediaType, context, requestReference);
                    }
                }

                Optional<MediaTypeCodec> registeredCodec = mediaTypeCodecRegistry.findCodec(defaultResponseMediaType, body.getClass());
                if (registeredCodec.isPresent()) {
                    MediaTypeCodec codec = registeredCodec.get();
                    return encodeBodyWithCodec(response, body, codec, responseMediaType, context, requestReference);
                }

                MediaTypeCodec defaultCodec = new TextPlainCodec(serverConfiguration.getDefaultCharset());

                return encodeBodyWithCodec(response, body, defaultCodec, responseMediaType,  context, requestReference);
            } else {
                return response;
            }
        });

        finalPublisher.subscribe(new ContextCompletionAwareSubscriber<MutableHttpResponse<?>>(context) {
            @Override
            protected void onComplete(MutableHttpResponse<?> message) {
                writeFinalNettyResponse(message, requestReference, context);
            }

            @Override
            protected void doOnError(Throwable t) {
                super.doOnError(t);
            }
        });
    }

    private void writeFinalNettyResponse(MutableHttpResponse<?> message, AtomicReference<HttpRequest<?>> requestReference, ChannelHandlerContext context) {
        NettyMutableHttpResponse nettyHttpResponse = (NettyMutableHttpResponse) message;
        FullHttpResponse nettyResponse = nettyHttpResponse.getNativeResponse();

        HttpRequest<?> httpRequest = requestReference.get();
        io.netty.handler.codec.http.HttpHeaders nettyHeaders = nettyResponse.headers();

        // default Connection header if not set explicitly
        if (!nettyHeaders.contains(HttpHeaderNames.CONNECTION)) {
            HttpStatus status = nettyHttpResponse.status();
            if (status.getCode() > 299 || !httpRequest.getHeaders().isKeepAlive()) {
                nettyHeaders.add(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            } else {
                nettyHeaders.add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
        }

        // default to Transfer-Encoding: chunked if Content-Length not set or not already set
        if (!nettyHeaders.contains(HttpHeaderNames.CONTENT_LENGTH) && !nettyHeaders.contains(HttpHeaderNames.TRANSFER_ENCODING)) {
            nettyHeaders.add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        }

        Optional<NettyCustomizableResponseTypeHandlerInvoker> customizableTypeBody = message.getBody(NettyCustomizableResponseTypeHandlerInvoker.class);
        if (customizableTypeBody.isPresent()) {
            NettyCustomizableResponseTypeHandlerInvoker handler = customizableTypeBody.get();
            handler.invoke(httpRequest, nettyHttpResponse, context);
        } else {
            // close handled by HttpServerKeepAliveHandler
            context.writeAndFlush(nettyResponse);
            context.read();
        }
    }

    private MutableHttpResponse<?> encodeBodyWithCodec(MutableHttpResponse<?> response,
                                                       Object body,
                                                       MediaTypeCodec codec,
                                                       MediaType mediaType,
                                                       ChannelHandlerContext context,
                                                       AtomicReference<HttpRequest<?>> requestReference) {
        ByteBuf byteBuf = encodeBodyAsByteBuf(body, codec, context, requestReference);
        int len = byteBuf.readableBytes();
        MutableHttpHeaders headers = response.getHeaders();
        if (!headers.contains(HttpHeaders.CONTENT_TYPE)) {
            headers.add(HttpHeaderNames.CONTENT_TYPE, mediaType);
        }
        headers.remove(HttpHeaders.CONTENT_LENGTH);
        headers.add(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(len));

        setBodyContent(response, byteBuf);
        return response;
    }

    private MutableHttpResponse<?> setBodyContent(MutableHttpResponse response, Object bodyContent) {
        @SuppressWarnings("unchecked")
        MutableHttpResponse<?> res = response.body(bodyContent);
        return res;
    }

    private ByteBuf encodeBodyAsByteBuf(Object body, MediaTypeCodec codec, ChannelHandlerContext context, AtomicReference<HttpRequest<?>> requestReference) {
        ByteBuf byteBuf;
        if (body instanceof ByteBuf) {
            byteBuf = (ByteBuf) body;
        } else if (body instanceof ByteBuffer) {
            ByteBuffer byteBuffer = (ByteBuffer) body;
            Object nativeBuffer = byteBuffer.asNativeBuffer();
            if (nativeBuffer instanceof ByteBuf) {
                byteBuf = (ByteBuf) nativeBuffer;
            } else {
                byteBuf = Unpooled.copiedBuffer(byteBuffer.asNioBuffer());
            }
        } else if (body instanceof byte[]) {
            byteBuf = Unpooled.copiedBuffer((byte[]) body);

        } else if (body instanceof Writable) {
            byteBuf = context.alloc().ioBuffer(128);
            ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf);
            Writable writable = (Writable) body;
            try {
                writable.writeTo(outputStream, requestReference.get().getCharacterEncoding());
            } catch (IOException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error(e.getMessage());
                }
            }

        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Encoding emitted response object [{}] using codec: {}", body, codec);
            }
            byteBuf = (ByteBuf) codec.encode(body, new NettyByteBufferFactory(context.alloc())).asNativeBuffer();
        }
        return byteBuf;
    }

    // builds the result emitter for a given route action
    private Flowable<?> buildResultEmitter(
            ChannelHandlerContext context, RouteMatch<?> finalRoute,
            AtomicReference<HttpRequest<?>> requestReference,
            boolean isReactiveReturnType,
            boolean isSingleResult) {
        Flowable<?> resultEmitter;
        if (isReactiveReturnType) {
            // if the return type is reactive, execute the action and obtain the Observable
            RouteMatch<?> routeMatch = finalRoute;
            if (!routeMatch.isExecutable()) {
                routeMatch = requestArgumentSatisfier.fulfillArgumentRequirements(routeMatch, requestReference.get(), true);
            }
            try {
                if (isSingleResult) {
                    // for a single result we are fine as is
                    resultEmitter = Flowable.defer(() -> {
                        Object result = finalRoute.execute();
                        return Publishers.convertPublisher(result, Publisher.class);
                    });
                } else {
                    // for a streaming response we wrap the result on an HttpResponse so that a single result is received
                    // then the result can be streamed chunk by chunk
                    resultEmitter = Flowable.create((emitter) -> {
                        Object result = finalRoute.execute();
                        MutableHttpResponse<Object> chunkedResponse = HttpResponse.ok(result);
                        chunkedResponse.header(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                        emitter.onNext(chunkedResponse);
                        emitter.onComplete();
                        // should be no back pressure
                    }, BackpressureStrategy.ERROR);
                }
            } catch (Throwable e) {
                resultEmitter = Flowable.error(new InternalServerException("Error executing route [" + routeMatch + "]: " + e.getMessage(), e));
            }
        } else {
            // for non-reactive results we build flowable that executes the
            // route
            resultEmitter = Flowable.create((emitter) -> {
                HttpRequest<?> httpRequest = requestReference.get();
                RouteMatch<?> routeMatch = finalRoute;
                if (!routeMatch.isExecutable()) {
                    routeMatch = requestArgumentSatisfier.fulfillArgumentRequirements(routeMatch, httpRequest, true);
                }
                Object result;
                try {
                    result = routeMatch.execute();
                } catch (Throwable e) {
                    emitter.onError(e);
                    return;
                }

                if (result == null || (result instanceof Optional && !((Optional) result).isPresent())) {
                    // empty flowable
                    emitter.onComplete();
                } else {
                    // emit the result
                    if (result instanceof Writable) {
                        ByteBuf byteBuf = context.alloc().ioBuffer(128);
                        ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf);
                        Writable writable = (Writable) result;
                        writable.writeTo(outputStream, requestReference.get().getCharacterEncoding());
                        emitter.onNext(byteBuf);
                    } else {
                        emitter.onNext(result);
                    }
                    emitter.onComplete();
                }

                // should be no back pressure
            }, BackpressureStrategy.ERROR);
        }
        return resultEmitter;
    }

    private MutableHttpResponse<?> messageToResponse(RouteMatch<?> finalRoute, Object message) {
        MutableHttpResponse<?> response;
        if (message instanceof HttpResponse) {
            response = ConversionService.SHARED.convert(message, NettyMutableHttpResponse.class)
                    .orElseThrow(() -> new InternalServerException("Emitted response is not mutable"));
        } else {
            if (message instanceof HttpStatus) {
                response = HttpResponse.status((HttpStatus) message);
            } else {
                HttpStatus status = HttpStatus.OK;

                if (finalRoute instanceof MethodBasedRouteMatch) {
                    final MethodBasedRouteMatch rm = (MethodBasedRouteMatch) finalRoute;
                    if (rm.hasAnnotation(Status.class)) {
                        status = rm.getValue(Status.class, HttpStatus.class).orElse(null);
                    }
                }

                if (status != null) {
                    response = HttpResponse.status(status).body(message);
                } else {
                    response = HttpResponse.ok(message);
                }
            }
        }
        return response;
    }

    private boolean isResponsePublisher(ReturnType<?> genericReturnType, Class<?> javaReturnType) {
        return Publishers.isConvertibleToPublisher(javaReturnType) && genericReturnType.getFirstTypeVariable().map(arg -> HttpResponse.class.isAssignableFrom(arg.getType())).orElse(false);
    }

    private Flowable<? extends MutableHttpResponse<?>> filterPublisher(
            AtomicReference<HttpRequest<?>> requestReference,
            Publisher<MutableHttpResponse<?>> routePublisher, ExecutorService executor) {
        Publisher<? extends io.micronaut.http.MutableHttpResponse<?>> finalPublisher;
        List<HttpFilter> filters = new ArrayList<>(router.findFilters(requestReference.get()));
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
            Publisher<? extends HttpResponse<?>> resultingPublisher = httpFilter.doFilter(requestReference.get(), filterChain);
            finalPublisher = (Publisher<? extends MutableHttpResponse<?>>) resultingPublisher;
        } else {
            finalPublisher = routePublisher;
        }

        // Handle the scheduler to subscribe on
        if (finalPublisher instanceof Flowable) {
            return ((Flowable<MutableHttpResponse<?>>) finalPublisher)
                    .subscribeOn(Schedulers.from(executor));
        } else {
            return Flowable.fromPublisher(finalPublisher)
                    .subscribeOn(Schedulers.from(executor));
        }
    }

    private void streamHttpContentChunkByChunk(
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

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Encoding emitted response object [{}] using codec: {}", message, codec);
                    }
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
                            .writeAndFlush(new DefaultLastHttpContent())
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

        httpContentPublisher = Publishers.then(httpContentPublisher, httpContent -> {
            // once an http content is written, read the next item if it is available
            context.read();
        });

        DelegateStreamedHttpResponse streamedResponse = new DelegateStreamedHttpResponse(nativeResponse, httpContentPublisher);
        io.netty.handler.codec.http.HttpHeaders headers = streamedResponse.headers();
        headers.add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        headers.add(HttpHeaderNames.CONTENT_TYPE, mediaType);
        context.writeAndFlush(streamedResponse);
        context.read();
    }

    @SuppressWarnings("unchecked")
    private void writeDefaultErrorResponse(ChannelHandlerContext ctx, NettyHttpRequest nettyHttpRequest, Throwable cause) {
        logException(cause);

        MutableHttpResponse<?> error = io.micronaut.http.HttpResponse.serverError()
                .body(new JsonError("Internal Server Error: " + cause.getMessage()));
        subscribeToResponsePublisher(
                ctx,
                MediaType.APPLICATION_JSON_TYPE,
                new AtomicReference<>(nettyHttpRequest),
                Flowable.just(error)
        );
    }

    private void logException(Throwable cause) {
        //handling connection reset by peer exceptions
        if (cause instanceof IOException && IGNORABLE_ERROR_MESSAGE.matcher(cause.getMessage()).matches()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Swallowed an IOException caused by client connectivity: " + cause.getMessage(), cause);
            }
        } else {
            if (LOG.isErrorEnabled()) {
                LOG.error("Unexpected error occurred: " + cause.getMessage(), cause);
            }
        }
    }

    private void applyConfiguredHeaders(MutableHttpHeaders headers) {
        if (serverConfiguration.isDateHeader() && !headers.contains("Date")) {
            headers.date(LocalDateTime.now());
        }
        serverConfiguration.getServerHeader().ifPresent((server) -> {
            if (!headers.contains("Server")) {
                headers.add("Server", server);
            }
        });
    }

    /**
     * Used as a handle to the {@link NettyCustomizableResponseTypeHandler}.
     */
    private static class NettyCustomizableResponseTypeHandlerInvoker {
        final NettyCustomizableResponseTypeHandler handler;
        final Object body;

        NettyCustomizableResponseTypeHandlerInvoker(NettyCustomizableResponseTypeHandler handler, Object body) {
            this.handler = handler;
            this.body = body;
        }

        @SuppressWarnings("unchecked")
        void invoke(HttpRequest<?> request, NettyMutableHttpResponse response, ChannelHandlerContext channelHandlerContext) {
            this.handler.handle(body, request, response, channelHandlerContext);
        }
    }
}
