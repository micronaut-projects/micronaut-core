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
package io.micronaut.http.reactive.execution;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.execution.ImperativeExecutionFlow;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * {@link Mono} implementation that is  based on an {@link ExecutionFlow}.
 *
 * @param <T> The value type
 * @author Jonas Konrad
 * @since 4.8.0
 */
@Internal
final class FlowAsMono<T> extends Mono<T> implements Fuseable {
    final ExecutionFlow<? extends T> flow;

    FlowAsMono(ExecutionFlow<? extends T> flow) {
        this.flow = flow;
    }

    @Override
    public void subscribe(CoreSubscriber<? super T> actual) {
        ImperativeExecutionFlow<? extends T> complete = flow.tryComplete();
        if (complete != null) {
            // these subscriptions support SYNC fusion
            if (complete.getError() != null) {
                Operators.error(actual, complete.getError());
            } else if (complete.getValue() != null) {
                actual.onSubscribe(Operators.scalarSubscription(actual, complete.getValue()));
            } else {
                actual.onSubscribe(Operators.emptySubscription());
            }
        } else {
            // fallback to a normal subscription
            new SubscriptionImpl(actual).callOnSubscribe();
        }
    }

    private final class SubscriptionImpl implements QueueSubscription<T> {
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<FlowAsMono.SubscriptionImpl> STATE = AtomicIntegerFieldUpdater.newUpdater(FlowAsMono.SubscriptionImpl.class, "state");
        private static final int STATE_WAITING = 0;
        private static final int STATE_SUBSCRIBING = 1;
        private static final int STATE_DONE = 2;

        private final CoreSubscriber<? super T> actual;

        /**
         * State flag to avoid reentrancy. We only do two distinct operations on the subscriber:
         * {@link CoreSubscriber#onSubscribe} and the completion operations (
         * {@link CoreSubscriber#onNext} and then {@link CoreSubscriber#onComplete()}). By the
         * reactive spec these must not happen in a reentrant fashion, i.e. we can't complete
         * inside the {@link CoreSubscriber#onSubscribe} call. This field acts like a simple lock
         * to avoid that.
         * <p>Just before {@link CoreSubscriber#onSubscribe}, the state is set to
         * {@link #STATE_SUBSCRIBING}. If the subscriber then calls {@link Subscription#request} in
         * that method and the request can immediately be fulfilled, the completion handler will
         * notice that the state is {@link #STATE_SUBSCRIBING}, switch it to {@link #STATE_DONE}
         * and hold back the result for now. Once {@link CoreSubscriber#onSubscribe} finishes,
         * {@link #callOnSubscribe()} will notice that the field is {@link #STATE_DONE} and forward
         * the actual result.
         * <p>If the request is <i>not</i> immediately fulfilled, {@link #callOnSubscribe()} will
         * set the state to {@link #STATE_WAITING}. Once the flow completes, the completion handler
         * will see this state and forward the result immediately instead of holding it back.
         */
        private volatile int state;

        private boolean requested;

        private T result;
        private Throwable error;

        SubscriptionImpl(CoreSubscriber<? super T> actual) {
            this.actual = actual;
        }

        void callOnSubscribe() {
            state = STATE_SUBSCRIBING;
            actual.onSubscribe(this);
            if (STATE.getAndSet(this, STATE_WAITING) == STATE_DONE) {
                // onComplete was already called but result held back to avoid reentrancy, need to
                // forward its result
                forward(result, error);
            }
        }

        @Override
        public void request(long n) {
            if (!requested) {
                requested = true;
                flow.onComplete((v, e) -> {
                    result = v;
                    error = e;
                    if (STATE.getAndSet(this, STATE_DONE) == STATE_WAITING) {
                        // onSubscribe is already done so we can forward immediately
                        forward(v, e);
                    }
                });
            }
        }

        private void forward(T v, Throwable e) {
            if (v != null) {
                actual.onNext(v);
            }
            if (error == null) {
                actual.onComplete();
            } else {
                actual.onError(e);
            }
        }

        @Override
        public void cancel() {
            requested = true;
            flow.cancel();
        }

        @Override
        public int requestFusion(int requestedMode) {
            // while we implement QueueSubscription, we don't actually support it. only the
            // short-circuit flows for ImperativeExecutionFlow above do. there's little value in
            // supporting it here
            return 0;
        }

        @Override
        public T poll() {
            throw noFusion();
        }

        @Override
        public int size() {
            throw noFusion();
        }

        @Override
        public boolean isEmpty() {
            throw noFusion();
        }

        @Override
        public void clear() {
            throw noFusion();
        }

        private static UnsupportedOperationException noFusion() {
            return new UnsupportedOperationException("fusion not supported");
        }
    }
}
