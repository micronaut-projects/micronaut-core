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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A Java contract with Reactor and future return types implemented by Python classes in the tests.
 */
public interface ReactiveFinder {

    Mono<String> find(String id);

    Flux<String> findAll();

    CompletableFuture<String> findFuture(String id);

    CompletionStage<String> findStage(String id);
}
