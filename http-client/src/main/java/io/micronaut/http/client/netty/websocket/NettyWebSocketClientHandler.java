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
package io.micronaut.http.client.netty.websocket;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.BoundExecutable;
import io.micronaut.core.bind.DefaultExecutableBinder;
import io.micronaut.core.bind.ExecutableBinder;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.netty.websocket.AbstractNettyWebSocketHandler;
import io.micronaut.http.netty.websocket.NettyWebSocketSession;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketPongMessage;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.bind.WebSocketState;
import io.micronaut.websocket.context.WebSocketBean;
import io.micronaut.websocket.exceptions.WebSocketClientException;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import io.micronaut.websocket.interceptor.WebSocketSessionAware;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.ScheduledFuture;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handler for WebSocket clients.
 *
 * @param <T> The type emitted.
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class NettyWebSocketClientHandler<T> extends AbstractNettyWebSocketHandler {
    private final WebSocketClientHandshaker handshaker;
    /**
     * Generic version of {@link #webSocketBean}.
     */
    private final WebSocketBean<T> genericWebSocketBean;
    private final DelayedExecutionFlow<T> completion = DelayedExecutionFlow.create();
    @Nullable
    private final UriMatchInfo matchInfo;
    @Nullable
    private NettyWebSocketSession clientSession;
    @Nullable
    private FullHttpResponse handshakeResponse;
    @Nullable
    private Argument<?> clientBodyArgument;
    @Nullable
    private Argument<?> clientPongArgument;
    /**
     * Set once the endpoint was handed to {@link #completion}, or the connect was cancelled
     * before that: exactly one of the two wins.
     */
    private final AtomicBoolean connectSettled = new AtomicBoolean();
    private volatile boolean connectCancelled;
    private final AtomicReference<@Nullable Channel> channel = new AtomicReference<>();
    @Nullable
    private final Duration handshakeTimeout;
    @Nullable
    private ScheduledFuture<?> handshakeTimeoutTask;
    /**
     * Whether the stages the handlers return are awaited, see {@link #awaitCompletionStages()}.
     */
    private volatile boolean awaitCompletionStages;
    /**
     * What the handshake says about the instance, see {@link #getHandshakeOutcome()}: empty when
     * it says nothing, {@code null} until it is known. The first outcome wins.
     */
    private final AtomicReference<@Nullable HandshakeResult> handshakeOutcome = new AtomicReference<>();
    /**
     * Whether the channel was connected: a close before that is a failure to connect. Event loop only.
     */
    private boolean channelConnected;

    /**
     * Default constructor.
     *
     * @param request                    The originating request that created the WebSocket.
     * @param webSocketBean              The WebSocket client bean.
     * @param handshaker                 The handshaker
     * @param requestBinderRegistry      The request binder registry
     * @param mediaTypeCodecRegistry     The media type codec registry
     * @param messageBodyHandlerRegistry The handler registry
     * @param conversionService          The conversionService
     */
    public NettyWebSocketClientHandler(
            MutableHttpRequest<?> request,
            WebSocketBean<T> webSocketBean,
            final WebSocketClientHandshaker handshaker,
            RequestBinderRegistry requestBinderRegistry,
            MediaTypeCodecRegistry mediaTypeCodecRegistry,
            MessageBodyHandlerRegistry messageBodyHandlerRegistry,
            ConversionService conversionService) {
        this(request, webSocketBean, handshaker, requestBinderRegistry, mediaTypeCodecRegistry, messageBodyHandlerRegistry, conversionService, null);
    }

    /**
     * Constructor with a handshake timeout.
     *
     * @param request                    The originating request that created the WebSocket.
     * @param webSocketBean              The WebSocket client bean.
     * @param handshaker                 The handshaker
     * @param requestBinderRegistry      The request binder registry
     * @param mediaTypeCodecRegistry     The media type codec registry
     * @param messageBodyHandlerRegistry The handler registry
     * @param conversionService          The conversionService
     * @param handshakeTimeout           How long to wait for the handshake response once connected,
     *                                   or {@code null} to wait without a limit
     * @since 5.3.0
     */
    public NettyWebSocketClientHandler(
            MutableHttpRequest<?> request,
            WebSocketBean<T> webSocketBean,
            final WebSocketClientHandshaker handshaker,
            RequestBinderRegistry requestBinderRegistry,
            MediaTypeCodecRegistry mediaTypeCodecRegistry,
            MessageBodyHandlerRegistry messageBodyHandlerRegistry,
            ConversionService conversionService,
            @Nullable Duration handshakeTimeout) {
        super(requestBinderRegistry, mediaTypeCodecRegistry, messageBodyHandlerRegistry, webSocketBean, request, Collections.emptyMap(), handshaker.version(), handshaker.actualSubprotocol(), null, conversionService);
        this.handshaker = handshaker;
        this.genericWebSocketBean = webSocketBean;
        String clientPath = webSocketBean.getBeanDefinition().stringValue(ClientWebSocket.class).orElse("");
        UriMatchTemplate matchTemplate = UriMatchTemplate.of(clientPath);
        this.matchInfo = matchTemplate.tryMatch(request.getPath());
        this.handshakeTimeout = handshakeTimeout;
        completion.onCancel(this::cancelConnect);
    }

    /**
     * The connect was cancelled: close the connection unless the endpoint was already handed over.
     * Runs on the cancelling thread.
     */
    private void cancelConnect() {
        connectCancelled = true;
        if (connectSettled.compareAndSet(false, true)) {
            Channel ch = channel.get();
            if (ch != null) {
                ch.close();
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idleStateEvent) {
            if (idleStateEvent.state() == IdleState.ALL_IDLE && clientSession != null && clientSession.isOpen()) {
                // close the connection if it is idle for too long
                clientSession.close(CloseReason.NORMAL);
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public Argument<?> getBodyArgument() {
        return Objects.requireNonNull(clientBodyArgument);
    }

    @Override
    public Argument<?> getPongArgument() {
        return Objects.requireNonNull(clientPongArgument);
    }

    @Override
    public NettyWebSocketSession getSession() {
        return Objects.requireNonNull(clientSession);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        channel.set(ctx.channel());
        if (connectCancelled) {
            // cancelled before the handler was added
            ctx.close();
            return;
        }
        if (ctx.channel().isActive()) {
            channelActive(ctx);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        if (connectCancelled) {
            ctx.close();
            return;
        }
        channelConnected = true;
        if (handshakeTimeout != null && !handshakeTimeout.isNegative() && !handshakeTimeout.isZero() && handshakeTimeoutTask == null) {
            handshakeTimeoutTask = ctx.executor().schedule(() -> {
                if (!handshaker.isHandshakeComplete()) {
                    // before the completion, which reports the outcome
                    settleHandshakeOutcome(LoadBalancer.Outcome.TIMEOUT);
                    if (completion.tryCompleteExceptionally(new ReadTimeoutException())) {
                        ctx.close();
                    }
                }
            }, handshakeTimeout.toNanos(), TimeUnit.NANOSECONDS);
        }
        handshaker.handshake(ctx.channel()).addListener(future -> {
            if (future.isSuccess()) {
                ctx.channel().config().setAutoRead(true);
                ctx.read();
            } else {
                completion.tryCompleteExceptionally(future.cause());
            }
        });
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        final Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            cancelHandshakeTimeout();
            if (connectCancelled) {
                // the connect was cancelled and the channel is closing: the endpoint is not opened
                return;
            }
            // web socket client connected
            FullHttpResponse res = (FullHttpResponse) msg;
            this.handshakeResponse = res;
            // the instance responded, like the response of an HTTP exchange
            settleHandshakeOutcome(res.status().code() >= 500 ? LoadBalancer.Outcome.SERVER_ERROR : LoadBalancer.Outcome.SUCCESS);
            try {
                handshaker.finishHandshake(ch, res);
            } catch (Exception e) {
                try {
                    completion.tryCompleteExceptionally(new WebSocketClientException("Error finishing WebSocket handshake: " + e.getMessage(), e));
                } finally {
                    // clientSession isn't set yet, so we do the close manually instead of through session.close
                    ch.writeAndFlush(new CloseWebSocketFrame(CloseReason.INTERNAL_ERROR.getCode(), CloseReason.INTERNAL_ERROR.getReason()));
                    ch.close();
                }
                return;
            }

            this.clientSession = createWebSocketSession(ctx);

            T targetBean = genericWebSocketBean.getTarget();

            if (targetBean instanceof WebSocketSessionAware aware) {
                aware.setWebSocketSession(clientSession);
            }

            ExecutableBinder<WebSocketState> binder = new DefaultExecutableBinder<>();
            BoundExecutable<?, ?> bound = binder.tryBind(Objects.requireNonNull(messageHandler).getExecutableMethod(), webSocketBinder, new WebSocketState(clientSession, originatingRequest));
            List<Argument<?>> unboundArguments = bound.getUnboundArguments();

            if (unboundArguments.size() == 1) {
                this.clientBodyArgument = unboundArguments.iterator().next();
            } else {
                this.clientBodyArgument = null;

                try {
                    completion.tryCompleteExceptionally(new WebSocketClientException("WebSocket @OnMessage method " + targetBean.getClass().getSimpleName() + "." + messageHandler.getExecutableMethod() + " should define exactly 1 message parameter, but found 2 possible candidates: " + unboundArguments));
                } finally {
                    if (getSession().isOpen()) {
                        getSession().close(CloseReason.INTERNAL_ERROR);
                    }
                }
                return;
            }

            if (pongHandler != null) {
                BoundExecutable<?, ?> boundPong = binder.tryBind(pongHandler.getExecutableMethod(), webSocketBinder, new WebSocketState(clientSession, originatingRequest));
                List<Argument<?>> unboundPongArguments = boundPong.getUnboundArguments();

                if (unboundPongArguments.size() == 1 && unboundPongArguments.get(0).isAssignableFrom(WebSocketPongMessage.class)) {
                    this.clientPongArgument = unboundPongArguments.get(0);
                } else {
                    this.clientPongArgument = null;

                    try {
                        completion.tryCompleteExceptionally(new WebSocketClientException("WebSocket @OnMessage pong handler method " + targetBean.getClass().getSimpleName() + "." + messageHandler.getExecutableMethod() + " should define exactly 1 pong message parameter, but found: " + unboundArguments));
                    } finally {
                        if (getSession().isOpen()) {
                            getSession().close(CloseReason.INTERNAL_ERROR);
                        }
                    }
                    return;
                }
            }

            callOpenMethod(ctx).onComplete((v, t) -> {
                if (t != null) {
                    completion.tryCompleteExceptionally(new WebSocketSessionException("Error opening WebSocket client session: " + t.getMessage(), t));
                } else if (connectSettled.compareAndSet(false, true)) {
                    completion.tryComplete(targetBean);
                }
                // else: the connect was cancelled while the open method ran, the channel is closing
            });
            return;
        }

        if (msg instanceof WebSocketFrame frame) {
            handleWebSocketFrame(ctx, frame);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    protected NettyWebSocketSession createWebSocketSession(ChannelHandlerContext ctx) {
        return new NettyWebSocketSession(
            Objects.requireNonNull(handshakeResponse).headers().get(HttpHeaderNames.SEC_WEBSOCKET_ACCEPT),
            ctx.channel(),
            originatingRequest,
            mediaTypeCodecRegistry,
            messageBodyHandlerRegistry,
            handshaker.version().toHttpHeaderValue(),
            ctx.pipeline().get(SslHandler.class) != null
        ) {
            @Override
            public ConvertibleValues<Object> getUriVariables() {
                if (matchInfo != null) {
                    return ConvertibleValues.of(matchInfo.getVariableValues(), conversionService);
                }
                return ConvertibleValues.empty();
            }
        };
    }

    private void cancelHandshakeTimeout() {
        ScheduledFuture<?> task = handshakeTimeoutTask;
        if (task != null) {
            handshakeTimeoutTask = null;
            task.cancel(false);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        cancelHandshakeTimeout();
        super.handlerRemoved(ctx);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        if (!handshaker.isHandshakeComplete()) {
            // e.g. a TLS failure: it says nothing about the instance
            settleHandshakeOutcome(null);
        }
        completion.tryCompleteExceptionally(cause);
        super.exceptionCaught(ctx, cause);
    }

    /**
     * A flow that completes with the client endpoint bean once the handshake has completed and the
     * open method has been called, or with the error that prevented that.
     *
     * @return The handshake completion flow
     * @since 5.3.0
     */
    public final ExecutionFlow<T> getHandshakeCompletedFlow() {
        return completion;
    }

    /**
     * The client endpoint bean of this connection.
     *
     * @return The client endpoint
     * @since 5.3.0
     */
    public final T getClientEndpoint() {
        return genericWebSocketBean.getTarget();
    }

    /**
     * Await the {@link java.util.concurrent.CompletionStage} the open and message handlers of the
     * endpoint return, like a publisher: the connect completes once the stage of the open handler
     * completed, and a failed stage reaches the error handler. Used by the connections of an
     * {@link io.micronaut.websocket.AsyncWebSocketClient}; call it before the handler is added.
     *
     * @since 5.3.0
     */
    public final void awaitCompletionStages() {
        awaitCompletionStages = true;
    }

    @Override
    protected boolean awaitsCompletionStages() {
        return awaitCompletionStages;
    }

    /**
     * Close the connection of an endpoint that the connect completed with, but that nobody
     * received because the connect was cancelled at the same time. Unlike the endpoint's own
     * {@link AutoCloseable#close()}, this closes the session even for an endpoint class that does
     * not close it.
     *
     * @since 5.3.0
     */
    public final void closeUnclaimed() {
        NettyWebSocketSession session = clientSession;
        if (session != null && session.isOpen()) {
            session.close(CloseReason.GOING_AWAY);
        } else {
            Channel ch = channel.get();
            if (ch != null) {
                ch.close();
            }
        }
    }

    @Override
    protected void handleCloseReason(ChannelHandlerContext ctx, CloseReason cr, boolean writeCloseReason) {
        if (!handshaker.isHandshakeComplete()) {
            // closed by the instance before it responded, or not connected at all
            settleHandshakeOutcome(channelConnected ? LoadBalancer.Outcome.RESET : LoadBalancer.Outcome.CONNECT_FAILURE);
            completion.tryCompleteExceptionally(new WebSocketClientException("Error opening WebSocket client session: " + cr.getReason()));
            return;
        }
        super.handleCloseReason(ctx, cr, writeCloseReason);
    }

    /**
     * The connect of the channel of this handler failed: the instance could not be reached, unless
     * the failure is local, e.g. of the setup of the pipeline.
     *
     * @param failure The failure of the connect
     * @since 5.3.0
     */
    public final void connectFailed(Throwable failure) {
        settleHandshakeOutcome(failure instanceof WebSocketSessionException ? null : LoadBalancer.Outcome.CONNECT_FAILURE);
    }

    /**
     * What the handshake says about the instance, to report to the load balancer that selected it,
     * like the outcome of an HTTP exchange: {@link LoadBalancer.Outcome#SUCCESS} or
     * {@link LoadBalancer.Outcome#SERVER_ERROR} once the instance responded to the upgrade,
     * {@link LoadBalancer.Outcome#CONNECT_FAILURE} when it could not be reached,
     * {@link LoadBalancer.Outcome#TIMEOUT} when it did not respond within the handshake timeout,
     * {@link LoadBalancer.Outcome#RESET} when it closed the connection before it responded.
     *
     * @return The outcome, or {@code null} if the handshake says nothing about the instance (yet),
     * or the connect was cancelled
     * @since 5.3.0
     */
    public final LoadBalancer.@Nullable Outcome getHandshakeOutcome() {
        if (connectCancelled) {
            return null;
        }
        HandshakeResult result = handshakeOutcome.get();
        return result == null ? null : result.outcome();
    }

    private void settleHandshakeOutcome(LoadBalancer.@Nullable Outcome outcome) {
        handshakeOutcome.compareAndSet(null, new HandshakeResult(outcome));
    }

    /**
     * A settled handshake, including one that provides no load-balancer outcome.
     *
     * @param outcome The outcome, or {@code null} when the handshake says nothing about the instance
     */
    @Internal
    private record HandshakeResult(LoadBalancer.@Nullable Outcome outcome) {
    }
}
