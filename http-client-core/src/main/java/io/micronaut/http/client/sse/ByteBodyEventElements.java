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
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.sse.Event;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The events of an event stream body, read from the body one piece at a time as they are asked
 * for: a read with no decoded event left requests the next piece of the body. The events of a
 * piece are decoded together.
 *
 * @param <B> The event data type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class ByteBodyEventElements<B> extends PulledBodyElements<Event<B>> implements Subscriber<ReadBuffer> {

    private final CloseableByteBody body;
    private final EventStreamDecoder decoder;
    private final Function<byte[], B> dataReader;

    // guarded by this
    private boolean subscribed;
    private @Nullable Subscription subscription;
    private boolean requested;
    private boolean done;

    /**
     * @param body          The event stream body
     * @param maxBufferSize The maximum size of a line, and of the data of one event
     * @param dataReader    Decodes the data of an event
     */
    ByteBodyEventElements(CloseableByteBody body, long maxBufferSize, Function<byte[], B> dataReader) {
        this.body = body;
        this.decoder = new EventStreamDecoder(maxBufferSize);
        this.dataReader = dataReader;
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
    public void onNext(ReadBuffer buffer) {
        synchronized (this) {
            requested = false;
        }
        List<Event<B>> events = new ArrayList<>(1);
        try (buffer) {
            for (Event<byte[]> event : decoder.decode(buffer.toArray())) {
                events.add(Event.of(event, dataReader.apply(event.getData())));
            }
        } catch (Throwable e) {
            abort(e);
            return;
        }
        for (Event<B> event : events) {
            push(event);
        }
        if (isWaiting()) {
            // the piece completed no event
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
        fail(wrap(t));
    }

    @Override
    public void onComplete() {
        synchronized (this) {
            if (done) {
                return;
            }
            done = true;
        }
        // an event not terminated by a blank line is discarded
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
        fail(wrap(error));
    }

    /**
     * The failure of the elements, an {@link HttpClientException}.
     *
     * @param error A failure to read or decode the events
     * @return The failure of the elements
     */
    static Throwable wrap(Throwable error) {
        return error instanceof HttpClientException ? error : new HttpClientException("Error consuming Server Sent Events: " + error.getMessage(), error);
    }
}
