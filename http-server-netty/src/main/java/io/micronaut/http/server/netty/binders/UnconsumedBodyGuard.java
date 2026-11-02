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
package io.micronaut.http.server.netty.binders;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Operators;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Registers streaming body arguments that claim the request body when they are bound, so that
 * the body is discarded if the route fails before it returns, or never subscribes to it (see
 * {@link BasicHttpAttributes#discardUnconsumedBodies}). Without this, the data received so far
 * stays buffered and the connection stops reading.
 *
 * @since 5.2.21
 */
@Internal
public final class UnconsumedBodyGuard {
    private static final Logger LOG = LoggerFactory.getLogger(UnconsumedBodyGuard.class);

    private UnconsumedBodyGuard() {
    }

    /**
     * Open a streaming body argument from the given body only once the route subscribes to it.
     * If the route fails before it subscribes, the body is closed instead. Closing discards the
     * body without asking the client for it, so a request with {@code Expect: 100-continue} is
     * not sent a {@code 100 Continue}, and it also releases a body that was already fully
     * received.
     *
     * @param request The request
     * @param body    The body, taken from the request by the binder
     * @param open    Opens the publisher for the body when the route subscribes
     * @param <T>     The item type
     * @return The publisher to pass to the route
     */
    public static <T> Publisher<T> openWhenSubscribed(HttpRequest<?> request, CloseableByteBody body, Function<? super CloseableByteBody, ? extends Publisher<T>> open) {
        LazyBody<T> lazy = new LazyBody<>(body, open);
        BasicHttpAttributes.addUnconsumedBodyDiscard(request, lazy::discard);
        return lazy;
    }

    /**
     * Discard the given stream if the route fails before it returns, or if the route never reads
     * from it.
     *
     * @param request The request
     * @param stream  The stream that was bound from the request body
     * @return The stream to pass to the route
     */
    public static InputStream discardIfUnconsumed(HttpRequest<?> request, InputStream stream) {
        UnreadTrackingInputStream tracking = new UnreadTrackingInputStream(stream);
        BasicHttpAttributes.addUnconsumedBodyDiscard(request, tracking::closeQuietly, () -> {
            if (!tracking.read) {
                tracking.closeQuietly();
            }
        });
        return tracking;
    }

    /**
     * Stream that records whether the route has started to read it.
     */
    private static final class UnreadTrackingInputStream extends FilterInputStream {
        private volatile boolean read;

        UnreadTrackingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            read = true;
            return super.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            read = true;
            return super.read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            read = true;
            return super.skip(n);
        }

        @Override
        public int available() throws IOException {
            read = true;
            return super.available();
        }

        @Override
        public long transferTo(OutputStream out) throws IOException {
            read = true;
            return in.transferTo(out);
        }

        @Override
        public void close() throws IOException {
            read = true;
            super.close();
        }

        void closeQuietly() {
            try {
                in.close();
            } catch (IOException e) {
                LOG.debug("Failed to discard request body stream", e);
            }
        }
    }

    /**
     * Publisher that either the route claims by subscribing, or the discard by closing the body,
     * whichever comes first.
     *
     * @param <T> The item type
     */
    private static final class LazyBody<T> extends AtomicInteger implements Publisher<T> {
        private static final int UNCLAIMED = 0;
        private static final int SUBSCRIBED = 1;
        private static final int DISCARDED = 2;

        private final transient CloseableByteBody body;
        private final transient Function<? super CloseableByteBody, ? extends Publisher<T>> open;

        LazyBody(CloseableByteBody body, Function<? super CloseableByteBody, ? extends Publisher<T>> open) {
            this.body = body;
            this.open = open;
        }

        @Override
        public void subscribe(Subscriber<? super T> s) {
            int previous = compareAndExchange(UNCLAIMED, SUBSCRIBED);
            if (previous == DISCARDED) {
                Operators.error(s, ByteBody.BodyDiscardedException.create());
                return;
            } else if (previous != UNCLAIMED) {
                Operators.error(s, new IllegalStateException("Only one subscriber allowed"));
                return;
            }
            Publisher<T> publisher;
            try {
                publisher = open.apply(body);
            } catch (Throwable t) {
                body.close();
                Operators.error(s, t);
                return;
            }
            publisher.subscribe(s);
        }

        void discard() {
            if (compareAndSet(UNCLAIMED, DISCARDED)) {
                body.close();
            }
        }
    }
}
