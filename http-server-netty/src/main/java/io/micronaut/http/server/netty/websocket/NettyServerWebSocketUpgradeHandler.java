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
package io.micronaut.http.server.netty.websocket;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.context.ServerHttpRequestContext;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.websocket.WebSocketSessionRepository;
import io.micronaut.http.server.RequestLifecycle;
import io.micronaut.http.server.RouteExecutor;
import io.micronaut.http.server.netty.NettyEmbeddedServices;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.RoutingInBoundHandler;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.handler.OutboundAccess;
import io.micronaut.http.server.netty.handler.RequestHandler;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import io.micronaut.websocket.context.WebSocketBean;
import io.micronaut.websocket.context.WebSocketBeanRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AsciiString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Handles WebSocket upgrade requests.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public final class NettyServerWebSocketUpgradeHandler implements RequestHandler {

    public static final String ID = ChannelPipelineCustomizer.HANDLER_WEBSOCKET_UPGRADE;
    public static final String SCHEME_WEBSOCKET = "ws://";
    public static final String SCHEME_SECURE_WEBSOCKET = "wss://";

    public static final String COMPRESSION_HANDLER = "WebSocketServerCompressionHandler";

    private static final Logger LOG = LoggerFactory.getLogger(NettyServerWebSocketUpgradeHandler.class);
    private static final AsciiString WEB_SOCKET_HEADER_VALUE = AsciiString.cached("websocket");

    private final Router router;
    private final WebSocketBeanRegistry webSocketBeanRegistry;
    private final WebSocketSessionRepository webSocketSessionRepository;
    private final RouteExecutor routeExecutor;
    private final NettyEmbeddedServices nettyEmbeddedServices;
    private final ConversionService conversionService;
    private final NettyHttpServerConfiguration serverConfiguration;
    private WebSocketServerHandshaker handshaker;
    private boolean cancelUpgrade = false;

    private RoutingInBoundHandler next;

    /**
     * Default constructor.
     *
     * @param embeddedServices The embedded server services
     * @param webSocketSessionRepository The websocket session repository
     * @param conversionService The conversion service
     * @param serverConfiguration The server configuration
     */
    public NettyServerWebSocketUpgradeHandler(NettyEmbeddedServices embeddedServices,
                                              WebSocketSessionRepository webSocketSessionRepository,
                                              ConversionService conversionService,
                                              NettyHttpServerConfiguration serverConfiguration) {
        this.router = embeddedServices.getRouter();
        this.webSocketBeanRegistry = WebSocketBeanRegistry.forServer(embeddedServices.getApplicationContext());
        this.webSocketSessionRepository = webSocketSessionRepository;
        this.routeExecutor = embeddedServices.getRouteExecutor();
        this.nettyEmbeddedServices = embeddedServices;
        this.conversionService = conversionService;
        this.serverConfiguration = serverConfiguration;
    }

    static boolean isWebSocketUpgrade(@NonNull io.netty.handler.codec.http.HttpRequest request) {
        HttpHeaders headers = request.headers();
        if (headers.containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true)) {
            return headers.containsValue(HttpHeaderNames.UPGRADE, WEB_SOCKET_HEADER_VALUE, true);
        }
        return false;
    }

    @Override
    public void accept(ChannelHandlerContext ctx, io.netty.handler.codec.http.HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
        if (isWebSocketUpgrade(request)) {
            NettyHttpRequest<?> msg = new NettyHttpRequest<>(request, body, ctx, conversionService, serverConfiguration);

            Optional<UriRouteMatch<Object, Object>> optionalRoute = router.find(HttpMethod.GET, msg.getPath(), msg)
                .filter(rm -> rm.isAnnotationPresent(OnMessage.class) || rm.isAnnotationPresent(OnOpen.class))
                .findFirst();

            WebsocketRequestLifecycle requestLifecycle = new WebsocketRequestLifecycle(routeExecutor, optionalRoute.orElse(null));
            ExecutionFlow<HttpResponse<?>> responseFlow = ExecutionFlow.async(ctx.channel().eventLoop(), () -> {
                try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().plus(new ServerHttpRequestContext(msg)).propagate()) {
                    return requestLifecycle.handle(msg);
                }
            });
            responseFlow.onComplete((response, throwable) -> {
                if (response != null) {
                    writeResponse(ctx, msg, requestLifecycle.shouldProceedNormally, response, outboundAccess);
                }
            });
        } else {
            next.accept(ctx, request, body, outboundAccess);
        }
    }

    @Override
    public void handleUnboundError(Throwable cause) {
        next.handleUnboundError(cause);
    }

    @Override
    public void responseWritten(Object attachment) {
        next.responseWritten(attachment);
    }

    private void writeResponse(ChannelHandlerContext ctx,
                               NettyHttpRequest<?> msg,
                               boolean shouldProceedNormally,
                               HttpResponse<?> actualResponse,
                               OutboundAccess outboundAccess) {
        if (cancelUpgrade) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Cancelling websocket upgrade, handler was removed while request was processing");
            }
            return;
        }

        if (shouldProceedNormally) {
            UriRouteMatch<Object, Object> routeMatch = RouteAttributes.getRouteMatch(actualResponse)
                .map(rm -> (UriRouteMatch<Object, Object>) rm)
                .orElseThrow(() -> new IllegalStateException("Route match is required!"));
            //Adding new handler to the existing pipeline to handle WebSocket Messages
            WebSocketBean<?> webSocketBean = webSocketBeanRegistry.getWebSocket(routeMatch.getTarget().getClass());

            handleHandshake(ctx, msg, webSocketBean, actualResponse);

            ChannelPipeline pipeline = ctx.pipeline();

            try {
                // re-configure the pipeline
                NettyServerWebSocketHandler webSocketHandler = new NettyServerWebSocketHandler(
                    nettyEmbeddedServices,
                    webSocketSessionRepository,
                    handshaker,
                    webSocketBean,
                    msg,
                    routeMatch,
                    ctx,
                    serverConfiguration.getThreadSelection(),
                    routeExecutor.getExecutorSelector(),
                    routeExecutor.getCoroutineHelper().orElse(null));
                pipeline.addBefore(ctx.name(), NettyServerWebSocketHandler.ID, webSocketHandler);

                pipeline.remove(ctx.name());
                try {
                    pipeline.remove(ChannelPipelineCustomizer.HANDLER_ACCESS_LOGGER);
                } catch (NoSuchElementException ignored) {
                }

                // websocket needs auto read for now
                ctx.channel().config().setAutoRead(true);
            } catch (Throwable e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error opening WebSocket: {}", e.getMessage(), e);
                }
                ctx.writeAndFlush(new CloseWebSocketFrame(CloseReason.INTERNAL_ERROR.getCode(), CloseReason.INTERNAL_ERROR.getReason()));
            }
        } else {
            next.writeResponse(outboundAccess, msg, actualResponse, null);
        }
    }

    /**
     * Do the handshaking for WebSocket request.
     *
     * @param ctx           The channel handler context
     * @param req           The request
     * @param webSocketBean The web socket bean
     * @param response      The response
     * @return The channel future
     **/
    private ChannelFuture handleHandshake(ChannelHandlerContext ctx, NettyHttpRequest req, WebSocketBean<?> webSocketBean, HttpResponse<?> response) {
        int maxFramePayloadLength = webSocketBean.messageMethod()
                .map(m -> m.intValue(OnMessage.class, "maxPayloadLength")
                .orElse(65536)).orElse(65536);
        String subprotocols = webSocketBean.getBeanDefinition().stringValue(ServerWebSocket.class, "subprotocols")
                                           .filter(s -> !StringUtils.isEmpty(s))
                                           .orElse(null);
        WebSocketServerHandshakerFactory wsFactory =
                new WebSocketServerHandshakerFactory(
                        getWebSocketURL(ctx, req),
                        subprotocols,
                        true,
                        maxFramePayloadLength
                );
        handshaker = wsFactory.newHandshaker(req.getNativeRequest());
        io.micronaut.http.HttpHeaders headers = response.getHeaders();
        io.netty.handler.codec.http.HttpHeaders nettyHeaders;
        if (headers instanceof NettyHttpHeaders httpHeaders) {
            nettyHeaders = httpHeaders.getNettyHeaders();
        } else {
            nettyHeaders = new DefaultHttpHeaders();
            for (Map.Entry<String, List<String>> entry : headers) {
                nettyHeaders.add(entry.getKey(), entry.getValue());
            }
        }
        Channel channel = ctx.channel();
        if (handshaker == null) {
            return WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(channel);
        } else {
            return handshaker.handshake(
                    channel,
                    req.toFullHttpRequest(),
                    nettyHeaders,
                    channel.newPromise()
            );
        }
    }

    /**
     * Obtains the web socket URL.
     *
     * @param ctx The context
     * @param req The request
     * @return The socket URL
     */
    private String getWebSocketURL(ChannelHandlerContext ctx, HttpRequest req) {
        boolean isSecure = ctx.pipeline().get(SslHandler.class) != null;
        return (isSecure ? SCHEME_SECURE_WEBSOCKET : SCHEME_WEBSOCKET) + req.getHeaders().get(HttpHeaderNames.HOST) + req.getUri();
    }

    @Override
    public void removed() {
        cancelUpgrade = true;
    }

    public void setNext(RoutingInBoundHandler next) {
        this.next = next;
    }

    private static final class WebsocketRequestLifecycle extends RequestLifecycle {
        @Nullable
        final RouteMatch<?> route;

        boolean shouldProceedNormally;

        WebsocketRequestLifecycle(RouteExecutor routeExecutor, @Nullable RouteMatch<?> route) {
            super(routeExecutor);
            this.route = route;
        }

        ExecutionFlow<HttpResponse<?>> handle(HttpRequest<?> request) {
            MutableHttpResponse<?> proceed = HttpResponse.ok();

            if (route != null) {
                RouteAttributes.setRouteMatch(request, route);
                RouteAttributes.setRouteInfo(request, route.getRouteInfo());
                RouteAttributes.setRouteMatch(proceed, route);
                RouteAttributes.setRouteInfo(proceed, route.getRouteInfo());
            }

            ExecutionFlow<HttpResponse<?>> response;
            if (route != null) {
                response = runWithFilters(request, (filteredRequest, propagatedContext) -> ExecutionFlow.just(proceed));
            } else {
                response = onError(request, new HttpStatusException(HttpStatus.NOT_FOUND, "WebSocket Not Found"))
                    .putInContext(ServerRequestContext.KEY, request);
            }
            return response.map(r -> {
                if (r == proceed) {
                    shouldProceedNormally = true;
                }
                return r;
            });
        }
    }
}
