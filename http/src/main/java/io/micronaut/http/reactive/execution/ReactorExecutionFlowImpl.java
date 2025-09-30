/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.execution.ImperativeExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The reactive flow implementation.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
final class ReactorExecutionFlowImpl implements ReactiveExecutionFlow<Object> {

    private Mono<Object> value;
    private List<Subscription> subscriptionsToCancel = new ArrayList<>(1);

    <K> ReactorExecutionFlowImpl(Publisher<K> value) {
        this(value instanceof Flux<K> flux ? flux.next() : Mono.from(value));
    }

    <K> ReactorExecutionFlowImpl(Mono<K> value) {
        this.value = (Mono<Object>) value;
    }

    public static <T> ExecutionFlow<T> defuse(Publisher<T> publisher, PropagatedContext propagatedContext) {
        if (publisher instanceof Fuseable.ScalarCallable<?> sc) {
            // Mono.just, Mono.error. No need for context propagation
            try {
                //noinspection unchecked
                return ExecutionFlow.just((T) sc.call());
            } catch (Throwable t) {
                return ExecutionFlow.error(t);
            }
        } else if (publisher instanceof FlowAsMono<T> flowAsMono) {
            // unwrap directly
            //noinspection unchecked
            return (ExecutionFlow<T>) flowAsMono.flow;
        }

        // special subscriber that (a) contains the propagated context and (b) can return an
        // imperative flow if the result is provided immediately in subscribe()
        var s = new CoreSubscriber<T>() {
            final AtomicReference<ExecutionFlow<T>> flow = new AtomicReference<>();

            boolean complete = false;

            @Override
            public Context currentContext() {
                return ReactorPropagation.addPropagatedContext(Context.empty(), propagatedContext);
            }

            @Override
            public void onSubscribe(Subscription s) {
                if (s instanceof Fuseable.QueueSubscription<?> qs && qs.requestFusion(Fuseable.SYNC) == Fuseable.SYNC) {
                    // we can avoid the subscribe / WIP dance. This is for example Mono.just(…).map(…)
                    T result;
                    try {
                        //noinspection unchecked
                        result = (T) qs.poll();
                    } catch (Throwable t) {
                        completeError(t);
                        return;
                    }
                    complete(result);
                    return;
                }
                // fallback, normal reactive subscription
                s.request(Long.MAX_VALUE);
            }

            private void complete(T result) {
                if (!flow.compareAndSet(null, ExecutionFlow.just(result))) {
                    ((DelayedExecutionFlow<T>) flow.get()).complete(result);
                }
                complete = true;
            }

            private void completeError(Throwable t) {
                if (!flow.compareAndSet(null, ExecutionFlow.error(t))) {
                    ((DelayedExecutionFlow<?>) flow.get()).completeExceptionally(t);
                }
                complete = true;
            }

            @Override
            public void onNext(T t) {
                if (complete) {
                    Operators.onNextDropped(t, Context.empty());
                    return;
                }
                complete(t);
            }

            @Override
            public void onError(Throwable t) {
                if (complete) {
                    Operators.onErrorDropped(t, Context.empty());
                    return;
                }
                completeError(t);
            }

            @Override
            public void onComplete() {
                if (!complete) {
                    complete(null);
                }
            }
        };
        try (PropagatedContext.Scope ignored = propagatedContext.propagate()) {
            publisher.subscribe(s);
        }
        ExecutionFlow<T> immediate = s.flow.getPlain();
        if (immediate != null) {
            return immediate;
        } else {
            DelayedExecutionFlow<T> flow = DelayedExecutionFlow.create();
            if (s.flow.compareAndSet(null, flow)) {
                return flow;
            } else {
                // data race
                return s.flow.getPlain();
            }
        }
    }

    @Override
    public <R> ExecutionFlow<R> flatMap(Function<? super Object, ? extends ExecutionFlow<? extends R>> transformer) {
        value = value.flatMap(value -> toMono(transformer.apply(value)));
        return (ExecutionFlow<R>) this;
    }

    @Override
    public <R> ExecutionFlow<R> then(Supplier<? extends ExecutionFlow<? extends R>> supplier) {
        value = value.then(Mono.fromSupplier(supplier).flatMap(ReactorExecutionFlowImpl::toMono));
        return (ExecutionFlow<R>) this;
    }

    @Override
    public <R> ExecutionFlow<R> map(Function<? super Object, ? extends R> function) {
        value = value.map(function);
        return (ExecutionFlow<R>) this;
    }

    @Override
    public ExecutionFlow<Object> onErrorResume(Function<? super Throwable, ? extends ExecutionFlow<?>> fallback) {
        value = value.onErrorResume(throwable -> toMono(fallback.apply(throwable)));
        return this;
    }

    @Override
    public ExecutionFlow<Object> putInContext(String key, Object value) {
        this.value = this.value.contextWrite(context -> context.put(key, value));
        return this;
    }

    @Override
    public @NonNull ExecutionFlow<Object> putInContextIfAbsent(@NonNull String key, @NonNull Object value) {
        this.value = this.value.contextWrite(context -> {
            if (!context.hasKey(key)) {
                return context.put(key, value);
            } else {
                return context;
            }
        });
        return this;
    }

    @Override
    public void cancel() {
        List<Subscription> stc;
        synchronized (this) {
            stc = subscriptionsToCancel;
            subscriptionsToCancel = null;
        }
        if (stc != null) {
            for (Subscription subscription : stc) {
                subscription.cancel();
            }
        }
    }

    @Override
    public void onComplete(BiConsumer<? super Object, Throwable> fn) {
        if (value instanceof Fuseable.ScalarCallable callable) {
            Object value;
            try {
                value = callable.call();
            } catch (Exception e) {
                fn.accept(null, e);
                return;
            }
            fn.accept(value, null);
            return;
        }
        value.subscribe(new CoreSubscriber<>() {

            Subscription subscription;
            Object value;

            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                boolean cancel;
                synchronized (ReactorExecutionFlowImpl.this) {
                    if (subscriptionsToCancel == null) {
                        cancel = true;
                    } else {
                        subscriptionsToCancel.add(subscription);
                        cancel = false;
                    }
                }
                if (cancel) {
                    s.cancel();
                } else {
                    s.request(Long.MAX_VALUE);
                }
            }

            @Override
            public void onNext(Object v) {
                value = v;
            }

            @Override
            public void onError(Throwable t) {
                fn.accept(null, t);
            }

            @Override
            public void onComplete() {
                fn.accept(value, null);
            }
        });
    }

    @Override
    public void completeTo(CompletableFuture<Object> completableFuture) {
        if (value instanceof Fuseable.ScalarCallable callable) {
            Object value;
            try {
                value = callable.call();
            } catch (Exception e) {
                completableFuture.completeExceptionally(e);
                return;
            }
            completableFuture.complete(value);
            return;
        }
        value.subscribe(new CoreSubscriber<>() {

            Subscription subscription;
            Object value;

            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Object v) {
                value = v;
            }

            @Override
            public void onError(Throwable t) {
                completableFuture.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                completableFuture.complete(value);
            }
        });
    }

    @Nullable
    @Override
    public ImperativeExecutionFlow<Object> tryComplete() {
        if (value instanceof Fuseable.ScalarCallable<?> callable) {
            try {
                return (ImperativeExecutionFlow<Object>) ExecutionFlow.<Object>just(callable.call());
            } catch (Exception e) {
                return (ImperativeExecutionFlow<Object>) ExecutionFlow.error(e);
            }
        }
        return null;
    }

    static <R> Mono<Object> toMono(ExecutionFlow<R> next) {
        if (next instanceof ReactorExecutionFlowImpl reactiveFlowImpl) {
            return reactiveFlowImpl.value;
        }
        ImperativeExecutionFlow<?> imperativeFlow = next.tryComplete();
        if (imperativeFlow != null) {
            Mono<Object> m;
            if (imperativeFlow.getError() != null) {
                m = Mono.error(imperativeFlow.getError());
            } else if (imperativeFlow.getValue() != null) {
                m = Mono.just(imperativeFlow.getValue());
            } else {
                m = Mono.empty();
            }
            Map<String, Object> context = imperativeFlow.getContext();
            if (!context.isEmpty()) {
                m = m.contextWrite(ctx -> {
                    for (Map.Entry<String, Object> e : context.entrySet()) {
                        ctx = ctx.put(e.getKey(), e.getValue());
                    }
                    return ctx;
                });
            }
            return m;
        }
        return new FlowAsMono<>(next);
    }

    static <R> Mono<Object> toMono(Supplier<ExecutionFlow<R>> next) {
        return Mono.defer(() -> toMono(next.get()));
    }

    @Override
    public Publisher<Object> toPublisher() {
        return value;
    }

    @Override
    public CompletableFuture<Object> toCompletableFuture() {
        return value.toFuture();
    }
}
