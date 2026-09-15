/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.LeakTracker;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.body.stream.BaseSharedBuffer;
import io.micronaut.http.body.stream.BaseStreamingByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * This is a reactive subscriber that accepts {@link ByteBody}s and concatenates them into a single
 * {@link BaseSharedBuffer}, optionally with separators.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
public class ConcatenatingSubscriber implements BufferConsumer.Upstream, CoreSubscriber<ByteBody>, BufferConsumer {
    protected final BaseSharedBuffer sharedBuffer;
    protected final BaseStreamingByteBody<?> rootBody;

    private final ByteBodyFactory byteBodyFactory;
    private final Separators separators;

    private long forwarded;
    private long consumed;

    private @Nullable Subscription subscription;
    private boolean cancelled;
    private volatile boolean disregardBackpressure;
    private boolean first = true;
    private BufferConsumer.@Nullable Upstream currentComponent;
    private boolean start = false;
    private boolean delayedSubscriberCompletion = false;
    private boolean currentComponentDone = false;

    public ConcatenatingSubscriber(ByteBodyFactory byteBodyFactory, Separators separators) {
        this.byteBodyFactory = byteBodyFactory;
        this.separators = separators;
        ByteBodyFactory.StreamingBody sb = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, this);
        this.sharedBuffer = sb.sharedBuffer();
        this.rootBody = sb.rootBody();
    }

    public static CloseableByteBody concatenate(ByteBodyFactory byteBodyFactory, Publisher<ByteBody> publisher, Separators separators) {
        ConcatenatingSubscriber subscriber = new ConcatenatingSubscriber(byteBodyFactory, separators);
        publisher.subscribe(subscriber);
        return subscriber.rootBody;
    }

    @Override
    public final void onSubscribe(Subscription s) {
        boolean start;
        boolean cancelled;
        synchronized (this) {
            this.subscription = s;
            cancelled = this.cancelled;
            start = this.start;
        }
        if (cancelled) {
            s.cancel();
        } else if (start) {
            s.request(1);
        }
    }

    @Override
    public final void onComplete() {
        synchronized (this) {
            if (currentComponent != null) {
                delayedSubscriberCompletion = true;
                return;
            }
        }

        // the trailing separator travels with the completion signal, so that it can be written as
        // part of the message that terminates the response instead of as a message of its own
        ReadBuffer trailing = first ? separators.empty : separators.afterLast;
        if (trailing == null) {
            forwardComplete(null);
        } else {
            ReadBuffer duplicate = trailing.duplicate();
            onForward(duplicate.readable());
            forwardComplete(duplicate);
        }
    }

    @Override
    public final void onError(Throwable t) {
        forwardError(t);
    }

    /**
     * Forward the given body to the shared buffer, preceded by the given separator.
     *
     * @param body The body
     * @param leadingSeparator The separator to emit before the body, or {@code null} for none
     * @return The {@link io.micronaut.http.body.stream.BufferConsumer.Upstream} to control
     * component backpressure, or {@code null} if all bytes were written immediately (as is the
     * case for an {@link AvailableByteBody})
     */
    protected final BufferConsumer.@Nullable Upstream forward(ByteBody body, @Nullable ReadBuffer leadingSeparator) {
        if (body instanceof AvailableByteBody abb) {
            ReadBuffer element = abb.toReadBuffer();
            // the separator goes into the same buffer as the element it precedes, so that one
            // element leads to one buffer downstream (and thus, for a netty response, one HTTP
            // chunk and one flush) instead of two
            add(leadingSeparator == null ? element : byteBodyFactory.readBufferFactory().compose(List.of(leadingSeparator.duplicate(), element)));
            complete();
            return null;
        }
        if (leadingSeparator != null) {
            add(leadingSeparator.duplicate());
        }
        try (BaseStreamingByteBody<?> s = byteBodyFactory.toStreaming(body)) {
            return s.primary(this);
        }
    }

    /**
     * Should be called by the subclass when bytes are sent to the sharedBuffer, for
     * {@link #onBytesConsumed} accounting.
     *
     * @param n The number of bytes forwarded
     */
    protected final void onForward(long n) {
        synchronized (this) {
            forwarded += n;
        }
    }

    @Override
    public final void onNext(ByteBody body) {
        boolean first = this.first;
        this.first = false;

        BufferConsumer.Upstream component = forward(body, first ? separators.beforeFirst : separators.between);
        if (component == null) {
            return;
        }

        long preAcknowledged;
        synchronized (this) {
            preAcknowledged = consumed - forwarded;
            currentComponent = component;
        }

        component.start();
        if (disregardBackpressure) {
            component.disregardBackpressure();
        } else if (preAcknowledged > 0) {
            component.onBytesConsumed(preAcknowledged);
        }
    }

    @Override
    public final void start() {
        Subscription initialDemand;
        synchronized (this) {
            if (start) {
                throw new IllegalStateException("Already started");
            }
            initialDemand = subscription;
            start = true;
        }
        if (initialDemand != null) {
            initialDemand.request(1);
        }
    }

    @Override
    public final void onBytesConsumed(long bytesConsumed) {
        long delta;
        Upstream currentComponent;
        boolean requestNewComponent;
        synchronized (this) {
            long newConsumed = consumed + bytesConsumed;
            if (newConsumed < consumed) {
                // overflow
                newConsumed = Long.MAX_VALUE;
            }
            delta = newConsumed - consumed;
            consumed = newConsumed;

            currentComponent = this.currentComponent;
            requestNewComponent = currentComponent == null && currentComponentDone && newConsumed >= forwarded;
        }
        if (currentComponent != null && delta > 0) {
            currentComponent.onBytesConsumed(bytesConsumed);
        } else if (requestNewComponent) {
            // Previous component is now fully consumed, request a new one.
            if (subscription != null) {
                subscription.request(1);
            }
        }
    }

    @Override
    public final void allowDiscard() {
        Upstream component;
        Subscription subscription;
        synchronized (this) {
            component = currentComponent;
            subscription = this.subscription;
            cancelled = true;
        }
        if (subscription != null) {
            subscription.cancel();
        }
        if (component != null) {
            component.allowDiscard();
        }
    }

    @Override
    public final void disregardBackpressure() {
        Upstream component;
        synchronized (this) {
            component = currentComponent;
            disregardBackpressure = true;
        }
        if (component != null) {
            component.disregardBackpressure();
        }
    }

    @Override
    public void add(ReadBuffer buffer) {
        int n = buffer.readable();
        onForward(n);
        sharedBuffer.add(buffer);
    }

    @Override
    public final void complete() {
        boolean delayedSubscriberCompletion;
        boolean requestNextComponent;
        synchronized (this) {
            currentComponent = null;
            delayedSubscriberCompletion = this.delayedSubscriberCompletion;
            requestNextComponent = !delayedSubscriberCompletion && (disregardBackpressure || consumed >= forwarded);
            currentComponentDone = !requestNextComponent;
        }
        if (delayedSubscriberCompletion) {
            // onComplete was held back, call it now
            onComplete();
        } else if (requestNextComponent) {
            // current component completed. request the next ByteBody
            if (subscription != null) {
                subscription.request(1);
            }
        }
        // if requestNextComponent is false, then the last component has not been fully consumed yet. we'll request the next later.
    }

    @Override
    public final void error(Throwable e) {
        Subscription s = subscription;
        if (s != null) {
            s.cancel();
        }
        forwardError(e);
    }

    /**
     * Forward completion to the shared buffer, optionally together with a final buffer of trailing
     * bytes.
     *
     * @param trailing The trailing bytes to emit with the completion, or {@code null} for none
     */
    protected void forwardComplete(@Nullable ReadBuffer trailing) {
        if (trailing == null) {
            sharedBuffer.complete();
        } else {
            sharedBuffer.addAndComplete(trailing);
        }
    }

    /**
     * Forward an error to the shared buffer.
     *
     * @param t The error
     */
    protected void forwardError(Throwable t) {
        sharedBuffer.error(t);
    }

    /**
     * Fixed buffers to insert before, after and between items.
     *
     * @param beforeFirst If there are any items, the buffer to insert before the first one
     * @param afterLast If there are any items, the buffer to insert after the last one
     * @param between Buffer to insert between any items
     * @param empty Buffer to insert if there are no items
     */
    public record Separators(
        @Nullable ReadBuffer beforeFirst,
        @Nullable ReadBuffer afterLast,
        @Nullable ReadBuffer between,
        @Nullable ReadBuffer empty
    ) {
        /**
         * No separators.
         */
        public static final Separators NONE = new Separators(null, null, null, null);
        /**
         * {@link #jsonSeparators(ReadBufferFactory)} using {@link ReadBufferFactory#getJdkFactory()}.
         */
        public static final Separators JDK_JSON = LeakTracker.Factory.staticInitializer(() -> jsonSeparators(ReadBufferFactory.getJdkFactory()));

        /**
         * Create the appropriate separators for JSON using the given buffer factory.
         *
         * @param factory The factory to use
         * @return The separators
         */
        public static Separators jsonSeparators(ReadBufferFactory factory) {
            return new Separators(
                factory.copyOf("[", StandardCharsets.UTF_8),
                factory.copyOf("]", StandardCharsets.UTF_8),
                factory.copyOf(",", StandardCharsets.UTF_8),
                factory.copyOf("[]", StandardCharsets.UTF_8)
            );
        }
    }
}
