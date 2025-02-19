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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.bind.BoundExecutable;
import io.micronaut.core.bind.DefaultExecutableBinder;
import io.micronaut.core.bind.ExecutableBinder;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.KotlinUtils;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.ContinuationArgumentBinder;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.netty.websocket.AbstractNettyWebSocketHandler;
import io.micronaut.http.netty.websocket.NettyWebSocketSession;
import io.micronaut.http.netty.websocket.WebSocketSessionRepository;
import io.micronaut.http.server.CoroutineHelper;
import io.micronaut.http.server.netty.NettyEmbeddedServices;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.scheduling.executor.ThreadSelection;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketPongMessage;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.bind.WebSocketState;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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

    private final NettyWebSocketSession serverSession;
    private final NettyEmbeddedServices nettyEmbeddedServices;
    @Nullable
    private final CoroutineHelper coroutineHelper;

    private final Argument<?> bodyArgument;
    private final Argument<?> pongArgument;
    private final ThreadSelection threadSelection;
    private final ExecutorSelector executorSelector;

    /**
     * Default constructor.
     *
     * @param nettyEmbeddedServices      The netty embedded services
     * @param webSocketSessionRepository The web socket sessions repository
     * @param handshaker                 The handshaker
     * @param webSocketBean              The web socket bean
     * @param request                    The request used to create the websocket
     * @param routeMatch                 The route match
     * @param ctx                        The channel handler context
     * @param executorSelector
     * @param coroutineHelper            Helper for kotlin coroutines
     */
    NettyServerWebSocketHandler(
        NettyEmbeddedServices nettyEmbeddedServices,
        WebSocketSessionRepository webSocketSessionRepository,
        WebSocketServerHandshaker handshaker,
        WebSocketBean<?> webSocketBean,
        HttpRequest<?> request,
        UriRouteMatch<Object, Object> routeMatch,
        ChannelHandlerContext ctx,
        ThreadSelection threadSelection,
        ExecutorSelector executorSelector,
        @Nullable CoroutineHelper coroutineHelper) {
        super(
                nettyEmbeddedServices.getRequestArgumentSatisfier().getBinderRegistry(),
                nettyEmbeddedServices.getMediaTypeCodecRegistry(),
                nettyEmbeddedServices.getMessageBodyHandlerRegistry(),
                webSocketBean,
                request,
                routeMatch.getVariableValues(),
                handshaker.version(),
                handshaker.selectedSubprotocol(),
                webSocketSessionRepository,
                nettyEmbeddedServices.getApplicationContext().getConversionService());

        this.threadSelection = threadSelection;
        this.executorSelector = executorSelector;

        this.serverSession = createWebSocketSession(ctx);

        ExecutableBinder<WebSocketState> binder = new DefaultExecutableBinder<>();

        if (messageHandler != null) {
            BoundExecutable<?, ?> bound = binder.tryBind(messageHandler.getExecutableMethod(), webSocketBinder, new WebSocketState(serverSession, originatingRequest));
            List<Argument<?>> unboundArguments = bound.getUnboundArguments();

            if (unboundArguments.size() == 1) {
                this.bodyArgument = unboundArguments.iterator().next();
            } else {
                this.bodyArgument = null;
                if (LOG.isErrorEnabled()) {
                    LOG.error("WebSocket @OnMessage method " + webSocketBean.getTarget() + "." + messageHandler.getExecutableMethod() + " should define exactly 1 message parameter, but found 2 possible candidates: " + unboundArguments);
                }

                if (serverSession.isOpen()) {
                    serverSession.close(CloseReason.INTERNAL_ERROR);
                }
            }
        } else {
            this.bodyArgument = null;
        }

        if (pongHandler != null) {
            BoundExecutable<?, ?> bound = binder.tryBind(pongHandler.getExecutableMethod(), webSocketBinder, new WebSocketState(serverSession, originatingRequest));
            List<Argument<?>> unboundArguments = bound.getUnboundArguments();
            if (unboundArguments.size() == 1 && unboundArguments.get(0).isAssignableFrom(WebSocketPongMessage.class)) {
                this.pongArgument = unboundArguments.get(0);
            } else {
                this.pongArgument = null;
                if (LOG.isErrorEnabled()) {
                    LOG.error("WebSocket @OnMessage pong handler method " + webSocketBean.getTarget() + "." + pongHandler.getExecutableMethod() + " should define exactly 1 message parameter assignable from a WebSocketPongMessage, but found: " + unboundArguments);
                }

                if (serverSession.isOpen()) {
                    serverSession.close(CloseReason.INTERNAL_ERROR);
                }
            }
        } else {
            this.pongArgument = null;
        }

        this.nettyEmbeddedServices = nettyEmbeddedServices;
        this.coroutineHelper = coroutineHelper;
        RouteAttributes.setRouteMatch(request, routeMatch);

        Flux.from(callOpenMethod(ctx)).subscribe(v -> { }, t -> {
            forwardErrorToUser(ctx, e -> {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error Opening WebSocket [" + webSocketBean + "]: " + e.getMessage(), e);
                }
            }, t);
        });

        ApplicationEventPublisher<WebSocketSessionOpenEvent> eventPublisher =
                nettyEmbeddedServices.getEventPublisher(WebSocketSessionOpenEvent.class);

        try {
            eventPublisher.publishEvent(new WebSocketSessionOpenEvent(serverSession));
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error publishing WebSocket opened event: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public NettyWebSocketSession getSession() {
        return serverSession;
    }

    @Override
    public Argument<?> getBodyArgument() {
        return bodyArgument;
    }

    @Override
    public Argument<?> getPongArgument() {
        return pongArgument;
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
                messageBodyHandlerRegistry,
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
        Publisher<?> actual = Publishers.convertToPublisher(conversionService, result);
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
        if (coroutineHelper != null) {
            Executable<?, ?> target = boundExecutable.getTarget();
            if (target instanceof ExecutableMethod<?, ?> executableMethod) {
                if (executableMethod.isSuspend()) {
                    return Flux.deferContextual(ctx -> {
                        try {
                            coroutineHelper.setupCoroutineContext(originatingRequest, ctx, PropagatedContext.getOrEmpty());

                            Object immediateReturnValue = invokeExecutable0(boundExecutable, messageHandler);

                            if (KotlinUtils.isKotlinCoroutineSuspended(immediateReturnValue)) {
                                return Mono.fromCompletionStage(ContinuationArgumentBinder.extractContinuationCompletableFutureSupplier(originatingRequest));
                            } else {
                                return Mono.empty();
                            }
                        } catch (Exception e) {
                            return Flux.error(e);
                        }
                    });
                }
            }
        }
        return invokeExecutable0(boundExecutable, messageHandler);
    }

    private Object invokeExecutable0(BoundExecutable boundExecutable, MethodExecutionHandle<?, ?> messageHandler) {
        return this.executorSelector.select(messageHandler.getExecutableMethod(), threadSelection)
            .map(
                executorService -> {
                    ReturnType<?> returnType = messageHandler.getExecutableMethod().getReturnType();
                    Mono<?> result;
                    if (returnType.isReactive()) {
                        result = Mono.from((Publisher<?>) boundExecutable.invoke(messageHandler.getTarget()))
                                     .contextWrite(reactorContext -> reactorContext.put(ServerRequestContext.KEY, originatingRequest));
                    } else if (returnType.isAsync()) {
                        result = Mono.fromFuture((Supplier<CompletableFuture<?>>) invokeWithContext(boundExecutable, messageHandler));
                    } else {
                        result = Mono.fromSupplier(invokeWithContext(boundExecutable, messageHandler));
                    }
                    return (Object) result.subscribeOn(Schedulers.fromExecutor(executorService));
                }
            ).orElseGet(invokeWithContext(boundExecutable, messageHandler));
    }

    private Supplier<?> invokeWithContext(BoundExecutable boundExecutable, MethodExecutionHandle<?, ?> messageHandler) {
        return () -> ServerRequestContext.with(originatingRequest,
            (Supplier<Object>) () -> boundExecutable.invoke(messageHandler.getTarget()));
    }

    @Override
    protected void messageHandled(ChannelHandlerContext ctx, Object message) {
        ctx.executor().execute(() -> {
            try {
                nettyEmbeddedServices.getEventPublisher(WebSocketMessageProcessedEvent.class)
                        .publishEvent(new WebSocketMessageProcessedEvent<>(getSession(), message));
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
            LOG.debug("Removing WebSocket Server session: {}", serverSession);
        }
        webSocketSessionRepository.removeChannel(channel);
        try {
            nettyEmbeddedServices.getEventPublisher(WebSocketSessionClosedEvent.class)
                    .publishEvent(new WebSocketSessionClosedEvent(serverSession));
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error publishing WebSocket closed event: " + e.getMessage(), e);
            }
        }
        super.handlerRemoved(ctx);
    }

}
