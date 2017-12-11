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

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.particleframework.context.BeanLocator;
import org.particleframework.core.annotation.Internal;
import org.particleframework.http.*;
import org.particleframework.http.exceptions.InternalServerException;
import org.particleframework.http.server.binding.RequestBinderRegistry;
import org.particleframework.http.server.exceptions.ExceptionHandler;
import org.particleframework.inject.qualifiers.Qualifiers;
import org.particleframework.web.router.RouteMatch;
import org.particleframework.web.router.Router;
import org.particleframework.web.router.exceptions.UnsatisfiedRouteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;

/**
 * Methods for error handling
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class RoutingInBoundErrorHandler {
    private static final Logger LOG = LoggerFactory.getLogger(RoutingInBoundErrorHandler.class);

    private final Router router;
    private final BeanLocator beanLocator;
    private final RequestArgumentSatisfier requestArgumentSatisfier;

    RoutingInBoundErrorHandler(Router router, BeanLocator beanLocator, RequestArgumentSatisfier requestArgumentSatisfier) {
        this.router = router;
        this.beanLocator = beanLocator;
        this.requestArgumentSatisfier = requestArgumentSatisfier;
    }

    /**
     * Handle an exception that is propagated from {@link io.netty.channel.ChannelHandlerAdapter#exceptionCaught(ChannelHandlerContext, Throwable)}
     *
     * @param ctx The context
     * @param cause The cause
     */
    void handleServerError(ChannelHandlerContext ctx, Throwable cause) {
        NettyHttpRequest nettyHttpRequest = NettyHttpRequest.get(ctx);
        if (nettyHttpRequest != null) {

            RouteMatch matchedRoute = nettyHttpRequest.getMatchedRoute();
            Class declaringType = matchedRoute != null ? matchedRoute.getDeclaringType() : null;
            try {
                if (declaringType != null) {
                    Optional<RouteMatch<Object>> match;
                    if (cause instanceof UnsatisfiedRouteException) {
                        match = router
                                .route(HttpStatus.BAD_REQUEST)
                                .map(route -> requestArgumentSatisfier.fulfillArgumentRequirements(route, nettyHttpRequest))
                                .filter(RouteMatch::isExecutable);

                    } else {
                        match = router
                                .route(declaringType, cause)
                                .map(route -> requestArgumentSatisfier.fulfillArgumentRequirements(route, nettyHttpRequest))
                                .filter(RouteMatch::isExecutable);
                    }


                    if (match.isPresent()) {
                        RouteMatch finalRoute = match.get();
                        Object result = finalRoute.execute();
                        ctx.writeAndFlush(result)
                                .addListener(NettyHttpServer.createCloseListener(nettyHttpRequest.getNativeRequest()));
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

    /**
     * Handle a 400 BAD REQUEST
     *
     * @param context The context
     * @param request The request
     * @param route The route
     */
    void handleBadRequest(ChannelHandlerContext context, HttpRequest<?>request, RouteMatch<Object> route) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Bad request: Unsatisfiable route reached: " + route);
        }

        try {
            context.writeAndFlush(router.route(HttpStatus.BAD_REQUEST)
                    .map(match -> requestArgumentSatisfier.fulfillArgumentRequirements(match, request))
                    .filter(RouteMatch::isExecutable)
                    .map(RouteMatch::execute)
                    .orElse(HttpResponse.badRequest()))
                    .addListener(ChannelFutureListener.CLOSE);
        } catch (Exception e) {
            throw new InternalServerException("Error executing status code 400 handler: " + e.getMessage(), e);
        }
    }

    /**
     * Handle a 404 NOT FOUND response
     *
     * @param ctx The context
     * @param request The request
     */
    void handleNotFound(ChannelHandlerContext ctx, HttpRequest<?> request) {
        Object notFoundResponse =
                findStatusRoute(HttpStatus.NOT_FOUND, request)
                        .map(RouteMatch::execute)
                        .orElse(HttpResponse.notFound());

        ctx.writeAndFlush(notFoundResponse)
                .addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Handle 415 UNSUPPORTED MEDIA TYPE
     *
     * @param ctx The context
     * @param request The request
     * @param contentType The content type
     */
    void handleUnsupportedMediaType(ChannelHandlerContext ctx, HttpRequest<?> request, MediaType contentType) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Matched route is not a supported media type: {}", contentType);
        }

        // if the content type is not accepted send by 415 - UNSUPPORTED MEDIA TYPE
        Object unsupportedResult =
                findStatusRoute(HttpStatus.UNSUPPORTED_MEDIA_TYPE, request)
                        .map(RouteMatch::execute)
                        .orElse(HttpResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE));

        ctx.writeAndFlush(unsupportedResult)
                .addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Handle a 405 METHOD NOT ALLOWED response
     * @param ctx The context
     * @param request The request
     * @param allowedMethods The allowed methods
     */
    void handleMethodNotAllowed(ChannelHandlerContext ctx, HttpRequest<?> request, Set<HttpMethod> allowedMethods) {
        // if there are other routes that match send back 405 - METHOD_NOT_ALLOWED
        Object notAllowedResponse =
                findStatusRoute(HttpStatus.METHOD_NOT_ALLOWED, request)
                        .map(RouteMatch::execute)
                        .orElse(HttpResponse.notAllowed(
                                allowedMethods
                        ));

        ctx.writeAndFlush(notAllowedResponse)
                .addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Find a route for the given status code
     *
     * @param status The status
     * @param request The request
     * @return An optional route
     */
    Optional<RouteMatch<Object>> findStatusRoute(HttpStatus status, HttpRequest<?> request) {
        return router.route(status)
                .map(match -> requestArgumentSatisfier.fulfillArgumentRequirements(match, request))
                .filter(RouteMatch::isExecutable);
    }



    @SuppressWarnings("unchecked")
    private void handleWithExceptionHandlers(ChannelHandlerContext ctx, NettyHttpRequest nettyHttpRequest, Throwable cause) {
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

    private void writeServerErrorResponse(ChannelHandlerContext ctx, NettyHttpRequest nettyHttpRequest, Throwable cause) {
        try {
            if (LOG.isErrorEnabled()) {
                LOG.error("Unexpected error occurred: " + cause.getMessage(), cause);
            }

            Object errorResponse = findStatusRoute(HttpStatus.INTERNAL_SERVER_ERROR, nettyHttpRequest)
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

    private void writeDefaultErrorResponse(ChannelHandlerContext ctx) {
        ctx.channel()
                .writeAndFlush(HttpResponse.serverError())
                .addListener(ChannelFutureListener.CLOSE);
    }
}
