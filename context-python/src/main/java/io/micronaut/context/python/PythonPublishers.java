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
package io.micronaut.context.python;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.SupplierUtil;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

/**
 * Publisher adapters of the Python bridge that keep the reactive context of the subscriber. The
 * Reactor context (a reactive transaction status, the propagated context of an HTTP request) flows
 * from the subscriber up to the source through Reactor subscribers only, so every adapter here is a
 * Reactor operator when Reactor is on the class path; without Reactor no such context exists and the
 * plain Reactive Streams adapters are used.
 *
 * @since 5.2.0
 */
@Internal
public final class PythonPublishers {

    private static final boolean REACTOR_AVAILABLE = ClassUtils.isPresent("reactor.core.publisher.Mono", PythonPublishers.class.getClassLoader());

    private PythonPublishers() {
    }

    /**
     * Map the items of a publisher Python code returned. {@link Publishers#map(Publisher, Function)}
     * wraps the subscriber in a plain Reactive Streams subscriber, so a source such as
     * {@code Mono.deferContextual} or a {@code MANDATORY} transaction would see an empty Reactor
     * context; here a {@code Mono} stays a {@code Mono} and the context of the subscriber is kept.
     *
     * @param publisher The source publisher
     * @param mapper The item mapper
     * @param <T> The source item type
     * @param <R> The mapped item type
     * @return The mapped publisher
     */
    public static <T, R> Publisher<R> map(Publisher<T> publisher, Function<T, R> mapper) {
        if (REACTOR_AVAILABLE) {
            return Reactor.map(publisher, mapper);
        }
        return Publishers.map(publisher, mapper);
    }

    /**
     * A publisher that starts a deferred computation (a Python coroutine) when it is first
     * subscribed, in the reactive context of that subscriber, and shares the result with every
     * subscriber: a cancelled subscription does not cancel the shared future, so a later subscriber
     * (a {@code timeout().retry()}) still receives the result.
     *
     * @param starter Starts the computation in the given context
     * @return The publisher
     */
    public static Publisher<Object> deferred(Function<PythonReactiveContext, CompletableFuture<Object>> starter) {
        if (REACTOR_AVAILABLE) {
            return Reactor.deferred(starter);
        }
        Supplier<CompletableFuture<Object>> started = SupplierUtil.memoized(() -> starter.apply(new PythonReactiveContext(null, PropagatedContext.getOrEmpty())));
        // each subscriber cancels a copy, never the shared future
        return Publishers.fromCompletableFuture(() -> started.get().copy());
    }

    /**
     * Subscribe to a publisher within a reactive context: the propagated context is in scope during
     * the subscription and the Reactor context is written above the subscriber, so the source sees
     * both.
     *
     * @param publisher The publisher
     * @param subscriber The subscriber
     * @param reactiveContext The reactive context, or {@code null} for a plain subscription
     */
    public static void subscribe(Publisher<?> publisher, Subscriber<Object> subscriber, @Nullable PythonReactiveContext reactiveContext) {
        if (reactiveContext == null) {
            subscribeUnchecked(publisher, subscriber);
            return;
        }
        try (PropagatedContext.Scope ignored = reactiveContext.propagatedContext().propagate()) {
            if (REACTOR_AVAILABLE && reactiveContext.reactorContext() != null) {
                Reactor.subscribe(publisher, subscriber, reactiveContext.reactorContext());
            } else {
                subscribeUnchecked(publisher, subscriber);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void subscribeUnchecked(Publisher<?> publisher, Subscriber<Object> subscriber) {
        ((Publisher<Object>) publisher).subscribe(subscriber);
    }

    /**
     * The Reactor adapters, only touched when Reactor is on the class path.
     */
    private static final class Reactor {

        private Reactor() {
        }

        static <T, R> Publisher<R> map(Publisher<T> publisher, Function<T, R> mapper) {
            if (publisher instanceof Mono<T> mono) {
                return mono.map(mapper);
            }
            return Flux.from(publisher).map(mapper);
        }

        static Publisher<Object> deferred(Function<PythonReactiveContext, CompletableFuture<Object>> starter) {
            Started started = new Started();
            // suppressCancel: the future is shared with every subscriber, one cancelling must not fail the others
            return Mono.deferContextual(contextView -> Mono.fromFuture(started.get(contextView, starter), true));
        }

        @SuppressWarnings("unchecked")
        static void subscribe(Publisher<?> publisher, Subscriber<Object> subscriber, Object reactorContext) {
            Context context = (Context) reactorContext;
            if (publisher instanceof Mono<?> mono) {
                ((Mono<Object>) mono).contextWrite(context).subscribe(subscriber);
            } else {
                ((Flux<Object>) Flux.from(publisher)).contextWrite(context).subscribe(subscriber);
            }
        }

        private static PythonReactiveContext reactiveContext(ContextView contextView) {
            PropagatedContext propagatedContext = ReactorPropagation.findPropagatedContext(contextView)
                .orElseGet(PropagatedContext::getOrEmpty);
            return new PythonReactiveContext(Context.of(contextView), propagatedContext);
        }

        /**
         * The future of the first subscriber, shared with the later ones.
         */
        private static final class Started {
            private @Nullable CompletableFuture<Object> future;

            synchronized CompletableFuture<Object> get(ContextView contextView, Function<PythonReactiveContext, CompletableFuture<Object>> starter) {
                if (future == null) {
                    future = starter.apply(reactiveContext(contextView));
                }
                return future;
            }
        }
    }
}
