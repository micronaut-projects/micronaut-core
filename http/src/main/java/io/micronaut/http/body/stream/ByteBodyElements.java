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
package io.micronaut.http.body.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.body.PieceReader;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The elements of a body, read through a {@link PieceReader} as they are asked for, without
 * Reactor: a read polls the next element of the pieces read so far, and requests the next piece
 * of the body when they complete no other element. An element is decoded when it is polled, so
 * nothing is decoded ahead of the caller. An element that the pieces read so far complete is
 * handed out at once ({@link #poll()}, a completed {@link #next()}).
 *
 * <p>To hide the latency of the connection, one piece is requested ahead once a requested piece
 * answered a read: the bytes of at most one piece are received before they are asked for.</p>
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class ByteBodyElements<T> extends PulledBodyElements<T> implements Subscriber<ReadBuffer> {

    private final CloseableByteBody body;
    private final PieceReader<T> reader;
    private final Function<Throwable, Throwable> wrap;

    // guarded by this, as are the calls of the reader
    private boolean subscribed;
    private @Nullable Subscription subscription;
    private boolean requested;
    private boolean inputEnded;
    /**
     * The input could not be read, or completed: it ended, and the elements the reader holds
     * are delivered before this failure.
     */
    private @Nullable Throwable inputFailure;
    private boolean done;
    /**
     * A piece arrived while no read waited for it: no other piece is requested ahead until a
     * read finds no element in the pieces read so far.
     */
    private boolean aheadReceived;

    /**
     * @param body   The body, which the elements take over
     * @param reader The reader of the pieces of the body, which the elements take over
     * @param wrap   The failure of the elements, from a failure to read the body or an element
     */
    public ByteBodyElements(CloseableByteBody body, PieceReader<T> reader, Function<Throwable, Throwable> wrap) {
        this.body = body;
        this.reader = reader;
        this.wrap = wrap;
    }

    /**
     * The pieces of a body as they are received, pulled one at a time, see
     * {@link io.micronaut.http.body.ByteBody#toReadBufferElements()}.
     *
     * @param body The body, which the elements take over
     * @return The pieces, each of which its consumer closes
     */
    @SuppressWarnings("java:S2095") // the elements own the piece reader, and close it
    public static ByteBodyElements<ReadBuffer> readBuffers(CloseableByteBody body) {
        return new ByteBodyElements<>(body, new ReadBufferPieces(), Function.identity());
    }

    /**
     * The elements as a publisher for the reactive API: the elements are pushed as the pieces of
     * the body are read, by a lock-free publisher without a future per element. These elements
     * can no longer be pulled.
     *
     * @return The publisher of the elements, for one subscriber
     */
    public Publisher<T> toPublisher() {
        synchronized (this) {
            if (subscribed || done) {
                throw new IllegalStateException("The elements were read already");
            }
            subscribed = true;
            done = true;
        }
        Publisher<T> elements = PieceReaders.publisher(InternalByteBody.toUnbufferedReadBufferPublisher(body), reader);
        return Flux.from(elements).onErrorMap(wrap::apply);
    }

    @Override
    protected void demand() {
        drain();
    }

    @Override
    protected @Nullable T pollSource() {
        // under the lock of these elements, which guards the reader too, while no read waits
        if (done) {
            return null;
        }
        T element;
        try {
            element = reader.poll();
        } catch (Throwable e) {
            Subscription s = inputEnded ? null : subscription;
            done = true;
            reader.close();
            if (s != null) {
                s.cancel();
            }
            fail(wrap.apply(e));
            return null;
        }
        if (element == null && inputEnded) {
            done = true;
            reader.close();
            if (inputFailure != null) {
                fail(wrap.apply(inputFailure));
            } else {
                end();
            }
        }
        return element;
    }

    /**
     * Answer a waiting read: with the next element of the pieces read so far, with the end of
     * the elements, or by requesting the next piece.
     */
    private void drain() {
        T element = null;
        CompletableFuture<Optional<T>> read = null;
        Throwable failure = null;
        boolean end = false;
        boolean subscribe = false;
        Subscription s = null;
        Subscription ahead = null;
        synchronized (this) {
            if (done || !isWaiting()) {
                return;
            }
            try {
                element = reader.poll();
            } catch (Throwable e) {
                failure = e;
                done = true;
                s = inputEnded ? null : subscription;
                reader.close();
            }
            if (element != null) {
                // taken with the element: a completion that arrives before the element is
                // handed over finds no read to end
                read = takeWaiting();
                if (!requested && !inputEnded && !aheadReceived && subscription != null) {
                    // the next piece is on its way while the caller handles this element
                    requested = true;
                    ahead = subscription;
                }
            } else if (failure == null) {
                if (inputEnded) {
                    done = true;
                    reader.close();
                    if (inputFailure != null) {
                        failure = inputFailure;
                    } else {
                        end = true;
                    }
                } else if (requested) {
                    // a piece is on its way
                    return;
                } else {
                    aheadReceived = false;
                    requested = true;
                    if (subscribed) {
                        // null until onSubscribe, which requests the first piece
                        s = subscription;
                    } else {
                        subscribed = true;
                        subscribe = true;
                    }
                }
            }
        }
        if (failure != null) {
            if (s != null) {
                s.cancel();
            }
            fail(wrap.apply(failure));
        } else if (element != null) {
            Objects.requireNonNull(read).complete(Optional.of(element));
            if (ahead != null) {
                ahead.request(1);
            }
        } else if (end) {
            end();
        } else if (subscribe) {
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
            reader.close();
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
        Subscription s = null;
        synchronized (this) {
            requested = false;
            if (done || inputEnded) {
                piece.close();
                return;
            }
            if (!isWaiting()) {
                aheadReceived = true;
            }
            try {
                reader.read(piece);
            } catch (Throwable e) {
                // the values the piece completed before the failure are delivered first, as
                // the reactive readers deliver them
                inputFailure = e;
                inputEnded = true;
                s = subscription;
            }
        }
        if (s != null) {
            s.cancel();
        }
        drain();
    }

    @Override
    public void onError(Throwable t) {
        synchronized (this) {
            if (done || inputEnded) {
                return;
            }
            done = true;
            reader.close();
        }
        fail(wrap.apply(t));
    }

    @Override
    public void onComplete() {
        synchronized (this) {
            if (done || inputEnded) {
                return;
            }
            inputEnded = true;
            try {
                reader.complete();
            } catch (Throwable e) {
                // e.g. the input ends inside a value: the values before it are delivered first
                inputFailure = e;
            }
        }
        drain();
    }
}
