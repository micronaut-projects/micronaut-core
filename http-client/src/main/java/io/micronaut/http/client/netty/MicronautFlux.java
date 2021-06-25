/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxOperator;
import reactor.util.context.Context;

import java.util.function.Consumer;

/**
 * Custom extensions to project reactor.
 *
 * @author James Kleeh
 * @since 3.0.0
 * @param <T> The stream type
 */
@Internal
class MicronautFlux<T> extends Flux<T> {

    private final Flux<T> flux;

    /**
     *
     * @param publisher Reactive Sequence
     */
    MicronautFlux(Flux<T> publisher) {
        super();
        this.flux = publisher;
    }

    @Override
    public void subscribe(CoreSubscriber<? super T> actual) {
        flux.subscribe(actual);
    }

    /**
     * Calls the specified Consumer with the current item after this item has been emitted to the downstream.
     * @param afterNext current item
     * @return Reactive sequence
     */
    public Flux<T> doAfterNext(Consumer<? super T> afterNext) {
        return onAssembly(new AfterNextOperator<>(flux, afterNext));
    }

    static class AfterNextOperator<T> extends FluxOperator<T, T> {

        private final Consumer<? super T> afterNext;

        /**
         * Build a {@link FluxOperator} wrapper around the passed parent {@link org.reactivestreams.Publisher}.
         *
         * @param source the {@link org.reactivestreams.Publisher} to decorate
         * @param afterNext Consumer with the current item after this item has been emitted to the downstream.
         */
        protected AfterNextOperator(Flux<? extends T> source, Consumer<? super T> afterNext) {
            super(source);
            this.afterNext = afterNext;
        }

        @Override
        public void subscribe(CoreSubscriber<? super T> actual) {
            source.subscribe(new CoreSubscriber<T>() {
                @Override
                public Context currentContext() {
                    return actual.currentContext();
                }

                @Override
                public void onSubscribe(Subscription s) {
                    actual.onSubscribe(s);
                }

                @Override
                public void onNext(T t) {
                    actual.onNext(t);
                    afterNext.accept(t);
                }

                @Override
                public void onError(Throwable t) {
                    actual.onError(t);
                }

                @Override
                public void onComplete() {
                    actual.onComplete();
                }
            });
        }
    }
}

