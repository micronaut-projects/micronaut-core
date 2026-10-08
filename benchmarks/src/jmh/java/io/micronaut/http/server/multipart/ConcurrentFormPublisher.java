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
package io.micronaut.http.server.multipart;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.body.stream.ReactorInterop;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Benchmark-only prototype for bounded, concurrent single-result field completion. Completed values retain
 * their concurrency slot until delivered, so a slow consumer cannot grow an unbounded queue.
 * Unlike sequential concatenation, independent delayed fields may complete out of order.
 *
 * @param <T> The source field type
 * @param <R> The completed field type
 */
@Internal
public final class ConcurrentFormPublisher<T, R> implements Publisher<R> {
    private final Publisher<T> source;
    private final Function<? super T, ? extends ExecutionFlow<? extends R>> mapper;
    private final Consumer<? super T> discardSource;
    private final Consumer<? super R> discardValue;
    private final int concurrency;

    /**
     * @param source The source fields
     * @param mapper Completes a field, taking ownership of it
     * @param discardSource Releases fields not handed to the mapper
     * @param discardValue Releases completed values not delivered
     * @param concurrency Maximum number of running or queued completions
     */
    public ConcurrentFormPublisher(Publisher<T> source,
                                   Function<? super T, ? extends ExecutionFlow<? extends R>> mapper,
                                   Consumer<? super T> discardSource, Consumer<? super R> discardValue,
                                   int concurrency) {
        if (concurrency < 1) {
            throw new IllegalArgumentException("Concurrency must be positive");
        }
        this.source = source;
        this.mapper = mapper;
        this.discardSource = discardSource;
        this.discardValue = discardValue;
        this.concurrency = concurrency;
    }

    @Override
    public void subscribe(Subscriber<? super R> subscriber) {
        State state = new State(subscriber);
        subscriber.onSubscribe(state);
        ReactorInterop.subscribe(source, state, () -> subscriber, item -> {
            @SuppressWarnings("unchecked")
            T field = (T) item;
            discardSource.accept(field);
        });
    }

    @Internal
    private final class State implements Subscriber<T>, Subscription {
        private final Subscriber<? super R> downstream;
        private final AtomicInteger work = new AtomicInteger();
        // All state below is guarded by this; callbacks run outside the monitor.
        private final ArrayDeque<R> queue = new ArrayDeque<>();
        private final Set<Slot> running = new HashSet<>();
        private @Nullable Subscription upstream;
        private long requested;
        private long replenish;
        private int active;
        private boolean done;
        private boolean cancelled;
        private boolean terminated;
        private @Nullable Throwable failure;

        State(Subscriber<? super R> downstream) {
            this.downstream = downstream;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            synchronized (this) {
                if (upstream != null || cancelled || terminated) {
                    subscription.cancel();
                    return;
                }
                upstream = subscription;
                replenish = concurrency;
            }
            drain();
        }

        @Override
        public void onNext(T field) {
            Slot slot;
            synchronized (this) {
                if (cancelled || terminated || failure != null) {
                    discardSource.accept(field);
                    return;
                }
                slot = new Slot();
                running.add(slot);
                active++;
            }
            ExecutionFlow<? extends R> flow;
            try {
                flow = Objects.requireNonNull(mapper.apply(field), "Null completion flow");
            } catch (Throwable error) {
                discardSource.accept(field);
                onError(error);
                return;
            }
            flow.onComplete((value, error) -> completed(slot, value, error));
            boolean cancel;
            synchronized (this) {
                slot.flow = flow;
                cancel = cancelled || terminated || failure != null;
            }
            if (cancel) {
                flow.cancel();
            }
        }

        private void completed(Slot slot, @Nullable R value, @Nullable Throwable error) {
            boolean discard;
            synchronized (this) {
                running.remove(slot);
                discard = cancelled || terminated || failure != null || error != null;
                if (error != null && failure == null && !cancelled && !terminated) {
                    failure = error;
                }
                if (!discard && value != null) {
                    queue.add(value);
                } else {
                    active--;
                    if (!discard) {
                        replenish++;
                    }
                }
            }
            if (discard && value != null) {
                discardValue.accept(value);
            }
            drain();
        }

        @Override
        public void onError(Throwable error) {
            synchronized (this) {
                if (cancelled || terminated || failure != null) {
                    return;
                }
                failure = error;
            }
            drain();
        }

        @Override
        public void onComplete() {
            synchronized (this) {
                done = true;
            }
            drain();
        }

        @Override
        public void request(long count) {
            if (count <= 0) {
                onError(new IllegalArgumentException("Demand must be positive"));
                return;
            }
            synchronized (this) {
                long sum = requested + count;
                requested = sum < 0 ? Long.MAX_VALUE : sum;
            }
            drain();
        }

        @Override
        public void cancel() {
            synchronized (this) {
                cancelled = true;
            }
            drain();
        }

        private void drain() {
            if (work.getAndIncrement() != 0) {
                return;
            }
            int missed = 1;
            do {
                while (true) {
                    R value = null;
                    Throwable error;
                    boolean stop;
                    boolean finish;
                    Subscription subscription;
                    long demand = 0;
                    synchronized (this) {
                        error = failure;
                        stop = cancelled || error != null;
                        finish = !stop && done && active == 0;
                        subscription = upstream;
                        if (terminated) {
                            break;
                        }
                        if (stop || finish) {
                            terminated = true;
                        } else if (requested > 0 && !queue.isEmpty()) {
                            value = queue.remove();
                            if (requested != Long.MAX_VALUE) {
                                requested--;
                            }
                            active--;
                            replenish++;
                        } else if (!done && subscription != null && replenish > 0) {
                            demand = replenish;
                            replenish = 0;
                        } else {
                            break;
                        }
                    }
                    if (stop) {
                        if (subscription != null) {
                            subscription.cancel();
                        }
                        cleanup();
                        if (!cancelled && error != null) {
                            downstream.onError(error);
                        }
                        break;
                    } else if (finish) {
                        downstream.onComplete();
                        break;
                    } else if (value != null) {
                        downstream.onNext(value);
                    } else {
                        Objects.requireNonNull(subscription).request(demand);
                    }
                }
                missed = work.addAndGet(-missed);
            } while (missed != 0);
        }

        private void cleanup() {
            ArrayDeque<R> discarded;
            Set<Slot> slots;
            synchronized (this) {
                discarded = new ArrayDeque<>(queue);
                queue.clear();
                slots = new HashSet<>(running);
                running.clear();
            }
            discarded.forEach(discardValue);
            for (Slot slot : slots) {
                ExecutionFlow<?> flow;
                synchronized (this) {
                    flow = slot.flow;
                }
                if (flow != null) {
                    flow.cancel();
                }
            }
        }

        @Internal
        private final class Slot {
            private @Nullable ExecutionFlow<?> flow;
        }
    }
}
