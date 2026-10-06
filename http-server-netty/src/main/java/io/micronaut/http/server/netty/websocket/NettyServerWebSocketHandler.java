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

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.bind.BoundExecutable;
import io.micronaut.core.bind.DefaultExecutableBinder;
import io.micronaut.core.bind.ExecutableBinder;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.execution.ImmediateExecutor;
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
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.server.CoroutineHelper;
import io.micronaut.http.server.netty.NettyEmbeddedServices;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.scheduling.executor.ThreadSelectionConfiguration;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.websocket.WebSocketRouteEndpoint;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketPingMessage;
import io.micronaut.websocket.WebSocketPongMessage;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.bind.WebSocketState;
import io.micronaut.websocket.context.WebSocketBean;
import io.micronaut.websocket.event.WebSocketMessageProcessedEvent;
import io.micronaut.websocket.event.WebSocketSessionClosedEvent;
import io.micronaut.websocket.event.WebSocketSessionOpenEvent;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.Fuseable;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

import java.security.Principal;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

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

    /**
     * The most frames of a WebSocket route that wait for its handlers, see {@link #pendingFrames}.
     */
    private static final int MAX_PENDING_FRAMES = 16;

    private final NettyWebSocketSession serverSession;
    private final Channel channel;
    private final NettyEmbeddedServices nettyEmbeddedServices;
    @Nullable
    private final CoroutineHelper coroutineHelper;

    @Nullable
    private final Argument<?> bodyArgument;
    @Nullable
    private final Argument<?> pongArgument;
    private final ThreadSelectionConfiguration threadSelection;
    private final ExecutorSelector executorSelector;
    /**
     * The executor of a WebSocket route of handler functions, or {@code null} for a
     * {@code @ServerWebSocket} bean, whose methods select their executor.
     */
    @Nullable
    private final Executor routeExecutor;
    /**
     * The endpoint of a WebSocket route of handler functions, or {@code null} for a bean.
     */
    @Nullable
    private final WebSocketRouteEndpoint routeEndpoint;
    @Nullable
    private final MethodExecutionHandle<?, ?> pingHandler;
    @Nullable
    private final Argument<?> pingArgument;
    /**
     * The most messages of a connection to a WebSocket route that are handled at the same time,
     * see {@link io.micronaut.web.router.websocket.WebSocketRouteSpec#maxConcurrentMessages(int)}.
     * {@code 0} for a bean, whose messages are handled as they are read.
     */
    private final int maxConcurrentMessages;
    /**
     * Whether the endpoint of a WebSocket route receives the messages as a stream, see
     * {@link io.micronaut.web.router.websocket.WebSocketRouteSpec#onMessages}.
     */
    private final boolean streamsMessages;
    /**
     * Whether the open handler of a WebSocket route runs: the messages wait. Event loop only.
     */
    private boolean opening;
    /**
     * The handlers of the messages of a WebSocket route that run. Event loop only.
     */
    private int handling;
    /**
     * The data frames of a WebSocket route that wait for the handlers, in order. The connection
     * reads at most {@link #MAX_PENDING_FRAMES} ahead, so a client cannot send messages faster than
     * they are handled, while it still reads its pings and its close. Event loop only.
     */
    private final ArrayDeque<WebSocketFrame> pendingFrames = new ArrayDeque<>();
    /**
     * The context of this handler once it is added. Event loop only.
     */
    @Nullable
    private ChannelHandlerContext handlerContext;

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
     * @param executorSelector           The executor selector
     * @param coroutineHelper            Helper for kotlin coroutines
     * @param routeEndpoint              The endpoint of a WebSocket route of handler functions, or {@code null} for a bean
     * @param routeExecutor              The executor of the WebSocket route of handler functions, or {@code null} for a bean
     */
    NettyServerWebSocketHandler(
        NettyEmbeddedServices nettyEmbeddedServices,
        WebSocketSessionRepository webSocketSessionRepository,
        WebSocketServerHandshaker handshaker,
        WebSocketBean<?> webSocketBean,
        HttpRequest<?> request,
        UriRouteMatch<Object, Object> routeMatch,
        ChannelHandlerContext ctx,
        ThreadSelectionConfiguration threadSelection,
        ExecutorSelector executorSelector,
        @Nullable CoroutineHelper coroutineHelper,
        @Nullable WebSocketRouteEndpoint routeEndpoint,
        @Nullable Executor routeExecutor) {
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
        this.routeExecutor = routeExecutor;
        this.routeEndpoint = routeEndpoint;
        this.maxConcurrentMessages = routeEndpoint == null ? 0 : routeEndpoint.maxConcurrentMessages();
        this.streamsMessages = routeEndpoint != null && routeEndpoint.streamsMessages();
        this.pingHandler = routeEndpoint == null ? null : routeEndpoint.pingMethod();
        this.pingArgument = routeEndpoint == null ? null : routeEndpoint.pingArgument();

        this.channel = ctx.channel();
        this.serverSession = createWebSocketSession(ctx);

        if (routeEndpoint != null) {
            // the handler functions declare the type of their message: it is never bound from the
            // upgrade request, e.g. from a query parameter of the same name
            this.bodyArgument = routeEndpoint.messageArgument();
            this.pongArgument = routeEndpoint.pongArgument();
        } else {
            ExecutableBinder<WebSocketState> binder = new DefaultExecutableBinder<>();
            this.bodyArgument = messageHandler == null ? null : bodyArgument(binder, webSocketBean, messageHandler);
            this.pongArgument = pongHandler == null ? null : pongArgument(binder, webSocketBean, pongHandler);
        }

        this.nettyEmbeddedServices = nettyEmbeddedServices;
        this.coroutineHelper = coroutineHelper;
        RouteAttributes.setRouteMatch(request, routeMatch);

        if (routeEndpoint != null) {
            // the first message is handled once the open handler is done
            opening = true;
            // the handlers of the route run on its executor, the event loop by default, with the
            // upgrade request as the current request
            Executor handlerExecutor = routeExecutor == ImmediateExecutor.INSTANCE ? channel.eventLoop() : Objects.requireNonNull(routeExecutor);
            Executor executor = command -> handlerExecutor.execute(() -> ServerRequestContext.with(originatingRequest, command));
            routeEndpoint.connected(serverSession, executor, error -> {
                ChannelHandlerContext handlerCtx = channel.pipeline().context(this);
                exceptionCaught(handlerCtx == null ? ctx : handlerCtx, error);
            });
        }
        callOpenMethod(ctx).onComplete((v, t) -> {
            if (t != null) {
                forwardErrorToUser(ctx, e -> {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error Opening WebSocket [" + webSocketBean + "]: " + e.getMessage(), e);
                    }
                }, t);
            }
            if (maxConcurrentMessages > 0) {
                channel.eventLoop().execute(() -> {
                    opening = false;
                    handlePending();
                });
            }
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

    private @Nullable Argument<?> bodyArgument(ExecutableBinder<WebSocketState> binder, WebSocketBean<?> webSocketBean, MethodExecutionHandle<?, ?> messageHandler) {
        BoundExecutable<?, ?> bound = binder.tryBind(messageHandler.getExecutableMethod(), webSocketBinder, new WebSocketState(serverSession, originatingRequest));
        List<Argument<?>> unboundArguments = bound.getUnboundArguments();

        if (unboundArguments.size() == 1) {
            return unboundArguments.getFirst();
        }
        if (LOG.isErrorEnabled()) {
            LOG.error("WebSocket @OnMessage method {}.{} should define exactly 1 message parameter, but found 2 possible candidates: {}", webSocketBean.getTarget(), messageHandler.getExecutableMethod(), unboundArguments);
        }

        if (serverSession.isOpen()) {
            serverSession.close(CloseReason.INTERNAL_ERROR);
        }
        return null;
    }

    private @Nullable Argument<?> pongArgument(ExecutableBinder<WebSocketState> binder, WebSocketBean<?> webSocketBean, MethodExecutionHandle<?, ?> pongHandler) {
        BoundExecutable<?, ?> bound = binder.tryBind(pongHandler.getExecutableMethod(), webSocketBinder, new WebSocketState(serverSession, originatingRequest));
        List<Argument<?>> unboundArguments = bound.getUnboundArguments();
        if (unboundArguments.size() == 1 && unboundArguments.getFirst().isAssignableFrom(WebSocketPongMessage.class)) {
            return unboundArguments.getFirst();
        }
        if (LOG.isErrorEnabled()) {
            LOG.error("WebSocket @OnMessage pong handler method {}.{} should define exactly 1 message parameter assignable from a WebSocketPongMessage, but found: {}", webSocketBean.getTarget(), pongHandler.getExecutableMethod(), unboundArguments);
        }

        if (serverSession.isOpen()) {
            serverSession.close(CloseReason.INTERNAL_ERROR);
        }
        return null;
    }

    @Override
    public NettyWebSocketSession getSession() {
        return serverSession;
    }

    @Override
    public Argument<?> getBodyArgument() {
        return Objects.requireNonNull(bodyArgument);
    }

    @Override
    public Argument<?> getPongArgument() {
        return Objects.requireNonNull(pongArgument);
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
        WebSocketSessionRepository requiredWebSocketSessionRepository = Objects.requireNonNull(webSocketSessionRepository);

        String id = originatingRequest.getHeaders().get(HttpHeaderNames.SEC_WEBSOCKET_KEY);
        final Channel channel = ctx.channel();

        NettyWebSocketSession session = new NettyWebSocketSession(
                Objects.requireNonNull(id),
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
                // the group only holds websocket channels, one attribute read each is all it takes
                Set<WebSocketSession> open = new HashSet<>();
                for (Channel ch : requiredWebSocketSessionRepository.getChannelGroup()) {
                    NettyWebSocketSession s = ch.attr(NettyWebSocketSession.WEB_SOCKET_SESSION_KEY).get();
                    if (s != null && s.isOpen()) {
                        open.add(s);
                    }
                }
                return open;
            }

            @Override
            public void close(CloseReason closeReason) {
                super.close(closeReason);
                requiredWebSocketSessionRepository.removeChannel(ctx.channel());
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

        requiredWebSocketSessionRepository.addChannel(channel);

        return session;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        handlerContext = ctx;
        super.handlerAdded(ctx);
        if (maxConcurrentMessages > 0) {
            handlePending();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (maxConcurrentMessages > 0) {
            WebSocketFrame frame = (WebSocketFrame) msg;
            if (frame instanceof CloseWebSocketFrame) {
                // handled at once, whatever the handlers that run, e.g. the open handler of a
                // stream that ends when the connection closes
                closePending(ctx);
            } else if (isData(frame) && (!pendingFrames.isEmpty() || !mayHandle())) {
                // handled in order, once the handlers before it are done; pings are answered meanwhile
                pendingFrames.add(frame.retain());
                return;
            }
        }
        super.channelRead0(ctx, msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        super.channelReadComplete(ctx);
        if (maxConcurrentMessages > 0) {
            readAhead(ctx);
        }
    }

    @Override
    protected void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame msg) {
        if (pingHandler != null && msg instanceof PingWebSocketFrame ping && serverSession.isOpen()) {
            // the content outlives the pong that answers the ping
            ByteBuf content = ping.content().retainedDuplicate();
            super.handleWebSocketFrame(ctx, msg);
            callPingHandler(ctx, pingHandler, content);
        } else {
            super.handleWebSocketFrame(ctx, msg);
        }
    }

    private void callPingHandler(ChannelHandlerContext ctx, MethodExecutionHandle<?, ?> handler, ByteBuf content) {
        WebSocketPingMessage message = new WebSocketPingMessage(NettyByteBufferFactory.DEFAULT.wrap(content));
        try {
            ExecutableBinder<WebSocketState> binder = new DefaultExecutableBinder<>(Map.of(Objects.requireNonNull(pingArgument), message));
            BoundExecutable<?, ?> boundExecutable = binder.bind(handler.getExecutableMethod(), webSocketBinder, new WebSocketState(serverSession, originatingRequest));
            invokeExecutable(boundExecutable, handler).onComplete((v, t) -> {
                content.release();
                if (t != null) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error Processing WebSocket Ping Message [{}]: {}", webSocketBean, t.getMessage(), t);
                    }
                    exceptionCaught(ctx, t);
                }
            });
        } catch (Throwable e) {
            content.release();
            if (LOG.isErrorEnabled()) {
                LOG.error("Error Processing WebSocket Ping Message [{}]: {}", webSocketBean, e.getMessage(), e);
            }
            exceptionCaught(ctx, e);
        }
    }

    @Override
    protected ExecutionFlow<?> invokeExecutable(BoundExecutable boundExecutable, MethodExecutionHandle<?, ?> handler) {
        if (maxConcurrentMessages > 0 && handler == messageHandler) {
            // the next messages wait while the most messages are handled
            handling++;
            ExecutionFlow<?> flow;
            try {
                flow = invokeHandler(boundExecutable, handler);
            } catch (RuntimeException e) {
                flow = ExecutionFlow.error(e);
            }
            CompletableFuture<?> handled = flow.toCompletableFuture();
            handled.whenComplete((result, error) -> channel.eventLoop().execute(() -> {
                // always later, so that the frame of the handler is done with first
                handling--;
                handlePending();
            }));
            return CompletableFutureExecutionFlow.just(handled);
        }
        return invokeHandler(boundExecutable, handler);
    }

    /**
     * Handle the frames of a WebSocket route that waited for the handlers before them, as far as
     * the handlers allow, and read on.
     */
    private void handlePending() {
        ChannelHandlerContext ctx = handlerContext;
        if (ctx == null) {
            // removed, or not added yet: it handles them once it is
            return;
        }
        WebSocketFrame frame;
        while (mayHandle() && (frame = pendingFrames.poll()) != null) {
            handlePending(ctx, frame);
        }
        readAhead(ctx);
    }

    /**
     * The connection closes: the stream of a messages handler receives the messages read before
     * the close, and the messages that wait for a handler are discarded.
     */
    private void closePending(ChannelHandlerContext ctx) {
        WebSocketFrame frame;
        while ((frame = pendingFrames.poll()) != null) {
            if (streamsMessages) {
                handlePending(ctx, frame);
            } else {
                frame.release();
            }
        }
    }

    private void handlePending(ChannelHandlerContext ctx, WebSocketFrame frame) {
        try {
            super.channelRead0(ctx, frame);
        } catch (Throwable e) {
            exceptionCaught(ctx, e);
        } finally {
            frame.release();
        }
    }

    /**
     * @return Whether a message of a WebSocket route can be handled: once the open handler is done
     * and while fewer than the most messages are handled
     */
    private boolean mayHandle() {
        return !opening && handling < maxConcurrentMessages;
    }

    private static boolean isData(WebSocketFrame frame) {
        return frame instanceof TextWebSocketFrame
            || frame instanceof BinaryWebSocketFrame
            || frame instanceof ContinuationWebSocketFrame;
    }

    private void readAhead(ChannelHandlerContext ctx) {
        if (pendingFrames.size() < MAX_PENDING_FRAMES) {
            ctx.read();
        }
    }

    private ExecutionFlow<?> invokeHandler(BoundExecutable boundExecutable, MethodExecutionHandle<?, ?> messageHandler) {
        if (coroutineHelper != null) {
            Executable<?, ?> target = boundExecutable.getTarget();
            if (target instanceof ExecutableMethod<?, ?> executableMethod) {
                if (executableMethod.isSuspend()) {
                    try {
                        coroutineHelper.setupCoroutineContext(originatingRequest, Context.empty(), PropagatedContext.getOrEmpty());

                        Object immediateReturnValue = invokeExecutable0(boundExecutable, messageHandler);

                        if (KotlinUtils.isKotlinCoroutineSuspended(immediateReturnValue)) {
                            Supplier<CompletableFuture<?>> supplier = ContinuationArgumentBinder.extractContinuationCompletableFutureSupplier(originatingRequest);
                            if (supplier == null) {
                                return ExecutionFlow.empty();
                            }
                            return CompletableFutureExecutionFlow.just(supplier.get());
                        } else {
                            return ExecutionFlow.empty();
                        }
                    } catch (Exception e) {
                        return ExecutionFlow.error(e);
                    }
                }
            }
        }
        return invokeExecutable0(boundExecutable, messageHandler);
    }

    private ExecutionFlow<?> invokeExecutable0(BoundExecutable boundExecutable, MethodExecutionHandle<?, ?> messageHandler) {
        Executor executor;
        if (routeExecutor == null) {
            executor = executorSelector.selectExecutor(messageHandler.getExecutableMethod(), threadSelection);
        } else if (streamsMessages && messageHandler == this.messageHandler) {
            // the stream receives the messages in the order they were read, and signals its
            // subscriber on the executor of the route
            executor = ImmediateExecutor.INSTANCE;
        } else {
            executor = routeExecutor;
        }
        ReturnType<?> returnType = messageHandler.getExecutableMethod().getReturnType();
        return ExecutionFlow.<Object>async(executor, () -> {
            Object result = invokeWithContext(boundExecutable, messageHandler).get();
            if (returnType.isReactive() || Publishers.isConvertibleToPublisher(result)) {
                Publisher<Object> converted = Publishers.convertToPublisher(conversionService, result);
                // scalar results (Mono.just, Mono.empty, Mono.error) need no reactor context
                Publisher<Object> publisher = converted instanceof Fuseable.ScalarCallable<?>
                    ? converted
                    : Flux.from(converted).contextWrite(Context.of(ServerRequestContext.KEY, originatingRequest));
                // subscribe eagerly so that synchronous results complete inline
                return ServerRequestContext.with(originatingRequest,
                    (Supplier<ExecutionFlow<Object>>) () -> ReactiveExecutionFlow.fromPublisherEager(publisher, PropagatedContext.getOrEmpty()));
            }
            if (returnType.isAsync()) {
                CompletionStage<Object> future = result instanceof CompletionStage<?> stage
                    ? stage.thenApply(v -> v)
                    : ((CompletableFuture<?>) result).thenApply(v -> v);
                return CompletableFutureExecutionFlow.just(future);
            }
            return ExecutionFlow.just(result);
        });
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
        handlerContext = null;
        WebSocketFrame frame;
        while ((frame = pendingFrames.poll()) != null) {
            frame.release();
        }
        if (routeEndpoint != null) {
            try {
                routeEndpoint.disconnected(serverSession);
            } catch (RuntimeException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error completing the WebSocket messages of [{}]: {}", webSocketBean, e.getMessage(), e);
                }
            }
        }
        Channel channel = ctx.channel();
        channel.attr(NettyWebSocketSession.WEB_SOCKET_SESSION_KEY).set(null);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Removing WebSocket Server session: {}", serverSession);
        }
        Objects.requireNonNull(webSocketSessionRepository).removeChannel(channel);
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
