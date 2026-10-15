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
package io.micronaut.http.client.sse;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.body.stream.PulledBodyElements;
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.micronaut.http.sse.Event;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.ByteArrayOutputStream;
import java.util.function.Function;

/**
 * A body that is not an event stream, as one event: received and decoded whole on the first
 * read, with a limit. An empty body has no event. Closing the elements while the body is
 * received cancels it.
 *
 * @param <B> The event data type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class SingleBodyElements<B> extends PulledBodyElements<Event<B>> implements Subscriber<ReadBuffer> {

    private final CloseableByteBody body;
    private final Function<byte[], B> reader;
    private final Function<Throwable, Throwable> wrap;
    private final long maxBufferSize;

    // guarded by this
    private boolean started;
    private boolean done;
    private @Nullable Subscription subscription;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    /**
     * @param body          The body
     * @param reader        Decodes the body
     * @param wrap          The failure of the events, from a failure to read or decode the body
     * @param maxBufferSize The maximum size of the body
     */
    SingleBodyElements(CloseableByteBody body, Function<byte[], B> reader, Function<Throwable, Throwable> wrap, long maxBufferSize) {
        this.body = body;
        this.reader = reader;
        this.wrap = wrap;
        this.maxBufferSize = maxBufferSize;
    }

    @Override
    protected void demand() {
        synchronized (this) {
            if (started) {
                return;
            }
            started = true;
        }
        InternalByteBody.toUnbufferedReadBufferPublisher(body).subscribe(this);
    }

    @Override
    protected void release() {
        boolean close;
        Subscription s;
        synchronized (this) {
            close = !started;
            started = true;
            s = done ? null : subscription;
            done = true;
        }
        if (close) {
            body.close();
        } else if (s != null) {
            // discards the rest of the body
            s.cancel();
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
            s.request(1);
        }
    }

    @Override
    public void onNext(ReadBuffer piece) {
        Subscription s;
        Throwable failure = null;
        try (piece) {
            synchronized (this) {
                if (done) {
                    return;
                }
                long length = (long) bytes.size() + piece.readable();
                if (length > maxBufferSize) {
                    done = true;
                    failure = new ContentLengthExceededException(maxBufferSize, length);
                } else {
                    byte[] array = piece.toArray();
                    bytes.write(array, 0, array.length);
                }
                s = subscription;
            }
        }
        if (failure != null) {
            if (s != null) {
                s.cancel();
            }
            fail(wrap.apply(failure));
        } else if (s != null) {
            s.request(1);
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
        byte[] received;
        synchronized (this) {
            if (done) {
                return;
            }
            done = true;
            received = bytes.toByteArray();
        }
        try {
            if (received.length > 0) {
                push(Event.of(reader.apply(received)));
            }
            end();
        } catch (Throwable e) {
            fail(wrap.apply(e));
        }
    }
}
