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

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A Java contract implemented by Python classes whose publishers must run in the subscriber's context.
 */
public interface ContextualFinder {

    /**
     * @return {@code outer/inner} where both are read from the Reactor context by nested publishers
     */
    Mono<String> nested();

    /**
     * @return The same as {@link #nested()} for the propagated context element
     */
    Mono<String> nestedElement();

    /**
     * @return The same as {@link #nested()} as a plain publisher
     */
    Publisher<String> nestedPublisher();

    /**
     * @return The same as {@link #nested()} as a Flux
     */
    Flux<String> nestedMany();
}
