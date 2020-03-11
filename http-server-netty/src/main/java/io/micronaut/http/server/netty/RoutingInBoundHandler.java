/*
 * Copyright 2017-2020 original authors
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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.annotations.Nullable;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.context.BeanContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
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
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.*;
import io.micronaut.http.annotation.Produces;
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
import io.micronaut.scheduling.executor.ThreadSelection;
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
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.functions.LongConsumer;
import io.reactivex.processors.UnicastProcessor;
import io.reactivex.schedulers.Schedulers;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.micronaut.core.util.KotlinUtils.isKotlinCoroutineSuspended;
import static io.micronaut.inject.util.KotlinExecutableMethodUtils.isKotlinFunctionReturnTypeUnit;

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

    private final Router router;
    private final ExecutorSelector executorSelector;
    private final StaticResourceResolver staticResourceResolver;
    private final ExecutorService ioExecutor;
    private final BeanContext beanContext;
    private final NettyHttpServerConfiguration serverConfiguration;
    private final HttpContentProcessorResolver httpContentProcessorResolver;
    private final RequestArgumentSatisfier requestArgumentSatisfier;
    private final MediaTypeCodecRegistry mediaTypeCodecRegistry;
    private final NettyCustomizableResponseTypeHandlerRegistry customizableResponseTypeHandlerRegistry;

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
        ExecutorService ioExecutor,
        HttpContentProcessorResolver httpContentProcessorResolver) {
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.customizableResponseTypeHandlerRegistry = customizableResponseTypeHandlerRegistry;
        this.beanContext = beanContext;
        this.staticResourceResolver = staticResourceResolver;
        this.ioExecutor = ioExecutor;
        this.executorSelector = executorSelector;
        this.router = router;
        this.requestArgumentSatisfier = requestArgumentSatisfier;
        this.serverConfiguration = serverConfiguration;
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
                                         Throwable cause,
                                         NettyHttpRequest nettyHttpRequest,
                                         boolean nettyException) {
        RouteMatch<?> errorRoute = null;
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
        } else if (cause instanceof BeanInstantiationException && declaringType != null) {
            // If the controller could not be instantiated, don't look for a local error route
            Optional<Class> rootBeanType = ((BeanInstantiationException) cause).getRootBeanType().map(BeanType::getBeanType);
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
            MediaType defaultResponseMediaType = resolveDefaultResponseContentType(
                    nettyHttpRequest,
                    errorRoute
            );
            try {
                final MethodBasedRouteMatch<?, ?> methodBasedRoute = (MethodBasedRouteMatch) errorRoute;
                Class<?> javaReturnType = errorRoute.getReturnType().getType();
                boolean isFuture = CompletionStage.class.isAssignableFrom(javaReturnType);
                boolean isReactiveReturnType = Publishers.isConvertibleToPublisher(javaReturnType) || isFuture;
                boolean isKotlinSuspendingFunction = methodBasedRoute.getExecutableMethod().isSuspend();
                boolean isKotlinFunctionReturnTypeUnit = isKotlinSuspendingFunction &&
                        isKotlinFunctionReturnTypeUnit(methodBasedRoute.getExecutableMethod());
                Flowable resultFlowable = Flowable.defer(() -> {
                      Object result = methodBasedRoute.execute();
                      MutableHttpResponse<?> response = errorResultToResponse(result, methodBasedRoute);
                      response.setAttribute(HttpAttributes.ROUTE_MATCH, methodBasedRoute);
                      return Flowable.just(response);
                });

                AtomicReference<HttpRequest<?>> requestReference = new AtomicReference<>(nettyHttpRequest);
                Flowable<MutableHttpResponse<?>> routePublisher = buildRoutePublisher(
                        methodBasedRoute.getDeclaringType(),
                        methodBasedRoute.getReturnType(),
                        isReactiveReturnType,
                        isKotlinSuspendingFunction,
                        isKotlinFunctionReturnTypeUnit,
                        methodBasedRoute.getAnnotationMetadata(),
                        requestReference,
                        resultFlowable);

                Flowable<? extends MutableHttpResponse<?>> filteredPublisher = filterPublisher(
                        requestReference,
                        routePublisher,
                        ctx.channel().eventLoop(),
                        nettyException);

                subscribeToResponsePublisher(
                        ctx,
                        defaultResponseMediaType,
                        requestReference,
                        filteredPublisher
                );

                if (serverConfiguration.isLogHandledExceptions()) {
                    logException(cause);
                }

            } catch (Throwable e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Exception occurred executing error handler. Falling back to default error handling: " + e.getMessage(), e);
                }
                writeDefaultErrorResponse(ctx, nettyHttpRequest, e);
            }
        } else {

            Optional<ExceptionHandler> exceptionHandler = beanContext
                    .findBean(ExceptionHandler.class, Qualifiers.byTypeArgumentsClosest(cause.getClass(), Object.class));

            if (exceptionHandler.isPresent()) {
                ExceptionHandler handler = exceptionHandler.get();
                MediaType defaultResponseMediaType = MediaType.fromType(handler.getClass()).orElse(MediaType.APPLICATION_JSON_TYPE);
                try {
                    Flowable resultFlowable = Flowable.defer(() -> {
                        Object result = handler.handle(nettyHttpRequest, cause);
                        MutableHttpResponse<?> response = errorResultToResponse(result, null);
                        return Flowable.just(response);
                    });

                    AtomicReference<HttpRequest<?>> requestReference = new AtomicReference<>(nettyHttpRequest);
                    Flowable<MutableHttpResponse<?>> routePublisher = buildRoutePublisher(
                            handler.getClass(),
                            ReturnType.of(HttpResponse.class),
                            false,
                            false,
                            false,
                            AnnotationMetadata.EMPTY_METADATA,
                            requestReference,
                            resultFlowable);

                    Flowable<? extends MutableHttpResponse<?>> filteredPublisher = filterPublisher(
                            requestReference,
                            routePublisher,
                            ctx.channel().eventLoop(),
                            nettyException);

                    subscribeToResponsePublisher(
                            ctx,
                            defaultResponseMediaType,
                            requestReference,
                            filteredPublisher
                    );

                    if (serverConfiguration.isLogHandledExceptions()) {
                        logException(cause);
                    }
                } catch (Throwable e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Exception occurred executing error handler. Falling back to default error handling.");
                    }
                    writeDefaultErrorResponse(ctx, nettyHttpRequest, e);
                }
            } else {
                logException(cause);

                Flowable resultFlowable = Flowable.defer(() ->
                        Flowable.just(HttpResponse.serverError().body(new JsonError("Internal Server Error: " + cause.getMessage())))
                );

                AtomicReference<HttpRequest<?>> requestReference = new AtomicReference<>(nettyHttpRequest);
                Flowable<MutableHttpResponse<?>> routePublisher = buildRoutePublisher(
                        null,
                        ReturnType.of(HttpResponse.class),
                        false,
                        false,
                        false,
                        AnnotationMetadata.EMPTY_METADATA,
                        requestReference,
                        resultFlowable);

                Flowable<? extends MutableHttpResponse<?>> filteredPublisher = filterPublisher(
                        requestReference,
                        routePublisher,
                        ctx.channel().eventLoop(),
                        nettyException);

                subscribeToResponsePublisher(
                        ctx,
                        MediaType.APPLICATION_JSON_TYPE,
                        requestReference,
                        filteredPublisher
                );
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
                    emitDefaultNotFoundResponse(ctx, request);
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
            handleRouteMatch(route, nettyHttpRequest, ctx);
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
            handleRouteMatch(routeMatch, nettyHttpRequest, ctx);
        } else {

            if (request.getMethod() != HttpMethod.HEAD) {
                JsonError error = newError(request, message);
                defaultResponse.body(error);
            }


            AtomicReference<HttpRequest<?>> requestReference = new AtomicReference<>(request);
            Flowable<? extends MutableHttpResponse<?>> responsePublisher = filterPublisher(
                    requestReference,
                    Flowable.just(defaultResponse),
                    ctx.channel().eventLoop(),
                    false
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
                ctx.channel().eventLoop(),
                false
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

    private MutableHttpResponse errorResultToResponse(Object result, @Nullable RouteMatch routeMatch) {
        MutableHttpResponse<?> response;
        if (result instanceof HttpResponse) {
            response = ConversionService.SHARED.convert(result, NettyMutableHttpResponse.class)
                    .orElseThrow(() -> new InternalServerException("Emitted response is not mutable"));
        } else {
            if (result instanceof HttpStatus) {
                response = HttpResponse.status((HttpStatus) result);
            } else {
                if (routeMatch != null) {
                    response = forStatus(routeMatch.getAnnotationMetadata(), HttpStatus.INTERNAL_SERVER_ERROR).body(result);
                } else {
                    response = HttpResponse.serverError().body(result);
                }
            }
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
            httpContentProcessorResolver.resolve(request, route).subscribe(buildSubscriber(request, context, route));
        } else {
            context.read();
            route = prepareRouteForExecution(route, request);
            route.execute();
        }
    }

    private boolean isJsonFormattable(Class javaType) {
        return !(javaType == byte[].class
                || ByteBuffer.class.isAssignableFrom(javaType)
                || ByteBuf.class.isAssignableFrom(javaType));
    }

    private Subscriber<Object> buildSubscriber(NettyHttpRequest<?> request,
                                               ChannelHandlerContext context,
                                               RouteMatch<?> finalRoute) {
        return new CompletionAwareSubscriber<Object>() {
            Boolean alwaysAddContent = request.getContentType()
                    .map(type -> type.equals(MediaType.APPLICATION_FORM_URLENCODED_TYPE))
                    .orElse(false);
            RouteMatch<?> routeMatch = finalRoute;
            AtomicBoolean executed = new AtomicBoolean(false);
            AtomicLong pressureRequested = new AtomicLong(0);
            ConcurrentHashMap<String, UnicastProcessor> subjects = new ConcurrentHashMap<>();
            ConcurrentHashMap<Integer, HttpDataReference> dataReferences = new ConcurrentHashMap<>();
            ConversionService conversionService = ConversionService.SHARED;
            Subscription s;
            LongConsumer onRequest = (num) -> pressureRequested.updateAndGet((p) -> {
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
                                HttpDataReference dataReference = dataReferences.computeIfAbsent(dataKey, (key) -> new HttpDataReference(data));
                                Argument typeVariable;

                                if (StreamingFileUpload.class.isAssignableFrom(argument.getType())) {
                                    typeVariable = ARGUMENT_PART_DATA;
                                } else {
                                    typeVariable = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                                }
                                Class typeVariableType = typeVariable.getType();

                                UnicastProcessor namedSubject = subjects.computeIfAbsent(name, (key) -> UnicastProcessor.create());

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
                                                        ioExecutor,
                                                        flowable));
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
                                    HttpDataReference.Component component = dataReference.addComponent((e) -> {
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
                                                    ioExecutor,
                                                    processFlowable(subject, dataKey, true));
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
                    writeDefaultErrorResponse(context, request, e);
                }
            }

            @Override
            protected void doOnComplete() {
                for (UnicastProcessor subject: subjects.values()) {
                    if (!subject.hasComplete()) {
                        subject.onComplete();
                    }
                }
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
        final ThreadSelection threadSelection = serverConfiguration.getThreadSelection();
        switch (threadSelection) {
            case MANUAL:
                if (route instanceof MethodReference) {
                    executor = executorSelector.select((MethodReference) route, threadSelection).orElse(null);
                } else {
                    executor = null;
                }
            break;
            case IO:
                executor = ioExecutor;
            break;
            case AUTO:
            default:
                if (route instanceof MethodReference) {
                    executor = executorSelector.select((MethodReference) route, threadSelection).orElse(null);
                } else {
                    executor = null;
                }
                break;
        }


        route = route.decorate(finalRoute -> {
            MediaType defaultResponseMediaType = resolveDefaultResponseContentType(
                    request,
                    finalRoute
            );
            ReturnType<?> genericReturnType = finalRoute.getReturnType();
            Class<?> javaReturnType = genericReturnType.getType();

            AtomicReference<io.micronaut.http.HttpRequest<?>> requestReference = new AtomicReference<>(request);
            boolean isFuture = CompletionStage.class.isAssignableFrom(javaReturnType);
            boolean isReactiveReturnType = Publishers.isConvertibleToPublisher(javaReturnType) || isFuture;
            boolean isKotlinSuspendingFunction =
                    finalRoute instanceof MethodBasedRouteMatch &&
                    ((MethodBasedRouteMatch) finalRoute).getExecutableMethod().isSuspend();
            boolean isKotlinFunctionReturnTypeUnit = isKotlinSuspendingFunction &&
                    isKotlinFunctionReturnTypeUnit(((MethodBasedRouteMatch) finalRoute).getExecutableMethod());
            boolean isSingle =
                    isReactiveReturnType && Publishers.isSingle(javaReturnType) ||
                            isResponsePublisher(genericReturnType, javaReturnType) ||
                                isFuture ||
                                    finalRoute.getAnnotationMetadata().booleanValue(Produces.class, "single").orElse(false) ||
                                        isKotlinSuspendingFunction;

            // build the result emitter. This result emitter emits the response from a controller action
            Flowable<?> resultEmitter = buildResultEmitter(
                    context,
                    finalRoute,
                    requestReference,
                    isReactiveReturnType,
                    isKotlinSuspendingFunction,
                    isKotlinFunctionReturnTypeUnit,
                    isSingle
            );

            // here we transform the result of the controller action into a MutableHttpResponse
            Flowable<MutableHttpResponse<?>> routePublisher = resultEmitter.map((message) -> {
                RouteMatch<?> routeMatch = finalRoute;
                MutableHttpResponse<?> finalResponse = messageToResponse(routeMatch, message);
                if (requestReference.get().getMethod().equals(HttpMethod.HEAD)) {
                    final Object o = finalResponse.getBody().orElse(null);
                    if (o instanceof ReferenceCounted) {
                        ((ReferenceCounted) o).release();
                    }
                    finalResponse.body(null);
                }
                HttpStatus status = finalResponse.getStatus();

                if (status.getCode() >= HttpStatus.BAD_REQUEST.getCode()) {
                    Class declaringType = ((MethodBasedRouteMatch) routeMatch).getDeclaringType();
                    // handle re-mapping of errors
                    Optional<RouteMatch<Object>> statusRoute = Optional.empty();
                    // if declaringType is not null, this means its a locally marked method handler
                    if (declaringType != null) {
                        statusRoute = router.findStatusRoute(declaringType, status, request);
                    }
                    io.micronaut.http.HttpRequest<?> httpRequest = requestReference.get();
                    if (!statusRoute.isPresent()) {
                        statusRoute = router.findStatusRoute(status, httpRequest);
                    }

                    if (statusRoute.isPresent()) {
                        routeMatch = statusRoute.get();
                        httpRequest.setAttribute(HttpAttributes.ROUTE_MATCH, routeMatch);

                        routeMatch = requestArgumentSatisfier.fulfillArgumentRequirements(routeMatch, httpRequest, true);

                        if (routeMatch.isExecutable()) {
                            Object result;
                            try {
                                result = routeMatch.execute();
                                finalResponse = messageToResponse(routeMatch, result);
                            } catch (Throwable e) {
                                throw new InternalServerException("Error executing status route [" + routeMatch + "]: " + e.getMessage(), e);
                            }
                        } else {
                            if (LOG.isWarnEnabled()) {
                                LOG.warn("Matched status route [" + routeMatch + "] not executed because one or more arguments could not be bound. Returning the original response.");
                            }
                        }
                    }

                }
                finalResponse.setAttribute(HttpAttributes.ROUTE_MATCH, routeMatch);
                return finalResponse;
            });

            routePublisher = buildRoutePublisher(
                    finalRoute.getDeclaringType(),
                    genericReturnType,
                    isReactiveReturnType,
                    isKotlinSuspendingFunction,
                    isKotlinFunctionReturnTypeUnit,
                    finalRoute.getAnnotationMetadata(),
                    requestReference,
                    routePublisher
            );

            // process the publisher through the available filters
            Flowable<? extends MutableHttpResponse<?>> filteredPublisher = filterPublisher(
                    requestReference,
                    routePublisher,
                    executor,
                    false
            );

            boolean isStreaming = isReactiveReturnType && !isSingle;

            Optional<Class<?>> javaPayloadType = genericReturnType.getFirstTypeVariable().map(Argument::getType);

            if (!isStreaming) {
                if (HttpResponse.class.isAssignableFrom(javaReturnType)) {
                    Optional<Argument<?>> generic = genericReturnType.getFirstTypeVariable();
                    if (generic.isPresent()) {
                        // Unwrap response type information
                        Class genericType = generic.get().getType();
                        isStreaming = Publishers.isConvertibleToPublisher(genericType) && !Publishers.isSingle(genericType);

                        if (isStreaming) {
                            javaPayloadType = generic.get().getFirstTypeVariable().map(Argument::getType);
                        }
                    }
                }
            }

            Class finalJavaPayloadType = javaPayloadType.orElse(Object.class);
            boolean finalIsStreaming = isStreaming;
            filteredPublisher  = filteredPublisher.switchMap((response) -> {
                Optional<?> responseBody = response.getBody();
                if (responseBody.isPresent()) {
                    Object body = responseBody.get();
                    if (finalIsStreaming) {
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

                        boolean isJson = responseMediaType.getExtension().equals(MediaType.EXTENSION_JSON) &&
                                isJsonFormattable(finalJavaPayloadType);

                        streamHttpContentChunkByChunk(
                                context,
                                request,
                                nettyResponse,
                                responseMediaType,
                                isJson,
                                bodyFlowable);
                    }
                });
            }

            return null;
        });
        return route;
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

    private Flowable<MutableHttpResponse<?>> buildRoutePublisher(
            Class<?> declaringType,
            ReturnType<?> genericReturnType,
            boolean isReactiveReturnType,
            boolean isKotlinSuspendingFunction,
            boolean isKotlinFunctionReturnTypeUnit,
            AnnotationMetadata annotationMetadata,
            AtomicReference<HttpRequest<?>> requestReference,
            Flowable<MutableHttpResponse<?>> routePublisher) {
        // In the case of an empty reactive type we switch handling so that
        // a 404 NOT_FOUND is returned
        routePublisher = routePublisher.switchIfEmpty(Flowable.create((emitter) -> {
            HttpRequest<?> httpRequest = requestReference.get();
            MutableHttpResponse<?> response;
            Class<?> javaReturnType = genericReturnType.getType();
            boolean isVoid = javaReturnType == void.class ||
                    Completable.class.isAssignableFrom(javaReturnType) ||
                    (isReactiveReturnType && genericReturnType.getFirstTypeVariable()
                            .filter(arg -> arg.getType() == Void.class).isPresent()) ||
                    (isKotlinSuspendingFunction && isKotlinFunctionReturnTypeUnit);

            if (isVoid) {
                // void return type with no response, nothing else to do
                response = forStatus(annotationMetadata);
            } else {
                // handle re-mapping of errors
                Optional<RouteMatch<Object>> statusRoute = Optional.empty();
                // if declaringType is not null, this means its a locally marked method handler
                if (declaringType != null) {
                    statusRoute = router.findStatusRoute(declaringType, HttpStatus.NOT_FOUND, httpRequest);
                }
                if (!statusRoute.isPresent()) {
                    statusRoute = router.findStatusRoute(HttpStatus.NOT_FOUND, httpRequest);
                }

                if (statusRoute.isPresent()) {
                    RouteMatch<?> newRoute = requestArgumentSatisfier.fulfillArgumentRequirements(statusRoute.get(), httpRequest, true);

                    if (newRoute.isExecutable()) {
                        try {
                            Object result = newRoute.execute();
                            response = messageToResponse(newRoute, result);
                        } catch (Throwable e) {
                            emitter.onError(new InternalServerException("Error executing status route [" + newRoute + "]: " + e.getMessage(), e));
                            return;
                        }

                    } else {
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("Matched status route [" + newRoute + "] not executed because one or more arguments could not be bound. Returning a default response.");
                        }
                        response = newNotFoundError(httpRequest);
                    }
                    response.setAttribute(HttpAttributes.ROUTE_MATCH, newRoute);
                } else {
                    response = newNotFoundError(httpRequest);
                }
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
                final NettyHttpRequest nettyHttpRequest = (NettyHttpRequest) requestReference.get();
                exceptionCaughtInternal(context, t, nettyHttpRequest, false);
            }
        });
    }

    private void writeFinalNettyResponse(MutableHttpResponse<?> message, AtomicReference<HttpRequest<?>> requestReference, ChannelHandlerContext context) {
        NettyMutableHttpResponse<?> nettyHttpResponse = (NettyMutableHttpResponse<?>) message;
        FullHttpResponse nettyResponse = nettyHttpResponse.getNativeResponse();

        HttpRequest<?> httpRequest = requestReference.get();
        io.netty.handler.codec.http.HttpHeaders nettyHeaders = nettyResponse.headers();

        // default Connection header if not set explicitly
        final io.micronaut.http.HttpVersion httpVersion = httpRequest.getHttpVersion();
        final boolean isHttp2 = httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0;
        if (!isHttp2) {
            if (!nettyHeaders.contains(HttpHeaderNames.CONNECTION)) {
                boolean expectKeepAlive = nettyResponse.protocolVersion().isKeepAliveDefault() || httpRequest.getHeaders().isKeepAlive();
                HttpStatus status = nettyHttpResponse.status();
                if (!expectKeepAlive || status.getCode() > 299) {
                    nettyHeaders.add(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                } else {
                    nettyHeaders.add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                }
            }
        }

        final Object body = message.body();
        if (body instanceof NettyCustomizableResponseTypeHandlerInvoker) {
            NettyCustomizableResponseTypeHandlerInvoker handler = (NettyCustomizableResponseTypeHandlerInvoker) body;
            handler.invoke(httpRequest, nettyHttpResponse, context);
        } else {
            // default to Transfer-Encoding: chunked if Content-Length not set or not already set
            if (!nettyHeaders.contains(HttpHeaderNames.CONTENT_LENGTH) && !nettyHeaders.contains(HttpHeaderNames.TRANSFER_ENCODING)) {
                nettyHeaders.add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            }
            // close handled by HttpServerKeepAliveHandler
            final NettyHttpRequest<?> nettyHttpRequest = (NettyHttpRequest<?>) requestReference.get();

            if (isHttp2) {
                final io.netty.handler.codec.http.HttpHeaders nativeHeaders = nettyHttpRequest.getNativeRequest().headers();
                final String streamId = nativeHeaders.get(NettyHttpRequest.STREAM_ID);
                if (streamId != null) {
                    nettyResponse.headers().set(NettyHttpRequest.STREAM_ID, streamId);
                }
            }

            context.writeAndFlush(nettyResponse)
                   .addListener(future -> {
                       context.read();
                       cleanupRequest(context, nettyHttpRequest);
                   });

        }
    }

    private MutableHttpResponse<?> encodeBodyWithCodec(MutableHttpResponse<?> response,
                                                       Object body,
                                                       MediaTypeCodec codec,
                                                       MediaType mediaType,
                                                       ChannelHandlerContext context,
                                                       AtomicReference<HttpRequest<?>> requestReference) {
        ByteBuf byteBuf;
        try {
            byteBuf = encodeBodyAsByteBuf(body, codec, context, requestReference);
            int len = byteBuf.readableBytes();
            MutableHttpHeaders headers = response.getHeaders();
            if (!headers.contains(HttpHeaders.CONTENT_TYPE)) {
                headers.add(HttpHeaderNames.CONTENT_TYPE, mediaType);
            }
            headers.remove(HttpHeaders.CONTENT_LENGTH);
            headers.add(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(len));

            setBodyContent(response, byteBuf);
            return response;
        } catch (LinkageError e) {
            // rxjava swallows linkage errors for some reasons so if one occurs, rethrow as a internal error
            throw new InternalServerException("Fatal error encoding bytebuf: " + e.getMessage() , e);
        }
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
                byteBuf = Unpooled.wrappedBuffer(byteBuffer.asNioBuffer());
            }
        } else if (body instanceof byte[]) {
            byteBuf = Unpooled.wrappedBuffer((byte[]) body);

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
            boolean isKotlinSuspendingFunction,
            boolean isKotlinFunctionReturnTypeUnit,
            boolean isSingleResult) {
        Flowable<?> resultEmitter;
        if (isReactiveReturnType || isKotlinSuspendingFunction) {
            // if the return type is reactive, execute the action and obtain the Observable
            try {
                if (isSingleResult) {
                    // for a single result we are fine as is
                    resultEmitter = Flowable.defer(() -> {
                        final RouteMatch<?> routeMatch = !finalRoute.isExecutable() ? requestArgumentSatisfier.fulfillArgumentRequirements(finalRoute, requestReference.get(), true) : finalRoute;
                        if (isKotlinSuspendingFunction) {
                            return executeKotlinSuspendingFunction(isKotlinFunctionReturnTypeUnit, routeMatch, requestReference.get());
                        } else {
                            Object result = routeMatch.execute();
                            return Publishers.convertPublisher(result, Publisher.class);
                        }
                    });
                } else {
                    // for a streaming response we wrap the result on an HttpResponse so that a single result is received
                    // then the result can be streamed chunk by chunk
                    resultEmitter = Flowable.create((emitter) -> {
                        final RouteMatch<?> routeMatch = !finalRoute.isExecutable() ? requestArgumentSatisfier.fulfillArgumentRequirements(finalRoute, requestReference.get(), true) : finalRoute;
                        Object result = routeMatch.execute();
                        MutableHttpResponse<Object> chunkedResponse = HttpResponse.ok(result);
                        chunkedResponse.getHeaders().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                        emitter.onNext(chunkedResponse);
                        emitter.onComplete();
                        // should be no back pressure
                    }, BackpressureStrategy.ERROR);
                }
            } catch (Throwable e) {
                resultEmitter = Flowable.error(new InternalServerException("Error executing route [" + finalRoute + "]: " + e.getMessage(), e));
            }
        } else {
            // for non-reactive results we build flowable that executes the
            // route
            resultEmitter = Flowable.defer(() -> {
                HttpRequest<?> httpRequest = requestReference.get();
                RouteMatch<?> routeMatch = finalRoute;
                if (!routeMatch.isExecutable()) {
                    routeMatch = requestArgumentSatisfier.fulfillArgumentRequirements(routeMatch, httpRequest, true);
                }
                Object result = routeMatch.execute();

                if (result instanceof Optional) {
                    result = ((Optional<?>) result).orElse(null);
                }

                if (result == null) {
                    // empty flowable
                    return Flowable.empty();
                } else {
                    // emit the result
                    if (result instanceof Writable) {
                        Writable writable = (Writable) result;
                        return Flowable.fromCallable(() -> {
                            ByteBuf byteBuf = context.alloc().ioBuffer(128);
                            ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf);
                            writable.writeTo(outputStream, requestReference.get().getCharacterEncoding());
                            return byteBuf;
                        }).subscribeOn(Schedulers.from(ioExecutor));

                    } else {
                        return Publishers.just(result);
                    }
                }
            });
        }
        return resultEmitter;
    }

    private Publisher<?> executeKotlinSuspendingFunction(boolean isKotlinFunctionReturnTypeUnit, RouteMatch<?> routeMatch, HttpRequest<?> source) {
        final Supplier<CompletableFuture<?>> supplier = ContinuationArgumentBinder.extractContinuationCompletableFutureSupplier(source);
        Object result = routeMatch.execute();
        if (isKotlinCoroutineSuspended(result)) {
            if (isKotlinFunctionReturnTypeUnit) {
                return Completable.fromPublisher(Publishers.fromCompletableFuture(supplier.get())).toFlowable();
            } else {
                return Publishers.fromCompletableFuture(supplier.get());
            }
        } else {
            if (isKotlinFunctionReturnTypeUnit) {
                return Completable.complete().toFlowable();
            } else {
                return Flowable.just(result);
            }
        }
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
                response = forStatus(finalRoute.getAnnotationMetadata()).body(message);
            }
        }
        return response;
    }

    private MutableHttpResponse<Object> forStatus(AnnotationMetadata annotationMetadata) {
        return forStatus(annotationMetadata, HttpStatus.OK);
    }

    private MutableHttpResponse<Object> forStatus(AnnotationMetadata annotationMetadata, HttpStatus defaultStatus) {
        return HttpResponse.status(
                annotationMetadata.enumValue(Status.class, HttpStatus.class)
                        .orElse(defaultStatus));
    }

    private boolean isResponsePublisher(ReturnType<?> genericReturnType, Class<?> javaReturnType) {
        return Publishers.isConvertibleToPublisher(javaReturnType) && genericReturnType.getFirstTypeVariable().map(arg -> HttpResponse.class.isAssignableFrom(arg.getType())).orElse(false);
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

        if (executor != null) {
            // Handle the scheduler to subscribe on
            if (finalPublisher instanceof Flowable) {
                return ((Flowable<MutableHttpResponse<?>>) finalPublisher)
                        .subscribeOn(Schedulers.from(executor));
            } else {
                return Flowable.fromPublisher(finalPublisher)
                        .subscribeOn(Schedulers.from(executor));
            }
        } else {
            if (finalPublisher instanceof Flowable) {
                return (Flowable<? extends MutableHttpResponse<?>>) finalPublisher;
            } else {
                return Flowable.fromPublisher(finalPublisher);
            }
        }
    }

    private void streamHttpContentChunkByChunk(
        ChannelHandlerContext context,
        NettyHttpRequest<?> request,
        FullHttpResponse nativeResponse,
        MediaType mediaType,
        boolean isJson,
        Publisher<Object> publisher) {

        NettyByteBufferFactory byteBufferFactory = new NettyByteBufferFactory(context.alloc());

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

        httpContentPublisher = Flowable.fromPublisher(httpContentPublisher)
                .doAfterTerminate(() -> cleanupRequest(context, request));

        DelegateStreamedHttpResponse streamedResponse = new DelegateStreamedHttpResponse(nativeResponse, httpContentPublisher);
        io.netty.handler.codec.http.HttpHeaders headers = streamedResponse.headers();
        headers.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        headers.set(HttpHeaderNames.CONTENT_TYPE, mediaType);
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
