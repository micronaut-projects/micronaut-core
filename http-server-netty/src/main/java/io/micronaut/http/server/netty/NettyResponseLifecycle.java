/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.server.netty;

import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.subscriber.LazySendingSubscriber;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ConcatenatingSubscriber;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.netty.EventLoopFlow;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.netty.body.StreamingNettyByteBody;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.micronaut.http.server.ResponseLifecycle;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.HttpContent;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.concurrent.Executor;

/**
 * Netty-specific version of {@link ResponseLifecycle}.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 * @author Jonas Konrad
 */
@Internal
final class NettyResponseLifecycle extends ResponseLifecycle {
    private final RoutingInBoundHandler routingInBoundHandler;
    private final NettyHttpRequest<?> request;

    public NettyResponseLifecycle(RoutingInBoundHandler routingInBoundHandler, NettyHttpRequest<?> request) {
        super(routingInBoundHandler.routeExecutor,
            routingInBoundHandler.messageBodyHandlerRegistry,
            routingInBoundHandler.conversionService,
            new NettyByteBodyFactory(request.getChannelHandlerContext().channel()));
        this.routingInBoundHandler = routingInBoundHandler;
        this.request = request;
    }

    @Override
    protected Executor ioExecutor() {
        return routingInBoundHandler.getIoExecutor();
    }

    @Override
    protected ExecutionFlow<? extends ByteBodyHttpResponse<?>> encodeNoBody(HttpResponse<?> response) {
        if (response instanceof NettyHttpResponseBuilder builder) {
            io.netty.handler.codec.http.HttpResponse nettyResponse = builder.toHttpResponse();
            if (nettyResponse instanceof StreamedHttpResponse streamed) {
                return LazySendingSubscriber.create(streamed).map(contents -> {
                    CloseableByteBody body = byteBodyFactory().adapt(Flux.from(contents).map(HttpContent::content), null, null);
                    return ByteBodyHttpResponseWrapper.wrap(response, body);
                }).onErrorResume(e -> (ExecutionFlow) handleStreamingError(request, e));
            }
        }

        return super.encodeNoBody(response);
    }

    private NettyByteBodyFactory byteBodyFactory() {
        return new NettyByteBodyFactory(request.getChannelHandlerContext().channel());
    }

    @Override
    protected @NonNull CloseableByteBody concatenate(Publisher<ByteBody> items) {
        return NettyConcatenatingSubscriber.concatenate(byteBodyFactory(), ConcatenatingSubscriber.Separators.NONE, items);
    }

    @Override
    protected @NonNull CloseableByteBody concatenateJson(Publisher<ByteBody> items) {
        return NettyConcatenatingSubscriber.concatenate(byteBodyFactory(), NettyConcatenatingSubscriber.JSON_NETTY, items);
    }

    private static class NettyConcatenatingSubscriber extends ConcatenatingSubscriber implements BufferConsumer {
        static final Separators JSON_NETTY = Separators.jsonSeparators(NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT));

        private final EventLoopFlow flow;

        NettyConcatenatingSubscriber(NettyByteBodyFactory byteBodyFactory, Separators separators) {
            super(byteBodyFactory, separators);
            this.flow = new EventLoopFlow(((StreamingNettyByteBody.SharedBuffer) sharedBuffer).eventLoop());
        }

        static CloseableByteBody concatenate(NettyByteBodyFactory byteBodyFactory, Separators separators, Publisher<ByteBody> publisher) {
            NettyConcatenatingSubscriber subscriber = new NettyConcatenatingSubscriber(byteBodyFactory, separators);
            publisher.subscribe(subscriber);
            return subscriber.rootBody;
        }

        @Override
        public void add(@NonNull ReadBuffer buffer) {
            if (flow.executeNow(() -> super.add(buffer))) {
                super.add(buffer);
            }
        }

        @Override
        protected void forwardComplete() {
            if (flow.executeNow(super::forwardComplete)) {
                super.forwardComplete();
            }
        }

        @Override
        protected void forwardError(Throwable t) {
            if (flow.executeNow(() -> super.forwardError(t))) {
                super.forwardError(t);
            }
        }
    }
}
