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
import java.net.URI;
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

import edu.umd.cs.findbugs.annotations.Nullable;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.context.BeanContext;
import io.micronaut.context.exceptions.BeanCreationException;
import io.micronaut.core.annotation.AnnotationMetadata;
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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.*;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.bind.binders.ContinuationArgumentBinder;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.netty.AbstractNettyHttpRequest;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.NettyMutableHttpResponse;
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
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
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
import io.reactivex.*;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.LongConsumer;
import io.reactivex.processors.UnicastProcessor;
import io.reactivex.schedulers.Schedulers;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
class RoutingInBoundHandler extends SimpleChannelInboundHandler<io.micronaut.http.HttpRequest<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingInBoundHandler.class);
    private static final Pattern IGNORABLE_ERROR_MESSAGE = Pattern.compile(
            "^.*(?:connection.*(?:reset|closed|abort|broken)|broken.*pipe).*$", Pattern.CASE_INSENSITIVE);
    private static final Argument ARGUMENT_PART_DATA = Argument.of(PartData.class);
    private static final Object NOT_FOUND = new Object();
    private static final Single<Object> NOT_FOUND_SINGLE = Single.just(NOT_FOUND);

    private final Router router;
    private final ExecutorSelector executorSelector;
    private final StaticResourceResolver staticResourceResolver;
    private final BeanContext beanContext;
    private final NettyHttpServerConfiguration serverConfiguration;
    private final HttpContentProcessorResolver httpContentProcessorResolver;
    private final RequestArgumentSatisfier requestArgumentSatisfier;
    private final MediaTypeCodecRegistry mediaTypeCodecRegistry;
    private final NettyCustomizableResponseTypeHandlerRegistry customizableResponseTypeHandlerRegistry;
    private final Supplier<ExecutorService> ioExecutorSupplier;
    private final String serverHeader;
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
            HttpContentProcessorResolver httpContentProcessorResolver) {
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
            if (LOG.isErrorEnabled()) {
                LOG.error("Micronaut Server Error - No request state present. Cause: " + cause.getMessage(), cause);
            }
            ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
            return;
        }

        exceptionCaughtInternal(ctx, cause, nettyHttpRequest, true);
    }

    private void exceptionCaughtInternal(ChannelHandlerContext ctx,
                                         Throwable t,
                                         NettyHttpRequest nettyHttpRequest,
                                         boolean nettyException) {
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
                buildExecutableRoute(
                        errorRoute,
                        nettyHttpRequest,
                        ctx,
                        ctx.executor(),
                        true,
                        nettyException
                ).execute();
            } catch (Throwable e) {
                writeDefaultErrorResponse(ctx, nettyHttpRequest, e, nettyException);
            }
        } else {

            Optional<ExceptionHandler> exceptionHandler = beanContext
                    .findBean(ExceptionHandler.class, Qualifiers.byTypeArgumentsClosest(cause.getClass(), Object.class));

            if (exceptionHandler.isPresent()) {
                ExceptionHandler handler = exceptionHandler.get();
                MediaType defaultResponseMediaType = MediaType.fromType(handler.getClass()).orElse(MediaType.APPLICATION_JSON_TYPE);
                try {
                    Publisher<MutableHttpResponse<?>> routePublisher = Flowable.fromCallable(() -> {
                        Object result = handler.handle(nettyHttpRequest, cause);
                        return errorResultToResponse(result);
                    });

                    filterPublisher(new AtomicReference<>(nettyHttpRequest), routePublisher, ctx.executor(), nettyException)
                            .firstOrError()
                            .subscribe((mutableHttpResponse, throwable) -> {
                                if (throwable != null) {
                                    writeDefaultErrorResponse(ctx, nettyHttpRequest, throwable, nettyException);
                                } else {
                                    encodeHttpResponse(
                                            ctx,
                                            nettyHttpRequest,
                                            mutableHttpResponse,
                                            mutableHttpResponse.body(),
                                            defaultResponseMediaType
                                    );
                                }
                            });


                    if (serverConfiguration.isLogHandledExceptions()) {
                        logException(cause);
                    }
                } catch (Throwable e) {
                    writeDefaultErrorResponse(ctx, nettyHttpRequest, e, nettyException);
                }
            } else {
                writeDefaultErrorResponse(
                        ctx,
                        nettyHttpRequest,
                        cause,
                        nettyException);
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

        final String requestMethodName = request.getMethodName();
        if (routeMatch == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No matching route found for URI {} and method {}", request.getUri(), httpMethod);
            }

            // if there is no route present try to locate a route that matches a different HTTP method
            final List<UriRouteMatch<?, ?>> anyMatchingRoutes = router
                    .findAny(request.getUri().toString(), request)
                    .collect(Collectors.toList());
            MediaType contentType = request.getContentType().orElse(null);
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

        if (LOG.isDebugEnabled()) {
            if (route instanceof MethodBasedRouteMatch) {
                LOG.debug("Matched route {} - {} to controller {}", requestMethodName, requestPath, route.getDeclaringType());
            } else {
                LOG.debug("Matched route {} - {}", requestMethodName, requestPath);
            }
        }
        // all ok proceed to try and execute the route
        if (route.isAnnotationPresent(OnMessage.class) || route.isAnnotationPresent(OnOpen.class)) {
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
            MutableHttpResponse<Object> defaultResponse,
            String message) {
        Optional<RouteMatch<Object>> statusRoute = router.findStatusRoute(defaultResponse.status(), request);
        if (statusRoute.isPresent()) {
            RouteMatch<Object> routeMatch = statusRoute.get();
            handleRouteMatch(routeMatch, nettyHttpRequest, ctx, false);
        } else {
            if (request.getMethod() != HttpMethod.HEAD) {
                JsonError error = newError(request, message);
                defaultResponse.body(error);
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
            MutableHttpResponse<Object> finalResponse,
            MediaType defaultResponseMediaType,
            boolean skipOncePerRequest) {
        AtomicReference<HttpRequest<?>> requestReference = new AtomicReference<>(request);
        filterPublisher(
                requestReference,
                Flowable.just(finalResponse),
                ctx.channel().eventLoop(),
                skipOncePerRequest
        ).singleOrError().subscribe((Consumer<MutableHttpResponse<?>>) mutableHttpResponse ->
            encodeHttpResponse(
                    ctx,
                    nettyHttpRequest,
                    mutableHttpResponse,
                    mutableHttpResponse.body(),
                    defaultResponseMediaType
            )
        , throwable -> exceptionCaughtInternal(ctx, throwable, nettyHttpRequest, false));
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
        MutableHttpResponse<Object> res = newNotFoundError(request);
        filterAndEncodeResponse(
                ctx,
                request,
                (NettyHttpRequest) request,
                res,
                MediaType.APPLICATION_JSON_TYPE,
                skipOncePerRequest);
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
        if (!route.isExecutable() &&
                io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod()) &&
                nativeRequest instanceof StreamedHttpRequest &&
                (!bodyArgument.isPresent() || !route.isSatisfied(bodyArgument.get().getName()))) {
            httpContentProcessorResolver.resolve(request, route).subscribe(buildSubscriber(request, context, route));
        } else {
            if (nativeRequest instanceof StreamedHttpRequest) {
                context.read();
            }
            route = prepareRouteForExecution(route, request, skipOncePerRequest);
            route.execute();
        }
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
                                               ChannelHandlerContext context,
                                               RouteMatch<?> finalRoute) {
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
                                        HttpDataReference.Component component = dataReference.addComponent(e -> {
                                            subject.onError(e);
                                            s.cancel();
                                        });
                                        if (component == null) {
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
                    try {
                        s.cancel();
                        exceptionCaught(context, t);
                    } catch (Exception e) {
                        // should never happen
                        writeDefaultErrorResponse(context, request, e, false);
                    }
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
                        try {
                            routeMatch = prepareRouteForExecution(routeMatch, request, false);
                            routeMatch.execute();
                        } catch (Exception e) {
                            context.pipeline().fireExceptionCaught(e);
                        }
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
                    try {
                        s.cancel();
                        exceptionCaught(context, t);
                    } catch (Exception e) {
                        // should never happen
                        writeDefaultErrorResponse(context, request, e, false);
                    }
                }

                @Override
                protected void doOnComplete() {
                    if (executed.compareAndSet(false, true)) {
                        try {
                            routeMatch = prepareRouteForExecution(routeMatch, request, false);
                            routeMatch.execute();
                        } catch (Exception e) {
                            context.pipeline().fireExceptionCaught(e);
                        }
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

    private RouteMatch<?> prepareRouteForExecution(RouteMatch<?> route, NettyHttpRequest<?> request, boolean skipOncePerRequest) {
        ChannelHandlerContext context = request.getChannelHandlerContext();
        // Select the most appropriate Executor
        ExecutorService executor;
        if (route instanceof MethodReference) {
            executor = executorSelector.select((MethodReference) route, serverConfiguration.getThreadSelection()).orElse(null);
        } else {
            executor = null;
        }

        boolean isErrorRoute = false;
        route = buildExecutableRoute(
                route,
                request,
                context,
                executor,
                isErrorRoute,
                skipOncePerRequest
        );
        return route;
    }

    private boolean isSingle(RouteMatch<?> finalRoute, Class<?> bodyClass) {
        return finalRoute.isSpecifiedSingle() || (finalRoute.isSingleResult() &&
                (finalRoute.isAsync() || finalRoute.isSuspended() || Publishers.isSingle(bodyClass)));
    }

    private RouteMatch<?> buildExecutableRoute(
            RouteMatch<?> route,
            NettyHttpRequest<?> request,
            ChannelHandlerContext context,
            ExecutorService executor,
            boolean isErrorRoute,
            boolean skipOncePerRequest) {
        route = route.decorate(finalRoute -> {
            MediaType defaultResponseMediaType = resolveDefaultResponseContentType(
                    request,
                    finalRoute
            );
            AtomicReference<HttpRequest<?>> requestReference = new AtomicReference<>(request);
            Flowable<? extends MutableHttpResponse<?>> filteredPublisher = buildResultEmitter(
                    request,
                    requestReference,
                    finalRoute,
                    executor,
                    isErrorRoute,
                    skipOncePerRequest
            );

            filteredPublisher.subscribe(new ContextCompletionAwareSubscriber<MutableHttpResponse<?>>(context) {
                @Override
                protected void onComplete(MutableHttpResponse<?> message) {

                    HttpRequest<?> incomingRequest = requestReference.get();
                    applyConfiguredHeaders(message.getHeaders());
                    MediaType specifiedMediaType = message.getContentType().orElse(null);
                    MediaType mediaType = specifiedMediaType != null ? specifiedMediaType : defaultResponseMediaType;

                    Object body = message.body();
                    HttpStatus status = message.status();
                    if (status.getCode() >= 400 && !isErrorRoute) {
                        RouteMatch<Object> statusRoute = findStatusRoute(incomingRequest, status, finalRoute);

                        if (statusRoute != null) {
                            incomingRequest.setAttribute(HttpAttributes.ROUTE_MATCH, statusRoute);
                            buildExecutableRoute(
                                    statusRoute,
                                    request,
                                    context,
                                    executor,
                                    true,
                                    true
                            ).execute();
                        } else {
                            //noinspection unchecked
                            MutableHttpResponse<Object> mutableHttpResponse = (MutableHttpResponse<Object>) message;
                            encodeHttpResponse(
                                    context,
                                    request,
                                    mutableHttpResponse,
                                    mutableHttpResponse.body(),
                                    defaultResponseMediaType
                            );
                        }
                    } else if (body != null) {
                        boolean isReactive = finalRoute.isAsyncOrReactive() || Publishers.isConvertibleToPublisher(body);
                        if (isReactive && Publishers.isConvertibleToPublisher(body)) {
                            Class<?> bodyClass = body.getClass();
                            boolean isSingle = isSingle(finalRoute, bodyClass);
                            boolean isCompletable = !isSingle && finalRoute.isVoid() && Publishers.isCompletable(bodyClass);
                            if (isSingle || isCompletable) {
                                // full response case
                                Single<Object> single = Publishers.convertPublisher(body, Maybe.class)
                                        .switchIfEmpty(NOT_FOUND_SINGLE);
                                single.subscribe((o, throwable) -> {
                                    if (o == NOT_FOUND) {
                                        if (isCompletable || finalRoute.isVoid() || finalRoute.isSuspended()) {
                                            message.body(null);
                                            message.header(HttpHeaders.CONTENT_LENGTH, HttpHeaderValues.ZERO);
                                            writeFinalNettyResponse(
                                                    message,
                                                    request,
                                                    context
                                            );
                                        } else if (!isErrorRoute) {
                                            RouteMatch<Object> statusRoute = findStatusRoute(incomingRequest, HttpStatus.NOT_FOUND, finalRoute);
                                            if (statusRoute != null) {
                                                buildExecutableRoute(
                                                        statusRoute,
                                                        request,
                                                        context,
                                                        executor,
                                                        true,
                                                        true)
                                                        .execute();
                                            } else {
                                                emitDefaultNotFoundResponse(context, requestReference.get(), skipOncePerRequest);
                                            }
                                        } else {
                                            emitDefaultNotFoundResponse(context, requestReference.get(), skipOncePerRequest);
                                        }
                                    } else if (throwable != null) {
                                        exceptionCaughtInternal(
                                                context,
                                                throwable,
                                                request,
                                                false
                                        );
                                    } else {
                                        MutableHttpResponse<?> finalResponse;
                                        if (o instanceof HttpResponse) {
                                            finalResponse = toMutableResponse((HttpResponse<?>) o);
                                            o = finalResponse.body();
                                        } else {
                                            finalResponse = message;
                                        }
                                        encodeHttpResponse(
                                                context,
                                                request,
                                                finalResponse,
                                                o,
                                                defaultResponseMediaType
                                        );
                                    }
                                });

                            } else {
                                // streaming case
                                Argument<?> typeArgument = finalRoute.getReturnType().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                                boolean isHttp2 = request.getHttpVersion() == io.micronaut.http.HttpVersion.HTTP_2_0;
                                if (HttpResponse.class.isAssignableFrom(typeArgument.getType()) && !typeArgument.getFirstTypeVariable().map(Argument::isAsyncOrReactive).orElse(false)) {
                                    // a response stream
                                    Flowable<HttpResponse<?>> bodyFlowable = Publishers.convertPublisher(body, Flowable.class);

                                    // HTTP/2 allows sending multiple responses down a single stream
                                    if (isHttp2) {
                                        bodyFlowable.subscribe(httpResponse -> encodeHttpResponse(
                                                context,
                                                request,
                                                toNettyResponse(httpResponse),
                                                httpResponse.body(),
                                                defaultResponseMediaType
                                        ), throwable -> exceptionCaughtInternal(
                                                context,
                                                throwable,
                                                request,
                                                false
                                        ));
                                    } else {
                                        // HTTP/1 we take the first response or error
                                        bodyFlowable.firstOrError().subscribe((httpResponse, throwable) -> {
                                            if (throwable == null) {
                                                encodeHttpResponse(
                                                        context,
                                                        request,
                                                        toNettyResponse(httpResponse),
                                                        httpResponse.body(),
                                                        defaultResponseMediaType
                                                );
                                            } else {
                                                exceptionCaughtInternal(
                                                        context,
                                                        throwable,
                                                        request,
                                                        false
                                                );
                                            }
                                        });
                                    }
                                } else {
                                    boolean isJson = mediaType.getExtension().equals(MediaType.EXTENSION_JSON) && isJsonFormattable(typeArgument);
                                    Flowable<Object> bodyFlowable = Publishers.convertPublisher(body, Flowable.class);
                                    NettyByteBufferFactory byteBufferFactory = new NettyByteBufferFactory(context.alloc());

                                    Publisher<HttpContent> httpContentPublisher = Publishers.map(bodyFlowable, new Function<Object, HttpContent>() {
                                        boolean first = true;

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

                                                if (LOG.isDebugEnabled()) {
                                                    LOG.debug("Encoding emitted response object [{}] using codec: {}", message, codec);
                                                }
                                                ByteBuffer<ByteBuf> encoded = codec.encode(message, byteBufferFactory);
                                                httpContent = new DefaultHttpContent(encoded.asNativeBuffer());
                                            }
                                            if (!isJson || first) {
                                                first = false;
                                                return httpContent;
                                            } else {
                                                return HttpContentUtil.prefixComma(httpContent);
                                            }
                                        }
                                    });

                                    if (isJson) {
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
                                            if (!request.getHeaders().isKeepAlive()) {
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

                                    httpContentPublisher = Publishers.then(httpContentPublisher, httpContent ->
                                        // once an http content is written, read the next item if it is available
                                        context.read()
                                    );

                                    httpContentPublisher = Flowable.fromPublisher(httpContentPublisher)
                                            .doAfterTerminate(() -> cleanupRequest(context, request));

                                    DelegateStreamedHttpResponse streamedResponse = new DelegateStreamedHttpResponse(
                                            toNettyResponse(message).getNativeResponse(),
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
                    exceptionCaughtInternal(context, t, nettyHttpRequest, false);
                }
            });

            return null;
        });
        return route;
    }

    private Flowable<? extends MutableHttpResponse<?>> buildResultEmitter(
            NettyHttpRequest<?> request,
            AtomicReference<HttpRequest<?>> requestReference,
            RouteMatch<?> finalRoute,
            ExecutorService executor,
            boolean isErrorRoute,
            boolean skipOncePerRequest) {
        // build the result emitter. This result emitter emits the response from a controller action
        Flowable<MutableHttpResponse<?>> resultEmitter = Flowable.defer(() -> {
            final RouteMatch<?> routeMatch;

            // ensure the route requirements are completely satisfied
            if (!finalRoute.isExecutable()) {
                routeMatch = requestArgumentSatisfier
                        .fulfillArgumentRequirements(finalRoute, requestReference.get(), true);
            } else {
                routeMatch = finalRoute;
            }

            boolean isSuspended = routeMatch.isSuspended();

            Object body = routeMatch.execute();
            if (body instanceof Optional) {
                body = ((Optional<?>) body).orElse(null);
            }

            HttpRequest<?> incomingRequest = requestReference.get();
            MutableHttpResponse<?> outgoingResponse;

            if (body == null) {
                if (routeMatch.isVoid()) {
                    outgoingResponse = forStatus(routeMatch.getAnnotationMetadata());
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
                        Single<Object> single = Publishers.convertPublisher(body, Maybe.class)
                                .switchIfEmpty(NOT_FOUND_SINGLE);
                        return single.map(o -> {
                            MutableHttpResponse<?> singleResponse;
                            if (o instanceof Optional) {
                                o = ((Optional) o).orElse(NOT_FOUND);
                            }
                            if (o == NOT_FOUND) {
                                if (isCompletable || finalRoute.isVoid()) {
                                    singleResponse = forStatus(routeMatch.getAnnotationMetadata(), HttpStatus.OK)
                                            .header(HttpHeaders.CONTENT_LENGTH, HttpHeaderValues.ZERO);
                                } else {
                                    singleResponse = newNotFoundError(request);
                                }
                            } else {
                                if (o instanceof HttpResponse) {
                                    singleResponse = toMutableResponse((HttpResponse<?>) o);
                                } else {
                                    singleResponse = forStatus(routeMatch.getAnnotationMetadata(), defaultHttpStatus)
                                            .body(o);
                                }
                            }
                            singleResponse.setAttribute(HttpAttributes.ROUTE_MATCH, finalRoute);
                            return singleResponse;
                        }).toFlowable();
                    }
                }
                // now we have the raw result, transform it as necessary
                if (body instanceof HttpStatus) {
                    outgoingResponse = HttpResponse.status((HttpStatus) body);
                } else {
                    if (isSuspended) {
                        boolean isKotlinFunctionReturnTypeUnit =
                                routeMatch instanceof MethodBasedRouteMatch &&
                                        isKotlinFunctionReturnTypeUnit(((MethodBasedRouteMatch) routeMatch).getExecutableMethod());
                        final Supplier<CompletableFuture<?>> supplier = ContinuationArgumentBinder.extractContinuationCompletableFutureSupplier(incomingRequest);
                        if (isKotlinCoroutineSuspended(body)) {
                            return Flowable.create(emitter -> {
                                CompletableFuture<?> f = supplier.get();
                                f.whenComplete((o, throwable) -> {
                                    if (throwable != null) {
                                        emitter.onError(throwable);
                                    } else {
                                        MutableHttpResponse<?> response;
                                        if (o instanceof HttpResponse) {
                                            response = toMutableResponse((HttpResponse<?>) o);
                                        } else {
                                            response = forStatus(routeMatch.getAnnotationMetadata(), defaultHttpStatus);
                                            if (!isKotlinFunctionReturnTypeUnit) {
                                                response = response.body(o);
                                            }
                                        }
                                        response.setAttribute(HttpAttributes.ROUTE_MATCH, finalRoute);
                                        emitter.onNext(response);
                                        emitter.onComplete();
                                    }
                                });
                            }, BackpressureStrategy.ERROR);
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
                                outgoingResponse = forStatus(routeMatch.getAnnotationMetadata(), defaultHttpStatus)
                                        .body(suspendedBody);
                            }
                        }

                    } else {
                        if (body instanceof HttpResponse) {
                            outgoingResponse = toMutableResponse((HttpResponse<?>) body);
                        } else {
                            outgoingResponse = forStatus(routeMatch.getAnnotationMetadata(), defaultHttpStatus)
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
            return Flowable.just(outgoingResponse);
        });

        // process the publisher through the available filters
        return filterPublisher(
                requestReference,
                resultEmitter,
                executor,
                skipOncePerRequest
        );
    }

    private void encodeHttpResponse(
            ChannelHandlerContext context,
            NettyHttpRequest<?> nettyRequest,
            MutableHttpResponse<?> response,
            Object body,
            MediaType defaultResponseMediaType) {
        boolean isNotHead = nettyRequest.getMethod() != HttpMethod.HEAD;
        if (isNotHead && body instanceof Writable) {
            Writable writable = (Writable) body;
            getIoExecutor().execute(() -> {
                ByteBuf byteBuf = context.alloc().ioBuffer(128);
                ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf);
                try {
                    writable.writeTo(outputStream, nettyRequest.getCharacterEncoding());
                    response.body(byteBuf);
                    if (!response.getHeaders().contains(HttpHeaders.CONTENT_TYPE)) {
                        response.header(HttpHeaders.CONTENT_TYPE, defaultResponseMediaType);
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

            try {
                if (isNotHead) {

                    encodeResponseBody(
                            context,
                            nettyRequest,
                            response,
                            body,
                            defaultResponseMediaType
                    );

                }
                writeFinalNettyResponse(
                        response,
                        nettyRequest,
                        context
                );
            } catch (Exception e) {
                exceptionCaughtInternal(context, e, nettyRequest, false);
            }
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
            MediaType defaultResponseMediaType) {
        if (body == null) {
            return;
        }

        MediaType specifiedMediaType = message.getContentType().orElse(null);
        MediaType responseMediaType = specifiedMediaType != null ? specifiedMediaType : defaultResponseMediaType;
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
                Optional<MediaTypeCodec> registeredCodec = mediaTypeCodecRegistry.findCodec(defaultResponseMediaType, body.getClass());
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
        io.netty.handler.codec.http.HttpResponse nettyResponse = NettyHttpResponseBuilder.toHttpResponse(message);
        io.netty.handler.codec.http.HttpHeaders nettyHeaders = nettyResponse.headers();
        HttpStatus httpStatus = message.status();

        // default Connection header if not set explicitly
        final io.micronaut.http.HttpVersion httpVersion = request.getHttpVersion();
        final boolean isHttp2 = httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0;
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

        final Object body = message.body();
        if (body instanceof NettyCustomizableResponseTypeHandlerInvoker) {
            NettyCustomizableResponseTypeHandlerInvoker handler = (NettyCustomizableResponseTypeHandlerInvoker) body;
            handler.invoke(request, message, context);
        } else {
            // default to Transfer-Encoding: chunked if Content-Length not set or not already set
            if (!nettyHeaders.contains(HttpHeaderNames.CONTENT_LENGTH) && !nettyHeaders.contains(HttpHeaderNames.TRANSFER_ENCODING)) {
                nettyHeaders.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            }
            // close handled by HttpServerKeepAliveHandler
            final NettyHttpRequest<?> nettyHttpRequest = (NettyHttpRequest<?>) request;

            if (isHttp2) {
                addHttp2StreamHeader(request, nettyResponse);
            }

            context.writeAndFlush(nettyResponse)
                    .addListener(future -> {
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
                    });

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
            HttpResponseStatus nettyStatus = HttpResponseStatus.valueOf(
                    httpStatus.getCode(),
                    httpStatus.getReason()
            );
            Object body = message.body();
            FullHttpResponse nettyResponse;
            if (body instanceof ByteBuf) {
                nettyResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, nettyStatus, (ByteBuf) body);
            } else {
                nettyResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, nettyStatus);
            }
            io.netty.handler.codec.http.HttpHeaders nettyHeaders = nettyResponse.headers();
            message.getHeaders().forEach((BiConsumer<String, List<String>>) nettyHeaders::set);
            nettyHttpResponse = new NettyMutableHttpResponse<>(
                    nettyResponse,
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
            if (LOG.isDebugEnabled()) {
                LOG.debug("Encoding emitted response object [{}] using codec: {}", body, codec);
            }
            byteBuf = codec.encode(body, new NettyByteBufferFactory(context.alloc())).asNativeBuffer();
        }
        return byteBuf;
    }

    private MutableHttpResponse<Object> forStatus(AnnotationMetadata annotationMetadata) {
        return forStatus(annotationMetadata, HttpStatus.OK);
    }

    private MutableHttpResponse<Object> forStatus(AnnotationMetadata annotationMetadata, HttpStatus defaultStatus) {
        return HttpResponse.status(
                annotationMetadata.enumValue(Status.class, HttpStatus.class)
                        .orElse(defaultStatus));
    }

    private Flowable<? extends MutableHttpResponse<?>> filterPublisher(
            AtomicReference<HttpRequest<?>> requestReference,
            Publisher<MutableHttpResponse<?>> routePublisher,
            @Nullable ExecutorService executor,
            boolean skipOncePerRequest) {
        Publisher<? extends io.micronaut.http.MutableHttpResponse<?>> finalPublisher;
        List<HttpFilter> filters = new ArrayList<>(router.findFilters(requestReference.get()));
        if (skipOncePerRequest) {
            filters.removeIf(filter -> filter instanceof OncePerRequestHttpServerFilter);
        }
        if (!filters.isEmpty()) {
            // make the action executor the last filter in the chain
            filters.add((HttpServerFilter) (req, chain) -> {
                if (executor != null) {
                    if (routePublisher instanceof Flowable) {
                        return ((Flowable<MutableHttpResponse<?>>) routePublisher)
                                .subscribeOn(Schedulers.from(executor));
                    } else {
                        return Flowable.fromPublisher(routePublisher)
                                .subscribeOn(Schedulers.from(executor));
                    }
                } else {
                    return routePublisher;
                }
            });

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

        if (finalPublisher instanceof Flowable) {
            return (Flowable<? extends MutableHttpResponse<?>>) finalPublisher;
        } else {
            return Flowable.fromPublisher(finalPublisher);
        }
    }

    private void writeDefaultErrorResponse(ChannelHandlerContext ctx, NettyHttpRequest nettyHttpRequest, Throwable cause, boolean skipOncePerRequest) {
        logException(cause);

        JsonError body = new JsonError("Internal Server Error: " + cause.getMessage());
        MutableHttpResponse<Object> error = io.micronaut.http.HttpResponse.serverError()
                .body(body);
        filterAndEncodeResponse(
                ctx,
                nettyHttpRequest,
                nettyHttpRequest,
                error,
                MediaType.APPLICATION_JSON_TYPE,
                skipOncePerRequest);
    }

    private void logException(Throwable cause) {
        //handling connection reset by peer exceptions
        String message = cause.getMessage();
        if (cause instanceof IOException && message != null && IGNORABLE_ERROR_MESSAGE.matcher(message).matches()) {
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
