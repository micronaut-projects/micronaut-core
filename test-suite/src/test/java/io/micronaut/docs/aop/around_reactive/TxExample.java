/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.aop.around_reactive;

// tag::example[]

import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class TxExample {

    private final TxManager txManager;

    public TxExample(TxManager txManager) {
        this.txManager = txManager;
    }

    @Tx
    Mono<String> doWorkMono(String taskName) {
        return Mono.deferContextual(contextView -> {
            String txName = txManager.findTx(contextView).get();
            return Mono.just("Doing job: " + taskName + " in transaction: " + txName);
        });
    }

    @Tx
    Flux<String> doWorkFlux(String taskName) {
        return Flux.deferContextual(contextView -> {
            String txName = txManager.findTx(contextView).get();
            return Mono.just("Doing job: " + taskName + " in transaction: " + txName);
        });
    }
}
// end::example[]
