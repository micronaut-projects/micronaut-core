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
package io.micronaut.retry;

import org.reactivestreams.Publisher;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Programmatic retry operations.
 *
 * @author graemerocher
 * @since 5.0.0
 */
public interface RetryOperations {

    /**
     * Executes synchronous work with retry.
     *
     * @param supplier The supplier to invoke for each attempt
     * @param <T> The result type
     * @return The computed result
     */
    <T> T execute(Supplier<T> supplier);

    /**
     * Executes asynchronous work backed by a completion stage with retry.
     *
     * @param supplier The supplier that creates a new completion stage for each attempt
     * @param <T> The result type
     * @return A completion stage representing the retried execution
     */
    <T> CompletionStage<T> executeCompletionStage(Supplier<? extends CompletionStage<T>> supplier);

    /**
     * Executes reactive work backed by a publisher with retry.
     *
     * @param supplier The supplier that creates a new publisher for each attempt
     * @param <T> The emitted type
     * @return A publisher representing the retried execution
     */
    <T> Publisher<T> executePublisher(Supplier<? extends Publisher<T>> supplier);
}
