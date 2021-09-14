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
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.bind.BoundExecutable;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.netty.websocket.AbstractNettyWebSocketHandler;
import io.micronaut.http.netty.websocket.NettyWebSocketSession;
import io.micronaut.http.netty.websocket.WebSocketSessionRepository;
import io.micronaut.http.server.netty.NettyEmbeddedServices;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.context.WebSocketBean;
import io.micronaut.websocket.event.WebSocketMessageProcessedEvent;
import io.micronaut.websocket.event.WebSocketSessionClosedEvent;
import io.micronaut.websocket.event.WebSocketSessionOpenEvent;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.security.Principal;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A handler for {@link WebSocketFrame} instances.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class NettyServerWebSocketHandler extends AbstractNettyWebSocketHandler {

    /**
     * The id of the handler used when adding it to the Netty pipeline.
     */
    public static final String ID = "websocket-handler";

    private final NettyEmbeddedServices nettyEmbeddedServices;

    /**
     * Default constructor.
     * @param nettyEmbeddedServices The netty embedded services
     * @param webSocketSessionRepository The web socket sessions repository
     * @param handshaker     The handshaker
     * @param request        The request used to create the websocket
     * @param routeMatch     The route match
     * @param webSocketBean  The web socket bean
     * @param ctx            The channel handler context
     */
    NettyServerWebSocketHandler(
            NettyEmbeddedServices nettyEmbeddedServices,
            WebSocketSessionRepository webSocketSessionRepository,
            WebSocketServerHandshaker handshaker,
            WebSocketBean<?> webSocketBean,
            HttpRequest<?> request,
            UriRouteMatch<Object, Object> routeMatch,
            ChannelHandlerContext ctx) {
        super(
                ctx,
                nettyEmbeddedServices.getRequestArgumentSatisfier().getBinderRegistry(),
                nettyEmbeddedServices.getMediaTypeCodecRegistry(),
                webSocketBean,
                request,
                routeMatch.getVariableValues(),
                handshaker.version(),
                handshaker.selectedSubprotocol(),
                webSocketSessionRepository);

        this.nettyEmbeddedServices = nettyEmbeddedServices;
        request.setAttribute(HttpAttributes.ROUTE_MATCH, routeMatch);
        request.setAttribute(HttpAttributes.ROUTE, routeMatch.getRoute());
        ApplicationEventPublisher<WebSocketSessionOpenEvent> eventPublisher =
                nettyEmbeddedServices.getEventPublisher(WebSocketSessionOpenEvent.class);

        try {
            eventPublisher.publishEvent(new WebSocketSessionOpenEvent(session));
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error publishing WebSocket opened event: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            writeCloseFrameAndTerminate(ctx, CloseReason.GOING_AWAY);
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public boolean acceptInboundMessage(Object msg) {
        return msg instanceof WebSocketFrame;
    }

    @Override
    protected NettyWebSocketSession createWebSocketSession(ChannelHandlerContext ctx) {
        String id = originatingRequest.getHeaders().get(HttpHeaderNames.SEC_WEBSOCKET_KEY);
        final Channel channel = ctx.channel();

        NettyWebSocketSession session = new NettyWebSocketSession(
                id,
                channel,
                originatingRequest,
                mediaTypeCodecRegistry,
                webSocketVersion.toHttpHeaderValue(),
                ctx.pipeline().get(SslHandler.class) != null
        ) {

            private final ConvertibleValues<Object> uriVars = ConvertibleValues.of(uriVariables);

            @Override
            public Optional<String> getSubprotocol() {
                return Optional.ofNullable(subProtocol);
            }

            @Override
            public Set<? extends WebSocketSession> getOpenSessions() {
                return webSocketSessionRepository.getChannelGroup().stream()
                        .flatMap((Function<Channel, Stream<WebSocketSession>>) ch -> {
                            NettyWebSocketSession s = ch.attr(NettyWebSocketSession.WEB_SOCKET_SESSION_KEY).get();
                            if (s != null && s.isOpen()) {
                                return Stream.of(s);
                            }
                            return Stream.empty();
                        }).collect(Collectors.toSet());
            }

            @Override
            public void close(CloseReason closeReason) {
                super.close(closeReason);
                webSocketSessionRepository.removeChannel(ctx.channel());
            }

            @Override
            public Optional<Principal> getUserPrincipal() {
                return originatingRequest.getAttribute(HttpAttributes.PRINCIPAL, Principal.class);
            }

            @Override
            public ConvertibleValues<Object> getUriVariables() {
                return uriVars;
            }

        };

        webSocketSessionRepository.addChannel(channel);

        return session;
    }

    @Override
    protected Publisher<?> instrumentPublisher(ChannelHandlerContext ctx, Object result) {
        Publisher<?> actual = Publishers.convertPublisher(result, Publisher.class);
        Publisher<?> traced = (Publisher<Object>) subscriber -> ServerRequestContext.with(originatingRequest,
                                                                                          () -> actual.subscribe(new Subscriber<Object>() {
              @Override
              public void onSubscribe(Subscription s) {
                  ServerRequestContext.with(
                          originatingRequest,
                          () -> subscriber.onSubscribe(
                                  s));
              }

              @Override
              public void onNext(Object object) {
                  ServerRequestContext.with(
                          originatingRequest,
                          () -> subscriber.onNext(
                                  object));
              }

              @Override
              public void onError(Throwable t) {
                  ServerRequestContext.with(
                          originatingRequest,
                          () -> subscriber.onError(
                                  t));
              }

              @Override
              public void onComplete() {
                  ServerRequestContext.with(
                          originatingRequest,
                          subscriber::onComplete);
              }
          }));

        return Flux.from(traced).subscribeOn(Schedulers.fromExecutorService(ctx.channel().eventLoop()));
    }

    @Override
    protected Object invokeExecutable(BoundExecutable boundExecutable, MethodExecutionHandle<?, ?> messageHandler) {
        return ServerRequestContext.with(originatingRequest,
                                         (Supplier<Object>) () -> boundExecutable.invoke(messageHandler.getTarget()));
    }

    @Override
    protected void messageHandled(ChannelHandlerContext ctx, NettyWebSocketSession session, Object message) {
        ctx.executor().execute(() -> {
            try {
                nettyEmbeddedServices.getEventPublisher(WebSocketMessageProcessedEvent.class)
                        .publishEvent(new WebSocketMessageProcessedEvent<>(session, message));
            } catch (Exception e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error publishing WebSocket message processed event: " + e.getMessage(), e);
                }
            }
        });
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        channel.attr(NettyWebSocketSession.WEB_SOCKET_SESSION_KEY).set(null);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Removing WebSocket Server session: " + session);
        }
        webSocketSessionRepository.removeChannel(channel);
        try {
            nettyEmbeddedServices.getEventPublisher(WebSocketSessionClosedEvent.class)
                    .publishEvent(new WebSocketSessionClosedEvent(session));
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error publishing WebSocket closed event: " + e.getMessage(), e);
            }
        }
        super.handlerRemoved(ctx);
    }

}
