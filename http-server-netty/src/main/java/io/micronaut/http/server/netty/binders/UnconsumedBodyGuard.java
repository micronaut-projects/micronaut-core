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
import io.netty.util.ReferenceCountUtil;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Operators;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wraps streaming body arguments that claim the request body when they are bound, so that the
 * body is discarded if the request is rejected before the route could consume the argument (see
 * {@link BasicHttpAttributes#discardUnconsumedBodies}). Without this, the data received so far
 * stays buffered and the connection stops reading.
 *
 * @since 5.2.10
 */
@Internal
public final class UnconsumedBodyGuard {
    private static final Logger LOG = LoggerFactory.getLogger(UnconsumedBodyGuard.class);

    private static final int UNTOUCHED = 0;
    private static final int CONSUMED = 1;
    private static final int DISCARDED = 2;

    private UnconsumedBodyGuard() {
    }

    /**
     * Guard a publisher that was bound from the request body.
     *
     * @param request   The request
     * @param publisher The publisher
     * @param <T>       The item type
     * @return The publisher to pass to the route
     */
    public static <T> Publisher<T> guard(HttpRequest<?> request, Publisher<T> publisher) {
        GuardedPublisher<T> guarded = new GuardedPublisher<>(publisher);
        BasicHttpAttributes.addUnconsumedBodyDiscard(request, guarded::discard);
        return guarded;
    }

    /**
     * Guard an input stream that was bound from the request body.
     *
     * @param request The request
     * @param stream  The stream
     * @return The stream to pass to the route
     */
    public static InputStream guard(HttpRequest<?> request, InputStream stream) {
        GuardedInputStream guarded = new GuardedInputStream(stream);
        BasicHttpAttributes.addUnconsumedBodyDiscard(request, guarded::discard);
        return guarded;
    }

    private static final class GuardedPublisher<T> extends AtomicInteger implements Publisher<T> {
        private final Publisher<T> delegate;

        GuardedPublisher(Publisher<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void subscribe(Subscriber<? super T> s) {
            if (get() == CONSUMED || compareAndExchange(UNTOUCHED, CONSUMED) != DISCARDED) {
                delegate.subscribe(s);
            } else {
                Operators.error(s, ByteBody.BodyDiscardedException.create());
            }
        }

        void discard() {
            if (compareAndSet(UNTOUCHED, DISCARDED)) {
                // subscribe and cancel right away, this releases what was buffered so far and
                // discards the rest of the body
                delegate.subscribe(DiscardingSubscriber.INSTANCE);
            }
        }
    }

    private static final class GuardedInputStream extends InputStream {
        private final InputStream delegate;
        private final AtomicInteger state = new AtomicInteger(UNTOUCHED);

        GuardedInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        private void touch() throws IOException {
            if (state.get() != CONSUMED && state.compareAndExchange(UNTOUCHED, CONSUMED) == DISCARDED) {
                throw ByteBody.BodyDiscardedException.create();
            }
        }

        void discard() {
            if (state.compareAndSet(UNTOUCHED, DISCARDED)) {
                try {
                    delegate.close();
                } catch (IOException e) {
                    LOG.debug("Failed to discard request body stream", e);
                }
            }
        }

        @Override
        public int read() throws IOException {
            touch();
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            touch();
            return delegate.read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            touch();
            return delegate.skip(n);
        }

        @Override
        public int available() throws IOException {
            touch();
            return delegate.available();
        }

        @Override
        public void close() throws IOException {
            state.compareAndSet(UNTOUCHED, CONSUMED);
            delegate.close();
        }
    }

    private enum DiscardingSubscriber implements Subscriber<Object> {
        INSTANCE;

        @Override
        public void onSubscribe(Subscription s) {
            s.cancel();
        }

        @Override
        public void onNext(Object o) {
            if (o instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    LOG.debug("Failed to release discarded request body item", e);
                }
            } else {
                ReferenceCountUtil.release(o);
            }
        }

        @Override
        public void onError(Throwable t) {
        }

        @Override
        public void onComplete() {
        }
    }
}
