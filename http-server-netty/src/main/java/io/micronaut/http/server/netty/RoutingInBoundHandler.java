/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.netty;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.micronaut.core.annotation.Nullable;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.context.BeanContext;
import io.micronaut.context.exceptions.BeanCreationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.Writable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.bind.binders.ContinuationArgumentBinder;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.netty.AbstractNettyHttpRequest;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.netty.stream.JsonSubscriber;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.http.server.netty.async.ContextCompletionAwareSubscriber;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.multipart.NettyPartData;
import io.micronaut.http.server.netty.multipart.NettyStreamingFileUpload;
import io.micronaut.http.server.netty.types.NettyCustomizableResponseTypeHandler;
import io.micronaut.http.server.netty.types.NettyCustomizableResponseTypeHandlerRegistry;
import io.micronaut.http.server.netty.types.files.NettyStreamedFileCustomizableResponseType;
import io.micronaut.http.server.netty.types.files.NettySystemFileCustomizableResponseType;
import io.micronaut.http.server.types.files.FileCustomizableResponseType;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.MethodReference;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.http.codec.TextPlainCodec;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.web.router.*;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import io.micronaut.web.router.resource.StaticResourceResolver;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.reactivex.*;
import io.reactivex.functions.LongConsumer;
import io.reactivex.processors.UnicastProcessor;
import io.reactivex.schedulers.Schedulers;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;

import static io.micronaut.core.util.KotlinUtils.isKotlinCoroutineSuspended;
import static io.micronaut.inject.util.KotlinExecutableMethodUtils.isKotlinFunctionReturnTypeUnit;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Internal implementation of the {@link io.netty.channel.ChannelInboundHandler} for Micronaut.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
@Sharable
@SuppressWarnings("FileLength")
class RoutingInBoundHandler extends SimpleChannelInboundHandler<io.micronaut.http.HttpRequest<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingInBoundHandler.class);
    private static final Pattern IGNORABLE_ERROR_MESSAGE = Pattern.compile(
            "^.*(?:connection.*(?:reset|closed|abort|broken)|broken.*pipe).*$", Pattern.CASE_INSENSITIVE);
    private static final Argument ARGUMENT_PART_DATA = Argument.of(PartData.class);
    private static final Object NOT_FOUND = new Object();

    private final Router router;
    private final ExecutorSelector executorSelector;
    private final StaticResourceResolver staticResourceResolver;
    private final BeanContext beanContext;
    private final NettyHttpServerConfiguration serverConfiguration;
    private final HttpContentProcessorResolver httpContentProcessorResolver;
    private final ErrorResponseProcessor<?> errorResponseProcessor;
    private final RequestArgumentSatisfier requestArgumentSatisfier;
    private final MediaTypeCodecRegistry mediaTypeCodecRegistry;
    private final NettyCustomizableResponseTypeHandlerRegistry customizableResponseTypeHandlerRegistry;
    private final Supplier<ExecutorService> ioExecutorSupplier;
    private final String serverHeader;
    private final boolean multipartEnabled;
    private ExecutorService ioExecutor;

    /**
     * @param beanContext                             The bean locator
     * @param router                                  The router
     * @param mediaTypeCodecRegistry                  The media type codec registry
     * @param customizableResponseTypeHandlerRegistry The customizable response type handler registry
     * @param staticResourceResolver                  The static resource resolver
     * @param serverConfiguration                     The Netty HTTP server configuration
     * @param requestArgumentSatisfier                The Request argument satisfier
     * @param executorSelector                        The executor selector
     * @param ioExecutor                              The IO executor
     * @param httpContentProcessorResolver            The http content processor resolver
     * @param errorResponseProcessor                  The factory to create error responses
     */
    RoutingInBoundHandler(
            BeanContext beanContext,
            Router router,
            MediaTypeCodecRegistry mediaTypeCodecRegistry,
            NettyCustomizableResponseTypeHandlerRegistry customizableResponseTypeHandlerRegistry,
            StaticResourceResolver staticResourceResolver,
            NettyHttpServerConfiguration serverConfiguration,
            RequestArgumentSatisfier requestArgumentSatisfier,
            ExecutorSelector executorSelector,
            Supplier<ExecutorService> ioExecutor,
            HttpContentProcessorResolver httpContentProcessorResolver,
            ErrorResponseProcessor<?> errorResponseProcessor) {
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.customizableResponseTypeHandlerRegistry = customizableResponseTypeHandlerRegistry;
        this.beanContext = beanContext;
        this.staticResourceResolver = staticResourceResolver;
        this.ioExecutorSupplier = ioExecutor;
        this.executorSelector = executorSelector;
        this.router = router;
        this.requestArgumentSatisfier = requestArgumentSatisfier;
        this.serverConfiguration = serverConfiguration;
        this.serverHeader = serverConfiguration.getServerHeader().orElse(null);
        this.httpContentProcessorResolver = httpContentProcessorResolver;
        this.errorResponseProcessor = errorResponseProcessor;
        Optional<Boolean> multipartEnabled = serverConfiguration.getMultipart().getEnabled();
        this.multipartEnabled = !multipartEnabled.isPresent() || multipartEnabled.get();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        cleanupIfNecessary(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        if (ctx.channel().isWritable()) {
            ctx.flush();
        }
        cleanupIfNecessary(ctx);
    }

    private void cleanupIfNecessary(ChannelHandlerContext ctx) {
        NettyHttpRequest.remove(ctx);
    }

    private void cleanupRequest(ChannelHandlerContext ctx, NettyHttpRequest request) {
        try {
            request.release();
        } finally {
            ctx.executor().execute(() -> {
                try {
                    beanContext.publishEvent(
                            new HttpRequestTerminatedEvent(
                                    request
                            )
                    );
                } catch (Exception e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error publishing request terminated event: " + e.getMessage(), e);
                    }
                }
            });
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
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        NettyHttpRequest nettyHttpRequest = NettyHttpRequest.remove(ctx);
        if (nettyHttpRequest == null) {
            if (cause instanceof SSLException || cause.getCause() instanceof SSLException || isIgnorable(cause)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Micronaut Server Error - No request state present. Cause: " + cause.getMessage(), cause);
                }
            } else {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Micronaut Server Error - No request state present. Cause: " + cause.getMessage(), cause);
                }
            }

            ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
            return;
        }

        exceptionCaughtInternal(ctx, cause, nettyHttpRequest, true);
    }

    private void exceptionCaughtInternal(ChannelHandlerContext ctx,
                                         Throwable t,
                                         NettyHttpRequest nettyHttpRequest,
                                         boolean skipOncePerRequest) {
        RouteMatch<?> errorRoute = null;
        // find the origination of of the route
        RouteMatch<?> originalRoute = nettyHttpRequest.getMatchedRoute();
        Class declaringType = null;
        if (originalRoute instanceof MethodExecutionHandle) {
            declaringType = ((MethodExecutionHandle) originalRoute).getDeclaringType();
        }

        final Throwable cause;
        // top level exceptions returned by CompletableFutures. These always wrap the real exception thrown.
        if ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            cause = t.getCause();
        } else {
            cause = t;
        }

        // when arguments do not match, then there is UnsatisfiedRouteException, we can handle this with a routed bad request
        if (cause instanceof UnsatisfiedRouteException) {
            if (declaringType != null) {
                // handle error with a method that is non global with bad request
                errorRoute = router.findStatusRoute(declaringType, HttpStatus.BAD_REQUEST, nettyHttpRequest).orElse(null);
            }
            if (errorRoute == null) {
                // handle error with a method that is global with bad request
                errorRoute = router.findStatusRoute(HttpStatus.BAD_REQUEST, nettyHttpRequest).orElse(null);
            }
        } else if (cause instanceof HttpStatusException) {
            HttpStatusException statusException = (HttpStatusException) cause;
            if (declaringType != null) {
                // handle error with a method that is non global with bad request
                errorRoute = router.findStatusRoute(declaringType, statusException.getStatus(), nettyHttpRequest).orElse(null);
            }
            if (errorRoute == null) {
                // handle error with a method that is global with bad request
                errorRoute = router.findStatusRoute(statusException.getStatus(), nettyHttpRequest).orElse(null);
            }
        } else if (cause instanceof BeanCreationException && declaringType != null) {
            // If the controller could not be instantiated, don't look for a local error route
            Optional<Class> rootBeanType = ((BeanCreationException) cause).getRootBeanType().map(BeanType::getBeanType);
            if (rootBeanType.isPresent() && declaringType == rootBeanType.get()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Failed to instantiate [{}]. Skipping lookup of a local error route", declaringType.getName());
                }
                declaringType = null;
            }
        }

        // any another other exception may arise. handle these with non global exception marked method or a global exception marked method.
        if (errorRoute == null) {
            if (declaringType != null) {
                errorRoute = router.findErrorRoute(declaringType, cause, nettyHttpRequest).orElse(null);
            }
            if (errorRoute == null) {
                errorRoute = router.findErrorRoute(cause, nettyHttpRequest).orElse(null);
            }
        }

        if (errorRoute != null) {

            if (LOG.isDebugEnabled()) {
                LOG.debug("Found matching exception handler for exception [{}]: {}", cause.getMessage(), errorRoute);
            }
            errorRoute = requestArgumentSatisfier.fulfillArgumentRequirements(errorRoute, nettyHttpRequest, false);
            try {
                executeRoute(
                        errorRoute,
                        nettyHttpRequest,
                        ctx,
                        ctx.executor(),
                        true,
                        skipOncePerRequest,
                        null
                );
            } catch (Throwable e) {
                writeDefaultErrorResponse(ctx, nettyHttpRequest, e, skipOncePerRequest);
            }
        } else {

            Optional<ExceptionHandler> exceptionHandler = beanContext
                    .findBean(ExceptionHandler.class, Qualifiers.byTypeArgumentsClosest(cause.getClass(), Object.class));

            if (exceptionHandler.isPresent()) {
                ExceptionHandler handler = exceptionHandler.get();
                try {
                    Flowable<MutableHttpResponse<?>> routePublisher = Flowable.fromCallable(() -> {
                        Object result = handler.handle(nettyHttpRequest, cause);
                        return errorResultToResponse(result);
                    });
                    filterPublisher(new AtomicReference<HttpRequest<?>>(nettyHttpRequest), routePublisher, skipOncePerRequest)
                            .subscribe(new CompletionAwareSubscriber<MutableHttpResponse<?>>() {

                                MutableHttpResponse<?> mutableHttpResponse;

                                @Override
                                public void doOnSubscribe(Subscription s) {
                                    s.request(1);
                                }

                                @Override
                                public void doOnNext(MutableHttpResponse<?> mutableHttpResponse) {
                                    this.mutableHttpResponse = mutableHttpResponse;
                                }

                                @Override
                                public void doOnError(Throwable throwable) {
                                    writeDefaultErrorResponse(ctx, nettyHttpRequest, throwable, skipOncePerRequest);
                                }

                                @Override
                                public void doOnComplete() {
                                    encodeHttpResponse(
                                            ctx,
                                            nettyHttpRequest,
                                            mutableHttpResponse,
                                            mutableHttpResponse.body(),
                                            () -> MediaType.fromType(handler.getClass()).orElse(MediaType.APPLICATION_JSON_TYPE)
                                    );
                                }
                            });


                    if (serverConfiguration.isLogHandledExceptions()) {
                        logException(cause);
                    }
                } catch (Throwable e) {
                    writeDefaultErrorResponse(ctx, nettyHttpRequest, e, skipOncePerRequest);
                }
            } else {
                if (isIgnorable(cause)) {
                    logIgnoredException(cause);
                    ctx.read();
                } else {
                    writeDefaultErrorResponse(
                            ctx,
                            nettyHttpRequest,
                            cause,
                            skipOncePerRequest);
                }
            }
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, io.micronaut.http.HttpRequest<?> request) {
        ctx.channel().config().setAutoRead(false);
        io.micronaut.http.HttpMethod httpMethod = request.getMethod();
        String requestPath = request.getUri().getPath();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Request {} {}", httpMethod, request.getUri());
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

        MediaType contentType = request.getContentType().orElse(null);
        final String requestMethodName = request.getMethodName();

        if (!multipartEnabled &&
                contentType != null &&
                contentType.equals(MediaType.MULTIPART_FORM_DATA_TYPE)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Multipart uploads have been disabled via configuration. Rejected request for URI {}, method {}, and content type {}", request.getUri(),
                        requestMethodName, contentType);
            }

            handleStatusError(
                    ctx,
                    request,
                    nettyHttpRequest,
                    HttpResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
                    "Content Type [" + contentType + "] not allowed");
            return;
        }

        UriRouteMatch<Object, Object> routeMatch = null;

        List<UriRouteMatch<Object, Object>> uriRoutes = router.findAllClosest(request);

        if (uriRoutes.size() > 1) {
            throw new DuplicateRouteException(requestPath, uriRoutes);
        } else if (uriRoutes.size() == 1) {
            UriRouteMatch<Object, Object> establishedRoute = uriRoutes.get(0);
            request.setAttribute(HttpAttributes.ROUTE, establishedRoute.getRoute());
            request.setAttribute(HttpAttributes.ROUTE_MATCH, establishedRoute);
            request.setAttribute(HttpAttributes.URI_TEMPLATE, establishedRoute.getRoute().getUriMatchTemplate().toString());
            routeMatch = establishedRoute;
        }

        RouteMatch<?> route;

        if (routeMatch == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No matching route: {} {}", httpMethod, request.getUri());
            }

            // if there is no route present try to locate a route that matches a different HTTP method
            final List<UriRouteMatch<?, ?>> anyMatchingRoutes = router
                    .findAny(request.getUri().toString(), request)
                    .collect(Collectors.toList());
            final Collection<MediaType> acceptedTypes = request.accept();
            final boolean hasAcceptHeader = CollectionUtils.isNotEmpty(acceptedTypes);

            Set<MediaType> acceptableContentTypes = contentType != null ? new HashSet<>(5) : null;
            Set<String> allowedMethods = new HashSet<>(5);
            Set<MediaType> produceableContentTypes = hasAcceptHeader ? new HashSet<>(5) : null;
            for (UriRouteMatch<?, ?> anyRoute : anyMatchingRoutes) {
                final String routeMethod = anyRoute.getRoute().getHttpMethodName();
                if (!requestMethodName.equals(routeMethod)) {
                    allowedMethods.add(routeMethod);
                }
                if (contentType != null && !anyRoute.doesConsume(contentType)) {
                    acceptableContentTypes.addAll(anyRoute.getRoute().getConsumes());
                }
                if (hasAcceptHeader && !anyRoute.doesProduce(acceptedTypes)) {
                    produceableContentTypes.addAll(anyRoute.getRoute().getProduces());
                }
            }

            if (CollectionUtils.isNotEmpty(acceptableContentTypes)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Content type not allowed for URI {}, method {}, and content type {}", request.getUri(),
                            requestMethodName, contentType);
                }

                handleStatusError(
                        ctx,
                        request,
                        nettyHttpRequest,
                        HttpResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
                        "Content Type [" + contentType + "] not allowed. Allowed types: " + acceptableContentTypes);
                return;
            }

            if (CollectionUtils.isNotEmpty(produceableContentTypes)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Content type not allowed for URI {}, method {}, and content type {}", request.getUri(),
                            requestMethodName, contentType);
                }

                handleStatusError(
                        ctx,
                        request,
                        nettyHttpRequest,
                        HttpResponse.status(HttpStatus.NOT_ACCEPTABLE),
                        "Specified Accept Types " + acceptedTypes + " not supported. Supported types: " + produceableContentTypes);
                return;
            }

            if (!allowedMethods.isEmpty()) {

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Method not allowed for URI {} and method {}", request.getUri(), requestMethodName);
                }

                handleStatusError(
                        ctx,
                        request,
                        nettyHttpRequest,
                        HttpResponse.notAllowedGeneric(allowedMethods),
                        "Method [" + requestMethodName + "] not allowed for URI [" + request.getUri() + "]. Allowed methods: " + allowedMethods);
                return;
            }

            Optional<? extends FileCustomizableResponseType> optionalFile = matchFile(requestPath);

            if (optionalFile.isPresent()) {
                route = new BasicObjectRouteMatch(optionalFile.get());
            } else {
                Optional<RouteMatch<Object>> statusRoute = router.findStatusRoute(HttpStatus.NOT_FOUND, request);
                if (statusRoute.isPresent()) {
                    route = statusRoute.get();
                } else {
                    emitDefaultNotFoundResponse(ctx, request, false);
                    return;
                }
            }

        } else {
            route = routeMatch;
        }

        if (LOG.isTraceEnabled()) {
            if (route instanceof MethodBasedRouteMatch) {
                LOG.trace("Matched route {} - {} to controller {}", requestMethodName, requestPath, route.getDeclaringType());
            } else {
                LOG.trace("Matched route {} - {}", requestMethodName, requestPath);
            }
        }
        // all ok proceed to try and execute the route
        if (route.isWebSocketRoute()) {
            handleStatusError(
                    ctx,
                    request,
                    nettyHttpRequest,
                    HttpResponse.status(HttpStatus.BAD_REQUEST),
                    "Not a WebSocket request");
        } else {
            handleRouteMatch(route, nettyHttpRequest, ctx, false);
        }
    }

    private void handleStatusError(
            ChannelHandlerContext ctx,
            HttpRequest<?> request,
            NettyHttpRequest nettyHttpRequest,
            MutableHttpResponse<?> defaultResponse,
            String message) {
        Optional<RouteMatch<Object>> statusRoute = router.findStatusRoute(defaultResponse.status(), request);
        if (statusRoute.isPresent()) {
            RouteMatch<Object> routeMatch = statusRoute.get();
            handleRouteMatch(routeMatch, nettyHttpRequest, ctx, false);
        } else {
            if (request.getMethod() != HttpMethod.HEAD) {
                defaultResponse = errorResponseProcessor.processResponse(ErrorContext.builder(request)
                        .errorMessage(message)
                        .build(), defaultResponse);
            }
            filterAndEncodeResponse(
                    ctx,
                    request,
                    nettyHttpRequest,
                    defaultResponse,
                    MediaType.APPLICATION_JSON_TYPE,
                    false
            );
        }
    }

    private void filterAndEncodeResponse(
            ChannelHandlerContext ctx,
            HttpRequest<?> request,
            NettyHttpRequest nettyHttpRequest,
            MutableHttpResponse<?> finalResponse,
            MediaType defaultResponseMediaType,
            boolean skipOncePerRequest) {
        AtomicReference<HttpRequest<?>> requestReference = new AtomicReference<>(request);
        filterPublisher(
                requestReference,
                Publishers.just(finalResponse),
                skipOncePerRequest
        ).subscribe(new CompletionAwareSubscriber<MutableHttpResponse<?>>() {

            MutableHttpResponse<?> mutableHttpResponse;

            @Override
            public void doOnSubscribe(Subscription s) {
                s.request(1);
            }

            @Override
            public void doOnNext(MutableHttpResponse<?> mutableHttpResponse) {
                this.mutableHttpResponse = mutableHttpResponse;
            }

            @Override
            public void doOnError(Throwable throwable) {
                exceptionCaughtInternal(ctx, throwable, nettyHttpRequest, false);
            }

            @Override
            public void doOnComplete() {
                encodeHttpResponse(
                        ctx,
                        nettyHttpRequest,
                        mutableHttpResponse,
                        mutableHttpResponse.body(),
                        () -> defaultResponseMediaType
                );
            }
        });
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

    private void emitDefaultNotFoundResponse(ChannelHandlerContext ctx, HttpRequest<?> request, boolean skipOncePerRequest) {
        MutableHttpResponse<?> res = newNotFoundError(request);
        filterAndEncodeResponse(
                ctx,
                request,
                (NettyHttpRequest) request,
                res,
                MediaType.APPLICATION_JSON_TYPE,
                skipOncePerRequest);
    }

    private MutableHttpResponse<?> newNotFoundError(HttpRequest<?> request) {
        return errorResponseProcessor.processResponse(
                ErrorContext.builder(request)
                        .errorMessage("Page Not Found")
                        .build(), HttpResponse.notFound());
    }

    private MutableHttpResponse errorResultToResponse(Object result) {
        MutableHttpResponse<?> response;
        if (result instanceof HttpResponse) {
            return toNettyResponse((HttpResponse<?>) result);
        } else {
            if (result instanceof HttpStatus) {
                response = HttpResponse.status((HttpStatus) result);
            } else {
                response = HttpResponse.serverError().body(result);
            }
        }
        return response;
    }

    private void handleRouteMatch(
            RouteMatch<?> route,
            NettyHttpRequest<?> request,
            ChannelHandlerContext context,
            boolean skipOncePerRequest) {
        // Set the matched route on the request
        request.setMatchedRoute(route);

        // try to fulfill the argument requirements of the route
        route = requestArgumentSatisfier.fulfillArgumentRequirements(route, request, false);

        // If it is not executable and the body is not required send back 400 - BAD REQUEST

        // decorate the execution of the route so that it runs an async executor
        request.setMatchedRoute(route);

        Optional<Argument<?>> bodyArgument = route.getBodyArgument()
            .filter(argument -> argument.getAnnotationMetadata().hasAnnotation(Body.class));

        // The request body is required, so at this point we must have a StreamedHttpRequest
        io.netty.handler.codec.http.HttpRequest nativeRequest = request.getNativeRequest();
        HttpContentProcessor<?> contentProcessor = null;
        if (!route.isExecutable() &&
                io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod()) &&
                nativeRequest instanceof StreamedHttpRequest &&
                (!bodyArgument.isPresent() || !route.isSatisfied(bodyArgument.get().getName()))) {
            contentProcessor = httpContentProcessorResolver.resolve(request, route);
        } else {
            context.read();
        }

        // Select the most appropriate Executor
        ExecutorService executor;
        if (route instanceof MethodReference) {
            executor = executorSelector.select((MethodReference) route, serverConfiguration.getThreadSelection()).orElse(null);
        } else {
            executor = null;
        }

        boolean isErrorRoute = false;
        executeRoute(
                route,
                request,
                context,
                executor,
                isErrorRoute,
                skipOncePerRequest,
                contentProcessor
        );
    }

    private boolean isJsonFormattable(Argument<?> argument) {
        Class<?> javaType = argument.getType();
        if (Publishers.isConvertibleToPublisher(javaType)) {
            javaType = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT).getType();
        }
        return !(javaType == byte[].class
                || ByteBuffer.class.isAssignableFrom(javaType)
                || ByteBuf.class.isAssignableFrom(javaType));
    }

    private Subscriber<Object> buildSubscriber(NettyHttpRequest<?> request,
                                               RouteMatch<?> finalRoute,
                                               SingleEmitter<RouteMatch<?>> emitter) {
        boolean isFormData = request.isFormOrMultipartData();
        if (isFormData) {
            return new CompletionAwareSubscriber<Object>() {
                final boolean alwaysAddContent = request.isFormData();
                RouteMatch<?> routeMatch = finalRoute;
                final AtomicBoolean executed = new AtomicBoolean(false);
                final AtomicLong pressureRequested = new AtomicLong(0);
                final ConcurrentHashMap<String, UnicastProcessor> subjects = new ConcurrentHashMap<>();
                final ConcurrentHashMap<Integer, HttpDataReference> dataReferences = new ConcurrentHashMap<>();
                final ConversionService conversionService = ConversionService.SHARED;
                Subscription s;
                final LongConsumer onRequest = num -> pressureRequested.updateAndGet(p -> {
                    long newVal = p - num;
                    if (newVal < 0) {
                        s.request(num - p);
                        return 0;
                    } else {
                        return newVal;
                    }
                });

                Flowable processFlowable(Flowable flowable, Integer dataKey, boolean controlsFlow) {
                    if (controlsFlow) {
                        flowable = flowable.doOnRequest(onRequest);
                    }
                    return flowable
                            .doAfterTerminate(() -> {
                                if (controlsFlow) {
                                    HttpDataReference dataReference = dataReferences.get(dataKey);
                                    dataReference.destroy();
                                }
                            });
                }

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
                                boolean isPublisher = Publishers.isConvertibleToPublisher(argument.getType());
                                boolean chunkedProcessing = false;

                                if (isPublisher) {
                                    Integer dataKey = System.identityHashCode(data);
                                    HttpDataReference dataReference = dataReferences.computeIfAbsent(dataKey, key -> new HttpDataReference(data));
                                    Argument typeVariable;

                                    if (StreamingFileUpload.class.isAssignableFrom(argument.getType())) {
                                        typeVariable = ARGUMENT_PART_DATA;
                                    } else {
                                        typeVariable = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                                    }
                                    Class typeVariableType = typeVariable.getType();

                                    UnicastProcessor namedSubject = subjects.computeIfAbsent(name, key -> UnicastProcessor.create());

                                    chunkedProcessing = PartData.class.equals(typeVariableType) ||
                                            Publishers.isConvertibleToPublisher(typeVariableType) ||
                                            ClassUtils.isJavaLangType(typeVariableType);

                                    if (Publishers.isConvertibleToPublisher(typeVariableType)) {
                                        boolean streamingFileUpload = StreamingFileUpload.class.isAssignableFrom(typeVariableType);
                                        if (streamingFileUpload) {
                                            typeVariable = ARGUMENT_PART_DATA;
                                        } else {
                                            typeVariable = typeVariable.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                                        }
                                        dataReference.subject.getAndUpdate(subject -> {
                                            if (subject == null) {
                                                UnicastProcessor childSubject = UnicastProcessor.create();
                                                Flowable flowable = processFlowable(childSubject, dataKey, true);
                                                if (streamingFileUpload && data instanceof FileUpload) {
                                                    namedSubject.onNext(new NettyStreamingFileUpload(
                                                            (FileUpload) data,
                                                            serverConfiguration.getMultipart(),
                                                            getIoExecutor(),
                                                            (Flowable<PartData>) flowable));
                                                } else {
                                                    namedSubject.onNext(flowable);
                                                }

                                                return childSubject;
                                            }
                                            return subject;
                                        });
                                    }

                                    UnicastProcessor subject;

                                    final UnicastProcessor ds = dataReference.subject.get();
                                    if (ds != null) {
                                        subject = ds;
                                    } else {
                                        subject = namedSubject;
                                    }

                                    Object part = data;

                                    if (chunkedProcessing) {
                                        HttpDataReference.Component component;
                                        try {
                                            component = dataReference.addComponent();
                                            if (component == null) {
                                                s.request(1);
                                                return;
                                            }
                                        } catch (IOException e) {
                                            subject.onError(e);
                                            s.cancel();
                                            return;
                                        }
                                        part = new NettyPartData(dataReference, component);
                                    }

                                    if (data instanceof FileUpload &&
                                            StreamingFileUpload.class.isAssignableFrom(argument.getType())) {
                                        dataReference.upload.getAndUpdate(upload -> {
                                            if (upload == null) {
                                                return new NettyStreamingFileUpload(
                                                        (FileUpload) data,
                                                        serverConfiguration.getMultipart(),
                                                        getIoExecutor(),
                                                        (Flowable<PartData>) processFlowable(subject, dataKey, true));
                                            }
                                            return upload;
                                        });
                                    }

                                    Optional<?> converted = conversionService.convert(part, typeVariable);

                                    converted.ifPresent(subject::onNext);

                                    if (data.isCompleted() && chunkedProcessing) {
                                        subject.onComplete();
                                    }

                                    value = () -> {
                                        StreamingFileUpload upload = dataReference.upload.get();
                                        if (upload != null) {
                                            return upload;
                                        } else {
                                            return processFlowable(namedSubject, dataKey, dataReference.subject.get() == null);
                                        }
                                    };

                                } else {
                                    if (data instanceof Attribute && !data.isCompleted()) {
                                        request.addContent(data);
                                        s.request(1);
                                        return;
                                    } else {
                                        value = () -> {
                                            if (data.refCnt() > 0) {
                                                return data;
                                            } else {
                                                return null;
                                            }
                                        };
                                    }
                                }

                                if (!executed) {
                                    String argumentName = argument.getName();
                                    if (!routeMatch.isSatisfied(argumentName)) {
                                        routeMatch = routeMatch.fulfill(Collections.singletonMap(argumentName, value.get()));
                                    }
                                    if (isPublisher && chunkedProcessing) {
                                        //accounting for the previous request
                                        pressureRequested.incrementAndGet();
                                    }
                                    if (routeMatch.isExecutable() || message instanceof LastHttpContent) {
                                        executeRoute();
                                        executed = true;
                                    }
                                }

                                if (alwaysAddContent) {
                                    request.addContent(data);
                                }

                                if (!executed || !chunkedProcessing) {
                                    s.request(1);
                                }

                            } else {
                                request.addContent(data);
                                s.request(1);
                            }
                        } else {
                            request.addContent((ByteBufHolder) message);
                            s.request(1);
                        }
                    } else {
                        ((NettyHttpRequest) request).setBody(message);
                        s.request(1);
                    }
                }

                @Override
                protected void doOnError(Throwable t) {
                    s.cancel();
                    emitter.onError(t);
                }

                @Override
                protected void doOnComplete() {
                    for (UnicastProcessor subject : subjects.values()) {
                        if (!subject.hasComplete()) {
                            subject.onComplete();
                        }
                    }
                    executeRoute();
                }

                private void executeRoute() {
                    if (executed.compareAndSet(false, true)) {
                        emitter.onSuccess(routeMatch);
                    }
                }
            };
        } else {
            return new CompletionAwareSubscriber<Object>() {
                private Subscription s;
                private RouteMatch<?> routeMatch = finalRoute;
                private AtomicBoolean executed = new AtomicBoolean(false);

                @Override
                protected void doOnSubscribe(Subscription subscription) {
                    this.s = subscription;
                    subscription.request(1);
                }

                @Override
                protected void doOnNext(Object message) {
                    if (message instanceof ByteBufHolder) {
                        request.addContent((ByteBufHolder) message);
                        s.request(1);
                    } else {
                        ((NettyHttpRequest) request).setBody(message);
                        s.request(1);
                    }
                }

                @Override
                protected void doOnError(Throwable t) {
                    s.cancel();
                    emitter.onError(t);
                }

                @Override
                protected void doOnComplete() {
                    if (executed.compareAndSet(false, true)) {
                        emitter.onSuccess(routeMatch);
                    }
                }
            };
        }

    }

    private ExecutorService getIoExecutor() {
        ExecutorService executor = this.ioExecutor;
        if (executor == null) {
            synchronized (this) { // double check
                executor = this.ioExecutor;
                if (executor == null) {
                    executor = this.ioExecutorSupplier.get();
                    this.ioExecutor = executor;
                }
            }
        }
        return executor;
    }

    private boolean isSingle(RouteMatch<?> finalRoute, Class<?> bodyClass) {
        return finalRoute.isSpecifiedSingle() || (finalRoute.isSingleResult() &&
                (finalRoute.isAsync() || finalRoute.isSuspended() || Publishers.isSingle(bodyClass)));
    }

    private void executeRoute(
            RouteMatch<?> routeMatch,
            NettyHttpRequest<?> request,
            ChannelHandlerContext context,
            ExecutorService executor,
            boolean isErrorRoute,
            boolean skipOncePerRequest,
            HttpContentProcessor<?> contentProcessor) {
        Supplier<MediaType> defaultResponseMediaType = () -> resolveDefaultResponseContentType(
                request,
                routeMatch
        );
        AtomicReference<HttpRequest<?>> requestReference = new AtomicReference<>(request);
        Publisher<? extends MutableHttpResponse<?>> filteredPublisher = buildResultEmitter(
                request,
                requestReference,
                routeMatch,
                executor,
                isErrorRoute,
                skipOncePerRequest,
                contentProcessor
        );

        filteredPublisher.subscribe(new ContextCompletionAwareSubscriber<MutableHttpResponse<?>>(context) {
            @Override
            protected void onComplete(MutableHttpResponse<?> message) {

                HttpRequest<?> incomingRequest = requestReference.get();
                applyConfiguredHeaders(message.getHeaders());

                HttpStatus status = message.status();
                if (status.getCode() >= 400 && !isErrorRoute) {
                    RouteMatch<Object> statusRoute = findStatusRoute(incomingRequest, status, routeMatch);

                    if (statusRoute != null) {
                        incomingRequest.setAttribute(HttpAttributes.ROUTE_MATCH, statusRoute);
                        executeRoute(
                                statusRoute,
                                request,
                                context,
                                executor,
                                true,
                                true,
                                null
                        );
                        return;
                    }
                }

                MediaType specifiedMediaType = message.getContentType().orElse(null);
                MediaType mediaType = specifiedMediaType != null ? specifiedMediaType : defaultResponseMediaType.get();

                Object body = message.body();
                if (body != null) {
                    boolean isReactive = routeMatch.isAsyncOrReactive() || Publishers.isConvertibleToPublisher(body);
                    if (isReactive && Publishers.isConvertibleToPublisher(body)) {
                        message.body(null);

                        Class<?> bodyClass = body.getClass();
                        boolean isSingle = isSingle(routeMatch, bodyClass);
                        boolean isCompletable = !isSingle && routeMatch.isVoid() && Publishers.isCompletable(bodyClass);
                        if (isSingle || isCompletable) {
                            // full response case
                            Publisher<Object> publisher = Publishers.convertPublisher(body, Publisher.class);
                            publisher.subscribe(new CompletionAwareSubscriber<Object>() {

                                Object result = NOT_FOUND;

                                @Override
                                protected void doOnSubscribe(Subscription subscription) {
                                    subscription.request(1);
                                }

                                @Override
                                protected void doOnNext(Object result) {
                                    this.result = result;
                                }

                                @Override
                                protected void doOnError(Throwable throwable) {
                                    exceptionCaughtInternal(
                                            context,
                                            throwable,
                                            request,
                                            false
                                    );
                                }

                                @Override
                                protected void doOnComplete() {
                                    if (result == NOT_FOUND) {
                                        if (isCompletable || routeMatch.isVoid() || routeMatch.isSuspended()) {
                                            message.body(null);
                                            message.header(HttpHeaders.CONTENT_LENGTH, HttpHeaderValues.ZERO);
                                            writeFinalNettyResponse(
                                                    message,
                                                    request,
                                                    context
                                            );
                                        } else if (!isErrorRoute) {
                                            RouteMatch<Object> statusRoute = findStatusRoute(incomingRequest, HttpStatus.NOT_FOUND, routeMatch);
                                            if (statusRoute != null) {
                                                executeRoute(
                                                        statusRoute,
                                                        request,
                                                        context,
                                                        executor,
                                                        true,
                                                        true,
                                                        null);
                                            } else {
                                                emitDefaultNotFoundResponse(context, requestReference.get(), skipOncePerRequest);
                                            }
                                        } else {
                                            emitDefaultNotFoundResponse(context, requestReference.get(), skipOncePerRequest);
                                        }
                                    } else {
                                        MutableHttpResponse<?> finalResponse;
                                        if (result instanceof HttpResponse) {
                                            finalResponse = toMutableResponse((HttpResponse<?>) result);
                                            result = finalResponse.body();
                                        } else {
                                            finalResponse = message;
                                        }
                                        encodeHttpResponse(
                                                context,
                                                request,
                                                finalResponse,
                                                result,
                                                defaultResponseMediaType
                                        );
                                    }
                                }
                            });
                        } else {
                            // streaming case
                            Argument<?> typeArgument = routeMatch.getReturnType().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                            boolean isHttp2 = request.getHttpVersion() == io.micronaut.http.HttpVersion.HTTP_2_0;
                            if (HttpResponse.class.isAssignableFrom(typeArgument.getType()) && !typeArgument.getFirstTypeVariable().map(Argument::isAsyncOrReactive).orElse(false)) {
                                // a response stream
                                Publisher<HttpResponse<?>> bodyPublisher = Publishers.convertPublisher(body, Publisher.class);
                                // HTTP/2 allows sending multiple responses down a single stream
                                if (isHttp2) {
                                    bodyPublisher.subscribe(new CompletionAwareSubscriber<HttpResponse<?>>() {
                                        @Override
                                        protected void doOnSubscribe(Subscription subscription) {
                                            subscription.request(1);
                                        }

                                        @Override
                                        protected void doOnNext(HttpResponse<?> message) {
                                            encodeHttpResponse(
                                                    context,
                                                    request,
                                                    toNettyResponse(message),
                                                    message.body(),
                                                    defaultResponseMediaType
                                            );
                                            subscription.request(1);
                                        }

                                        @Override
                                        protected void doOnError(Throwable throwable) {
                                            exceptionCaughtInternal(
                                                    context,
                                                    throwable,
                                                    request,
                                                    false
                                            );
                                        }

                                        @Override
                                        protected void doOnComplete() {
                                        }

                                    });
                                } else {
                                    // HTTP/1 we take the first response or error
                                    bodyPublisher.subscribe(new CompletionAwareSubscriber<HttpResponse<?>>() {

                                        final AtomicBoolean received = new AtomicBoolean();

                                        @Override
                                        protected void doOnSubscribe(Subscription subscription) {
                                            subscription.request(1);
                                        }

                                        @Override
                                        protected void doOnNext(HttpResponse<?> message) {
                                            encodeHttpResponse(
                                                    context,
                                                    request,
                                                    toNettyResponse(message),
                                                    message.body(),
                                                    defaultResponseMediaType
                                            );
                                            received.set(true);
                                        }

                                        @Override
                                        protected void doOnError(Throwable throwable) {
                                            exceptionCaughtInternal(
                                                    context,
                                                    throwable,
                                                    request,
                                                    false
                                            );
                                        }

                                        @Override
                                        protected void doOnComplete() {
                                            if (!received.get()) {
                                                doOnError(new NoSuchElementException());
                                            }
                                        }

                                    });
                                }
                            } else {
                                boolean isJson = mediaType.getExtension().equals(MediaType.EXTENSION_JSON) && isJsonFormattable(typeArgument);
                                Publisher<Object> bodyPublisher = applyExecutorToPublisher(Publishers.convertPublisher(body, Publisher.class), executor);
                                NettyByteBufferFactory byteBufferFactory = new NettyByteBufferFactory(context.alloc());

                                Publisher<HttpContent> httpContentPublisher = Publishers.map(bodyPublisher, new Function<Object, HttpContent>() {
                                    @Override
                                    public HttpContent apply(Object message) {
                                        HttpContent httpContent;
                                        if (message instanceof ByteBuf) {
                                            httpContent = new DefaultHttpContent((ByteBuf) message);
                                        } else if (message instanceof ByteBuffer) {
                                            ByteBuffer<?> byteBuffer = (ByteBuffer<?>) message;
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

                                            if (LOG.isTraceEnabled()) {
                                                LOG.trace("Encoding emitted response object [{}] using codec: {}", message, codec);
                                            }
                                            ByteBuffer<ByteBuf> encoded = codec.encode(message, byteBufferFactory);
                                            httpContent = new DefaultHttpContent(encoded.asNativeBuffer());
                                        }
                                        return httpContent;
                                    }
                                });

                                if (isJson) {
                                    // if the Publisher is returning JSON then in order for it to be valid JSON for each emitted element
                                    // we must wrap the JSON in array and delimit the emitted items
                                    httpContentPublisher = Flowable.fromPublisher(httpContentPublisher)
                                            .lift((FlowableOperator<HttpContent, HttpContent>) JsonSubscriber::new);
                                }

                                httpContentPublisher = Publishers.then(httpContentPublisher, httpContent ->
                                    // once an http content is written, read the next item if it is available
                                    context.read()
                                );

                                httpContentPublisher = Flowable.fromPublisher(httpContentPublisher)
                                        .doAfterTerminate(() -> cleanupRequest(context, request));

                                DelegateStreamedHttpResponse streamedResponse = new DelegateStreamedHttpResponse(
                                        toNettyResponse(message).toHttpResponse(),
                                        httpContentPublisher
                                );
                                io.netty.handler.codec.http.HttpHeaders headers = streamedResponse.headers();
                                headers.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                                headers.set(HttpHeaderNames.CONTENT_TYPE, mediaType);

                                if (isHttp2) {
                                    addHttp2StreamHeader(request, streamedResponse);
                                }
                                context.writeAndFlush(streamedResponse);
                                context.read();
                            }
                        }

                    } else {
                        // non-reactive.. encode and write full response
                        encodeHttpResponse(
                                context,
                                request,
                                message,
                                body,
                                defaultResponseMediaType
                        );

                    }
                } else {
                    // message with an empty body
                    writeFinalNettyResponse(
                            message,
                            requestReference.get(),
                            context
                    );
                }

            }

            @Override
            protected void doOnError(Throwable t) {
                final NettyHttpRequest nettyHttpRequest = (NettyHttpRequest) requestReference.get();
                exceptionCaughtInternal(context, t, nettyHttpRequest, true);
            }
        });
    }

    private Publisher<? extends MutableHttpResponse<?>> buildResultEmitter(
            NettyHttpRequest<?> request,
            AtomicReference<HttpRequest<?>> requestReference,
            RouteMatch<?> finalRoute,
            ExecutorService executor,
            boolean isErrorRoute,
            boolean skipOncePerRequest,
            HttpContentProcessor<?> contentProcessor) {
        // build the result emitter. This result emitter emits the response from a controller action
        Publisher<MutableHttpResponse<?>> executeRoutePublisher;
        if (contentProcessor != null) {
            executeRoutePublisher = Single.<RouteMatch<?>>create(emitter ->
                    contentProcessor.subscribe(buildSubscriber(request, finalRoute, emitter)))
                    .flatMapPublisher((route) -> createExecuteRoutePublisher(request, requestReference, route, isErrorRoute, executor));
        } else {
            executeRoutePublisher = createExecuteRoutePublisher(request, requestReference, finalRoute, isErrorRoute, executor);
        }

        // process the publisher through the available filters
        return filterPublisher(
                requestReference,
                executeRoutePublisher,
                skipOncePerRequest
        );
    }

    private Publisher<MutableHttpResponse<?>> createExecuteRoutePublisher(NettyHttpRequest<?> request,
                                                                                    AtomicReference<HttpRequest<?>> requestReference,
                                                                                    RouteMatch<?> routeMatch,
                                                                                    boolean isErrorRoute,
                                                                                    Executor executor) {
        return new Publisher<MutableHttpResponse<?>>() {
            @Override
            public void subscribe(Subscriber<? super MutableHttpResponse<?>> subscriber) {
                if (executor == null) {
                    doSubscribe(subscriber);
                } else {
                    executor.execute(() -> {
                        doSubscribe(subscriber);
                    });
                }
            }

            private void doSubscribe(Subscriber<? super MutableHttpResponse<?>> subscriber) {
                subscriber.onSubscribe(new Subscription() {

                    boolean done;

                    @Override
                    public void request(long n) {
                        if (done) {
                            return;
                        }
                        done = true;
                        try {
                            ServerRequestContext.set(requestReference.get());
                            emitRouteResponse((Subscriber<MutableHttpResponse<?>>) subscriber, request, requestReference, routeMatch, isErrorRoute);
                        } finally {
                            ServerRequestContext.set(null);
                        }
                    }

                    @Override
                    public void cancel() {
                    }

                });
            }
        };
    }

    private void emitRouteResponse(Subscriber<MutableHttpResponse<?>> subscriber,
                                   NettyHttpRequest<?> request,
                                   AtomicReference<HttpRequest<?>> requestReference,
                                   RouteMatch<?> routeMatch,
                                   boolean isErrorRoute) {
        try {
            final RouteMatch<?> finalRoute;

            // ensure the route requirements are completely satisfied
            if (!routeMatch.isExecutable()) {
                finalRoute = requestArgumentSatisfier
                        .fulfillArgumentRequirements(routeMatch, requestReference.get(), true);
            } else {
                finalRoute = routeMatch;
            }

            boolean isSuspended = finalRoute.isSuspended();

            Object body = finalRoute.execute();
            if (body instanceof Optional) {
                body = ((Optional<?>) body).orElse(null);
            }

            HttpRequest<?> incomingRequest = requestReference.get();
            MutableHttpResponse<?> outgoingResponse;

            if (body == null) {
                if (finalRoute.isVoid()) {
                    outgoingResponse = forStatus(finalRoute);
                    if (HttpMethod.permitsRequestBody(request.getMethod())) {
                        outgoingResponse.header(HttpHeaders.CONTENT_LENGTH, HttpHeaderValues.ZERO);
                    }
                } else {
                    outgoingResponse = newNotFoundError(request);
                }
            } else {
                HttpStatus defaultHttpStatus = isErrorRoute ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK;
                boolean isReactive = finalRoute.isAsyncOrReactive() || Publishers.isConvertibleToPublisher(body);
                if (isReactive) {
                    Class<?> bodyClass = body.getClass();
                    boolean isSingle = isSingle(finalRoute, bodyClass);
                    boolean isCompletable = !isSingle && finalRoute.isVoid() && Publishers.isCompletable(bodyClass);
                    if (isSingle || isCompletable) {
                        // full response case
                        Publisher<Object> publisher = Publishers.convertPublisher(body, Publisher.class);
                        Publishers.mapOrSupplyEmpty(publisher, new Publishers.MapOrSupplyEmpty<Object, MutableHttpResponse<?>>() {
                            @Override
                            public MutableHttpResponse<?>  map(Object o) {
                                MutableHttpResponse<?> singleResponse;
                                if (o instanceof Optional) {
                                    Optional optional = (Optional) o;
                                    if (optional.isPresent()) {
                                        o = ((Optional<?>) o).get();
                                    } else {
                                        return supplyEmpty();
                                    }
                                }
                                if (o instanceof HttpResponse) {
                                    singleResponse = toMutableResponse((HttpResponse<?>) o);
                                } else if (o instanceof HttpStatus) {
                                    singleResponse = forStatus(routeMatch, (HttpStatus) o);
                                } else {
                                    singleResponse = forStatus(routeMatch, defaultHttpStatus)
                                            .body(o);
                                }
                                singleResponse.setAttribute(HttpAttributes.ROUTE_MATCH, finalRoute);
                                return singleResponse;
                            }

                            @Override
                            public MutableHttpResponse<?> supplyEmpty() {
                                MutableHttpResponse<?> singleResponse;
                                if (isCompletable || finalRoute.isVoid()) {
                                    singleResponse = forStatus(finalRoute, HttpStatus.OK)
                                            .header(HttpHeaders.CONTENT_LENGTH, HttpHeaderValues.ZERO);
                                } else {
                                    singleResponse = newNotFoundError(request);
                                }
                                singleResponse.setAttribute(HttpAttributes.ROUTE_MATCH, finalRoute);
                                return singleResponse;
                            }

                        }).subscribe(new CompletionAwareSubscriber<MutableHttpResponse<?>>() {

                            @Override
                            public void doOnSubscribe(Subscription s) {
                                s.request(1);
                            }

                            @Override
                            public void doOnNext(MutableHttpResponse<?> mutableHttpResponse) {
                                subscriber.onNext(mutableHttpResponse);
                            }

                            @Override
                            public void doOnError(Throwable t) {
                                subscriber.onError(t);
                            }

                            @Override
                            public void doOnComplete() {
                                subscriber.onComplete();
                            }
                        });
                        return;
                    }
                }
                // now we have the raw result, transform it as necessary
                if (body instanceof HttpStatus) {
                    outgoingResponse = HttpResponse.status((HttpStatus) body);
                } else {
                    if (isSuspended) {
                        boolean isKotlinFunctionReturnTypeUnit =
                                finalRoute instanceof MethodBasedRouteMatch &&
                                        isKotlinFunctionReturnTypeUnit(((MethodBasedRouteMatch) finalRoute).getExecutableMethod());
                        final Supplier<CompletableFuture<?>> supplier = ContinuationArgumentBinder.extractContinuationCompletableFutureSupplier(incomingRequest);
                        if (isKotlinCoroutineSuspended(body)) {
                            CompletableFuture<?> f = supplier.get();
                            f.whenComplete((o, throwable) -> {
                                if (throwable != null) {
                                    subscriber.onError(throwable);
                                } else {
                                    if (o == null) {
                                        subscriber.onNext(newNotFoundError(request));
                                    } else {
                                        MutableHttpResponse<?> response;
                                        if (o instanceof HttpResponse) {
                                            response = toMutableResponse((HttpResponse<?>) o);
                                        } else {
                                            response = forStatus(routeMatch, defaultHttpStatus);
                                            if (!isKotlinFunctionReturnTypeUnit) {
                                                response = response.body(o);
                                            }
                                        }
                                        response.setAttribute(HttpAttributes.ROUTE_MATCH, finalRoute);
                                        subscriber.onNext(response);
                                    }
                                    subscriber.onComplete();
                                }
                            });
                            return;
                        } else {
                            Object suspendedBody;
                            if (isKotlinFunctionReturnTypeUnit) {
                                suspendedBody = Completable.complete();
                            } else {
                                suspendedBody = body;
                            }
                            if (suspendedBody instanceof HttpResponse) {
                                outgoingResponse = toMutableResponse((HttpResponse<?>) suspendedBody);
                            } else {
                                outgoingResponse = forStatus(finalRoute, defaultHttpStatus)
                                        .body(suspendedBody);
                            }
                        }

                    } else {
                        if (body instanceof HttpResponse) {
                            outgoingResponse = toMutableResponse((HttpResponse<?>) body);
                        } else {
                            outgoingResponse = forStatus(finalRoute, defaultHttpStatus)
                                    .body(body);
                        }
                    }
                }

                // for head request we never emit the body
                if (incomingRequest != null && incomingRequest.getMethod().equals(HttpMethod.HEAD)) {
                    final Object o = outgoingResponse.getBody().orElse(null);
                    if (o instanceof ReferenceCounted) {
                        ((ReferenceCounted) o).release();
                    }
                    outgoingResponse.body(null);
                }
            }
            outgoingResponse.setAttribute(HttpAttributes.ROUTE_MATCH, finalRoute);

            subscriber.onNext(outgoingResponse);
            subscriber.onComplete();
        } catch (Throwable e) {
            subscriber.onError(e);
        }
    }

    private void encodeHttpResponse(
            ChannelHandlerContext context,
            NettyHttpRequest<?> nettyRequest,
            MutableHttpResponse<?> response,
            Object body,
            Supplier<MediaType> defaultResponseMediaType) {
        boolean isNotHead = nettyRequest.getMethod() != HttpMethod.HEAD;

        if (isNotHead) {
            if (body instanceof Writable) {
                getIoExecutor().execute(() -> {
                    ByteBuf byteBuf = context.alloc().ioBuffer(128);
                    ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf);
                    try {
                        Writable writable = (Writable) body;
                        writable.writeTo(outputStream, nettyRequest.getCharacterEncoding());
                        response.body(byteBuf);
                        if (!response.getHeaders().contains(HttpHeaders.CONTENT_TYPE)) {
                            response.header(HttpHeaders.CONTENT_TYPE, defaultResponseMediaType.get());
                        }
                        writeFinalNettyResponse(
                                response,
                                nettyRequest,
                                context
                        );
                    } catch (IOException e) {
                        exceptionCaughtInternal(context, e, nettyRequest, false);
                    }
                });
            } else {
                encodeResponseBody(
                        context,
                        nettyRequest,
                        response,
                        body,
                        defaultResponseMediaType
                );

                writeFinalNettyResponse(
                        response,
                        nettyRequest,
                        context
                );
            }
        } else {
            response.body(null);
            writeFinalNettyResponse(
                    response,
                    nettyRequest,
                    context
            );
        }
    }

    @Nullable
    private RouteMatch<Object> findStatusRoute(HttpRequest<?> incomingRequest, HttpStatus status, RouteMatch<?> finalRoute) {
        Class<?> declaringType = getDeclaringType(finalRoute);
        // handle re-mapping of errors
        RouteMatch<Object> statusRoute = null;
        // if declaringType is not null, this means its a locally marked method handler
        if (declaringType != null) {
            statusRoute = router.findStatusRoute(declaringType, status, incomingRequest)
                    .orElseGet(() -> router.findStatusRoute(status, incomingRequest).orElse(null));
        }
        return statusRoute;
    }

    private void encodeResponseBody(
            ChannelHandlerContext context,
            HttpRequest<?> request,
            MutableHttpResponse<?> message,
            Object body,
            Supplier<MediaType> defaultResponseMediaType) {
        if (body == null) {
            return;
        }

        MediaType specifiedMediaType = message.getContentType().orElse(null);
        MediaType responseMediaType = specifiedMediaType != null ? specifiedMediaType : defaultResponseMediaType.get();
        if (body instanceof CharSequence) {
            ByteBuf byteBuf = Unpooled.wrappedBuffer(body.toString().getBytes(message.getCharacterEncoding()));
            setResponseBody(message, responseMediaType, byteBuf);
        } else if (body instanceof byte[]) {
            ByteBuf byteBuf = Unpooled.wrappedBuffer((byte[]) body);
            setResponseBody(message, responseMediaType, byteBuf);
        } else if (body instanceof ByteBuffer) {
            ByteBuffer<?> byteBuffer = (ByteBuffer) body;
            Object nativeBuffer = byteBuffer.asNativeBuffer();
            if (nativeBuffer instanceof ByteBuf) {
                setResponseBody(message, responseMediaType, (ByteBuf) nativeBuffer);
            } else if (nativeBuffer instanceof java.nio.ByteBuffer) {
                ByteBuf byteBuf = Unpooled.wrappedBuffer((java.nio.ByteBuffer) nativeBuffer);
                setResponseBody(message, responseMediaType, byteBuf);
            }
        } else if (body instanceof ByteBuf) {
            setResponseBody(message, responseMediaType, (ByteBuf) body);
        } else {
            Optional<NettyCustomizableResponseTypeHandler> typeHandler = customizableResponseTypeHandlerRegistry
                    .findTypeHandler(body.getClass());
            if (typeHandler.isPresent()) {
                NettyCustomizableResponseTypeHandler th = typeHandler.get();
                setBodyContent(message, new NettyCustomizableResponseTypeHandlerInvoker(th, body));
            } else if (specifiedMediaType != null) {
                Optional<MediaTypeCodec> registeredCodec = mediaTypeCodecRegistry.findCodec(responseMediaType, body.getClass());
                if (registeredCodec.isPresent()) {
                    MediaTypeCodec codec = registeredCodec.get();
                    if (!message.getHeaders().contains(HttpHeaders.CONTENT_TYPE)) {
                        message.header(HttpHeaders.CONTENT_TYPE, responseMediaType);
                    }
                    encodeBodyWithCodec(message, body, codec, responseMediaType, context, request);
                }
            } else {
                Optional<MediaTypeCodec> registeredCodec = mediaTypeCodecRegistry.findCodec(responseMediaType, body.getClass());
                if (registeredCodec.isPresent()) {
                    MediaTypeCodec codec = registeredCodec.get();
                    if (!message.getHeaders().contains(HttpHeaders.CONTENT_TYPE)) {
                        message.header(HttpHeaders.CONTENT_TYPE, responseMediaType);
                    }
                    encodeBodyWithCodec(message, body, codec, responseMediaType, context, request);
                } else {
                    if (!message.getHeaders().contains(HttpHeaders.CONTENT_TYPE)) {
                        message.header(HttpHeaders.CONTENT_TYPE, responseMediaType);
                    }
                    MediaTypeCodec defaultCodec = new TextPlainCodec(serverConfiguration.getDefaultCharset());
                    encodeBodyWithCodec(message, body, defaultCodec, responseMediaType, context, request);
                }
            }

        }
    }

    @SuppressWarnings("rawtypes")
    private @Nullable
    Class<?> getDeclaringType(RouteMatch<?> route) {
        if (route instanceof MethodBasedRouteMatch) {
            return ((MethodBasedRouteMatch) route).getDeclaringType();
        }
        return null;
    }

    private MediaType resolveDefaultResponseContentType(NettyHttpRequest<?> request, RouteMatch<?> finalRoute) {
        final List<MediaType> producesList = finalRoute.getProduces();
        final Iterator<MediaType> i = request.accept().iterator();
        if (i.hasNext()) {
            final MediaType mt = i.next();
            if (producesList.contains(mt)) {
                return mt;
            }
        }

        MediaType defaultResponseMediaType;
        final Iterator<MediaType> produces = producesList.iterator();
        if (produces.hasNext()) {
            defaultResponseMediaType = produces.next();
        } else {
            defaultResponseMediaType = MediaType.APPLICATION_JSON_TYPE;
        }
        return defaultResponseMediaType;
    }

    private void writeFinalNettyResponse(MutableHttpResponse<?> message, HttpRequest<?> request, ChannelHandlerContext context) {
        HttpStatus httpStatus = message.status();

        final io.micronaut.http.HttpVersion httpVersion = request.getHttpVersion();
        final boolean isHttp2 = httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0;

        final Object body = message.body();
        if (body instanceof NettyCustomizableResponseTypeHandlerInvoker) {
            // default Connection header if not set explicitly
            if (!isHttp2) {
                if (!message.getHeaders().contains(HttpHeaders.CONNECTION)) {
                    if (httpStatus.getCode() > 499) {
                        message.getHeaders().set(HttpHeaders.CONNECTION, HttpHeaderValues.CLOSE);
                    } else {
                        message.getHeaders().set(HttpHeaders.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                    }
                }
            }
            NettyCustomizableResponseTypeHandlerInvoker handler = (NettyCustomizableResponseTypeHandlerInvoker) body;
            message.body(null);
            handler.invoke(request, message, context);
        } else {
            io.netty.handler.codec.http.HttpResponse nettyResponse = NettyHttpResponseBuilder.toHttpResponse(message);
            io.netty.handler.codec.http.HttpHeaders nettyHeaders = nettyResponse.headers();

            // default Connection header if not set explicitly
            if (!isHttp2) {
                if (!nettyHeaders.contains(HttpHeaderNames.CONNECTION)) {
                    boolean expectKeepAlive = nettyResponse.protocolVersion().isKeepAliveDefault() || request.getHeaders().isKeepAlive();
                    if (!expectKeepAlive || httpStatus.getCode() > 499) {
                        nettyHeaders.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                    } else {
                        nettyHeaders.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                    }
                }
            }

            // default to Transfer-Encoding: chunked if Content-Length not set or not already set
            if (!nettyHeaders.contains(HttpHeaderNames.CONTENT_LENGTH) && !nettyHeaders.contains(HttpHeaderNames.TRANSFER_ENCODING)) {
                nettyHeaders.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            }
            // close handled by HttpServerKeepAliveHandler
            final NettyHttpRequest<?> nettyHttpRequest = (NettyHttpRequest<?>) request;

            if (isHttp2) {
                addHttp2StreamHeader(request, nettyResponse);
            }
            io.netty.handler.codec.http.HttpRequest nativeRequest = nettyHttpRequest.getNativeRequest();

            GenericFutureListener<Future<? super Void>> requestCompletor = future -> {
                try {
                    if (!future.isSuccess()) {
                        final Throwable throwable = future.cause();
                        if (!(throwable instanceof ClosedChannelException)) {
                            if (throwable instanceof Http2Exception.StreamException) {
                                Http2Exception.StreamException se = (Http2Exception.StreamException) throwable;
                                if (se.error() == Http2Error.STREAM_CLOSED) {
                                    // ignore
                                    return;
                                }
                            }
                            if (LOG.isErrorEnabled()) {
                                LOG.error("Error writing final response: " + throwable.getMessage(), throwable);
                            }
                        }
                    }
                } finally {
                    cleanupRequest(context, nettyHttpRequest);
                    context.read();
                }
            };
            if (nativeRequest instanceof StreamedHttpRequest && !((StreamedHttpRequest) nativeRequest).isConsumed()) {
                StreamedHttpRequest streamedHttpRequest = (StreamedHttpRequest) nativeRequest;
                // We have to clear the buffer of FlowControlHandler before writing the response
                // If this is a streamed request and there is still content to consume then subscribe
                // and write the buffer is empty.

                //noinspection ReactiveStreamsSubscriberImplementation
                streamedHttpRequest.subscribe(new Subscriber<HttpContent>() {
                    private Subscription streamSub;

                    @Override
                    public void onSubscribe(Subscription s) {
                        streamSub = s;
                        s.request(1);
                    }

                    @Override
                    public void onNext(HttpContent httpContent) {
                        httpContent.release();
                        streamSub.request(1);
                    }

                    @Override
                    public void onError(Throwable t) {
                        context.writeAndFlush(nettyResponse)
                                .addListener(requestCompletor);
                    }

                    @Override
                    public void onComplete() {
                        context.writeAndFlush(nettyResponse)
                                .addListener(requestCompletor);
                    }
                });
            } else {
                context.writeAndFlush(nettyResponse)
                        .addListener(requestCompletor);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Response {} - {} {}",
                            nettyResponse.status().code(),
                            request.getMethodName(),
                            request.getUri());
                }
            }
        }
    }

    private void addHttp2StreamHeader(HttpRequest<?> request, io.netty.handler.codec.http.HttpResponse nettyResponse) {
        final String streamId = request.getHeaders().get(AbstractNettyHttpRequest.STREAM_ID);
        if (streamId != null) {
            nettyResponse.headers().set(AbstractNettyHttpRequest.STREAM_ID, streamId);
        }
    }

    private MutableHttpResponse<?> toMutableResponse(HttpResponse<?> message) {
        MutableHttpResponse<?> mutableHttpResponse;
        if (message instanceof MutableHttpResponse) {
            mutableHttpResponse = (MutableHttpResponse<?>) message;
        } else {
            HttpStatus httpStatus = message.status();
            mutableHttpResponse = HttpResponse.status(httpStatus, httpStatus.getReason());
            mutableHttpResponse.body(message.body());
            message.getHeaders().forEach((name, value) -> {
                for (String val: value) {
                    mutableHttpResponse.header(name, val);
                }
            });
            mutableHttpResponse.getAttributes().putAll(message.getAttributes());
        }
        return mutableHttpResponse;
    }

    @NotNull
    private NettyMutableHttpResponse<?> toNettyResponse(HttpResponse<?> message) {
        NettyMutableHttpResponse<?> nettyHttpResponse;
        if (message instanceof NettyMutableHttpResponse) {
            nettyHttpResponse = (NettyMutableHttpResponse<?>) message;
        } else {
            HttpStatus httpStatus = message.status();
            Object body = message.body();
            io.netty.handler.codec.http.HttpHeaders nettyHeaders = new DefaultHttpHeaders(serverConfiguration.isValidateHeaders());
            message.getHeaders().forEach((BiConsumer<String, List<String>>) nettyHeaders::set);
            nettyHttpResponse = new NettyMutableHttpResponse<>(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.valueOf(httpStatus.getCode(), httpStatus.getReason()),
                    body instanceof ByteBuf ? body : null,
                    ConversionService.SHARED
            );
        }
        return nettyHttpResponse;
    }

    private MutableHttpResponse<?> encodeBodyWithCodec(MutableHttpResponse<?> response,
                                                       Object body,
                                                       MediaTypeCodec codec,
                                                       MediaType mediaType,
                                                       ChannelHandlerContext context,
                                                       HttpRequest<?> request) {
        ByteBuf byteBuf;
        try {
            byteBuf = encodeBodyAsByteBuf(body, codec, context, request);
            setResponseBody(response, mediaType, byteBuf);
            return response;
        } catch (LinkageError e) {
            // rxjava swallows linkage errors for some reasons so if one occurs, rethrow as a internal error
            throw new InternalServerException("Fatal error encoding bytebuf: " + e.getMessage(), e);
        }
    }

    private void setResponseBody(MutableHttpResponse<?> response, MediaType mediaType, ByteBuf byteBuf) {
        int len = byteBuf.readableBytes();
        MutableHttpHeaders headers = response.getHeaders();
        if (!headers.contains(HttpHeaders.CONTENT_TYPE)) {
            headers.add(HttpHeaderNames.CONTENT_TYPE, mediaType);
        }
        headers.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(len));

        setBodyContent(response, byteBuf);
    }

    private MutableHttpResponse<?> setBodyContent(MutableHttpResponse response, Object bodyContent) {
        @SuppressWarnings("unchecked")
        MutableHttpResponse<?> res = response.body(bodyContent);
        return res;
    }

    private ByteBuf encodeBodyAsByteBuf(Object body, MediaTypeCodec codec, ChannelHandlerContext context, HttpRequest<?> request) {
        ByteBuf byteBuf;
        if (body instanceof ByteBuf) {
            byteBuf = (ByteBuf) body;
        } else if (body instanceof ByteBuffer) {
            ByteBuffer byteBuffer = (ByteBuffer) body;
            Object nativeBuffer = byteBuffer.asNativeBuffer();
            if (nativeBuffer instanceof ByteBuf) {
                byteBuf = (ByteBuf) nativeBuffer;
            } else {
                byteBuf = Unpooled.wrappedBuffer(byteBuffer.asNioBuffer());
            }
        } else if (body instanceof byte[]) {
            byteBuf = Unpooled.wrappedBuffer((byte[]) body);

        } else if (body instanceof Writable) {
            byteBuf = context.alloc().ioBuffer(128);
            ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf);
            Writable writable = (Writable) body;
            try {
                writable.writeTo(outputStream, request.getCharacterEncoding());
            } catch (IOException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error(e.getMessage());
                }
            }
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Encoding emitted response object [{}] using codec: {}", body, codec);
            }
            byteBuf = codec.encode(body, new NettyByteBufferFactory(context.alloc())).asNativeBuffer();
        }
        return byteBuf;
    }

    private MutableHttpResponse<Object> forStatus(RouteMatch routeMatch) {
        return forStatus(routeMatch, HttpStatus.OK);
    }

    private MutableHttpResponse<Object> forStatus(RouteMatch routeMatch, HttpStatus defaultStatus) {
        HttpStatus status = routeMatch.findStatus(defaultStatus);
        return new NettyMutableHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(status.getCode()), ConversionService.SHARED);
    }

    private Publisher<MutableHttpResponse<?>> filterPublisher(
            AtomicReference<HttpRequest<?>> requestReference,
            Publisher<MutableHttpResponse<?>> upstreamResponsePublisher,
            boolean skipOncePerRequest) {
        List<HttpFilter> httpFilters = router.findFilters(requestReference.get());
        if (httpFilters.isEmpty()) {
            return upstreamResponsePublisher;
        }
        List<HttpFilter> filters = new ArrayList<>(httpFilters);
        if (skipOncePerRequest) {
            filters.removeIf(filter -> filter instanceof OncePerRequestHttpServerFilter);
        }
        if (filters.isEmpty()) {
            return upstreamResponsePublisher;
        }
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
                if (pos == len) {
                    return upstreamResponsePublisher;
                }
                HttpFilter httpFilter = filters.get(pos);
                return (Publisher<MutableHttpResponse<?>>) httpFilter.doFilter(requestReference.getAndSet(request), this);
            }
        };
        Optional<HttpRequest<Object>> prevRequest = ServerRequestContext.currentRequest();
        try {
            ServerRequestContext.set(requestReference.get());
            HttpFilter httpFilter = filters.get(0);
            return (Publisher<MutableHttpResponse<?>>) httpFilter.doFilter(requestReference.get(), filterChain);
        } finally {
            if (prevRequest.isPresent()) {
                ServerRequestContext.set(prevRequest.get());
            } else {
                ServerRequestContext.set(null);
            }
        }
    }

    private <T> Publisher<T> applyExecutorToPublisher(
            Publisher<T> publisher,
            @Nullable ExecutorService executor) {
        if (executor != null) {
            final Scheduler scheduler = Schedulers.from(executor);
            return publisherToFlowable(publisher)
                        .subscribeOn(scheduler)
                        .observeOn(scheduler);
        } else {
            return publisher;
        }
    }

    private <T> Flowable<T> publisherToFlowable(
            Publisher<T> publisher) {
        if (publisher instanceof Flowable) {
            return (Flowable<T>) publisher;
        } else {
            return Flowable.fromPublisher(publisher);
        }
    }

    private void writeDefaultErrorResponse(ChannelHandlerContext ctx, NettyHttpRequest nettyHttpRequest, Throwable cause, boolean skipOncePerRequest) {
        logException(cause);

        MutableHttpResponse<?> response = errorResponseProcessor.processResponse(
                ErrorContext.builder(nettyHttpRequest)
                        .cause(cause)
                        .errorMessage("Internal Server Error: " + cause.getMessage())
                        .build(), io.micronaut.http.HttpResponse.serverError());

        filterAndEncodeResponse(
                ctx,
                nettyHttpRequest,
                nettyHttpRequest,
                response,
                MediaType.APPLICATION_JSON_TYPE,
                skipOncePerRequest);
    }

    private void logException(Throwable cause) {
        //handling connection reset by peer exceptions
        if (isIgnorable(cause)) {
            logIgnoredException(cause);
        } else {
            if (LOG.isErrorEnabled()) {
                LOG.error("Unexpected error occurred: " + cause.getMessage(), cause);
            }
        }
    }

    private boolean isIgnorable(Throwable cause) {
        String message = cause.getMessage();
        return cause instanceof IOException && message != null && IGNORABLE_ERROR_MESSAGE.matcher(message).matches();
    }

    private void logIgnoredException(Throwable cause) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Swallowed an IOException caused by client connectivity: " + cause.getMessage(), cause);
        }
    }

    private void applyConfiguredHeaders(MutableHttpHeaders headers) {
        if (serverConfiguration.isDateHeader() && !headers.contains(HttpHeaders.DATE)) {
            headers.date(LocalDateTime.now());
        }
        if (serverHeader != null && !headers.contains(HttpHeaders.SERVER)) {
            headers.add(HttpHeaders.SERVER, serverHeader);
        }
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
        void invoke(HttpRequest<?> request, MutableHttpResponse response, ChannelHandlerContext channelHandlerContext) {
            this.handler.handle(body, request, response, channelHandlerContext);
        }
    }
}
