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
package io.micronaut.core.async.flow;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.flow.CompletableFutureFlow;
import io.micronaut.core.flow.Flow;
import io.micronaut.core.flow.ImperativeFlow;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Mono;

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
final class ReactiveFlowImpl implements ReactiveFlow<Object> {

    private Mono<Object> value;

    public <K> ReactiveFlowImpl(Publisher<K> value) {
        this(Mono.from(value));
    }

    public <K>  ReactiveFlowImpl(Mono<K> value) {
        this.value = (Mono<Object>) value;
    }

    @Override
    public <R> Flow<R> flatMap(Function<? super Object, ? extends Flow<? extends R>> transformer) {
        value = value.flatMap(value -> toMono(transformer.apply(value)));
        return (Flow<R>) this;
    }

    @Override
    public <R> Flow<R> then(Supplier<? extends Flow<? extends R>> supplier) {
        value = value.then(Mono.fromSupplier(supplier).flatMap(ReactiveFlowImpl::toMono));
        return (Flow<R>) this;
    }

    @Override
    public <R> Flow<R> map(Function<? super Object, ? extends R> function) {
        value = value.map(function);
        return (Flow<R>) this;
    }

    @Override
    public Flow<Object> onErrorResume(Function<? super Throwable, ? extends Flow<?>> fallback) {
        value = value.onErrorResume(throwable -> toMono(fallback.apply(throwable)));
        return this;
    }

    @Override
    public Flow<Object> putInContext(String key, Object value) {
        this.value = this.value.contextWrite(context -> context.put(key, value));
        return this;
    }

    @Override
    public void onComplete(BiConsumer<? super Object, Throwable> fn) {
        value.subscribe(new Subscriber<>() {

            Subscription subscription;
            final AtomicReference<Object> value = new AtomicReference<>();

            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                s.request(1);
            }

            @Override
            public void onNext(Object v) {
                subscription.request(1); // ???
                value.set(v);
            }

            @Override
            public void onError(Throwable t) {
                fn.accept(null, t);
            }

            @Override
            public void onComplete() {
                fn.accept(value.get(), null);
            }
        });
    }

    static <R> Mono<Object> toMono(Flow<R> next) {
        if (next instanceof ReactiveFlowImpl reactiveFlowImpl) {
            return reactiveFlowImpl.value;
        } else if (next instanceof CompletableFutureFlow<?> completableFutureFlow) {
            return Mono.fromCompletionStage(completableFutureFlow.toCompletableFuture());
        } else if (next instanceof ImperativeFlow<?> imperativeFlow) {
            Mono<Object> m;
            if (imperativeFlow.getError() != null) {
                m = Mono.error(imperativeFlow.getError());
            } else if (imperativeFlow.getValue() != null) {
                m = Mono.just(imperativeFlow.getValue());
            } else {
                m = Mono.empty();
            }
            if (imperativeFlow.getContext() != null) {
                m = m.contextWrite(context -> {
                    for (Map.Entry<String, Object> e : imperativeFlow.getContext().entrySet()) {
                        context = context.put(e.getKey(), e.getValue());
                    }
                    return context;
                });
            }
            return m;
        } else {
            throw new IllegalStateException();
        }
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
