/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.http.netty.websocket.NettyRxWebSocketSession;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.websocket.CloseReason;
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
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;

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
    private final WebSocketBean<T> webSocketBean;
    private final MutableHttpRequest<?> originatingRequest;
    private final FlowableEmitter<T> emitter;
    private final UriMatchInfo matchInfo;
    private final MediaTypeCodecRegistry codecRegistry;
    private ChannelPromise handshakeFuture;
    private NettyRxWebSocketSession clientSession;
    private WebSocketStateBinderRegistry webSocketStateBinderRegistry;
    private FullHttpResponse handshakeResponse;
    private Argument<?> clientBodyArgument;

    /**
     * Default constructor.
     *  @param request The originating request that created the WebSocket.
     * @param webSocketBean The WebSocket client bean.
     * @param handshaker The handshaker
     * @param requestBinderRegistry The request binder registry
     * @param mediaTypeCodecRegistry The media type codec registry
     * @param emitter The socket emitter
     */
    public NettyWebSocketClientHandler(
            MutableHttpRequest<?> request,
            WebSocketBean<T> webSocketBean,
            final WebSocketClientHandshaker handshaker,
            RequestBinderRegistry requestBinderRegistry,
            MediaTypeCodecRegistry mediaTypeCodecRegistry,
            FlowableEmitter<T> emitter) {
        super(null, requestBinderRegistry, mediaTypeCodecRegistry, webSocketBean, request, Collections.emptyMap(), handshaker.version(), null);
        this.codecRegistry = mediaTypeCodecRegistry;
        this.handshaker = handshaker;
        this.webSocketBean = webSocketBean;
        this.originatingRequest = request;
        this.emitter = emitter;
        this.webSocketStateBinderRegistry = new WebSocketStateBinderRegistry(requestBinderRegistry != null ? requestBinderRegistry : new DefaultRequestBinderRegistry(ConversionService.SHARED));
        String clientPath = webSocketBean.getBeanDefinition().stringValue(ClientWebSocket.class).orElse("");
        UriMatchTemplate matchTemplate = UriMatchTemplate.of(clientPath);
        this.matchInfo = matchTemplate.match(request.getPath()).orElse(null);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
            if (idleStateEvent.state() == IdleState.ALL_IDLE) {
                // close the connection if it is idle for too long
                if (clientSession != null && clientSession.isOpen()) {
                    clientSession.close(CloseReason.NORMAL);
                }
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
    public NettyRxWebSocketSession getSession() {
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
            handshaker.finishHandshake(ch, res);
            handshakeFuture.setSuccess();

            this.clientSession = createWebSocketSession(ctx);

            T targetBean = webSocketBean.getTarget();

            if (targetBean instanceof WebSocketSessionAware) {
                ((WebSocketSessionAware) targetBean).setWebSocketSession(clientSession);
            }

            WebSocketState webSocketState = new WebSocketState(clientSession, originatingRequest);

            ExecutableBinder<WebSocketState> binder = new DefaultExecutableBinder<>();
            BoundExecutable<?, ?> bound = binder.tryBind(messageHandler.getExecutableMethod(), webSocketBinder, new WebSocketState(clientSession, originatingRequest));
            List<Argument<?>> unboundArguments = bound.getUnboundArguments();

            if (unboundArguments.size() == 1) {
                this.clientBodyArgument = unboundArguments.iterator().next();
            } else {
                this.clientBodyArgument = null;

                try {
                    emitter.onError(new WebSocketClientException("WebSocket @OnMessage method " + targetBean.getClass().getSimpleName() + "." + messageHandler.getExecutableMethod() + " should define exactly 1 message parameter, but found 2 possible candidates: " + unboundArguments));
                } finally {
                    if (getSession().isOpen()) {
                        getSession().close(CloseReason.INTERNAL_ERROR);
                    }
                }
                return;
            }

            Optional<? extends MethodExecutionHandle<?, ?>> opt = webSocketBean.openMethod();
            if (opt.isPresent()) {
                MethodExecutionHandle<?, ?> openMethod = opt.get();

                try {
                    BoundExecutable openMethodBound = binder.bind(openMethod.getExecutableMethod(), webSocketStateBinderRegistry, webSocketState);
                    Object target = openMethod.getTarget();
                    Object result = openMethodBound.invoke(target);

                    if (Publishers.isConvertibleToPublisher(result)) {
                        Flowable<?> flowable = Publishers.convertPublisher(result, Flowable.class);
                        flowable.subscribe(
                                o -> { },
                                error -> emitter.onError(new WebSocketSessionException("Error opening WebSocket client session: " + error.getMessage(), error)),
                                () -> {
                                    emitter.onNext(targetBean);
                                    emitter.onComplete();
                                }
                        );
                    } else {
                        emitter.onNext(targetBean);
                        emitter.onComplete();
                    }
                } catch (Throwable e) {
                    emitter.onError(new WebSocketClientException("Error opening WebSocket client session: " + e.getMessage(), e));
                    if (getSession().isOpen()) {
                        getSession().close(CloseReason.INTERNAL_ERROR);
                    }
                }
            } else {
                emitter.onNext(targetBean);
                emitter.onComplete();
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
    protected NettyRxWebSocketSession createWebSocketSession(ChannelHandlerContext ctx) {
        if (ctx != null) {
            return new NettyRxWebSocketSession(
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

}
