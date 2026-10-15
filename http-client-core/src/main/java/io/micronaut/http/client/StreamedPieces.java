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
package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.body.stream.PieceReaders;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * The pieces of the body of {@code dataStream} and {@code exchangeStream} of a client that
 * shares them from {@link AbstractHttpClient}: as a publisher, the body is read as it arrives,
 * with the bytes that wait for the subscriber limited by {@code max-content-length}, as the
 * Netty client reads them, and the pieces are byte array buffers, or the lines of an event
 * stream that the request accepts. Pulled as {@link BodyElements}, e.g. by a client filter, they
 * are the pieces of {@link BodyPieces}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class StreamedPieces implements BodyElements<ByteBuffer<?>> {

    private final CloseableByteBody body;
    private final boolean lines;
    private final long maxBufferSize;

    // guarded by this
    private boolean claimed;
    private @Nullable BodyElements<ByteBuffer<?>> pulled;

    /**
     * @param body          The body, which the pieces take over
     * @param lines         Whether the body is split into the lines of an event stream
     * @param maxBufferSize The largest number of bytes that wait for the subscriber, and of a line
     */
    StreamedPieces(CloseableByteBody body, boolean lines, long maxBufferSize) {
        this.body = body;
        this.lines = lines;
        this.maxBufferSize = maxBufferSize;
    }

    /**
     * The pieces as a publisher.
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
        PieceReader<ByteBuffer<?>> reader = lines
            ? BodyPieces.lineReader(maxBufferSize, ByteArrayBufferFactory.INSTANCE::wrap)
            : BodyPieces.reader();
        return PieceReaders.publisher(new BufferLimitedPublisher(body.toReadBufferPublisher(), maxBufferSize), reader);
    }

    private BodyElements<ByteBuffer<?>> pulled() {
        synchronized (this) {
            if (pulled == null) {
                if (claimed) {
                    throw new IllegalStateException("The pieces of the body were already read");
                }
                claimed = true;
                pulled = lines ? BodyPieces.lines(body, maxBufferSize) : BodyPieces.elements(body);
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
