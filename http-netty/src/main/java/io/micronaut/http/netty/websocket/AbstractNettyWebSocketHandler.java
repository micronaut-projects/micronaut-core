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
package io.micronaut.http.netty.websocket;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.bind.ArgumentBinderRegistry;
import io.micronaut.core.bind.BoundExecutable;
import io.micronaut.core.bind.DefaultExecutableBinder;
import io.micronaut.core.bind.ExecutableBinder;
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.bind.WebSocketState;
import io.micronaut.websocket.bind.WebSocketStateBinderRegistry;
import io.micronaut.websocket.context.WebSocketBean;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.reactivex.Flowable;
import io.reactivex.functions.BiConsumer;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract implementation that handles WebSocket frames.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public abstract class AbstractNettyWebSocketHandler extends SimpleChannelInboundHandler<Object> {

    /**
     * The id of the handler used when adding it to the Netty pipeline.
     */
    public static final String ID = "websocket-handler";

    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    protected final ArgumentBinderRegistry<WebSocketState> webSocketBinder;
    protected final Map<String, Object> uriVariables;
    protected final WebSocketBean<?> webSocketBean;
    protected final HttpRequest<?> originatingRequest;
    protected final MethodExecutionHandle<?, ?> messageHandler;
    protected final NettyRxWebSocketSession session;
    protected final MediaTypeCodecRegistry mediaTypeCodecRegistry;
    protected final WebSocketVersion webSocketVersion;
    protected final WebSocketSessionRepository webSocketSessionRepository;
    private final Argument<?> bodyArgument;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Default constructor.
     *
     * @param ctx                        The channel handler context
     * @param binderRegistry             The request binder registry
     * @param mediaTypeCodecRegistry     The codec registry
     * @param webSocketBean              The websocket bean
     * @param request                    The originating request
     * @param uriVariables               The URI variables
     * @param version                    The websocket version being used
     * @param webSocketSessionRepository The web socket repository if they are supported (like on the server), null otherwise
     */
    protected AbstractNettyWebSocketHandler(
            ChannelHandlerContext ctx,
            RequestBinderRegistry binderRegistry,
            MediaTypeCodecRegistry mediaTypeCodecRegistry,
            WebSocketBean<?> webSocketBean,
            HttpRequest<?> request,
            Map<String, Object> uriVariables,
            WebSocketVersion version,
            WebSocketSessionRepository webSocketSessionRepository) {
        this.webSocketSessionRepository = webSocketSessionRepository;
        this.webSocketBinder = new WebSocketStateBinderRegistry(binderRegistry);
        this.uriVariables = uriVariables;
        this.webSocketBean = webSocketBean;
        this.originatingRequest = request;
        this.messageHandler = webSocketBean.messageMethod().orElse(null);
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.webSocketVersion = version;
        this.session = createWebSocketSession(ctx);

        if (session != null) {

            ExecutableBinder<WebSocketState> binder = new DefaultExecutableBinder<>();

            if (messageHandler != null) {
                BoundExecutable<?, ?> bound = binder.tryBind(messageHandler.getExecutableMethod(), webSocketBinder, new WebSocketState(session, originatingRequest));
                List<Argument<?>> unboundArguments = bound.getUnboundArguments();

                if (unboundArguments.size() == 1) {
                    this.bodyArgument = unboundArguments.iterator().next();
                } else {
                    this.bodyArgument = null;
                    if (LOG.isErrorEnabled()) {
                        LOG.error("WebSocket @OnMessage method " + webSocketBean.getTarget() + "." + messageHandler.getExecutableMethod() + " should define exactly 1 message parameter, but found 2 possible candidates: " + unboundArguments);
                    }

                    if (session.isOpen()) {
                        session.close(CloseReason.INTERNAL_ERROR);
                    }
                }
            } else {
                this.bodyArgument = null;
            }

            Optional<? extends MethodExecutionHandle<?, ?>> executionHandle = webSocketBean.openMethod();
            if (executionHandle.isPresent()) {
                MethodExecutionHandle<?, ?> openMethod = executionHandle.get();
                BoundExecutable boundExecutable = null;
                try {
                    boundExecutable = bindMethod(request, webSocketBinder, openMethod, Collections.emptyList());
                } catch (Throwable e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error Binding method @OnOpen for WebSocket [" + webSocketBean + "]: " + e.getMessage(), e);
                    }

                    if (session.isOpen()) {
                        session.close(CloseReason.INTERNAL_ERROR);
                    }
                }

                if (boundExecutable != null) {
                    try {
                        BoundExecutable finalBoundExecutable = boundExecutable;
                        Object result = invokeExecutable(finalBoundExecutable, openMethod);
                        if (Publishers.isConvertibleToPublisher(result)) {
                            Flowable<?> flowable = instrumentPublisher(ctx, result);
                            flowable.subscribe(
                                    o -> {
                                    },
                                    error -> {
                                        if (LOG.isErrorEnabled()) {
                                            LOG.error("Error Opening WebSocket [" + webSocketBean + "]: " + error.getMessage(), error);
                                        }
                                        if (session.isOpen()) {
                                            session.close(CloseReason.INTERNAL_ERROR);
                                        }
                                    },
                                    () -> {
                                    }
                            );
                        }
                    } catch (Throwable e) {
                        if (LOG.isErrorEnabled()) {
                            LOG.error("Error Opening WebSocket [" + webSocketBean + "]: " + e.getMessage(), e);
                        }
                        if (session.isOpen()) {
                            session.close(CloseReason.INTERNAL_ERROR);
                        }
                    }
                }
            }
        } else {
            this.bodyArgument = null;
        }
    }

    /**
     * @return The body argument for the message handler
     */
    public Argument<?> getBodyArgument() {
        return bodyArgument;
    }

    /**
     * @return The session
     */
    public NettyRxWebSocketSession getSession() {
        return session;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Optional<? extends MethodExecutionHandle<?, ?>> opt = webSocketBean.errorMethod();

        if (opt.isPresent()) {
            MethodExecutionHandle<?, ?> errorMethod = opt.get();
            try {
                BoundExecutable boundExecutable = bindMethod(
                        originatingRequest,
                        webSocketBinder,
                        errorMethod,
                        Collections.singletonList(cause)
                );

                Object target = errorMethod.getTarget();
                Object result;
                try {
                    result = boundExecutable.invoke(target);
                } catch (Exception e) {

                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error invoking to @OnError handler " + target.getClass().getSimpleName() + "." + errorMethod.getExecutableMethod() + ": " + e.getMessage(), e);
                    }
                    handleUnexpected(ctx, e);
                    return;
                }
                if (Publishers.isConvertibleToPublisher(result)) {
                    Flowable<?> flowable = instrumentPublisher(ctx, result);
                    flowable.toList().subscribe((BiConsumer<List<?>, Throwable>) (objects, throwable) -> {
                        if (throwable != null && LOG.isErrorEnabled()) {
                            LOG.error("Error subscribing to @OnError handler " + target.getClass().getSimpleName() + "." + errorMethod.getExecutableMethod() + ": " + throwable.getMessage(), throwable);
                        }
                        handleUnexpected(ctx, throwable);
                    });
                }

            } catch (UnsatisfiedArgumentException e) {
                handleUnexpected(ctx, cause);
            }

        } else {
            handleUnexpected(ctx, cause);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        handleCloseReason(ctx, CloseReason.ABNORMAL_CLOSURE);
    }

    /**
     * Subclasses should implement to create the actual {@link NettyRxWebSocketSession}.
     *
     * @param ctx The context
     * @return The session
     */
    protected abstract NettyRxWebSocketSession createWebSocketSession(ChannelHandlerContext ctx);

    /**
     * Subclasses can override to customize publishers returned from message handlers.
     *
     * @param ctx    The context
     * @param result The result
     * @return The flowable
     */
    protected Flowable<?> instrumentPublisher(ChannelHandlerContext ctx, Object result) {
        Flowable<?> actual = Publishers.convertPublisher(result, Flowable.class);
        return actual.subscribeOn(Schedulers.from(ctx.channel().eventLoop()));
    }

    /**
     * Invokes the given executable.
     *
     * @param boundExecutable The bound executable
     * @param messageHandler  The message handler
     * @return The result
     */
    protected Object invokeExecutable(BoundExecutable boundExecutable, MethodExecutionHandle<?, ?> messageHandler) {
        return boundExecutable.invoke(messageHandler.getTarget());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * Handles WebSocket frame request.
     *
     * @param ctx The context
     * @param msg The frame
     */
    protected void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame msg) {
        if (msg instanceof TextWebSocketFrame || msg instanceof BinaryWebSocketFrame) {

            if (messageHandler == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("WebSocket bean [" + webSocketBean.getTarget() + "] received message, but defined no @OnMessage handler. Dropping frame...");
                }
                writeCloseFrameAndTerminate(
                        ctx,
                        CloseReason.UNSUPPORTED_DATA
                );
            } else {

                Argument<?> bodyArgument = this.getBodyArgument();
                Optional<?> converted = ConversionService.SHARED.convert(msg.content(), bodyArgument);

                if (!converted.isPresent()) {
                    MediaType mediaType;
                    try {
                        mediaType = messageHandler.stringValue(Consumes.class).map(MediaType::new).orElse(MediaType.APPLICATION_JSON_TYPE);
                    } catch (IllegalArgumentException e) {
                        exceptionCaught(ctx, e);
                        return;
                    }
                    try {
                        converted = mediaTypeCodecRegistry.findCodec(mediaType).map(codec -> codec.decode(bodyArgument, new NettyByteBufferFactory(ctx.alloc()).wrap(msg.content())));
                    } catch (CodecException e) {
                        if (LOG.isErrorEnabled()) {
                            LOG.error("Error Processing WebSocket Message [" + webSocketBean + "]: " + e.getMessage(), e);
                        }
                        exceptionCaught(ctx, e);
                        return;
                    }
                }

                if (converted.isPresent()) {
                    Object v = converted.get();

                    NettyRxWebSocketSession currentSession = getSession();
                    ExecutableBinder<WebSocketState> executableBinder = new DefaultExecutableBinder<>(
                            Collections.singletonMap(bodyArgument, v)
                    );

                    try {
                        BoundExecutable boundExecutable = executableBinder.bind(
                                messageHandler.getExecutableMethod(),
                                webSocketBinder,
                                new WebSocketState(currentSession, originatingRequest)
                        );

                        Object result = invokeExecutable(boundExecutable, messageHandler);
                        if (Publishers.isConvertibleToPublisher(result)) {
                            Flowable<?> flowable = instrumentPublisher(ctx, result);
                            flowable.subscribe(
                                    o -> {
                                    },
                                    error -> {
                                        if (LOG.isErrorEnabled()) {
                                            LOG.error("Error Processing WebSocket Message [" + webSocketBean + "]: " + error.getMessage(), error);
                                        }
                                        exceptionCaught(ctx, error);
                                    },
                                    () -> messageHandled(ctx, session, v)
                            );
                        } else {
                            messageHandled(ctx, session, v);
                        }
                    } catch (Throwable e) {
                        if (LOG.isErrorEnabled()) {
                            LOG.error("Error Processing WebSocket Message [" + webSocketBean + "]: " + e.getMessage(), e);
                        }
                        exceptionCaught(ctx, e);
                    }

                } else {
                    writeCloseFrameAndTerminate(
                            ctx,
                            CloseReason.UNSUPPORTED_DATA.getCode(),
                            CloseReason.UNSUPPORTED_DATA.getReason() + ": " + "Received data cannot be converted to target type: " + bodyArgument
                    );
                }
            }

        } else if (msg instanceof PingWebSocketFrame) {
            // respond with pong
            PingWebSocketFrame frame = (PingWebSocketFrame) msg;
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content()));

        } else if (msg instanceof PongWebSocketFrame) {
            return;
        } else if (msg instanceof CloseWebSocketFrame) {
            CloseWebSocketFrame cwsf = (CloseWebSocketFrame) msg;
            handleCloseFrame(ctx, cwsf);
        } else {
            writeCloseFrameAndTerminate(
                    ctx,
                    CloseReason.UNSUPPORTED_DATA
            );
        }
    }

    /**
     * Method called once a message has been handled by the handler.
     *
     * @param ctx     The channel handler context
     * @param session The session
     * @param message The message that was handled
     */
    protected void messageHandled(ChannelHandlerContext ctx, NettyRxWebSocketSession session, Object message) {
        // no-op
    }

    /**
     * Writes the give close reason and terminates the session.
     * @param ctx The context
     * @param closeReason The reason
     */
    protected void writeCloseFrameAndTerminate(ChannelHandlerContext ctx, CloseReason closeReason) {
        final int code = closeReason.getCode();
        final String reason = closeReason.getReason();
        writeCloseFrameAndTerminate(ctx, code, reason);
    }

    /**
     * Used to close thee session with a given reason.
     * @param ctx The context
     * @param cr The reason
     */
    private void handleCloseReason(ChannelHandlerContext ctx, CloseReason cr) {
        if (closed.compareAndSet(false, true)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Closing WebSocket session {} with reason {}", getSession(), cr);
            }
            Optional<? extends MethodExecutionHandle<?, ?>> opt = webSocketBean.closeMethod();
            if (opt.isPresent()) {
                MethodExecutionHandle<?, ?> methodExecutionHandle = opt.get();
                Object target = methodExecutionHandle.getTarget();
                try {

                    BoundExecutable boundExecutable = bindMethod(
                            originatingRequest,
                            webSocketBinder,
                            methodExecutionHandle,
                            Collections.singletonList(cr)
                    );

                    invokeAndClose(ctx, target, boundExecutable, methodExecutionHandle, true);
                } catch (Throwable e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error invoking @OnClose handler for WebSocket bean [" + target + "]: " + e.getMessage(), e);
                    }
                }
            } else {
                writeCloseFrameAndTerminate(ctx, cr);
            }
        }
    }

    private void handleCloseFrame(ChannelHandlerContext ctx, CloseWebSocketFrame cwsf) {
        CloseReason cr = new CloseReason(cwsf.statusCode(), cwsf.reasonText());
        handleCloseReason(ctx, cr);
    }

    private void invokeAndClose(ChannelHandlerContext ctx, Object target, BoundExecutable boundExecutable, MethodExecutionHandle<?, ?> methodExecutionHandle, boolean isClose) {
        Object result;
        try {
            result = invokeExecutable(boundExecutable, methodExecutionHandle);
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error invoking @OnClose handler " + target.getClass().getSimpleName() + "." + methodExecutionHandle.getExecutableMethod() + ": " + e.getMessage(), e);
            }
            ctx.close();
            return;
        }

        if (Publishers.isConvertibleToPublisher(result)) {
            Flowable<?> flowable = instrumentPublisher(ctx, result);
            flowable.toList().subscribe((BiConsumer<List<?>, Throwable>) (objects, throwable) -> {
                if (throwable != null && LOG.isErrorEnabled()) {
                    LOG.error("Error subscribing to @" + (isClose ? "OnClose" : "OnError") + " handler for WebSocket bean [" + target + "]: " + throwable.getMessage(), throwable);
                }
                ctx.close();
            });

        } else {
            ctx.close();
        }
    }

    private BoundExecutable bindMethod(HttpRequest<?> request, ArgumentBinderRegistry<WebSocketState> binderRegistry, MethodExecutionHandle<?, ?> openMethod, List<?> parameters) {
        ExecutableMethod<?, ?> executable = openMethod.getExecutableMethod();
        Map<Argument<?>, Object> preBound = prepareBoundVariables(executable, parameters);
        ExecutableBinder<WebSocketState> executableBinder = new DefaultExecutableBinder<>(
                preBound
        );
        return executableBinder.bind(executable, binderRegistry, new WebSocketState(getSession(), request));
    }

    private Map<Argument<?>, Object> prepareBoundVariables(ExecutableMethod<?, ?> executable, List<?> parameters) {
        Map<Argument<?>, Object> preBound = new HashMap<>(executable.getArguments().length);
        for (Argument argument : executable.getArguments()) {
            Class type = argument.getType();
            for (Object object : parameters) {
                if (type.isInstance(object)) {
                    preBound.put(argument, object);
                    break;
                }
            }
        }
        return preBound;
    }

    private void handleUnexpected(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException) {
            String msg = cause.getMessage();
            if (msg != null && msg.contains("Connection reset")) {
                // ignore client connection drops
                return;
            }
        }

        if (LOG.isErrorEnabled()) {
            LOG.error("Unexpected Exception in WebSocket [" + webSocketBean.getTarget() + "]: " + cause.getMessage(), cause);
        }
        Channel channel = ctx.channel();
        if (channel.isOpen()) {
            final CloseReason internalError = CloseReason.INTERNAL_ERROR;
            writeCloseFrameAndTerminate(ctx, internalError);
        }
    }

    private void writeCloseFrameAndTerminate(ChannelHandlerContext ctx, int code, String reason) {
        final CloseWebSocketFrame closeFrame = new CloseWebSocketFrame(code, reason);
        ctx.channel().writeAndFlush(closeFrame)
                     .addListener(future -> handleCloseFrame(ctx, new CloseWebSocketFrame(code, reason)));
    }
}
