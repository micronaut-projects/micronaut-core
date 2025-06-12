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
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketPongMessage;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.bind.WebSocketState;
import io.micronaut.websocket.bind.WebSocketStateBinderRegistry;
import io.micronaut.websocket.context.WebSocketBean;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
    protected final MethodExecutionHandle<?, ?> pongHandler;
    protected final MediaTypeCodecRegistry mediaTypeCodecRegistry;
    protected final MessageBodyHandlerRegistry messageBodyHandlerRegistry;
    protected final WebSocketVersion webSocketVersion;
    protected final String subProtocol;
    protected final WebSocketSessionRepository webSocketSessionRepository;
    protected final ConversionService conversionService;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicReference<CompositeByteBuf> frameBuffer = new AtomicReference<>();

    /**
     * Default constructor.
     *
     * @param binderRegistry             The request binder registry
     * @param mediaTypeCodecRegistry     The codec registry
     * @param messageBodyHandlerRegistry The handler registry
     * @param webSocketBean              The websocket bean
     * @param request                    The originating request
     * @param uriVariables               The URI variables
     * @param version                    The websocket version being used
     * @param subProtocol                The handler sub-protocol
     * @param webSocketSessionRepository The web socket repository if they are supported (like on the server), null otherwise
     * @param conversionService          The conversion service
     */
    protected AbstractNettyWebSocketHandler(
            RequestBinderRegistry binderRegistry,
            MediaTypeCodecRegistry mediaTypeCodecRegistry,
            MessageBodyHandlerRegistry messageBodyHandlerRegistry,
            WebSocketBean<?> webSocketBean,
            HttpRequest<?> request,
            Map<String, Object> uriVariables,
            WebSocketVersion version,
            String subProtocol,
            WebSocketSessionRepository webSocketSessionRepository,
            ConversionService conversionService) {
        this.subProtocol = subProtocol;
        this.webSocketSessionRepository = webSocketSessionRepository;
        this.webSocketBinder = new WebSocketStateBinderRegistry(binderRegistry, conversionService);
        this.uriVariables = uriVariables;
        this.webSocketBean = webSocketBean;
        this.originatingRequest = request;
        this.messageHandler = webSocketBean.messageMethod().orElse(null);
        this.pongHandler = webSocketBean.pongMethod().orElse(null);
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.webSocketVersion = version;
        this.conversionService = conversionService;
        this.messageBodyHandlerRegistry = messageBodyHandlerRegistry;
    }

    /**
     * Calls the open method of the websocket bean.
     *
     * @param ctx The handler context
     * @return Publisher for any errors, or the result of the open method
     */
    protected Publisher<?> callOpenMethod(ChannelHandlerContext ctx) {
        WebSocketSession session = getSession();

        Optional<? extends MethodExecutionHandle<?, ?>> executionHandle = webSocketBean.openMethod();
        if (executionHandle.isPresent()) {
            MethodExecutionHandle<?, ?> openMethod = executionHandle.get();

            BoundExecutable<?, ?> boundExecutable;
            try {
                boundExecutable = bindMethod(originatingRequest, webSocketBinder, openMethod, Collections.emptyList());
            } catch (Throwable e) {
                if (session.isOpen()) {
                    session.close(CloseReason.INTERNAL_ERROR);
                }
                return Mono.error(e);
            }

            try {
                Object result = invokeExecutable(boundExecutable, openMethod);
                if (Publishers.isConvertibleToPublisher(result)) {
                    return Flux.from(instrumentPublisher(ctx, result)).doOnError(t -> {
                        if (session.isOpen()) {
                            session.close(CloseReason.INTERNAL_ERROR);
                        }
                    });
                } else {
                    return Mono.empty();
                }
            } catch (Throwable e) {
                // since we failed to call onOpen, we should always close here
                if (session.isOpen()) {
                    session.close(CloseReason.INTERNAL_ERROR);
                }
                return Mono.error(e);
            }
        } else {
            return Mono.empty();
        }
    }

    /**
     * @return The body argument for the message handler
     */
    public abstract Argument<?> getBodyArgument();

    /**
     * @return The pong argument for the pong handler
     */
    public abstract Argument<?> getPongArgument();

    /**
     * @return The session
     */
    public abstract NettyWebSocketSession getSession();

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cleanupBuffer();
        forwardErrorToUser(ctx, e -> handleUnexpected(ctx, e), cause);
    }

    protected final void forwardErrorToUser(ChannelHandlerContext ctx, Consumer<Throwable> fallback, Throwable cause) {
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
                    result = invokeExecutable(boundExecutable, errorMethod);
                } catch (Exception e) {

                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error invoking to @OnError handler {}.{}: {}", target.getClass().getSimpleName(), errorMethod.getExecutableMethod(), e.getMessage(), e);
                    }
                    fallback.accept(e);
                    return;
                }
                if (Publishers.isConvertibleToPublisher(result)) {
                    Mono<?> unhandled = Mono.from(instrumentPublisher(ctx, result));
                    unhandled.subscribe(unhandledResult -> fallback.accept(cause), throwable -> {
                        if (throwable != null && LOG.isErrorEnabled()) {
                            LOG.error("Error subscribing to @OnError handler {}.{}: {}", target.getClass().getSimpleName(), errorMethod.getExecutableMethod(), throwable.getMessage(), throwable);
                        }
                        fallback.accept(cause);
                    });
                }

            } catch (UnsatisfiedArgumentException e) {
                fallback.accept(cause);
            }

        } else {
            fallback.accept(cause);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        // don't write this close reason, only call the @OnClose handler.
        // ABNORMAL_CLOSURE isn't allowed on the wire anyway
        handleCloseReason(ctx, CloseReason.ABNORMAL_CLOSURE, false);
    }

    /**
     * Subclasses should implement to create the actual {@link NettyWebSocketSession}.
     *
     * @param ctx The context
     * @return The session
     */
    protected abstract NettyWebSocketSession createWebSocketSession(ChannelHandlerContext ctx);

    /**
     * Subclasses can override to customize publishers returned from message handlers.
     *
     * @param ctx    The context
     * @param result The result
     * @return The flowable
     */
    protected Publisher<?> instrumentPublisher(ChannelHandlerContext ctx, Object result) {
        Publisher<?> actual = Publishers.convertToPublisher(conversionService, result);
        return Flux.from(actual).subscribeOn(Schedulers.fromExecutorService(ctx.channel().eventLoop()));
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
        if (msg instanceof WebSocketFrame frame) {
            handleWebSocketFrame(ctx, frame);
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
        if (msg instanceof TextWebSocketFrame || msg instanceof BinaryWebSocketFrame || msg instanceof ContinuationWebSocketFrame) {

            if (messageHandler == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("WebSocket bean [{}] received message, but defined no @OnMessage handler. Dropping frame...", webSocketBean.getTarget());
                }
                writeCloseFrameAndTerminate(
                        ctx,
                        CloseReason.UNSUPPORTED_DATA
                );
            } else {
                Argument<?> bodyArgument = this.getBodyArgument();
                Object data;

                if (WebSocketFrame.class.isAssignableFrom(bodyArgument.getType())) {
                    data = msg.retain();
                } else {
                    ByteBuf msgContent = msg.content().retain();
                    if (!msg.isFinalFragment()) {
                        frameBuffer.updateAndGet((buffer) -> {
                            if (buffer == null) {
                                buffer = ctx.alloc().compositeBuffer();
                            }
                            buffer.addComponent(true, msgContent);
                            return buffer;
                        });
                        return;
                    }

                    ByteBuf content;
                    CompositeByteBuf buffer = frameBuffer.getAndSet(null);
                    if (buffer == null) {
                        content = msgContent;
                    } else {
                        buffer.addComponent(true, msgContent);
                        content = buffer;
                    }

                    data = conversionService.convert(content, ByteBuf.class, bodyArgument).orElse(null);
                    content.release();
                }

                if (data == null) {
                    MediaType mediaType;
                    try {
                        mediaType = messageHandler.stringValue(Consumes.class).map(MediaType::of).orElse(MediaType.APPLICATION_JSON_TYPE);
                    } catch (IllegalArgumentException e) {
                        exceptionCaught(ctx, e);
                        return;
                    }
                    try {
                        data = mediaTypeCodecRegistry.findCodec(mediaType)
                            .map(codec -> codec.decode(bodyArgument, new NettyByteBufferFactory(ctx.alloc()).wrap(msg.content())))
                            .orElse(null);
                    } catch (CodecException e) {
                        messageProcessingException(ctx, e);
                        return;
                    }
                    if (data == null) {
                        MessageBodyReader<?> reader = messageBodyHandlerRegistry.findReader(bodyArgument, mediaType)
                            .orElse(null);
                        if (reader != null) {
                            ByteBuffer<ByteBuf> byteBuffer = new NettyByteBufferFactory(ctx.alloc()).wrap(msg.content().retain());
                            data = reader.read((Argument) bodyArgument, mediaType, new SimpleHttpHeaders(), byteBuffer);
                        }
                    }
                }

                if (data != null) {

                    NettyWebSocketSession currentSession = getSession();
                    ExecutableBinder<WebSocketState> executableBinder = new DefaultExecutableBinder<>(
                            Collections.singletonMap(bodyArgument, data)
                    );

                    try {
                        BoundExecutable boundExecutable = executableBinder.bind(
                                messageHandler.getExecutableMethod(),
                                webSocketBinder,
                                new WebSocketState(currentSession, originatingRequest)
                        );

                        Object result = invokeExecutable(boundExecutable, messageHandler);
                        if (Publishers.isConvertibleToPublisher(result)) {
                            Flux<?> flowable = Flux.from(instrumentPublisher(ctx, result));
                            Object finalData = data;
                            flowable.subscribe(
                                    o -> {
                                    },
                                    error -> messageProcessingException(ctx, error),
                                    () -> messageHandled(ctx, finalData)
                            );
                        } else {
                            messageHandled(ctx, data);
                        }
                    } catch (Throwable e) {
                        messageProcessingException(ctx, e);
                    }

                } else {
                    writeCloseFrameAndTerminate(
                            ctx,
                            CloseReason.UNSUPPORTED_DATA.getCode(),
                            CloseReason.UNSUPPORTED_DATA.getReason() + ": " + "Received data cannot be data to target type: " + bodyArgument
                    );
                }
            }
        } else if (msg instanceof PingWebSocketFrame pingWebSocketFrame) {
            // respond with pong
            PingWebSocketFrame frame = pingWebSocketFrame.retain();
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content()));

        } else if (msg instanceof PongWebSocketFrame) {
            if (pongHandler != null) {
                ByteBuf content = msg.content();
                WebSocketPongMessage message = new WebSocketPongMessage(NettyByteBufferFactory.DEFAULT.wrap(content));

                NettyWebSocketSession currentSession = getSession();
                ExecutableBinder<WebSocketState> executableBinder = new DefaultExecutableBinder<>(
                        Collections.singletonMap(getPongArgument(), message)
                );

                try {
                    BoundExecutable boundExecutable = executableBinder.bind(
                            pongHandler.getExecutableMethod(),
                            webSocketBinder,
                            new WebSocketState(currentSession, originatingRequest)
                    );

                    Object result = invokeExecutable(boundExecutable, pongHandler);
                    if (Publishers.isConvertibleToPublisher(result)) {
                        // delay the buffer release until the publisher has completed
                        content.retain();
                        Flux<?> flowable = Flux.from(instrumentPublisher(ctx, result));
                        flowable.subscribe(
                                o -> {
                                },
                                error -> {
                                    if (LOG.isErrorEnabled()) {
                                        LOG.error("Error Processing WebSocket Pong Message [{}]: {}", webSocketBean, error.getMessage(), error);
                                    }
                                    exceptionCaught(ctx, error);
                                },
                                content::release
                        );
                    }
                } catch (Throwable e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error Processing WebSocket Message [{}]: {}", webSocketBean, e.getMessage(), e);
                    }
                    exceptionCaught(ctx, e);
                }
            }
        } else if (msg instanceof CloseWebSocketFrame cwsf) {
            handleCloseFrame(ctx, cwsf);
        } else {
            writeCloseFrameAndTerminate(
                    ctx,
                    CloseReason.UNSUPPORTED_DATA
            );
        }
    }

    private void messageProcessingException(ChannelHandlerContext ctx, Throwable e) {
        if (LOG.isErrorEnabled()) {
            LOG.error("Error Processing WebSocket Message [{}]: {}", webSocketBean, e.getMessage(), e);
        }
        exceptionCaught(ctx, e);
    }

    /**
     * Method called once a message has been handled by the handler.
     *
     * @param ctx     The channel handler context
     * @param message The message that was handled
     */
    protected void messageHandled(ChannelHandlerContext ctx, Object message) {
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
     * Used to close the session with a given reason.
     * @param ctx The context
     * @param cr The reason
     * @param writeCloseReason Whether to allow writing the close reason to the remote
     */
    protected void handleCloseReason(ChannelHandlerContext ctx, CloseReason cr, boolean writeCloseReason) {
        cleanupBuffer();
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
                        LOG.error("Error invoking @OnClose handler for WebSocket bean [{}]: {}", target, e.getMessage(), e);
                    }
                }
            } else {
                if (writeCloseReason) {
                    writeCloseFrameAndTerminate(ctx, cr);
                }
            }
        }
    }

    private void handleCloseFrame(ChannelHandlerContext ctx, CloseWebSocketFrame cwsf) {
        CloseReason cr = new CloseReason(cwsf.statusCode(), cwsf.reasonText());
        handleCloseReason(ctx, cr, true);
    }

    private void invokeAndClose(ChannelHandlerContext ctx, Object target, BoundExecutable boundExecutable, MethodExecutionHandle<?, ?> methodExecutionHandle, boolean isClose) {
        Object result;
        try {
            result = invokeExecutable(boundExecutable, methodExecutionHandle);
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error invoking @OnClose handler {}.{}: {}", target.getClass().getSimpleName(), methodExecutionHandle.getExecutableMethod(), e.getMessage(), e);
            }
            ctx.close();
            return;
        }

        if (Publishers.isConvertibleToPublisher(result)) {
            Flux<?> reactiveSequence = Flux.from(instrumentPublisher(ctx, result));
            reactiveSequence.collectList().subscribe((Consumer<List<?>>) objects -> {

            }, throwable -> {
                if (throwable != null && LOG.isErrorEnabled()) {
                    LOG.error("Error subscribing to @{} handler for WebSocket bean [{}]: {}", (isClose ? "OnClose" : "OnError"), target, throwable.getMessage(), throwable);
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
        Map<Argument<?>, Object> preBound = CollectionUtils.newHashMap(executable.getArguments().length);
        for (Argument argument : executable.getArguments()) {
            Class<?> type = argument.getType();
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
            LOG.error("Unexpected Exception in WebSocket [{}]: {}", webSocketBean.getTarget(), cause.getMessage(), cause);
        }
        Channel channel = ctx.channel();
        if (channel.isOpen()) {
            final CloseReason internalError = CloseReason.INTERNAL_ERROR;
            writeCloseFrameAndTerminate(ctx, internalError);
        }
    }

    private void writeCloseFrameAndTerminate(ChannelHandlerContext ctx, int code, String reason) {
        cleanupBuffer();
        final CloseWebSocketFrame closeFrame = new CloseWebSocketFrame(code, reason);
        ctx.channel().writeAndFlush(closeFrame)
                     .addListener(future -> handleCloseFrame(ctx, new CloseWebSocketFrame(code, reason)));
    }

    private void cleanupBuffer() {
        CompositeByteBuf buffer = frameBuffer.getAndSet(null);
        if (buffer != null) {
            buffer.release();
        }
    }
}
