/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.http.server.netty.websocket;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.*;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.context.WebSocketBean;
import io.micronaut.websocket.context.WebSocketBeanRegistry;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public static final String ID = "websocket-upgrade-handler";
    public static final String SCHEME_WEBSOCKET = "ws://";
    public static final String SCHEME_SECURE_WEBSOCKET = "wss://";

    private static final Logger LOG = LoggerFactory.getLogger(NettyServerWebSocketUpgradeHandler.class);


    private final Router router;
    private final RequestBinderRegistry binderRegistry;
    private final WebSocketBeanRegistry webSocketBeanRegistry;
    private final MediaTypeCodecRegistry mediaTypeCodecRegistry;
    private WebSocketServerHandshaker handshaker;

    /**
     * Default constructor.
     * @param router The router
     * @param binderRegistry the request binder registry
     * @param webSocketBeanRegistry The web socket bean registyr
     * @param mediaTypeCodecRegistry The codec registry
     */
    public NettyServerWebSocketUpgradeHandler(Router router, RequestBinderRegistry binderRegistry, WebSocketBeanRegistry webSocketBeanRegistry, MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        this.router = router;
        this.binderRegistry = binderRegistry;
        this.webSocketBeanRegistry = webSocketBeanRegistry;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
    }

    @Override
    protected final void channelRead0(ChannelHandlerContext ctx, NettyHttpRequest<?> msg) {

        HttpHeaders headers = msg.getHeaders();
        if ("Upgrade".equalsIgnoreCase(headers.get(HttpHeaderNames.CONNECTION)) &&
                "WebSocket".equalsIgnoreCase(headers.get(HttpHeaderNames.UPGRADE))) {

            Optional<UriRouteMatch<Object, Object>> routeMatch = router.find(HttpMethod.GET, msg.getUri()).findFirst();

            if (routeMatch.isPresent()) {

                UriRouteMatch<Object, Object> rm = routeMatch.get();
                if (rm.isAnnotationPresent(OnMessage.class)) {
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

                            handleHandshake(ctx, msg, actualResponse);

                            ChannelPipeline pipeline = ctx.pipeline();

                            try {
                                // re-configure the pipeline
                                NettyWebSocketServerHandler webSocketHandler = new NettyWebSocketServerHandler(
                                        handshaker,
                                        msg,
                                        rm,
                                        webSocketBean,
                                        binderRegistry,
                                        mediaTypeCodecRegistry,
                                        ctx
                                );
                                pipeline.addAfter("wsdecoder", NettyWebSocketServerHandler.ID, webSocketHandler);
                                pipeline.remove(NettyHttpServer.HTTP_STREAMS_CODEC);
                                pipeline.remove(NettyServerWebSocketUpgradeHandler.this);
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
                }
                return;
            }

            ctx.fireExceptionCaught(new HttpStatusException(HttpStatus.NOT_FOUND, "WebSocket Not Found"));
        } else {
            // pass the message along
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * Do the handshaking for WebSocket request.
     * @param ctx The channel handler context
     * @param req The request
     * @param response The response
     **/
    protected ChannelFuture handleHandshake(ChannelHandlerContext ctx, NettyHttpRequest req, MutableHttpResponse<?> response) {
        WebSocketServerHandshakerFactory wsFactory =
                new WebSocketServerHandshakerFactory(
                        getWebSocketURL(ctx, req),
                        null,
                        true
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
     *
     * @param ctx The context
     * @param req The request
     * @return The socket URL
     */
    protected String getWebSocketURL(ChannelHandlerContext ctx, HttpRequest req) {
        boolean isSecure = ctx.pipeline().get(SslHandler.class) != null;
        return (isSecure ? SCHEME_SECURE_WEBSOCKET : SCHEME_WEBSOCKET) + req.getHeaders().get(HttpHeaderNames.HOST) + req.getUri() ;
    }
}
