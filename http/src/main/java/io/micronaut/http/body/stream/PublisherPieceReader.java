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
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.codec.CodecException;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * A {@link PieceReader} over the publisher of a chunked reader that only reads a publisher. The
 * publisher must emit while it is fed, as a map of its input does: the piece is handed to it
 * during {@link #read}, and an element is requested during {@link #poll()}. A signal outside of
 * these calls, or a requested element that is still missing after the end of the input, fails
 * the reader with an {@link IllegalStateException}: such a reader must implement
 * {@link io.micronaut.http.body.ChunkedMessageBodyReader#openPieceReader} itself.
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class PublisherPieceReader<T> implements PieceReader<T>, Subscriber<T> {

    private final String readerName;
    private final Input input = new Input();
    private final ArrayDeque<T> elements = new ArrayDeque<>(1);
    private @Nullable Subscription output;
    private boolean requested;
    private boolean ended;
    private final AtomicReference<@Nullable Throwable> failure = new AtomicReference<>();
    // the thread in a call of the reader: a signal of the chunked reader on any other thread is asynchronous
    private final AtomicReference<@Nullable Thread> caller = new AtomicReference<>();
    private boolean closed;

    /**
     * @param readerName The name of the reader, for the failure of an asynchronous reader
     * @param chunked    Reads the elements of the input
     */
    PublisherPieceReader(String readerName, Function<Publisher<ByteBuffer<?>>, Publisher<? extends T>> chunked) {
        this.readerName = readerName;
        caller.set(Thread.currentThread());
        try {
            chunked.apply(input).subscribe(this);
        } finally {
            caller.set(null);
        }
    }

    @Override
    public void read(ReadBuffer piece) throws IOException {
        if (closed) {
            piece.close();
            return;
        }
        ByteBuffer<?> buffer;
        try (piece) {
            buffer = piece.toByteBuffer();
        }
        caller.set(Thread.currentThread());
        try {
            input.offer(buffer);
        } finally {
            caller.set(null);
        }
        rethrow();
    }

    @Override
    public void complete() throws IOException {
        if (closed) {
            return;
        }
        caller.set(Thread.currentThread());
        try {
            input.complete();
        } finally {
            caller.set(null);
        }
        rethrow();
    }

    @Override
    public @Nullable T poll() throws IOException {
        T element = elements.poll();
        if (element != null) {
            return element;
        }
        rethrow();
        if (ended || closed) {
            return null;
        }
        Subscription s = output;
        if (!requested && s != null) {
            requested = true;
            caller.set(Thread.currentThread());
            try {
                s.request(1);
            } finally {
                caller.set(null);
            }
            element = elements.poll();
            if (element != null) {
                return element;
            }
            rethrow();
        }
        if (input.completed && !ended) {
            throw new IllegalStateException("The chunked reader " + readerName
                + " did not emit an element or the end while it was read: a reader that emits asynchronously must implement openPieceReader");
        }
        return null;
    }

    @Override
    public boolean isEmpty() {
        // the reactive reader ended without a failure: polling requests no other element
        return elements.isEmpty() && failure.get() == null && (ended || closed);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Subscription s = output;
        if (s != null) {
            s.cancel();
        }
        input.discard();
        T element;
        while ((element = elements.poll()) != null) {
            if (element instanceof ReferenceCounted counted) {
                counted.release();
            }
        }
    }

    @Override
    public void onSubscribe(Subscription s) {
        output = s;
    }

    @Override
    public void onNext(T element) {
        if (!checkSynchronous()) {
            // the element of an asynchronous emission is not read: the reader fails instead
            if (element instanceof ReferenceCounted counted) {
                counted.release();
            }
            return;
        }
        requested = false;
        elements.add(element);
    }

    @Override
    public void onError(Throwable t) {
        if (checkSynchronous()) {
            failure.set(t);
        }
    }

    @Override
    public void onComplete() {
        if (checkSynchronous()) {
            ended = true;
        }
    }

    /**
     * Whether a signal of the chunked reader happens on the thread of a call of this reader. A
     * signal on another thread, even one that overlaps such a call, fails the reader and is
     * dropped, so that the state of the reader is touched only by the thread that calls it.
     *
     * @return Whether the signal is synchronous
     */
    private boolean checkSynchronous() {
        if (caller.get() == Thread.currentThread()) {
            return true;
        }
        failure.compareAndSet(null, new IllegalStateException("The chunked reader " + readerName
            + " emitted outside of a read: a reader that emits asynchronously must implement openPieceReader"));
        return false;
    }

    private void rethrow() throws IOException {
        Throwable f = failure.get();
        if (f == null) {
            return;
        }
        if (f instanceof IOException e) {
            throw e;
        }
        if (f instanceof RuntimeException e) {
            throw e;
        }
        if (f instanceof Error e) {
            throw e;
        }
        throw new CodecException("Error reading the elements: " + f.getMessage(), f);
    }

    /**
     * The input of the chunked reader: the pieces handed to {@link #read}, emitted as the reader
     * requests them.
     */
    private static final class Input implements Publisher<ByteBuffer<?>>, Subscription {
        private final ArrayDeque<ByteBuffer<?>> queue = new ArrayDeque<>(1);
        private @Nullable Subscriber<? super ByteBuffer<?>> subscriber;
        private long demand;
        private boolean completed;
        private boolean terminated;
        private boolean cancelled;
        private boolean emitting;

        @Override
        public void subscribe(Subscriber<? super ByteBuffer<?>> s) {
            if (subscriber != null) {
                s.onSubscribe(this);
                s.onError(new IllegalStateException("The input of a chunked reader can be subscribed to only once"));
                return;
            }
            subscriber = s;
            s.onSubscribe(this);
            drain();
        }

        void offer(ByteBuffer<?> buffer) {
            if (cancelled) {
                release(buffer);
                return;
            }
            queue.add(buffer);
            drain();
        }

        void complete() {
            completed = true;
            drain();
        }

        void discard() {
            cancelled = true;
            ByteBuffer<?> buffer;
            while ((buffer = queue.poll()) != null) {
                release(buffer);
            }
        }

        @Override
        public void request(long n) {
            demand = demand + n < 0 ? Long.MAX_VALUE : demand + n;
            drain();
        }

        @Override
        public void cancel() {
            discard();
        }

        private void drain() {
            Subscriber<? super ByteBuffer<?>> s = subscriber;
            if (emitting || s == null) {
                return;
            }
            emitting = true;
            try {
                while (!cancelled && !terminated) {
                    if (demand > 0 && !queue.isEmpty()) {
                        demand--;
                        s.onNext(queue.poll());
                    } else if (completed && queue.isEmpty()) {
                        terminated = true;
                        s.onComplete();
                    } else {
                        break;
                    }
                }
            } finally {
                emitting = false;
            }
        }

        private static void release(ByteBuffer<?> buffer) {
            if (buffer instanceof ReferenceCounted counted) {
                counted.release();
            }
        }
    }
}
