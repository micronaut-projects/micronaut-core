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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.subscriber.LazySendingSubscriber;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ConcatenatingSubscriber;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.netty.EventLoopFlow;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.body.AvailableNettyByteBody;
import io.micronaut.http.netty.body.ByteBufConsumer;
import io.micronaut.http.netty.body.NettyBodyAdapter;
import io.micronaut.http.netty.body.NettyByteBody;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.netty.body.StreamingNettyByteBody;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.micronaut.http.server.ResponseLifecycle;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.HttpContent;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
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
                    CloseableByteBody body = NettyBodyAdapter.adapt(Flux.from(contents).map(HttpContent::content), eventLoop());
                    return ByteBodyHttpResponseWrapper.wrap(response, body);
                }).onErrorResume(e -> (ExecutionFlow) handleStreamingError(request, e));
            }
        }

        return super.encodeNoBody(response);
    }

    private EventLoop eventLoop() {
        return request.getChannelHandlerContext().channel().eventLoop();
    }

    @Override
    protected @NonNull CloseableByteBody concatenate(Publisher<ByteBody> items) {
        return NettyConcatenatingSubscriber.concatenate(eventLoop(), items);
    }

    @Override
    protected @NonNull CloseableByteBody concatenateJson(Publisher<ByteBody> items) {
        return JsonNettyConcatenatingSubscriber.concatenateJson(eventLoop(), items);
    }

    private static class NettyConcatenatingSubscriber extends ConcatenatingSubscriber implements ByteBufConsumer {
        final StreamingNettyByteBody.SharedBuffer sharedBuffer;
        private final EventLoop eventLoop;
        private final EventLoopFlow flow;

        NettyConcatenatingSubscriber(EventLoop eventLoop) {
            this.eventLoop = eventLoop;
            this.flow = new EventLoopFlow(eventLoop);
            sharedBuffer = new StreamingNettyByteBody.SharedBuffer(eventLoop, BodySizeLimits.UNLIMITED, this);
        }

        static CloseableByteBody concatenate(EventLoop eventLoop, Publisher<ByteBody> publisher) {
            NettyConcatenatingSubscriber subscriber = new NettyConcatenatingSubscriber(eventLoop);
            publisher.subscribe(subscriber);
            return new StreamingNettyByteBody(subscriber.sharedBuffer);
        }

        @Override
        protected Upstream forward(ByteBody body) {
            NettyByteBody adapted = NettyBodyAdapter.adapt(body, eventLoop);
            if (adapted instanceof StreamingNettyByteBody streaming) {
                return streaming.primary(this);
            } else {
                add(AvailableNettyByteBody.toByteBuf((AvailableNettyByteBody) adapted));
                complete();
                return null;
            }
        }

        @Override
        public void add(@NonNull ByteBuf buffer) {
            int n = buffer.readableBytes();
            onForward(n);
            add0(buffer);
        }

        void add0(@NonNull ByteBuf buffer) {
            if (flow.executeNow(() -> sharedBuffer.add(buffer))) {
                sharedBuffer.add(buffer);
            }
        }

        @Override
        protected void forwardComplete() {
            if (flow.executeNow(sharedBuffer::complete)) {
                sharedBuffer.complete();
            }
        }

        @Override
        protected void forwardError(Throwable t) {
            if (flow.executeNow(() -> sharedBuffer.error(t))) {
                sharedBuffer.error(t);
            }
        }
    }

    private static final class JsonNettyConcatenatingSubscriber extends NettyConcatenatingSubscriber {
        private static final ByteBuf START_ARRAY = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("[", StandardCharsets.UTF_8)).asReadOnly();
        private static final ByteBuf END_ARRAY = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("]", StandardCharsets.UTF_8)).asReadOnly();
        private static final ByteBuf SEPARATOR = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer(",", StandardCharsets.UTF_8)).asReadOnly();
        private static final ByteBuf EMPTY_ARRAY = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("[]", StandardCharsets.UTF_8)).asReadOnly();

        JsonNettyConcatenatingSubscriber(EventLoop eventLoop) {
            super(eventLoop);
        }

        static CloseableByteBody concatenateJson(EventLoop eventLoop, Publisher<ByteBody> publisher) {
            JsonNettyConcatenatingSubscriber subscriber = new JsonNettyConcatenatingSubscriber(eventLoop);
            publisher.subscribe(subscriber);
            return new StreamingNettyByteBody(subscriber.sharedBuffer);
        }

        @Override
        protected long emitLeadingSeparator(boolean first) {
            add0((first ? START_ARRAY : SEPARATOR).duplicate());
            return 1;
        }

        @Override
        protected long emitFinalSeparator(boolean first) {
            add0((first ? EMPTY_ARRAY : END_ARRAY).duplicate());
            return first ? 2 : 1;
        }
    }
}
