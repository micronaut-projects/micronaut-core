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
package io.micronaut.http.server;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.execution.ImperativeExecutionFlow;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxOperator;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

import java.util.Objects;
import java.util.function.Function;

/**
 * {@code concatMap} for a mapper that returns an {@link ExecutionFlow}: the items are mapped one
 * at a time, in order, and each item is only requested once the previous result is emitted. A
 * flow that is already complete when the mapper returns is emitted right away, without the inner
 * subscription {@code concatMap} makes for every item, which is the common case for the pieces
 * of a streamed response.
 * <p>
 * An upstream error or completion that arrives while a flow is pending is delivered after the
 * result of that flow. Results that cannot be emitted, because the subscriber cancelled or the
 * stream failed, go to the discard hook of the subscriber context.
 *
 * @param <T> The upstream item type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class FlowConcatMap<T, R> extends FluxOperator<T, R> {
    private final Function<? super T, ? extends ExecutionFlow<? extends R>> mapper;

    FlowConcatMap(Flux<? extends T> source, Function<? super T, ? extends ExecutionFlow<? extends R>> mapper) {
        super(source);
        this.mapper = mapper;
    }

    @Override
    public void subscribe(CoreSubscriber<? super R> actual) {
        source.subscribe(new MapSubscriber<>(actual, mapper));
    }

    private static final class MapSubscriber<T, R> implements CoreSubscriber<T>, Subscription {
        private final CoreSubscriber<? super R> actual;
        private final Function<? super T, ? extends ExecutionFlow<? extends R>> mapper;
        private @Nullable Subscription upstream;

        // all of the following are guarded by this
        private long demand;
        /**
         * An item is requested from the upstream and has not arrived yet.
         */
        private boolean requested;
        /**
         * An item is being mapped, or its result is being emitted.
         */
        private boolean active;
        private boolean upstreamDone;
        private @Nullable Throwable upstreamError;
        /**
         * The subscriber has received its terminal signal.
         */
        private boolean terminated;
        private boolean cancelled;

        MapSubscriber(CoreSubscriber<? super R> actual, Function<? super T, ? extends ExecutionFlow<? extends R>> mapper) {
            this.actual = actual;
            this.mapper = mapper;
        }

        @Override
        public Context currentContext() {
            return actual.currentContext();
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (Operators.validate(upstream, s)) {
                upstream = s;
                actual.onSubscribe(this);
            }
        }

        @Override
        public void request(long n) {
            if (!Operators.validate(n)) {
                return;
            }
            boolean requestUpstream;
            synchronized (this) {
                demand = Operators.addCap(demand, n);
                requestUpstream = claimRequest();
            }
            if (requestUpstream) {
                upstream().request(1);
            }
        }

        /**
         * Must hold the lock.
         *
         * @return Whether the caller should request the next item from the upstream
         */
        private boolean claimRequest() {
            if (requested || active || upstreamDone || cancelled || terminated || demand <= 0) {
                return false;
            }
            requested = true;
            return true;
        }

        @Override
        public void cancel() {
            synchronized (this) {
                if (cancelled) {
                    return;
                }
                cancelled = true;
            }
            upstream().cancel();
        }

        @Override
        public void onNext(T t) {
            synchronized (this) {
                requested = false;
                if (cancelled || terminated) {
                    Operators.onDiscard(t, actual.currentContext());
                    return;
                }
                active = true;
            }
            ExecutionFlow<? extends R> flow;
            try {
                flow = Objects.requireNonNull(mapper.apply(t), "The mapper returned a null flow");
            } catch (Throwable e) {
                Operators.onDiscard(t, actual.currentContext());
                done(null, Operators.onOperatorError(e, actual.currentContext()));
                return;
            }
            ImperativeExecutionFlow<? extends R> complete = flow.tryComplete();
            if (complete != null) {
                done(complete.getValue(), complete.getError());
            } else {
                flow.onComplete(this::done);
            }
        }

        private void done(@Nullable R value, @Nullable Throwable error) {
            boolean drop;
            synchronized (this) {
                drop = cancelled || terminated;
                if (!drop && error != null) {
                    terminated = true;
                }
            }
            if (drop) {
                discard(value);
                if (error != null) {
                    Operators.onErrorDropped(error, actual.currentContext());
                }
                return;
            }
            if (error != null) {
                discard(value);
                upstream().cancel();
                actual.onError(error);
                return;
            }
            if (value != null) {
                actual.onNext(value);
            }
            Throwable terminalError = null;
            boolean complete = false;
            boolean requestUpstream = false;
            synchronized (this) {
                active = false;
                if (value != null) {
                    demand--;
                }
                if (terminated || cancelled) {
                    return;
                }
                if (upstreamError != null) {
                    terminated = true;
                    terminalError = upstreamError;
                } else if (upstreamDone) {
                    terminated = true;
                    complete = true;
                } else {
                    requestUpstream = claimRequest();
                }
            }
            if (terminalError != null) {
                actual.onError(terminalError);
            } else if (complete) {
                actual.onComplete();
            } else if (requestUpstream) {
                upstream().request(1);
            }
        }

        @Override
        public void onError(Throwable t) {
            synchronized (this) {
                if (terminated || upstreamDone) {
                    Operators.onErrorDropped(t, actual.currentContext());
                    return;
                }
                upstreamDone = true;
                if (active) {
                    // delivered once the pending flow completes, see done
                    upstreamError = t;
                    return;
                }
                terminated = true;
            }
            actual.onError(t);
        }

        @Override
        public void onComplete() {
            synchronized (this) {
                if (terminated || upstreamDone) {
                    return;
                }
                upstreamDone = true;
                if (active) {
                    // completed once the pending flow completes, see done
                    return;
                }
                terminated = true;
            }
            actual.onComplete();
        }

        private void discard(@Nullable R value) {
            if (value != null) {
                Operators.onDiscard(value, actual.currentContext());
            }
        }

        private Subscription upstream() {
            return Objects.requireNonNull(upstream);
        }
    }
}
