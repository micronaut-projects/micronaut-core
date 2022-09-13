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
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.bind.BoundExecutable;
import io.micronaut.core.bind.DefaultExecutableBinder;
import io.micronaut.core.bind.ExecutableBinder;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.bind.DefaultRequestBinderRegistry;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.netty.websocket.AbstractNettyWebSocketHandler;
import io.micronaut.http.netty.websocket.NettyWebSocketSession;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketPongMessage;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.bind.WebSocketState;
import io.micronaut.websocket.bind.WebSocketStateBinderRegistry;
import io.micronaut.websocket.context.WebSocketBean;
import io.micronaut.websocket.exceptions.WebSocketClientException;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import io.micronaut.websocket.interceptor.WebSocketSessionAware;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
    private final Sinks.One<T> completion = Sinks.one();
    private final UriMatchInfo matchInfo;
    private final MediaTypeCodecRegistry codecRegistry;
    private ChannelPromise handshakeFuture;
    private NettyWebSocketSession clientSession;
    private final WebSocketStateBinderRegistry webSocketStateBinderRegistry;
    private FullHttpResponse handshakeResponse;
    private Argument<?> clientBodyArgument;
    private Argument<?> clientPongArgument;

    /**
     * Default constructor.
     *  @param request The originating request that created the WebSocket.
     * @param webSocketBean The WebSocket client bean.
     * @param handshaker The handshaker
     * @param requestBinderRegistry The request binder registry
     * @param mediaTypeCodecRegistry The media type codec registry
     */
    public NettyWebSocketClientHandler(
            MutableHttpRequest<?> request,
            WebSocketBean<T> webSocketBean,
            final WebSocketClientHandshaker handshaker,
            RequestBinderRegistry requestBinderRegistry,
            MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        super(null, requestBinderRegistry, mediaTypeCodecRegistry, webSocketBean, request, Collections.emptyMap(), handshaker.version(), handshaker.actualSubprotocol(), null);
        this.codecRegistry = mediaTypeCodecRegistry;
        this.handshaker = handshaker;
        this.genericWebSocketBean = webSocketBean;
        this.webSocketStateBinderRegistry = new WebSocketStateBinderRegistry(requestBinderRegistry != null ? requestBinderRegistry : new DefaultRequestBinderRegistry(ConversionService.SHARED));
        String clientPath = webSocketBean.getBeanDefinition().stringValue(ClientWebSocket.class).orElse("");
        UriMatchTemplate matchTemplate = UriMatchTemplate.of(clientPath);
        this.matchInfo = matchTemplate.match(request.getPath()).orElse(null);

        callOpenMethod(null);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
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
        return clientBodyArgument;
    }

    @Override
    public Argument<?> getPongArgument() {
        return clientPongArgument;
    }

    @Override
    public NettyWebSocketSession getSession() {
        return clientSession;
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        handshaker.handshake(ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        final Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            // web socket client connected
            FullHttpResponse res = (FullHttpResponse) msg;
            this.handshakeResponse = res;
            try {
                handshaker.finishHandshake(ch, res);
            } catch (Exception e) {
                try {
                    completion.tryEmitError(new WebSocketClientException("Error finishing WebSocket handshake: " + e.getMessage(), e));
                } finally {
                    // clientSession isn't set yet, so we do the close manually instead of through session.close
                    ch.writeAndFlush(new CloseWebSocketFrame(CloseReason.INTERNAL_ERROR.getCode(), CloseReason.INTERNAL_ERROR.getReason()));
                    ch.close();
                }
                return;
            }
            handshakeFuture.setSuccess();

            this.clientSession = createWebSocketSession(ctx);

            T targetBean = genericWebSocketBean.getTarget();

            if (targetBean instanceof WebSocketSessionAware) {
                ((WebSocketSessionAware) targetBean).setWebSocketSession(clientSession);
            }


            ExecutableBinder<WebSocketState> binder = new DefaultExecutableBinder<>();
            BoundExecutable<?, ?> bound = binder.tryBind(messageHandler.getExecutableMethod(), webSocketBinder, new WebSocketState(clientSession, originatingRequest));
            List<Argument<?>> unboundArguments = bound.getUnboundArguments();

            if (unboundArguments.size() == 1) {
                this.clientBodyArgument = unboundArguments.iterator().next();
            } else {
                this.clientBodyArgument = null;

                try {
                    completion.tryEmitError(new WebSocketClientException("WebSocket @OnMessage method " + targetBean.getClass().getSimpleName() + "." + messageHandler.getExecutableMethod() + " should define exactly 1 message parameter, but found 2 possible candidates: " + unboundArguments));
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
                        completion.tryEmitError(new WebSocketClientException("WebSocket @OnMessage pong handler method " + targetBean.getClass().getSimpleName() + "." + messageHandler.getExecutableMethod() + " should define exactly 1 pong message parameter, but found: " + unboundArguments));
                    } finally {
                        if (getSession().isOpen()) {
                            getSession().close(CloseReason.INTERNAL_ERROR);
                        }
                    }
                    return;
                }
            }

            Optional<? extends MethodExecutionHandle<?, ?>> opt = webSocketBean.openMethod();
            if (opt.isPresent()) {
                MethodExecutionHandle<?, ?> openMethod = opt.get();

                WebSocketState webSocketState = new WebSocketState(clientSession, originatingRequest);
                try {
                    BoundExecutable openMethodBound = binder.bind(openMethod.getExecutableMethod(), webSocketStateBinderRegistry, webSocketState);
                    Object target = openMethod.getTarget();
                    Object result = openMethodBound.invoke(target);

                    if (Publishers.isConvertibleToPublisher(result)) {
                        Publisher<?> reactiveSequence = Publishers.convertPublisher(result, Publisher.class);
                        Flux.from(reactiveSequence).subscribe(
                                o -> { },
                                error -> completion.tryEmitError(new WebSocketSessionException("Error opening WebSocket client session: " + error.getMessage(), error)),
                                () -> {
                                    completion.tryEmitValue(targetBean);
                                }
                        );
                    } else {
                        completion.tryEmitValue(targetBean);
                    }
                } catch (Throwable e) {
                    completion.tryEmitError(new WebSocketClientException("Error opening WebSocket client session: " + e.getMessage(), e));
                    if (getSession().isOpen()) {
                        getSession().close(CloseReason.INTERNAL_ERROR);
                    }
                }
            } else {
                completion.tryEmitValue(targetBean);
            }
            return;
        }

        if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        } else {
            ctx.fireChannelRead(msg);
        }


    }

    @Override
    protected NettyWebSocketSession createWebSocketSession(ChannelHandlerContext ctx) {
        if (ctx != null) {
            return new NettyWebSocketSession(
                    handshakeResponse.headers().get(HttpHeaderNames.SEC_WEBSOCKET_ACCEPT),
                    ctx.channel(),
                    originatingRequest,
                    codecRegistry,
                    handshaker.version().toHttpHeaderValue(),
                    ctx.pipeline().get(SslHandler.class) != null
            ) {
                @Override
                public ConvertibleValues<Object> getUriVariables() {
                    if (matchInfo != null) {
                        return ConvertibleValues.of(matchInfo.getVariableValues());
                    }
                    return ConvertibleValues.empty();
                }
            };
        }
        return null;
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }

        super.exceptionCaught(ctx, cause);
    }

    public final Mono<T> getHandshakeCompletedMono() {
        return completion.asMono();
    }
}
