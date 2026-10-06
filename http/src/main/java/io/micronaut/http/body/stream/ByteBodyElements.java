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
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.function.Function;

/**
 * The elements of a body, read through a {@link PieceReader} as they are asked for, without
 * Reactor: a read polls the next element of the pieces read so far, and requests the next piece
 * of the body only when they complete no other element. An element is decoded when it is
 * polled, so nothing is decoded ahead of the caller.
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
    private boolean done;

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

    @Override
    protected void demand() {
        drain();
    }

    /**
     * Answer a waiting read: with the next element of the pieces read so far, with the end of
     * the elements, or by requesting the next piece.
     */
    private void drain() {
        T element = null;
        Throwable failure = null;
        boolean end = false;
        boolean subscribe = false;
        Subscription s = null;
        synchronized (this) {
            if (done || !isWaiting()) {
                return;
            }
            try {
                element = reader.poll();
            } catch (Throwable e) {
                failure = e;
                done = true;
                s = subscription;
                reader.close();
            }
            if (element == null && failure == null) {
                if (inputEnded) {
                    end = true;
                    done = true;
                    reader.close();
                } else if (requested) {
                    // a piece is on its way
                    return;
                } else {
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
            push(element);
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
        Throwable failure = null;
        Subscription s = null;
        synchronized (this) {
            requested = false;
            if (done) {
                piece.close();
                return;
            }
            try {
                reader.read(piece);
            } catch (Throwable e) {
                failure = e;
                done = true;
                s = subscription;
                reader.close();
            }
        }
        if (failure != null) {
            if (s != null) {
                s.cancel();
            }
            fail(wrap.apply(failure));
            return;
        }
        drain();
    }

    @Override
    public void onError(Throwable t) {
        synchronized (this) {
            if (done) {
                return;
            }
            done = true;
            reader.close();
        }
        fail(wrap.apply(t));
    }

    @Override
    public void onComplete() {
        Throwable failure = null;
        synchronized (this) {
            if (done) {
                return;
            }
            try {
                reader.complete();
                inputEnded = true;
            } catch (Throwable e) {
                failure = e;
                done = true;
                reader.close();
            }
        }
        if (failure != null) {
            fail(wrap.apply(failure));
            return;
        }
        drain();
    }
}
