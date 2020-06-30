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

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.*;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.websocket.WebSocketSessionRepository;
import io.micronaut.http.server.netty.NettyHttpRequest;
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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.ssl.SslHandler;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.BiConsumer;
import io.reactivex.schedulers.Schedulers;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles WebSocket upgrade requests.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class NettyServerWebSocketUpgradeHandler extends SimpleChannelInboundHandler<NettyHttpRequest<?>> {

    public static final String ID = ChannelPipelineCustomizer.HANDLER_WEBSOCKET_UPGRADE;
    public static final String SCHEME_WEBSOCKET = "ws://";
    public static final String SCHEME_SECURE_WEBSOCKET = "wss://";

    private static final Logger LOG = LoggerFactory.getLogger(NettyServerWebSocketUpgradeHandler.class);

    private final Router router;
    private final RequestBinderRegistry binderRegistry;
    private final WebSocketBeanRegistry webSocketBeanRegistry;
    private final MediaTypeCodecRegistry mediaTypeCodecRegistry;
    private final WebSocketSessionRepository webSocketSessionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private WebSocketServerHandshaker handshaker;

    /**
     * Default constructor.
     *
     * @param webSocketSessionRepository The websocket sessions repository
     * @param router                     The router
     * @param binderRegistry             the request binder registry
     * @param webSocketBeanRegistry      The web socket bean register
     * @param mediaTypeCodecRegistry     The codec registry
     * @param eventPublisher             The event publisher
     */
    public NettyServerWebSocketUpgradeHandler(
            WebSocketSessionRepository webSocketSessionRepository,
            Router router,
            RequestBinderRegistry binderRegistry,
            WebSocketBeanRegistry webSocketBeanRegistry,
            MediaTypeCodecRegistry mediaTypeCodecRegistry,
            ApplicationEventPublisher eventPublisher) {
        this.router = router;
        this.binderRegistry = binderRegistry;
        this.webSocketBeanRegistry = webSocketBeanRegistry;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.webSocketSessionRepository = webSocketSessionRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public boolean acceptInboundMessage(Object msg) {
        if (msg instanceof NettyHttpRequest) {
            NettyHttpRequest<?> request = (NettyHttpRequest) msg;
            HttpHeaders headers = request.getHeaders();
            String connectValue = headers.get(HttpHeaderNames.CONNECTION, String.class).orElse("").toLowerCase(Locale.ENGLISH);
            return connectValue.contains(HttpHeaderValues.UPGRADE) &&
                    "WebSocket".equalsIgnoreCase(headers.get(HttpHeaderNames.UPGRADE));
        }

        return false;
    }

    @Override
    protected final void channelRead0(ChannelHandlerContext ctx, NettyHttpRequest<?> msg) {

        Optional<UriRouteMatch<Object, Object>> routeMatch = router.find(HttpMethod.GET, msg.getUri().toString(), msg)
                .filter(rm -> rm.isAnnotationPresent(OnMessage.class) || rm.isAnnotationPresent(OnOpen.class))
                .findFirst();

        if (routeMatch.isPresent()) {
            UriRouteMatch<Object, Object> rm = routeMatch.get();
            msg.setAttribute(HttpAttributes.ROUTE_MATCH, rm);
            List<HttpFilter> filters = router.findFilters(msg);
            AtomicReference<HttpRequest<?>> requestReference = new AtomicReference<>(msg);
            MutableHttpResponse<?> proceed = HttpResponse.ok();
            Publisher<MutableHttpResponse<?>> routePublisher = Flowable.create(emitter -> {
                emitter.onNext(proceed);
                emitter.onComplete();
            }, BackpressureStrategy.ERROR);


            Publisher<? extends MutableHttpResponse<?>> finalPublisher;

            if (!filters.isEmpty()) {
                // make the action executor the last filter in the chain
                filters = new ArrayList<>(filters);
                filters.add((HttpServerFilter) (req, chain) -> routePublisher);

                AtomicInteger integer = new AtomicInteger();
                int len = filters.size();
                List<HttpFilter> finalFilters = filters;
                ServerFilterChain filterChain = new ServerFilterChain() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public Publisher<MutableHttpResponse<?>> proceed(io.micronaut.http.HttpRequest<?> request) {
                        int pos = integer.incrementAndGet();
                        if (pos > len) {
                            throw new IllegalStateException("The FilterChain.proceed(..) method should be invoked exactly once per filter execution. The method has instead been invoked multiple times by an erroneous filter definition.");
                        }
                        HttpFilter httpFilter = finalFilters.get(pos);
                        return (Publisher<MutableHttpResponse<?>>) httpFilter.doFilter(requestReference.getAndSet(request), this);
                    }
                };
                HttpFilter httpFilter = filters.get(0);
                Publisher<? extends HttpResponse<?>> resultingPublisher = httpFilter.doFilter(requestReference.get(), filterChain);
                finalPublisher = (Publisher<? extends MutableHttpResponse<?>>) resultingPublisher;
            } else {
                finalPublisher = routePublisher;
            }

            Channel channel = ctx.channel();
            Single.fromPublisher(finalPublisher).subscribeOn(Schedulers.from(channel.eventLoop())).subscribe((BiConsumer<MutableHttpResponse<?>, Throwable>) (actualResponse, throwable) -> {
                if (throwable != null) {
                    ctx.fireExceptionCaught(throwable);
                } else if (actualResponse == proceed) {
                    //Adding new handler to the existing pipeline to handle WebSocket Messages
                    WebSocketBean<?> webSocketBean = webSocketBeanRegistry.getWebSocket(rm.getTarget().getClass());

                    handleHandshake(ctx, msg, webSocketBean, actualResponse);

                    ChannelPipeline pipeline = ctx.pipeline();

                    try {
                        // re-configure the pipeline
                        pipeline.remove(ChannelPipelineCustomizer.HANDLER_HTTP_STREAM);
                        pipeline.remove(NettyServerWebSocketUpgradeHandler.this);
                        ChannelHandler accessLoggerHandler = pipeline.get(ChannelPipelineCustomizer.HANDLER_ACCESS_LOGGER);
                        if (accessLoggerHandler !=  null) {
                            pipeline.remove(accessLoggerHandler);
                        }
                        NettyServerWebSocketHandler webSocketHandler = new NettyServerWebSocketHandler(
                                webSocketSessionRepository,
                                handshaker,
                                msg,
                                rm,
                                webSocketBean,
                                binderRegistry,
                                mediaTypeCodecRegistry,
                                eventPublisher,
                                ctx
                        );
                        pipeline.addAfter("wsdecoder", NettyServerWebSocketHandler.ID, webSocketHandler);

                    } catch (Throwable e) {
                        if (LOG.isErrorEnabled()) {
                            LOG.error("Error opening WebSocket: " + e.getMessage(), e);
                        }
                        ctx.writeAndFlush(new CloseWebSocketFrame(CloseReason.INTERNAL_ERROR.getCode(), CloseReason.INTERNAL_ERROR.getReason()));
                    }
                } else {
                    ctx.writeAndFlush(actualResponse);
                }
            });
        } else {
            ctx.fireExceptionCaught(new HttpStatusException(HttpStatus.NOT_FOUND, "WebSocket Not Found"));
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
    protected ChannelFuture handleHandshake(ChannelHandlerContext ctx, NettyHttpRequest req, WebSocketBean<?> webSocketBean, MutableHttpResponse<?> response) {
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
        MutableHttpHeaders headers = response.getHeaders();
        io.netty.handler.codec.http.HttpHeaders nettyHeaders;
        if (headers instanceof NettyHttpHeaders) {
            nettyHeaders = ((NettyHttpHeaders) headers).getNettyHeaders();
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
                    req.getNativeRequest(),
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
    protected String getWebSocketURL(ChannelHandlerContext ctx, HttpRequest req) {
        boolean isSecure = ctx.pipeline().get(SslHandler.class) != null;
        return (isSecure ? SCHEME_SECURE_WEBSOCKET : SCHEME_WEBSOCKET) + req.getHeaders().get(HttpHeaderNames.HOST) + req.getUri();
    }
}
