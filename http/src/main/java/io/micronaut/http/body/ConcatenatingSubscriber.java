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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.body.stream.BaseSharedBuffer;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * This is a reactive subscriber that accepts {@link ByteBody}s and concatenates them into a single
 * {@link BaseSharedBuffer}, optionally with separators.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
public abstract class ConcatenatingSubscriber implements BufferConsumer.Upstream, CoreSubscriber<ByteBody>, BufferConsumer {
    private long forwarded;
    private long consumed;

    private Subscription subscription;
    private boolean cancelled;
    private volatile boolean disregardBackpressure;
    private boolean first = true;
    private BufferConsumer.Upstream currentComponent;
    private boolean start = false;
    private boolean delayedSubscriberCompletion = false;
    private boolean currentComponentDone = false;

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

    /**
     * Called before any new {@link ByteBody} component to emit an additional separator.
     *
     * @param first {@code true} iff this is the first element (i.e. the start of the output)
     * @return The number of bytes written for {@link #onBytesConsumed} accounting
     */
    protected long emitLeadingSeparator(boolean first) {
        return 0;
    }

    /**
     * Called before after all {@link ByteBody} components to emit additional trailing bytes.
     *
     * @param first {@code true} iff this is the first element, i.e. there were no component {@link ByteBody}s
     * @return The number of bytes written for {@link #onBytesConsumed} accounting
     */
    protected long emitFinalSeparator(boolean first) {
        return 0;
    }

    @Override
    public final void onComplete() {
        synchronized (this) {
            if (currentComponent != null) {
                delayedSubscriberCompletion = true;
                return;
            }
        }

        long emitted = emitFinalSeparator(first);
        if (emitted != 0) {
            synchronized (this) {
                forwarded += emitted;
            }
        }
        forwardComplete();
    }

    @Override
    public final void onError(Throwable t) {
        forwardError(t);
    }

    /**
     * Forward the given body to the shared buffer.
     *
     * @param body The body
     * @return The {@link io.micronaut.http.body.stream.BufferConsumer.Upstream} to control
     * component backpressure, or {@code null} if all bytes were written immediately (as is the
     * case for an {@link AvailableByteBody})
     */
    @Nullable
    protected abstract BufferConsumer.Upstream forward(ByteBody body);

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
        onForward(emitLeadingSeparator(first));
        first = false;

        BufferConsumer.Upstream component = forward(body);
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
            subscription.request(1);
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
            subscription.request(1);
        }
        // if requestNextComponent is false, then the last component has not been fully consumed yet. we'll request the next later.
    }

    @Override
    public final void error(Throwable e) {
        subscription.cancel();
        forwardError(e);
    }

    /**
     * Forward completion to the shared buffer.
     */
    protected abstract void forwardComplete();

    /**
     * Forward an error to the shared buffer.
     *
     * @param t The error
     */
    protected abstract void forwardError(Throwable t);

    /**
     * Concatenating implementation that writes to a {@link ReactiveByteBufferByteBody}.
     */
    public static class ByteBufferConcatenatingSubscriber extends ConcatenatingSubscriber implements ReactiveByteBufferByteBody.ByteBufferConsumer {
        final ReactiveByteBufferByteBody.SharedBuffer sharedBuffer = new ReactiveByteBufferByteBody.SharedBuffer(BodySizeLimits.UNLIMITED, this);

        private ByteBufferConcatenatingSubscriber() {
        }

        public static CloseableByteBody concatenate(Publisher<ByteBody> publisher) {
            ByteBufferConcatenatingSubscriber subscriber = new ByteBufferConcatenatingSubscriber();
            publisher.subscribe(subscriber);
            return new ReactiveByteBufferByteBody(subscriber.sharedBuffer);
        }

        @Override
        protected Upstream forward(ByteBody body) {
            return ByteBufferBodyAdapter.adapt(Flux.from(body.toByteArrayPublisher()).map(ByteBuffer::wrap)).primary(this);
        }

        @Override
        public void add(@NonNull ByteBuffer buffer) {
            int n = buffer.remaining();
            onForward(n);
            sharedBuffer.add(buffer);
        }

        @Override
        protected void forwardComplete() {
            sharedBuffer.complete();
        }

        @Override
        protected void forwardError(Throwable t) {
            sharedBuffer.error(t);
        }
    }

    /**
     * Concatenating implementation that writes to a {@link ReactiveByteBufferByteBody}, with
     * JSON-style separators.
     */
    public static final class JsonByteBufferConcatenatingSubscriber extends ByteBufferConcatenatingSubscriber {
        private static final ByteBuffer START_ARRAY = ByteBuffer.wrap("[".getBytes(StandardCharsets.UTF_8));
        private static final ByteBuffer END_ARRAY = ByteBuffer.wrap("]".getBytes(StandardCharsets.UTF_8));
        private static final ByteBuffer SEPARATOR = ByteBuffer.wrap(",".getBytes(StandardCharsets.UTF_8));
        private static final ByteBuffer EMPTY_ARRAY = ByteBuffer.wrap("[]".getBytes(StandardCharsets.UTF_8));

        private JsonByteBufferConcatenatingSubscriber() {
        }

        public static CloseableByteBody concatenateJson(Publisher<ByteBody> publisher) {
            JsonByteBufferConcatenatingSubscriber subscriber = new JsonByteBufferConcatenatingSubscriber();
            publisher.subscribe(subscriber);
            return new ReactiveByteBufferByteBody(subscriber.sharedBuffer);
        }

        @Override
        protected long emitLeadingSeparator(boolean first) {
            sharedBuffer.add((first ? START_ARRAY : SEPARATOR).asReadOnlyBuffer());
            return 1;
        }

        @Override
        protected long emitFinalSeparator(boolean first) {
            sharedBuffer.add((first ? EMPTY_ARRAY : END_ARRAY).asReadOnlyBuffer());
            return first ? 2 : 1;
        }
    }
}
