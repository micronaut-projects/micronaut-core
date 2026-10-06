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
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.client.exceptions.HttpClientException;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.List;
import java.util.function.Function;

/**
 * The elements of a body, read from the body one piece at a time as they are asked for, without
 * Reactor: a read with no decoded element left requests the next piece of the body, and a piece
 * that completes no element requests another one. The elements of a piece are decoded together.
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class ByteBodyElements<T> extends PulledBodyElements<T> implements Subscriber<ReadBuffer> {

    private final CloseableByteBody body;
    private final PieceDecoder<T> decoder;
    private final Function<Throwable, Throwable> wrap;

    // guarded by this
    private boolean subscribed;
    private @Nullable Subscription subscription;
    private boolean requested;
    private boolean done;

    /**
     * @param body    The body
     * @param decoder Decodes a piece of the body into the elements it completes
     * @param wrap    The failure of the elements, from a failure to read or decode the body
     */
    public ByteBodyElements(CloseableByteBody body, PieceDecoder<T> decoder, Function<Throwable, Throwable> wrap) {
        this.body = body;
        this.decoder = decoder;
        this.wrap = wrap;
    }

    /**
     * The pieces of a body as heap buffers, which are not reference counted. Empty pieces are
     * skipped.
     *
     * @param body The body
     * @return The pieces
     */
    public static BodyElements<ByteBuffer<?>> pieces(CloseableByteBody body) {
        return new ByteBodyElements<>(
            body,
            piece -> piece.readable() == 0 ? List.of() : List.of(ByteArrayBufferFactory.INSTANCE.wrap(piece.toArray())),
            ByteBodyElements::wrap
        );
    }

    /**
     * @param error A failure to read the body
     * @return The failure of the elements, an {@link HttpClientException}
     */
    public static Throwable wrap(Throwable error) {
        return error instanceof HttpClientException ? error : new HttpClientException("Error reading the response body: " + error.getMessage(), error);
    }

    @Override
    protected void demand() {
        boolean subscribe = false;
        Subscription s = null;
        synchronized (this) {
            if (done || requested) {
                return;
            }
            requested = true;
            if (subscribed) {
                // null until onSubscribe, which requests the first piece
                s = subscription;
            } else {
                subscribed = true;
                subscribe = true;
            }
        }
        if (subscribe) {
            // the reader decodes piece by piece with a limit of its own
            InternalByteBody.toUnbufferedReadBufferPublisher(body).subscribe(this);
        } else if (s != null) {
            s.request(1);
        }
    }

    @Override
    protected void release() {
        Subscription s;
        boolean close;
        synchronized (this) {
            s = done ? null : subscription;
            close = !subscribed;
            done = true;
            subscribed = true;
        }
        if (s != null) {
            // discards the rest of the body
            s.cancel();
        } else if (close) {
            body.close();
        }
    }

    @Override
    public void onSubscribe(Subscription s) {
        boolean cancel;
        synchronized (this) {
            subscription = s;
            cancel = done;
        }
        if (cancel) {
            s.cancel();
        } else {
            // the read that subscribed
            s.request(1);
        }
    }

    @Override
    public void onNext(ReadBuffer piece) {
        synchronized (this) {
            requested = false;
        }
        List<T> elements;
        try (piece) {
            elements = decoder.decode(piece);
        } catch (Throwable e) {
            abort(e);
            return;
        }
        for (T element : elements) {
            push(element);
        }
        if (isWaiting()) {
            // the piece completed no element
            demand();
        }
    }

    @Override
    public void onError(Throwable t) {
        synchronized (this) {
            if (done) {
                return;
            }
            done = true;
        }
        fail(wrap.apply(t));
    }

    @Override
    public void onComplete() {
        synchronized (this) {
            if (done) {
                return;
            }
            done = true;
        }
        end();
    }

    private void abort(Throwable error) {
        Subscription s;
        synchronized (this) {
            if (done) {
                return;
            }
            done = true;
            s = subscription;
        }
        if (s != null) {
            s.cancel();
        }
        fail(wrap.apply(error));
    }

    /**
     * Decodes a piece of a body.
     *
     * @param <T> The type of an element
     */
    @FunctionalInterface
    public interface PieceDecoder<T> {
        /**
         * Decode a piece. The piece is closed after the call.
         *
         * @param piece The piece
         * @return The elements the piece completes
         * @throws Exception If decoding fails
         */
        List<T> decode(ReadBuffer piece) throws Exception;
    }
}
