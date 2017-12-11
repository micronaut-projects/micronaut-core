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
import org.particleframework.core.type.Argument;
import org.particleframework.http.*;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.http.server.binding.RequestBinderRegistry;
import org.particleframework.http.server.cors.CorsHandler;
import org.particleframework.http.server.netty.configuration.NettyHttpServerConfiguration;
import org.particleframework.http.server.netty.multipart.NettyPart;
import org.particleframework.http.server.netty.encoders.HttpResponseEncoder;
import org.particleframework.runtime.executor.ExecutorSelector;
import org.particleframework.web.router.RouteMatch;
import org.particleframework.web.router.Router;
import org.particleframework.web.router.UriRouteMatch;
import org.particleframework.web.router.qualifier.ConsumesMediaTypeQualifier;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Internal implementation of the {@link ChannelInboundHandler} for Particle
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class RoutingInBoundHandler extends SimpleChannelInboundHandler<HttpRequest<?>>  {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingInBoundHandler.class);

    private final Router router;
    private final ExecutorSelector executorSelector;
    private final ExecutorService ioExecutor;
    private final BeanLocator beanLocator;
    private final boolean corsEnabled;
    private final CorsHandler corsHandler;
    private final NettyHttpServerConfiguration serverConfiguration;
    private final RequestArgumentSatisfier requestArgumentSatisfier;
    private final RoutingInBoundErrorHandler routingInBoundErrorHandler;

    RoutingInBoundHandler(
            BeanLocator beanLocator,
            Router router,
            NettyHttpServerConfiguration serverConfiguration,
            RequestBinderRegistry binderRegistry,
            ExecutorSelector executorSelector,
            ExecutorService ioExecutor) {
        this.beanLocator = beanLocator;
        this.ioExecutor = ioExecutor;
        this.executorSelector = executorSelector;
        this.router = router;
        this.requestArgumentSatisfier = new RequestArgumentSatisfier(binderRegistry);
        this.routingInBoundErrorHandler = new RoutingInBoundErrorHandler(
                router,
                beanLocator,
                requestArgumentSatisfier
        );
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
                        .addListener(NettyHttpServer.createCloseListener(nettyHttpRequest.getNativeRequest()));
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


        HttpMethod httpMethod = request.getMethod();
        URI requestPath = request.getPath();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Matching route {} - {}", httpMethod, requestPath);
        }

        // find a matching route
        Optional<UriRouteMatch<Object>> routeMatch = router.find(httpMethod, requestPath)
                        .filter((match) -> match.test(request))
                        .findFirst();

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
                routingInBoundErrorHandler.handleMethodNotAllowed(ctx, request, existingRoutes);

            } else {
                // if no alternative route was found send back 404 - NOT_FOUND
                routingInBoundErrorHandler.handleNotFound(ctx, request);
            }
        }
        else {
            UriRouteMatch<Object> route = routeMatch.get();
            // Check that the route is an accepted content type
            MediaType contentType = request.getContentType().orElse(null);
            if (!route.accept(contentType)) {
                routingInBoundErrorHandler.handleUnsupportedMediaType(ctx, request, contentType);
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Matched route {} - {} to controller {}", httpMethod, requestPath, route.getDeclaringType().getName());
                }
                // all ok proceed to try and execute the route
                handleRouteMatch(route, (NettyHttpRequest) request, ctx);
            }
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        routingInBoundErrorHandler.handleServerError(ctx, cause);
    }


    private void handleRouteMatch(
            RouteMatch<Object> route,
            NettyHttpRequest<?> request,
            ChannelHandlerContext context) {
        // Set the matched route on the request
        request.setMatchedRoute(route);

        // try to fulfill the argument requirements of the route
        route = requestArgumentSatisfier.fulfillArgumentRequirements(route, request);

        // If it is not executable and the body is not required send back 400 - BAD REQUEST
        if (!route.isExecutable() && !request.isBodyRequired()) {
            routingInBoundErrorHandler.handleBadRequest(
                    context,
                    request,
                    route
            );
        } else {

            // decorate the execution of the route so that it runs an async executor
            request.setMatchedRoute(route);

            if (route.isExecutable()) {
                // The request body is not required so simply execute the route
                route = prepareRouteForExecution(route, request);
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
                    routingInBoundErrorHandler.handleBadRequest(
                            context,
                            request,
                            route
                    );
                }
            }
            else {
                routingInBoundErrorHandler.handleBadRequest(
                        context,
                        request,
                        route
                );
            }

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
                            result = routingInBoundErrorHandler
                                        .findStatusRoute(status, request)
                                        .map(RouteMatch::execute)
                                        .orElse(result);
                        }
                    }
                    else {
                        result = HttpResponse.ok(result);
                    }

                    Object finalResult = result;
                    context.writeAndFlush(result)
                           .addListener((ChannelFuture future) -> {
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




}
