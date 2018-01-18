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

import com.typesafe.netty.http.StreamedHttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import org.particleframework.context.BeanLocator;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.async.publisher.Publishers;
import org.particleframework.core.async.subscriber.CompletionAwareSubscriber;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.io.buffer.ByteBuffer;
import org.particleframework.core.type.Argument;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.*;
import org.particleframework.http.codec.MediaTypeCodec;
import org.particleframework.http.codec.MediaTypeCodecRegistry;
import org.particleframework.http.filter.HttpFilter;
import org.particleframework.http.filter.HttpServerFilter;
import org.particleframework.http.filter.ServerFilterChain;
import org.particleframework.http.hateos.Link;
import org.particleframework.http.hateos.VndError;
import org.particleframework.http.netty.buffer.NettyByteBufferFactory;
import org.particleframework.http.server.binding.RequestBinderRegistry;
import org.particleframework.runtime.http.codec.TextPlainCodec;
import org.particleframework.http.server.exceptions.ExceptionHandler;
import org.particleframework.http.server.netty.async.ContextCompletionAwareSubscriber;
import org.particleframework.http.server.netty.async.DefaultCloseHandler;
import org.particleframework.http.server.netty.configuration.NettyHttpServerConfiguration;
import org.particleframework.http.server.netty.multipart.NettyPart;
import org.particleframework.http.server.netty.types.NettySpecialTypeHandler;
import org.particleframework.http.server.netty.types.NettySpecialTypeHandlerRegistry;
import org.particleframework.inject.qualifiers.Qualifiers;
import org.particleframework.runtime.executor.ExecutorSelector;
import org.particleframework.web.router.MethodBasedRouteMatch;
import org.particleframework.web.router.RouteMatch;
import org.particleframework.web.router.Router;
import org.particleframework.web.router.UriRouteMatch;
import org.particleframework.web.router.exceptions.UnsatisfiedRouteException;
import org.particleframework.web.router.qualifier.ConsumesMediaTypeQualifier;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Internal implementation of the {@link ChannelInboundHandler} for Particle
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class RoutingInBoundHandler extends SimpleChannelInboundHandler<HttpRequest<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingInBoundHandler.class);

    private final Router router;
    private final ExecutorSelector executorSelector;
    private final ExecutorService ioExecutor;
    private final BeanLocator beanLocator;
    private final NettyHttpServerConfiguration serverConfiguration;
    private final RequestArgumentSatisfier requestArgumentSatisfier;
    private final MediaTypeCodecRegistry mediaTypeCodecRegistry;
    private final NettySpecialTypeHandlerRegistry specialTypeHandlerRegistry;

    RoutingInBoundHandler(
            BeanLocator beanLocator,
            Router router,
            MediaTypeCodecRegistry mediaTypeCodecRegistry,
            NettySpecialTypeHandlerRegistry specialTypeHandlerRegistry,
            NettyHttpServerConfiguration serverConfiguration,
            RequestBinderRegistry binderRegistry,
            ExecutorSelector executorSelector,
            ExecutorService ioExecutor) {
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.specialTypeHandlerRegistry = specialTypeHandlerRegistry;
        this.beanLocator = beanLocator;
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
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest<?> request) throws Exception {
        ctx.channel().config().setAutoRead(false);
        HttpMethod httpMethod = request.getMethod();
        String requestPath = request.getPath();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Matching route {} - {}", httpMethod, requestPath);
        }

        // find a matching route
        Optional<UriRouteMatch<Object>> routeMatch = router.find(httpMethod, requestPath)
                .filter((match) -> match.test(request))
                .findFirst();

        RouteMatch<Object> route;

        if (!routeMatch.isPresent()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No matching route found for URI {} and method {}", request.getUri(), httpMethod);
            }

            // if there is no route present try to locate a route that matches a different HTTP method
            Set<HttpMethod> existingRoutes = router
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
                    VndError error = newError(request, "Method [" + httpMethod + "] not allowed. Allowed methods: " + existingRoutes);

                    MutableHttpResponse<Object> defaultResponse = HttpResponse.notAllowed(existingRoutes)
                                                                              .body(error);
                    emitDefaultErrorResponse(ctx, request, defaultResponse);
                    return;
                }

            } else {
                Optional<RouteMatch<Object>> statusRoute = router.route(HttpStatus.NOT_FOUND);
                if (statusRoute.isPresent()) {
                    route = statusRoute.get();
                } else {
                    emitDefaultNotFoundResponse(ctx, request);
                    return;
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
            Optional<RouteMatch<Object>> statusRoute = router.route(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            if (statusRoute.isPresent()) {
                route = statusRoute.get();
            } else {
                VndError error = newError(request, "Unsupported Media Type: " + contentType);
                MutableHttpResponse<Object> res = HttpResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
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

    private void emitDefaultNotFoundResponse(ChannelHandlerContext ctx, HttpRequest<?> request) {
        VndError error = newError(request, "Page Not Found");
        MutableHttpResponse<Object> res = HttpResponse.notFound()
                                                      .body(error);
        emitDefaultErrorResponse(ctx, request, res);
    }

    private VndError newError(HttpRequest<?> request, String message) {
        URI uri = request.getUri();
        return new VndError(message)
                     .link(Link.SELF, Link.of(uri));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        NettyHttpRequest nettyHttpRequest = NettyHttpRequest.get(ctx);
        RouteMatch<Object> errorRoute = null;
        if(nettyHttpRequest == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Particle Server Error - No request state present. Cause: " + cause.getMessage(), cause);
            }
            ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
            return;
        }

        if (cause instanceof UnsatisfiedRouteException) {
            errorRoute = router.route(HttpStatus.BAD_REQUEST).orElse(null);
        }
        if (errorRoute == null) {

            RouteMatch<?> originalRoute = nettyHttpRequest.getMatchedRoute();
            Optional<RouteMatch<Object>> errorRouteMatch;
            if (originalRoute instanceof MethodBasedRouteMatch) {
                Class declaringType = ((MethodBasedRouteMatch) originalRoute).getDeclaringType();
                errorRouteMatch = declaringType != null ? router.route(declaringType, cause) : Optional.empty();
            } else {
                errorRouteMatch = Optional.empty();
            }
            errorRoute = errorRouteMatch.orElseGet(() -> router.route(cause).orElse(null));
        }

        if (errorRoute != null) {
            if(LOG.isErrorEnabled()) {
                LOG.error("Unexpected error occurred: " + cause.getMessage(), cause);
            }
            if(LOG.isDebugEnabled()) {
                LOG.debug("Found matching exception handler for exception [{}]: {}", cause.getMessage(), errorRoute);
            }
            errorRoute = requestArgumentSatisfier.fulfillArgumentRequirements(errorRoute, nettyHttpRequest, false);
            MediaType defaultResponseMediaType = errorRoute.getProduces().stream().findFirst().orElse(MediaType.APPLICATION_JSON_TYPE);
            try {
                Object result = errorRoute.execute();
                HttpResponse response = errorResultToResponse(result);

                processResponse(ctx, nettyHttpRequest, response, defaultResponseMediaType, errorRoute);
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
                    HttpResponse response = errorResultToResponse(result);
                    processResponse(ctx, nettyHttpRequest, response, defaultResponseMediaType, null);
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
            response = HttpResponse.serverError();
        } else if (result instanceof HttpResponse) {
            response = (MutableHttpResponse) result;
        } else {
            response = HttpResponse.serverError()
                    .body(result);
            MediaType.fromType(result.getClass()).ifPresent(response::contentType);
        }
        return response;
    }

    private void handleRouteMatch(
            RouteMatch<Object> route,
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
        if(!route.isExecutable() && HttpMethod.permitsRequestBody(request.getMethod()) && nativeRequest instanceof StreamedHttpRequest) {
            Optional<MediaType> contentType = request.getContentType();
            HttpContentProcessor<?> processor = contentType.flatMap(type ->
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


    private Subscriber<Object> buildSubscriber(NettyHttpRequest request, ChannelHandlerContext context, RouteMatch<Object> finalRoute) {
        return new CompletionAwareSubscriber<Object>() {
            NettyPart currentPart;
            RouteMatch<Object> routeMatch = finalRoute;
            AtomicBoolean executed = new AtomicBoolean(false);

            @Override
            protected void doOnSubscribe(Subscription subscription) {
                subscription.request(1);
            }

            @Override
            protected void doOnNext(Object message) {
                boolean executed = this.executed.get();
                if (message instanceof ByteBufHolder) {
                    if (message instanceof HttpData) {
                        HttpData data = (HttpData) message;
                        String name = data.getName();
                        if (executed) {
                            if (currentPart != null) {
                                if (currentPart.getName().equals(name)) {
                                    FileUpload upload = (FileUpload) data;
                                    currentPart.onNext(upload);
                                    if (upload.isCompleted()) {
                                        currentPart.onComplete();
                                    }
                                } else {
                                    onComplete();
                                }
                            } else {
                                onComplete();
                            }
                        } else {
                            Optional<Argument<?>> requiredInput = routeMatch.getRequiredInput(name);

                            if (requiredInput.isPresent()) {
                                Object input = data;
                                if (data instanceof FileUpload) {
                                    Argument<?> argument = requiredInput.get();
                                    FileUpload fileUpload = (FileUpload) data;
                                    if (org.particleframework.http.multipart.FileUpload.class.isAssignableFrom(argument.getType())) {
                                        currentPart = createPart(fileUpload);
                                        input = currentPart;
                                    }
                                }
                                routeMatch = routeMatch.fulfill(Collections.singletonMap(name, input));
                            } else {
                                request.addContent(data);
                            }
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
                        doOnComplete();
                    } else {
                        // the route is not yet executable, so keep going
                        subscription.request(1);
                    }
                }
            }

            private NettyPart createPart(FileUpload fileUpload) {
                return new NettyPart(
                        fileUpload,
                        serverConfiguration.getMultipart(),
                        ioExecutor,
                        subscription
                );
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


    private RouteMatch<Object> prepareRouteForExecution(RouteMatch<Object> route, NettyHttpRequest<?> request) {
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

            Publisher<? extends HttpResponse<?>> finalPublisher;
            Publisher<MutableHttpResponse<?>> routePublisher = Publishers.fromCompletableFuture(() -> {
                CompletableFuture<MutableHttpResponse<?>> completableFuture = new CompletableFuture<>();
                executor.submit(() -> {

                    MutableHttpResponse<?> response;

                    try {
                        RouteMatch<Object> routeMatch = finalRoute;
                        if (!routeMatch.isExecutable()) {
                            routeMatch = requestArgumentSatisfier.fulfillArgumentRequirements(routeMatch, request, true);
                        }
                        Object result = routeMatch.execute();

                        if (result == null) {
                            Class<?> javaReturnType = routeMatch.getReturnType().getType();
                            if( javaReturnType != void.class) {
                                // handle re-mapping of errors
                                result = router.route(HttpStatus.NOT_FOUND)
                                        .map((match) -> requestArgumentSatisfier.fulfillArgumentRequirements(match, request, true))
                                        .filter(RouteMatch::isExecutable)
                                        .map(RouteMatch::execute)
                                        .orElse(NettyHttpResponse.getOr(request, HttpResponse.notFound()));
                                if (result instanceof MutableHttpResponse) {
                                    response = (MutableHttpResponse<?>) result;
                                } else {
                                    response = HttpResponse.status(HttpStatus.NOT_FOUND)
                                            .body(result);
                                }
                            }
                            else {
                                response = NettyHttpResponse.getOr(request, HttpResponse.ok());
                            }
                        } else if (result instanceof HttpResponse) {
                            HttpStatus status = ((HttpResponse) result).getStatus();
                            if (status.getCode() >= 300) {
                                // handle re-mapping of errors
                                result = router.route(status)
                                        .map((match) -> requestArgumentSatisfier.fulfillArgumentRequirements(match, request, true))
                                        .filter(RouteMatch::isExecutable)
                                        .map(RouteMatch::execute)
                                        .orElse(result);
                            }
                            if (result instanceof MutableHttpResponse) {
                                response = (MutableHttpResponse<?>) result;
                            } else {
                                response = HttpResponse.status(status)
                                                       .body(result);
                            }
                        } else {
                            response = HttpResponse.ok(result);
                        }

                        completableFuture.complete(response);

                    } catch (Throwable e) {
                        completableFuture.completeExceptionally(e);
                    }
                });
                return completableFuture;
            });

            finalPublisher = filterPublisher(request, routePublisher);

            finalPublisher.subscribe(new CompletionAwareSubscriber<HttpResponse<?>>() {
                @Override
                protected void doOnSubscribe(Subscription subscription) {
                    subscription.request(1);
                }

                @Override
                protected void doOnNext(HttpResponse<?> message) {
                    processResponse(context, request, message, defaultResponseMediaType, finalRoute);
                }

                @Override
                protected void doOnError(Throwable t) {
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

    private Publisher<? extends HttpResponse<?>> filterPublisher(HttpRequest<?> request, Publisher<MutableHttpResponse<?>> routePublisher) {
        Publisher<? extends HttpResponse<?>> finalPublisher;
        List<HttpFilter> filters = new ArrayList<>( router.findFilters(request) );
        if(!filters.isEmpty()) {
            // make the action executor the last filter in the chain
            filters.add((HttpServerFilter) (req, chain) -> routePublisher);

            AtomicInteger integer = new AtomicInteger();
            int len = filters.size();
            ServerFilterChain filterChain = new ServerFilterChain() {
                @SuppressWarnings("unchecked")
                @Override
                public Publisher<MutableHttpResponse<?>> proceed(HttpRequest<?> request) {
                    int pos = integer.incrementAndGet();
                    if(pos > len) {
                        throw new IllegalStateException("The FilterChain.proceed(..) method should be invoked exactly once per filter execution. The method has instead been invoked multiple times by an erroneous filter definition.");
                    }
                    HttpFilter httpFilter = filters.get(pos);
                    return (Publisher<MutableHttpResponse<?>>) httpFilter.doFilter(request, this);
                }
            };
            HttpFilter httpFilter = filters.get(0);
            finalPublisher = httpFilter.doFilter(request, filterChain);
        }
        else {
            finalPublisher = routePublisher;
        }
        return finalPublisher;
    }

    private void processResponse(
            ChannelHandlerContext context,
            NettyHttpRequest<?> request,
            HttpResponse<?> defaultResponse,
            MediaType defaultResponseMediaType,
            RouteMatch<Object> route) {
        Optional<?> optionalBody = defaultResponse.getBody();
        FullHttpResponse nativeResponse = ((NettyHttpResponse) defaultResponse).getNativeResponse();
        boolean isChunked = HttpUtil.isTransferEncodingChunked(nativeResponse);
        if (optionalBody.isPresent()) {
            // a response body is present so we need to process it
            Object body = optionalBody.get();
            Class<?> bodyType = body.getClass();


            Optional<MediaType> responseType = defaultResponse.getContentType();

            Publisher<Object> publisher;
            if (Publishers.isPublisher(bodyType)) {
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
                    publisher = Publishers.fromCompletableFuture(() -> CompletableFuture.completedFuture(body));
                }
            }

            if (isChunked) {
                // if the transfer encoding is chunked then create a com.typesafe.netty.http.StreamedHttpResponse
                // that will send the encoded data chunk by chunk

                // adapt the publisher to produce HTTP content
                writeHttpContentChunkByChunk(context, request, nativeResponse, responseType, defaultResponseMediaType, publisher);
            } else {
                // if the transfer encoding is not chunked then we must send a content length header so subscribe the
                // publisher, encode the result as a io.netty.handler.codec.http.FullHttpResponse
                boolean isPublisher = Publishers.isPublisher(body.getClass());
                boolean isSingle = !isPublisher || Publishers.isSingle(body.getClass()) || HttpResponse.class.isAssignableFrom(bodyType);
                if (isSingle) {
                    publisher.subscribe(new ContextCompletionAwareSubscriber<Object>(context) {

                        @Override
                        protected void onComplete(Object message) {
                            try {
                                if (message != null) {

                                    Object body;
                                    FullHttpResponse fullHttpResponse;
                                    NettyHttpResponse response;

                                    if (message instanceof HttpResponse) {
                                        response = (NettyHttpResponse<?>) message;
                                        body = response.getBody().orElse(null);
                                        fullHttpResponse = response.getNativeResponse();
                                    } else {
                                        response = (NettyHttpResponse<?>) defaultResponse;
                                        body = message;
                                        fullHttpResponse = nativeResponse;
                                    }

                                    if (responseType.isPresent()) {
                                        Optional<MediaTypeCodec> codec = mediaTypeCodecRegistry.findCodec(responseType.get(), body.getClass());
                                        if (codec.isPresent()) {
                                            writeSingleMessage(context, request, fullHttpResponse, body, codec.get(), responseType.get());
                                            return;
                                        }
                                    }

                                    Optional<NettySpecialTypeHandler> typeHandler = specialTypeHandlerRegistry.findTypeHandler(body.getClass());
                                    if (typeHandler.isPresent()) {
                                        typeHandler.get().handle(body, request.getNativeRequest(), response, context);
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
                                context.read();
                            }
                        }
                    });
                } else {
                    // unbound publisher, we cannot know the content length, so we write chunk by chunk
                    writeHttpContentChunkByChunk(context, request, nativeResponse, responseType, defaultResponseMediaType, publisher);
                }
            }
        } else {
            // no body returned so just write the Netty response as is
            writeNettyResponse(context, request, nativeResponse);
        }
    }

    private void writeHttpResponse(ChannelHandlerContext context, HttpRequest<?> request, HttpResponse<?> responseMessage, FullHttpResponse nativeResponse, MediaTypeCodec codec, MediaType responseType) {
        Object body = responseMessage.getBody().orElse(null);
        if (body != null && codec == null) {
            codec = mediaTypeCodecRegistry.findCodec(
                    responseType,
                    body.getClass()
            ).orElse(new TextPlainCodec(serverConfiguration.getDefaultCharset()));
        }
        writeSingleMessage(context, request, nativeResponse, body, codec, responseType);
    }

    @SuppressWarnings("unchecked")
    private Publisher<Object> convertPublisher(Object body, Class<?> bodyType) {
        return ConversionService.SHARED.convert(body, Publisher.class)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported Reactive type: " + bodyType));
    }


    void writeDefaultErrorResponse(ChannelHandlerContext ctx, NettyHttpRequest nettyHttpRequest, Throwable cause) {
        if (LOG.isErrorEnabled()) {
            LOG.error("Unexpected error occurred: " + cause.getMessage(), cause);
        }

        NettyHttpResponse error = (NettyHttpResponse) HttpResponse.serverError()
                                                        .body(new VndError("Internal Server Error: " + cause.getMessage()));
        writeHttpResponse(
            ctx,
            nettyHttpRequest,
                error,
                error.getNativeResponse(),
                null,
                MediaType.APPLICATION_VND_ERROR_TYPE
        );
    }

    private Class<?> resolveBodyType(RouteMatch<Object> route, Class<?> bodyType) {
        if (route != null) {
            bodyType = route.getReturnType().getFirstTypeVariable().map(Argument::getType).orElse(null);
        }
        if (bodyType == null) {
            bodyType = Object.class;
        }
        return bodyType;
    }

    private void emitDefaultErrorResponse(ChannelHandlerContext ctx, HttpRequest<?> request, MutableHttpResponse<Object> defaultResponse) {
        Publisher<MutableHttpResponse<?>> notAllowedResponse = Publishers.just(defaultResponse);
        notAllowedResponse  = (Publisher<MutableHttpResponse<?>>) filterPublisher(request, notAllowedResponse);
        notAllowedResponse.subscribe(new CompletionAwareSubscriber<MutableHttpResponse<?>>() {
            @Override
            protected void doOnSubscribe(Subscription subscription) {
                subscription.request(1);
            }

            @Override
            protected void doOnNext(MutableHttpResponse<?> message) {
                writeHttpResponse(ctx,
                        request,
                        message,
                        ((NettyHttpResponse)message).getNativeResponse(),
                        null,
                        MediaType.APPLICATION_VND_ERROR_TYPE);
                writeNettyResponse(ctx, request, ((NettyHttpResponse) message).getNativeResponse());
            }

            @Override
            protected void doOnError(Throwable t) {
                if(LOG.isErrorEnabled()) {
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
            Optional<MediaType> responseType,
            MediaType defaultResponseType,
            Publisher<Object> publisher) {

        MediaType mediaType = responseType.orElse(defaultResponseType);

        Publisher<HttpContent> httpContentPublisher = Publishers.map(publisher, message -> {
            if (message instanceof ByteBuf) {
                return new DefaultHttpContent((ByteBuf) message);
            } else if (message instanceof HttpContent) {
                return (HttpContent) message;
            } else {

                MediaTypeCodec codec = mediaTypeCodecRegistry.findCodec(mediaType, message.getClass()).orElse(
                        new TextPlainCodec(serverConfiguration.getDefaultCharset()));

                ByteBuffer encoded = codec.encode(message, new NettyByteBufferFactory(context.alloc()));
                return new DefaultHttpContent((ByteBuf) encoded.asNativeBuffer());
            }
        });

        if (mediaType.equals(MediaType.TEXT_EVENT_STREAM_TYPE)) {

            httpContentPublisher = Publishers.onComplete(httpContentPublisher, () -> {
                CompletableFuture<Void> future = new CompletableFuture<>();
                if (request == null || !request.getHeaders().isKeepAlive()) {
                    context.pipeline()
                            .writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT))
                            .addListener(f -> {
                                if(f.isSuccess()) {
                                    future.complete(null);
                                }
                                else {
                                    future.completeExceptionally(f.cause());
                                }
                                    }
                            );
                }
                return future;
            });
        }

        DelegateStreamedHttpResponse streamedResponse = new DelegateStreamedHttpResponse(nativeResponse, httpContentPublisher);
        streamedResponse.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        writeNettyResponse(
                context,
                request,
                streamedResponse
        );
    }

    private void writeSingleMessage(
            ChannelHandlerContext context,
            HttpRequest<?> request,
            FullHttpResponse nativeResponse,
            Object message,
            MediaTypeCodec codec,
            MediaType mediaType) {
        if (message != null) {
            ByteBuf byteBuf;

            if (message instanceof ByteBuf) {
                byteBuf = (ByteBuf) message;
            } else {
                byteBuf = (ByteBuf) codec.encode(message, new NettyByteBufferFactory(context.alloc())).asNativeBuffer();
            }
            int len = byteBuf.readableBytes();
            FullHttpResponse newResponse = nativeResponse.replace(byteBuf);
            HttpHeaders headers = newResponse.headers();
            headers.add(HttpHeaderNames.CONTENT_TYPE, mediaType);
            headers.add(HttpHeaderNames.CONTENT_LENGTH, len);
            writeNettyResponse(context, request, newResponse);
        } else {
            writeNettyResponse(context, request, nativeResponse);
        }

    }

    private void writeNettyResponse(
            ChannelHandlerContext context,
            HttpRequest<?> request,
            io.netty.handler.codec.http.HttpResponse nettyResponse) {
        context.writeAndFlush(nettyResponse)
                .addListener(new DefaultCloseHandler(context, ((NettyHttpRequest) request).getNativeRequest(), nettyResponse));
    }


}
