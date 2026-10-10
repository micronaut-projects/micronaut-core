/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.client.netty;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.util.ReferenceCountUtil;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * The pieces of a response body for {@code dataStream} and {@code exchangeStream}, as Netty
 * buffers, without Reactor: as the publisher streams of the client always delivered them, an
 * empty buffer is skipped, and a buffer is released after {@code onNext} unless the subscriber
 * retained it. A buffer that is shared, e.g. the slice of a line, is wrapped in a composite
 * buffer, so that the subscriber has a reference count of its own.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class NettyPiecesPublisher implements Publisher<ByteBuffer<?>> {

    private final Publisher<ByteBuf> source;

    /**
     * @param source The buffers, which the subscriber takes over
     */
    NettyPiecesPublisher(Publisher<ByteBuf> source) {
        this.source = source;
    }

    @Override
    public void subscribe(Subscriber<? super ByteBuffer<?>> subscriber) {
        source.subscribe(new PiecesSubscriber(subscriber));
    }

    /**
     * The buffer of a piece, with a reference count that only the subscriber holds.
     *
     * @param byteBuf The buffer, which the piece takes over
     * @return The piece
     */
    static ByteBuffer<?> piece(ByteBuf byteBuf) {
        if (byteBuf.refCnt() > 1) {
            // if we aren't the exclusive owner of this buffer, we need to detect whether the
            // downstream consumer releases it or not. For that, we need our own refCnt. A
            // composite buffer provides that.
            CompositeByteBuf composite = byteBuf.alloc().compositeBuffer(1);
            composite.addComponent(true, byteBuf);
            return NettyByteBufferFactory.DEFAULT.wrap(composite);
        }
        return NettyByteBufferFactory.DEFAULT.wrap(byteBuf);
    }

    /**
     * Release the buffer of a piece after {@code onNext}, unless the subscriber released it.
     *
     * @param piece The piece
     */
    static void releaseAfterNext(ByteBuffer<?> piece) {
        if (piece.asNativeBuffer() instanceof ByteBuf byteBuf && byteBuf.refCnt() > 0) {
            ReferenceCountUtil.safeRelease(byteBuf);
        }
    }

    private static final class PiecesSubscriber implements Subscriber<ByteBuf>, Subscription {
        private final Subscriber<? super ByteBuffer<?>> downstream;
        private @Nullable Subscription upstream;

        PiecesSubscriber(Subscriber<? super ByteBuffer<?>> downstream) {
            this.downstream = downstream;
        }

        @Override
        public void onSubscribe(Subscription s) {
            upstream = s;
            downstream.onSubscribe(this);
        }

        @Override
        public void onNext(ByteBuf byteBuf) {
            if (byteBuf instanceof EmptyByteBuf) {
                byteBuf.release();
                Subscription s = upstream;
                if (s != null) {
                    // the empty buffer was requested: ask for another one in its place
                    s.request(1);
                }
                return;
            }
            ByteBuffer<?> piece = piece(byteBuf);
            try {
                downstream.onNext(piece);
            } finally {
                releaseAfterNext(piece);
            }
        }

        @Override
        public void onError(Throwable t) {
            downstream.onError(t);
        }

        @Override
        public void onComplete() {
            downstream.onComplete();
        }

        @Override
        public void request(long n) {
            Subscription s = upstream;
            if (s != null) {
                s.request(n);
            }
        }

        @Override
        public void cancel() {
            Subscription s = upstream;
            if (s != null) {
                s.cancel();
            }
        }
    }
}
