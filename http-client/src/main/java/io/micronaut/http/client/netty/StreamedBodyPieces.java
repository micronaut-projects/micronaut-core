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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.PieceReaders;
import io.micronaut.http.client.BodyPieces;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * The pieces of the body of {@code dataStream} and {@code exchangeStream}, read as those
 * publishers always read them: the buffers of the body as Netty buffers, as they arrived, with the
 * bytes waiting for the subscriber limited like buffered content, or the lines of an event
 * stream that the request accepts. Pulled as {@link BodyElements}, e.g. by a client filter,
 * they are the pieces of {@link BodyPieces}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class StreamedBodyPieces implements BodyElements<ByteBuffer<?>> {

    private final CloseableByteBody body;
    private final boolean lines;
    private final long maxLineLength;

    // guarded by this
    private boolean claimed;
    private @Nullable BodyElements<ByteBuffer<?>> pulled;

    /**
     * @param body          The body, which the pieces take over
     * @param lines         Whether the body is split into the lines of an event stream
     * @param maxLineLength The largest number of bytes of a line
     */
    StreamedBodyPieces(CloseableByteBody body, boolean lines, long maxLineLength) {
        this.body = body;
        this.lines = lines;
        this.maxLineLength = maxLineLength;
    }

    /**
     * The pieces as a publisher of Netty buffers, which are released after {@code onNext} unless
     * the subscriber retained them.
     *
     * @return The publisher, for one subscriber
     */
    Publisher<ByteBuffer<?>> publisher() {
        synchronized (this) {
            if (claimed) {
                throw new IllegalStateException("The pieces of the body were already read");
            }
            claimed = true;
        }
        Publisher<ByteBuf> buffers;
        if (lines) {
            buffers = PieceReaders.publisher(body.toReadBufferPublisher(), BodyPieces.lineReader(maxLineLength, line -> line.length == 0 ? Unpooled.buffer(0) : Unpooled.wrappedBuffer(line)));
        } else {
            buffers = NettyByteBodyFactory.toByteBufs(body);
        }
        return new NettyPiecesPublisher(buffers);
    }

    private BodyElements<ByteBuffer<?>> pulled() {
        synchronized (this) {
            if (pulled == null) {
                if (claimed) {
                    throw new IllegalStateException("The pieces of the body were already read");
                }
                claimed = true;
                pulled = lines ? BodyPieces.lines(body, maxLineLength) : BodyPieces.elements(body);
            }
            return pulled;
        }
    }

    @Override
    public CompletionStage<Optional<ByteBuffer<?>>> next() {
        return pulled().next();
    }

    @Override
    public CompletionStage<Void> forEach(Function<? super ByteBuffer<?>, ? extends CompletionStage<?>> consumer) {
        return pulled().forEach(consumer);
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        BodyElements<ByteBuffer<?>> elements;
        boolean closeBody;
        synchronized (this) {
            elements = pulled;
            closeBody = !claimed;
            claimed = true;
        }
        if (elements != null) {
            return elements.closeAsync();
        }
        if (closeBody) {
            body.close();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
        closeAsync();
    }
}
