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

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.Writable;
import io.micronaut.core.io.buffer.ByteBuffer;
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
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.netty.AbstractNettyHttpRequest;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.netty.stream.JsonSubscriber;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.RouteExecutor;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.multipart.NettyPartData;
import io.micronaut.http.server.netty.multipart.NettyStreamingFileUpload;
import io.micronaut.http.server.netty.types.NettyCustomizableResponseTypeHandler;
import io.micronaut.http.server.netty.types.NettyCustomizableResponseTypeHandlerRegistry;
import io.micronaut.http.server.netty.types.files.NettyStreamedFileCustomizableResponseType;
import io.micronaut.http.server.netty.types.files.NettySystemFileCustomizableResponseType;
import io.micronaut.http.server.types.files.FileCustomizableResponseType;
import io.micronaut.runtime.http.codec.TextPlainCodec;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
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
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.publisher.UnicastProcessor;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;
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
@Sharable
@SuppressWarnings("FileLength")
class RoutingInBoundHandler extends SimpleChannelInboundHandler<io.micronaut.http.HttpRequest<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingInBoundHandler.class);
    private static final Pattern IGNORABLE_ERROR_MESSAGE = Pattern.compile(
            "^.*(?:connection.*(?:reset|closed|abort|broken)|broken.*pipe).*$", Pattern.CASE_INSENSITIVE);
    private static final Argument ARGUMENT_PART_DATA = Argument.of(PartData.class);
    private final Router router;
    private final StaticResourceResolver staticResourceResolver;
    private final NettyHttpServerConfiguration serverConfiguration;
    private final HttpContentProcessorResolver httpContentProcessorResolver;
    private final ErrorResponseProcessor<?> errorResponseProcessor;
    private final RequestArgumentSatisfier requestArgumentSatisfier;
    private final MediaTypeCodecRegistry mediaTypeCodecRegistry;
    private final NettyCustomizableResponseTypeHandlerRegistry customizableResponseTypeHandlerRegistry;
    private final Supplier<ExecutorService> ioExecutorSupplier;
    private final boolean multipartEnabled;
    private ExecutorService ioExecutor;
    private final ApplicationEventPublisher<HttpRequestTerminatedEvent> terminateEventPublisher;
    private final RouteExecutor routeExecutor;

    /**
     * @param router                                  The router
     * @param mediaTypeCodecRegistry                  The media type codec registry
     * @param customizableResponseTypeHandlerRegistry The customizable response type handler registry
     * @param staticResourceResolver                  The static resource resolver
     * @param serverConfiguration                     The Netty HTTP server configuration
     * @param requestArgumentSatisfier                The Request argument satisfier
     * @param ioExecutor                              The IO executor
     * @param httpContentProcessorResolver            The http content processor resolver
     * @param errorResponseProcessor                  The factory to create error responses
     * @param terminateEventPublisher                 The terminate event publisher
     * @param routeExecutor                           The route executor
     */
    RoutingInBoundHandler(
            Router router,
            MediaTypeCodecRegistry mediaTypeCodecRegistry,
            NettyCustomizableResponseTypeHandlerRegistry customizableResponseTypeHandlerRegistry,
            StaticResourceResolver staticResourceResolver,
            NettyHttpServerConfiguration serverConfiguration,
            RequestArgumentSatisfier requestArgumentSatisfier,
            Supplier<ExecutorService> ioExecutor,
            HttpContentProcessorResolver httpContentProcessorResolver,
            ErrorResponseProcessor<?> errorResponseProcessor,
            ApplicationEventPublisher<HttpRequestTerminatedEvent> terminateEventPublisher,
            RouteExecutor routeExecutor) {
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.customizableResponseTypeHandlerRegistry = customizableResponseTypeHandlerRegistry;
        this.staticResourceResolver = staticResourceResolver;
        this.ioExecutorSupplier = ioExecutor;
        this.router = router;
        this.requestArgumentSatisfier = requestArgumentSatisfier;
        this.serverConfiguration = serverConfiguration;
        this.httpContentProcessorResolver = httpContentProcessorResolver;
        this.errorResponseProcessor = errorResponseProcessor;
        this.terminateEventPublisher = terminateEventPublisher;
        Optional<Boolean> multipartEnabled = serverConfiguration.getMultipart().getEnabled();
        this.multipartEnabled = !multipartEnabled.isPresent() || multipartEnabled.get();
        this.routeExecutor = routeExecutor;
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
            if (terminateEventPublisher != ApplicationEventPublisher.NO_OP) {
                ctx.executor().execute(() -> {
                    try {
                        terminateEventPublisher.publishEvent(new HttpRequestTerminatedEvent(request));
                    } catch (Exception e) {
                        if (LOG.isErrorEnabled()) {
                            LOG.error("Error publishing request terminated event: " + e.getMessage(), e);
                        }
                    }
                });
            }
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
        NettyHttpRequest<?> nettyHttpRequest = NettyHttpRequest.remove(ctx);
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
        ServerRequestContext.set(nettyHttpRequest);
        filterAndEncodeResponse(
                ctx,
                nettyHttpRequest,
                routeExecutor.onError(cause, nettyHttpRequest));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, io.micronaut.http.HttpRequest<?> request) {
        ctx.channel().config().setAutoRead(false);
        io.micronaut.http.HttpMethod httpMethod = request.getMethod();
        String requestPath = request.getUri().getPath();
        ServerRequestContext.set(request);

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
            request.setAttribute(HttpAttributes.ROUTE_INFO, establishedRoute);
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
                        nettyHttpRequest,
                        HttpResponse.notAllowedGeneric(allowedMethods),
                        "Method [" + requestMethodName + "] not allowed for URI [" + request.getUri() + "]. Allowed methods: " + allowedMethods);
                return;
            }

            Optional<? extends FileCustomizableResponseType> optionalFile = matchFile(requestPath);

            if (optionalFile.isPresent()) {
                filterAndEncodeResponse(ctx, nettyHttpRequest, Flux.just(HttpResponse.ok(optionalFile.get())));
            } else {
                handleStatusError(ctx, nettyHttpRequest, HttpResponse.status(HttpStatus.NOT_FOUND), "Page Not Found");
            }
            return;

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
                    nettyHttpRequest,
                    HttpResponse.status(HttpStatus.BAD_REQUEST),
                    "Not a WebSocket request");
        } else {
            handleRouteMatch(route, nettyHttpRequest, ctx);
        }
    }

    private void handleStatusError(
            ChannelHandlerContext ctx,
            NettyHttpRequest<?> nettyHttpRequest,
            MutableHttpResponse<?> defaultResponse,
            String message) {
        Optional<RouteMatch<Object>> statusRoute = router.findStatusRoute(defaultResponse.status(), nettyHttpRequest);
        if (statusRoute.isPresent()) {
            RouteMatch<Object> routeMatch = statusRoute.get();
            handleRouteMatch(routeMatch, nettyHttpRequest, ctx);
        } else {
            if (nettyHttpRequest.getMethod() != HttpMethod.HEAD) {
                defaultResponse = errorResponseProcessor.processResponse(ErrorContext.builder(nettyHttpRequest)
                        .errorMessage(message)
                        .build(), defaultResponse);
                if (!defaultResponse.getContentType().isPresent()) {
                    defaultResponse = defaultResponse.contentType(MediaType.APPLICATION_JSON_TYPE);
                }
            }
            filterAndEncodeResponse(
                    ctx,
                    nettyHttpRequest,
                    Publishers.just(defaultResponse)
            );
        }
    }

    private void filterAndEncodeResponse(
            ChannelHandlerContext channelContext,
            NettyHttpRequest<?> request,
            Publisher<MutableHttpResponse<?>> responsePublisher) {
        AtomicReference<HttpRequest<?>> requestReference = new AtomicReference<>(request);

        Flux.from(routeExecutor.filterPublisher(requestReference, responsePublisher))
                .contextWrite(ctx -> ctx.put(ServerRequestContext.KEY, request))
                .subscribe(new Subscriber<MutableHttpResponse<?>>() {
                    Subscription subscription;
                    AtomicBoolean empty = new AtomicBoolean();
                    @Override
                    public void onSubscribe(Subscription s) {
                        this.subscription = s;
                        s.request(1);
                    }

                    @Override
                    public void onNext(MutableHttpResponse<?> response) {
                        empty.set(false);
                        encodeHttpResponse(
                                channelContext,
                                request,
                                response,
                                response.body()
                        );
                        subscription.request(1);
                    }

                    @Override
                    public void onError(Throwable t) {
                        empty.set(false);
                        final MutableHttpResponse<?> response = routeExecutor.createDefaultErrorResponse(request, t);
                        encodeHttpResponse(
                                channelContext,
                                request,
                                response,
                                response.body()
                        );
                    }

                    @Override
                    public void onComplete() {
                        if (empty.get()) {
                            channelContext.read();
                        }
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

    private void handleRouteMatch(
            RouteMatch<?> originalRoute,
            NettyHttpRequest<?> request,
            ChannelHandlerContext context) {

        // try to fulfill the argument requirements of the route
        RouteMatch<?> route = requestArgumentSatisfier.fulfillArgumentRequirements(originalRoute, request, false);

        Optional<Argument<?>> bodyArgument = route.getBodyArgument()
            .filter(argument -> argument.getAnnotationMetadata().hasAnnotation(Body.class));

        // The request body is required, so at this point we must have a StreamedHttpRequest
        io.netty.handler.codec.http.HttpRequest nativeRequest = request.getNativeRequest();
        Flux<RouteMatch<?>> routeMatchPublisher;
        if (!route.isExecutable() &&
                io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod()) &&
                nativeRequest instanceof StreamedHttpRequest &&
                (!bodyArgument.isPresent() || !route.isSatisfied(bodyArgument.get().getName()))) {
            routeMatchPublisher = Mono.<RouteMatch<?>>create(emitter -> httpContentProcessorResolver.resolve(request, route)
                    .subscribe(buildSubscriber(request, route, emitter))
            ).flux();
        } else {
            context.read();
            routeMatchPublisher = Flux.just(route);
        }

        final Flux<MutableHttpResponse<?>> routeResponse = routeExecutor.executeRoute(
                request,
                true,
                routeMatchPublisher
        );
        routeResponse
                .contextWrite(ctx -> ctx.put(ServerRequestContext.KEY, request))
                .subscribe(new CompletionAwareSubscriber<HttpResponse<?>>() {
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
                        message.body()
                );
                subscription.request(1);
            }

            @Override
            protected void doOnError(Throwable throwable) {
                final MutableHttpResponse<?> defaultErrorResponse = routeExecutor.createDefaultErrorResponse(request, throwable);
                encodeHttpResponse(
                        context,
                        request,
                        toNettyResponse(defaultErrorResponse),
                        defaultErrorResponse.body()
                );
            }

            @Override
            protected void doOnComplete() {
            }
        });
    }

    private Subscriber<Object> buildSubscriber(NettyHttpRequest<?> request,
                                               RouteMatch<?> finalRoute,
                                               MonoSink<RouteMatch<?>> emitter) {
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

                Flux processFlowable(Flux flowable, Integer dataKey, boolean controlsFlow) {
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
                                                Flux flowable = processFlowable(childSubject, dataKey, true);
                                                if (streamingFileUpload && data instanceof FileUpload) {
                                                    namedSubject.onNext(new NettyStreamingFileUpload(
                                                            (FileUpload) data,
                                                            serverConfiguration.getMultipart(),
                                                            getIoExecutor(),
                                                            (Flux<PartData>) flowable));
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
                                                        (Flux<PartData>) processFlowable(subject, dataKey, true));
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
                    emitter.error(t);
                }

                @Override
                protected void doOnComplete() {
                    for (UnicastProcessor subject : subjects.values()) {
                        if (!subject.hasCompleted()) {
                            subject.onComplete();
                        }
                    }
                    executeRoute();
                }

                private void executeRoute() {
                    if (executed.compareAndSet(false, true)) {
                        emitter.success(routeMatch);
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
                    emitter.error(t);
                }

                @Override
                protected void doOnComplete() {
                    if (executed.compareAndSet(false, true)) {
                        emitter.success(routeMatch);
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

    private void encodeHttpResponse(
            ChannelHandlerContext context,
            NettyHttpRequest<?> nettyRequest,
            MutableHttpResponse<?> response,
            Object body) {
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
                        if (!response.getContentType().isPresent()) {
                            response.getAttribute(HttpAttributes.ROUTE_INFO, RouteInfo.class).ifPresent((routeInfo) ->
                                    response.contentType(routeExecutor.resolveDefaultResponseContentType(nettyRequest, routeInfo)));
                        }
                        writeFinalNettyResponse(
                                response,
                                nettyRequest,
                                context
                        );
                    } catch (IOException e) {
                        final MutableHttpResponse<?> errorResponse = routeExecutor.createDefaultErrorResponse(nettyRequest, e);
                        writeFinalNettyResponse(
                                errorResponse,
                                nettyRequest,
                                context
                        );
                    }
                });
            } else if (body instanceof Publisher) {
                response.body(null);
                DelegateStreamedHttpResponse streamedResponse = new DelegateStreamedHttpResponse(
                        toNettyResponse(response).toHttpResponse(),
                        mapToHttpContent(nettyRequest, response, body, context)
                );
                context.writeAndFlush(streamedResponse);
                context.read();
            } else {
                encodeResponseBody(
                        context,
                        nettyRequest,
                        response,
                        body
                );

                writeFinalNettyResponse(
                        response,
                        nettyRequest,
                        context
                );
            }
        } else {
            response.body(null);
            response.contentType(null);
            writeFinalNettyResponse(
                    response,
                    nettyRequest,
                    context
            );
        }
    }

    private Flux<HttpContent> mapToHttpContent(NettyHttpRequest<?> request,
                                               MutableHttpResponse<?> response,
                                               Object body,
                                               ChannelHandlerContext context) {
        final Optional<RouteInfo> optionalRoute = response.getAttribute(HttpAttributes.ROUTE_INFO, RouteInfo.class);
        MediaType mediaType = response.getContentType().orElseGet(() -> optionalRoute
                    .map(routeInfo -> routeExecutor.resolveDefaultResponseContentType(request, routeInfo))
                    .orElse(null));
        boolean isJson = mediaType != null && mediaType.getExtension().equals(MediaType.EXTENSION_JSON) && isJsonFormattable(optionalRoute.map(RouteInfo::getBodyType).orElse(null));
        NettyByteBufferFactory byteBufferFactory = new NettyByteBufferFactory(context.alloc());

        Flux<Object> bodyPublisher = Flux.from(Publishers.convertPublisher(body, Publisher.class));

        Flux<HttpContent> httpContentPublisher = bodyPublisher.map(message -> {
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
        });

        if (isJson) {
            // if the Publisher is returning JSON then in order for it to be valid JSON for each emitted element
            // we must wrap the JSON in array and delimit the emitted items

            httpContentPublisher = JsonSubscriber.lift(httpContentPublisher);
        }

        httpContentPublisher = httpContentPublisher
                .contextWrite(reactorContext -> reactorContext.put(ServerRequestContext.KEY, request))
                .doOnNext(httpContent ->
                        // once an http content is written, read the next item if it is available
                        context.read())
                .doAfterTerminate(() -> cleanupRequest(context, request));

        return httpContentPublisher;
    }

    private boolean isJsonFormattable(Argument<?> argument) {
        if (argument == null) {
            return false;
        }
        Class<?> javaType = argument.getType();
        if (Publishers.isConvertibleToPublisher(javaType)) {
            javaType = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT).getType();
        }
        return !(javaType == byte[].class
                || ByteBuffer.class.isAssignableFrom(javaType)
                || ByteBuf.class.isAssignableFrom(javaType));
    }

    private void encodeResponseBody(
            ChannelHandlerContext context,
            HttpRequest<?> request,
            MutableHttpResponse<?> message,
            Object body) {
        if (body == null) {
            return;
        }

        Optional<NettyCustomizableResponseTypeHandler> typeHandler = customizableResponseTypeHandlerRegistry
                .findTypeHandler(body.getClass());
        if (typeHandler.isPresent()) {
            NettyCustomizableResponseTypeHandler th = typeHandler.get();
            setBodyContent(message, new NettyCustomizableResponseTypeHandlerInvoker(th, body));
        } else {
            MediaType mediaType = message.getContentType().orElse(null);
            if (mediaType == null) {
                mediaType = message.getAttribute(HttpAttributes.ROUTE_INFO, RouteInfo.class).map(routeInfo -> routeExecutor.resolveDefaultResponseContentType(request, routeInfo)).orElse(null);
                message.contentType(mediaType);
            }
            if (body instanceof CharSequence) {
                ByteBuf byteBuf = Unpooled.wrappedBuffer(body.toString().getBytes(message.getCharacterEncoding()));
                setResponseBody(message, byteBuf);
            } else if (body instanceof byte[]) {
                ByteBuf byteBuf = Unpooled.wrappedBuffer((byte[]) body);
                setResponseBody(message, byteBuf);
            } else if (body instanceof ByteBuffer) {
                ByteBuffer<?> byteBuffer = (ByteBuffer) body;
                Object nativeBuffer = byteBuffer.asNativeBuffer();
                if (nativeBuffer instanceof ByteBuf) {
                    setResponseBody(message, (ByteBuf) nativeBuffer);
                } else if (nativeBuffer instanceof java.nio.ByteBuffer) {
                    ByteBuf byteBuf = Unpooled.wrappedBuffer((java.nio.ByteBuffer) nativeBuffer);
                    setResponseBody(message, byteBuf);
                }
            } else if (body instanceof ByteBuf) {
                setResponseBody(message, (ByteBuf) body);
            } else {
                Optional<MediaTypeCodec> registeredCodec = mediaTypeCodecRegistry.findCodec(mediaType, body.getClass());
                if (registeredCodec.isPresent()) {
                    MediaTypeCodec codec = registeredCodec.get();
                    encodeBodyWithCodec(message, body, codec, context, request);
                } else {
                    MediaTypeCodec defaultCodec = new TextPlainCodec(serverConfiguration.getDefaultCharset());
                    encodeBodyWithCodec(message, body, defaultCodec, context, request);
                }
            }
        }

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
                    if (httpStatus.getCode() < 500 || serverConfiguration.isKeepAliveOnServerError()) {
                        message.getHeaders().set(HttpHeaders.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                    } else {
                        message.getHeaders().set(HttpHeaders.CONNECTION, HttpHeaderValues.CLOSE);
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
                    if (expectKeepAlive || httpStatus.getCode() < 500 || serverConfiguration.isKeepAliveOnServerError()) {
                        nettyHeaders.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                    } else {
                        nettyHeaders.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
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

    @NonNull
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
                                                       ChannelHandlerContext context,
                                                       HttpRequest<?> request) {
        ByteBuf byteBuf;
        try {
            byteBuf = encodeBodyAsByteBuf(body, codec, context, request);
            setResponseBody(response, byteBuf);
            return response;
        } catch (LinkageError e) {
            // rxjava swallows linkage errors for some reasons so if one occurs, rethrow as a internal error
            throw new InternalServerException("Fatal error encoding bytebuf: " + e.getMessage(), e);
        }
    }

    private void setResponseBody(MutableHttpResponse<?> response, ByteBuf byteBuf) {
        int len = byteBuf.readableBytes();
        MutableHttpHeaders headers = response.getHeaders();
        headers.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(len));

        setBodyContent(response, byteBuf);
    }

    private MutableHttpResponse<?> setBodyContent(MutableHttpResponse<?> response, Object bodyContent) {
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

    private boolean isIgnorable(Throwable cause) {
        String message = cause.getMessage();
        return cause instanceof IOException && message != null && IGNORABLE_ERROR_MESSAGE.matcher(message).matches();
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
