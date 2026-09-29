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
package io.micronaut.python.annotation.processing.test.reactive;

import io.micronaut.core.async.propagation.ReactorPropagation;
import java.util.function.Function;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A Java service whose publishers read the Reactor context of their subscriber, the way a reactive
 * transaction manager reads the transaction status. The Python tests instantiate it or expose it
 * through a Python factory.
 */
public class ContextualService {

    public static final String KEY = "tx";

    public static final String NONE = "none";

    /**
     * @return The {@code tx} entry of the subscriber's Reactor context
     */
    public Mono<String> value() {
        return Mono.deferContextual(ctx -> Mono.just(ctx.getOrDefault(KEY, NONE)));
    }

    /**
     * @return The name of the {@link TransactionElement} propagated through the subscriber's Reactor context
     */
    public Mono<String> element() {
        return Mono.deferContextual(ctx -> Mono.just(ReactorPropagation.findContextElement(ctx, TransactionElement.class)
            .map(TransactionElement::name)
            .orElse(NONE)));
    }

    /**
     * Runs the handler with the given transaction in the Reactor context, the way
     * {@code ReactiveTransactionOperations.withTransaction} does.
     *
     * @param tx The transaction name
     * @param handler The handler
     * @param <T> The item type
     * @return The handler's publisher
     */
    public <T> Flux<T> withTransaction(String tx, Function<String, Publisher<T>> handler) {
        return Flux.just(tx)
            .flatMap(handler)
            .contextWrite(ctx -> ReactorPropagation.addContextElement(ctx.put(KEY, tx), new TransactionElement(tx)));
    }
}
