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
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.*;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import org.particleframework.context.BeanLocator;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.async.subscriber.CompletionAwareSubscriber;
import org.particleframework.core.bind.ArgumentBinder;
import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionError;
import org.particleframework.core.type.Argument;
import org.particleframework.http.*;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.exceptions.InternalServerException;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.http.server.binding.RequestBinderRegistry;
import org.particleframework.http.server.binding.binders.BodyArgumentBinder;
import org.particleframework.http.server.binding.binders.NonBlockingBodyArgumentBinder;
import org.particleframework.http.server.cors.CorsHandler;
import org.particleframework.http.server.exceptions.ExceptionHandler;
import org.particleframework.http.server.netty.configuration.NettyHttpServerConfiguration;
import org.particleframework.http.server.netty.multipart.NettyPart;
import org.particleframework.http.server.netty.encoders.HttpResponseEncoder;
import org.particleframework.inject.qualifiers.Qualifiers;
import org.particleframework.runtime.executor.ExecutorSelector;
import org.particleframework.web.router.RouteMatch;
import org.particleframework.web.router.Router;
import org.particleframework.web.router.UnresolvedArgument;
import org.particleframework.web.router.UriRouteMatch;
import org.particleframework.web.router.exceptions.UnsatisfiedRouteException;
import org.particleframework.web.router.qualifier.ConsumesMediaTypeQualifier;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Internal implementation of the {@link ChannelInboundHandler} for Particle
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class RoutingInBoundHandler extends SimpleChannelInboundHandler<HttpRequest<?>>  {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingInBoundHandler.class);

    private final Optional<Router> router;
    private final ExecutorSelector executorSelector;
    private final ExecutorService ioExecutor;
    private final BeanLocator beanLocator;
    private final RequestBinderRegistry binderRegistry;
    private final boolean corsEnabled;
    private final CorsHandler corsHandler;
    private final NettyHttpServerConfiguration serverConfiguration;


    RoutingInBoundHandler(
            BeanLocator beanLocator,
            Optional<Router> router,
            NettyHttpServerConfiguration serverConfiguration,
            RequestBinderRegistry binderRegistry,
            ExecutorSelector executorSelector,
            ExecutorService ioExecutor) {
        this.beanLocator = beanLocator;
        this.ioExecutor = ioExecutor;
        this.executorSelector = executorSelector;
        this.router = router;
        this.binderRegistry = binderRegistry;
        this.serverConfiguration = serverConfiguration;
        HttpServerConfiguration.CorsConfiguration corsConfiguration = serverConfiguration.getCors();
        this.corsEnabled = corsConfiguration.isEnabled();
        this.corsHandler = this.corsEnabled ? new CorsHandler(corsConfiguration) : null;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        super.channelReadComplete(ctx);
        NettyHttpRequest request = NettyHttpRequest.get(ctx);
        if(request != null) {
            request.release();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        ctx.flush();
        NettyHttpRequest request = NettyHttpRequest.get(ctx);
        if(request != null) {
            request.release();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest<?> request) throws Exception {
        ChannelPipeline pipeline = ctx.pipeline();
        NettyHttpRequest nettyHttpRequest = (NettyHttpRequest) request;
        if (corsEnabled && request.getHeaders().getOrigin().isPresent()) {
            Optional<MutableHttpResponse<?>> corsResponse = corsHandler.handleRequest(request);
            if (corsResponse.isPresent()) {
                ctx.writeAndFlush(corsResponse.get())
                        .addListener(createCloseListener(nettyHttpRequest.getNativeRequest()));
                return;
            } else {
                pipeline.addAfter(HttpResponseEncoder.NAME, NettyHttpServer.CORS_HANDLER, new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                        if (msg instanceof MutableHttpResponse) {
                            corsHandler.handleResponse(request, (MutableHttpResponse<?>) msg);
                        }
                        ctx.pipeline().remove(this);
                        super.write(ctx, msg, promise);
                    }
                });
            }
        }


        if (LOG.isDebugEnabled()) {
            LOG.debug("Matching route {} - {}", request.getMethod(), request.getPath());
        }

        // find a matching route
        Optional<UriRouteMatch<Object>> routeMatch = router.flatMap((router) ->
                router.find(request.getMethod(), request.getPath())
                        .filter((match) -> match.test(request))
                        .findFirst()
        );

        if (!routeMatch.isPresent()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No matching route found for URI {} and method {}", request.getUri(), request.getMethod());
            }

            // if there is no route present try to locate a route that matches a different HTTP method
            Set<HttpMethod> existingRoutes = router
                    .map(router ->
                            router.findAny(request.getUri().toString())
                    ).orElse(Stream.empty())
                    .map(UriRouteMatch::getHttpMethod)
                    .collect(Collectors.toSet());

            if (!existingRoutes.isEmpty()) {
                // if there are other routes that match send back 405 - METHOD_NOT_ALLOWED
                Object notAllowedResponse =
                        findStatusRoute(HttpStatus.METHOD_NOT_ALLOWED, request, binderRegistry)
                                .map(RouteMatch::execute)
                                .orElse(HttpResponse.notAllowed(
                                        existingRoutes
                                ));

                ctx.writeAndFlush(notAllowedResponse)
                        .addListener(ChannelFutureListener.CLOSE);
            } else {
                // if no alternative route was found send back 404 - NOT_FOUND
                Object notFoundResponse =
                        findStatusRoute(HttpStatus.NOT_FOUND, request, binderRegistry)
                                .map(RouteMatch::execute)
                                .orElse(HttpResponse.notFound());

                ctx.writeAndFlush(notFoundResponse)
                        .addListener(ChannelFutureListener.CLOSE);
            }
        }
        else {
            routeMatch.ifPresent((route -> {
                // Check that the route is an accepted content type
                MediaType contentType = request.getContentType().orElse(null);
                if (!route.accept(contentType)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Matched route is not a supported media type: {}", contentType);
                    }

                    // if the content type is not accepted send by 415 - UNSUPPORTED MEDIA TYPE
                    Object unsupportedResult =
                            findStatusRoute(HttpStatus.UNSUPPORTED_MEDIA_TYPE, request, binderRegistry)
                                    .map(RouteMatch::execute)
                                    .orElse(HttpResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE));

                    ctx.writeAndFlush(unsupportedResult)
                            .addListener(ChannelFutureListener.CLOSE);
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Matched route {} - {} to controller {}", request.getMethod(), request.getPath(), route.getDeclaringType().getName());
                    }
                    // all ok proceed to try and execute the route
                    handleRouteMatch(route, (NettyHttpRequest) request, binderRegistry, ctx);
                }
            }));
        }

        


    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        NettyHttpRequest nettyHttpRequest = NettyHttpRequest.get(ctx);
        if (nettyHttpRequest != null) {

            RouteMatch matchedRoute = nettyHttpRequest.getMatchedRoute();
            Class declaringType = matchedRoute != null ? matchedRoute.getDeclaringType() : null;
            try {
                if (declaringType != null) {
                    Optional<RouteMatch<Object>> match;
                    if (cause instanceof UnsatisfiedRouteException) {
                        match = router
                                .flatMap(router -> router.route(HttpStatus.BAD_REQUEST))
                                .map(route -> fulfillArgumentRequirements(route, nettyHttpRequest, binderRegistry))
                                .filter(RouteMatch::isExecutable);

                    } else {
                        match = router
                                .flatMap(router -> router.route(declaringType, cause))
                                .map(route -> fulfillArgumentRequirements(route, nettyHttpRequest, binderRegistry))
                                .filter(RouteMatch::isExecutable);
                    }


                    if (match.isPresent()) {
                        RouteMatch finalRoute = match.get();
                        Object result = finalRoute.execute();
                        ctx.writeAndFlush(result)
                                .addListener(createCloseListener(nettyHttpRequest.getNativeRequest()));
                    } else {
                        handleWithExceptionHandlers(ctx, nettyHttpRequest, cause);
                    }
                } else {
                    handleWithExceptionHandlers(ctx, nettyHttpRequest, cause);
                }

            } catch (Throwable e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Exception occurred executing error handler. Falling back to default error handling: " + e.getMessage(), e);
                }
                writeServerErrorResponse(ctx, nettyHttpRequest, cause);
            }
        } else {
            if (LOG.isErrorEnabled()) {
                LOG.error("Unexpected error occurred: " + cause.getMessage(), cause);
            }

            writeDefaultErrorResponse(ctx);
        }

    }

    protected void handleWithExceptionHandlers(ChannelHandlerContext ctx, NettyHttpRequest nettyHttpRequest, Throwable cause) {
        Optional<ExceptionHandler> exceptionHandler = beanLocator
                .findBean(ExceptionHandler.class, Qualifiers.byTypeArguments(cause.getClass(), Object.class));

        if (exceptionHandler.isPresent()) {
            Object result = exceptionHandler
                    .get()
                    .handle(nettyHttpRequest, cause);
            ctx.writeAndFlush(result)
                    .addListener(ChannelFutureListener.CLOSE);
        } else {
            writeServerErrorResponse(ctx, nettyHttpRequest, cause);
        }
    }

    void writeServerErrorResponse(ChannelHandlerContext ctx, NettyHttpRequest nettyHttpRequest, Throwable cause) {
        try {
            if (LOG.isErrorEnabled()) {
                LOG.error("Unexpected error occurred: " + cause.getMessage(), cause);
            }

            Object errorResponse = findStatusRoute(HttpStatus.INTERNAL_SERVER_ERROR, nettyHttpRequest, binderRegistry)
                    .map(RouteMatch::execute)
                    .orElse(HttpResponse.serverError());
            ctx.channel()
                    .writeAndFlush(errorResponse)
                    .addListener(ChannelFutureListener.CLOSE);

        } catch (Throwable e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Exception occurred executing error handler. Falling back to default error handling: " + e.getMessage(), e);
            }
            writeDefaultErrorResponse(ctx);

        }
    }

    void writeDefaultErrorResponse(ChannelHandlerContext ctx) {
        ctx.channel()
                .writeAndFlush(HttpResponse.serverError())
                .addListener(ChannelFutureListener.CLOSE);
    }

    private void handleRouteMatch(RouteMatch<Object> route, NettyHttpRequest request, RequestBinderRegistry binderRegistry, ChannelHandlerContext context) {
        // Set the matched route on the request
        request.setMatchedRoute(route);

        // try to fulfill the argument requirements of the route
        route = fulfillArgumentRequirements(route, request, binderRegistry);

        // If it is not executable and the body is not required send back 400 - BAD REQUEST
        if (!route.isExecutable() && !request.isBodyRequired()) {
            badRoute(route, request, binderRegistry, context);
        } else {

            // decorate the execution of the route so that it runs an async executor
            request.setMatchedRoute(route);

            if (route.isExecutable()) {
                // The request body is not required so simply execute the route
                route = prepareRouteForExecution(route, request, binderRegistry);
                route.execute();
            } else if(HttpMethod.permitsRequestBody(request.getMethod())){

                // The request body is required, so at this point we must have a StreamedHttpRequest
                io.netty.handler.codec.http.HttpRequest nativeRequest = request.getNativeRequest();
                if (nativeRequest instanceof StreamedHttpRequest) {
                    Optional<MediaType> contentType = request.getContentType();
                    HttpContentProcessor<?> processor = contentType.flatMap(type ->
                            beanLocator.findBean(HttpContentSubscriberFactory.class,
                                    new ConsumesMediaTypeQualifier<>(type))
                    ).map(factory ->
                            factory.build(request)
                    ).orElse(new DefaultHttpContentProcessor(request, serverConfiguration));

                    processor.subscribe(buildSubscriber(request, context, route));

                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Request body expected, but was empty.");
                    }
                    context.writeAndFlush(handleBadRequest(request, binderRegistry))
                            .addListener(ChannelFutureListener.CLOSE);

                }
            }
            else {
                badRoute(route, request, binderRegistry, context);
            }

        }
    }



    private void badRoute(RouteMatch<Object> route, NettyHttpRequest request, RequestBinderRegistry binderRegistry, ChannelHandlerContext context) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Bad request: Unsatisfiable route reached: " + route);
        }
        context.writeAndFlush(handleBadRequest(request, binderRegistry))
                .addListener(ChannelFutureListener.CLOSE);
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
                if(message instanceof ByteBufHolder) {
                    if(message instanceof HttpData) {
                        HttpData data = (HttpData) message;
                        String name = data.getName();
                        if(executed) {
                            if(currentPart != null) {
                                if( currentPart.getName().equals(name) ) {
                                    FileUpload upload = (FileUpload) data;
                                    currentPart.onNext(upload);
                                    if(upload.isCompleted()) {
                                        currentPart.onComplete();
                                    }
                                }
                                else {
                                    onComplete();
                                }
                            }
                            else {
                                onComplete();
                            }
                        }
                        else {
                            Optional<Argument<?>> requiredInput = routeMatch.getRequiredInput(name);

                            if(requiredInput.isPresent()) {
                                Object input = data;
                                if(data instanceof FileUpload) {
                                    Argument<?> argument = requiredInput.get();
                                    FileUpload fileUpload = (FileUpload) data;
                                    if(org.particleframework.http.multipart.FileUpload.class.isAssignableFrom(argument.getType())) {
                                        currentPart = createPart(fileUpload);
                                        input = currentPart;
                                    }
                                }
                                routeMatch = routeMatch.fulfill(Collections.singletonMap(name, input));
                            }
                            else {
                                request.addContent(data);
                            }
                        }
                    }
                    else {
                        request.addContent((ByteBufHolder) message);
                        if(!routeMatch.isExecutable() && message instanceof LastHttpContent) {
                            Optional<Argument<?>> bodyArgument = routeMatch.getBodyArgument();
                            if(bodyArgument.isPresent()) {
                                Argument<?> argument = bodyArgument.get();
                                String bodyArgumentName = argument.getName();
                                if(routeMatch.isRequiredInput(bodyArgumentName)) {
                                    Optional body = request.getBody();
                                    if(body.isPresent()) {
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
                }
                else {
                    request.setBody(message);
                }


                if(!executed) {
                    if(routeMatch.isExecutable() || message instanceof LastHttpContent) {
                        // we have enough data to satisfy the route, continue
                        doOnComplete();
                    }
                    else {
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
                context.pipeline().fireExceptionCaught(t);
            }

            @Override
            protected void doOnComplete() {
                if(executed.compareAndSet(false, true)) {
                    try {
                        routeMatch = prepareRouteForExecution(routeMatch, request, binderRegistry);
                        routeMatch.execute();
                    } catch (Exception e) {
                        context.pipeline().fireExceptionCaught(e);
                    }
                }
            }

        };
    }

    private RouteMatch<Object> prepareRouteForExecution(RouteMatch<Object> route, NettyHttpRequest request, RequestBinderRegistry binderRegistry) {
        ChannelHandlerContext context = request.getChannelHandlerContext();
        // Select the most appropriate Executor
        ExecutorService executor = executorSelector.select(route)
                .orElse(context.channel().eventLoop());

        route = route.decorate(finalRoute -> {
            executor.submit(() -> {

                Object result;
                try {
                    result = finalRoute.execute();

                    if (result == null) {
                        result = NettyHttpResponse.getOr(request, HttpResponse.ok());
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
                    ChannelFuture channelFuture = context.writeAndFlush(result);

                    Object finalResult = result;
                    channelFuture.addListener((ChannelFuture future) -> {
                        if (!future.isSuccess()) {
                            Throwable cause = future.cause();
                            if (LOG.isErrorEnabled()) {
                                LOG.error("Error encoding response: " + cause.getMessage(), cause);
                            }
                            Channel channel = context.channel();
                            if (channel.isWritable()) {
                                context.pipeline().fireExceptionCaught(cause);
                            }
                            else {
                                channel.close();
                            }
                        }
                        else if(finalResult instanceof HttpResponse) {
                            HttpResponse res = (HttpResponse) finalResult;
                            if(res.getStatus().getCode() >= 300) {
                                future.channel().close();
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

    private Optional<RouteMatch<Object>> findStatusRoute(HttpStatus status, HttpRequest<?> request, RequestBinderRegistry binderRegistry) {
        return this.router.flatMap(router ->
                router.route(status)
        ).map(match -> fulfillArgumentRequirements(match, request, binderRegistry))
                .filter(RouteMatch::isExecutable);
    }

    private RouteMatch<Object> fulfillArgumentRequirements(RouteMatch<Object> route, HttpRequest<?> request, RequestBinderRegistry binderRegistry) {
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
                    ArgumentConversionContext conversionContext = ConversionContext.of(
                            argument,
                            request.getLocale().orElse(null),
                            request.getCharacterEncoding()
                    );

                    if (argumentBinder instanceof BodyArgumentBinder) {
                        if (argumentBinder instanceof NonBlockingBodyArgumentBinder) {
                            Optional bindingResult = argumentBinder
                                    .bind(conversionContext, request);

                            if (bindingResult.isPresent()) {
                                argumentValues.put(argumentName, bindingResult.get());
                            }

                        } else {
                            argumentValues.put(argumentName, (UnresolvedArgument) () ->
                                    argumentBinder.bind(conversionContext, request)
                            );
                            ((NettyHttpRequest)request).setBodyRequired(true);
                        }
                    } else {

                        Optional bindingResult = argumentBinder
                                .bind(conversionContext, request);
                        if (argument.getType() == Optional.class) {
                            argumentValues.put(argumentName, bindingResult);
                        } else if (bindingResult.isPresent()) {
                            argumentValues.put(argumentName, bindingResult.get());
                        } else if (HttpMethod.requiresRequestBody(request.getMethod())) {
                            argumentValues.put(argumentName, (UnresolvedArgument) () -> {
                                Optional result = argumentBinder.bind(conversionContext, request);
                                Optional<ConversionError> lastError = conversionContext.getLastError();
                                if (lastError.isPresent()) {
                                    return lastError;
                                }
                                return result;
                            });
                        }
                    }
                }
            }
        }

        route = route.fulfill(argumentValues);
        return route;
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

    private ChannelFutureListener createCloseListener(io.netty.handler.codec.http.HttpRequest msg) {
        return future -> {
            if (!io.netty.handler.codec.http.HttpUtil.isKeepAlive(msg)) {
                future.channel().close();
            }
        };
    }
}
