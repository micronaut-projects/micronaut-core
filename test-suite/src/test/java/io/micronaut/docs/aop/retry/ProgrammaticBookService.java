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
package io.micronaut.docs.aop.retry;

import io.micronaut.retry.CircuitBreakerOperations;
import io.micronaut.retry.CircuitBreakerOperationsFactory;
import io.micronaut.retry.CircuitBreakerPolicy;
import io.micronaut.retry.RetryOperations;
import io.micronaut.retry.RetryOperationsFactory;
import io.micronaut.retry.RetryPolicy;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class ProgrammaticBookService {

    private final RetryOperations retryOperations;
    private final CircuitBreakerOperations circuitBreakerOperations;
    private final AtomicInteger syncCounter = new AtomicInteger();
    private final AtomicInteger reactiveCounter = new AtomicInteger();
    private final AtomicInteger asyncCounter = new AtomicInteger();
    private final AtomicInteger circuitCounter = new AtomicInteger();

    public ProgrammaticBookService(RetryOperationsFactory retryOperationsFactory,
                                   CircuitBreakerOperationsFactory circuitBreakerOperationsFactory) {
        // tag::programmatic-policy[]
        RetryPolicy retryPolicy = RetryPolicy.builder()
            .maxAttempts(5)
            .delay(Duration.ofMillis(5))
            .build();
        CircuitBreakerPolicy circuitBreakerPolicy = CircuitBreakerPolicy.builder()
            .maxAttempts(3)
            .delay(Duration.ofMillis(5))
            .resetTimeout(Duration.ofMillis(100))
            .build();
        // end::programmatic-policy[]
        this.retryOperations = retryOperationsFactory.createRetryOperations(retryPolicy);
        this.circuitBreakerOperations = circuitBreakerOperationsFactory.createCircuitBreakerOperations(circuitBreakerPolicy);
    }

    public void reset() {
        syncCounter.set(0);
        reactiveCounter.set(0);
        asyncCounter.set(0);
        circuitCounter.set(0);
    }

    // tag::programmatic-sync[]
    public List<Book> listBooks() {
        return retryOperations.execute(() -> {
            if (syncCounter.incrementAndGet() < 3) {
                throw new IllegalStateException("Temporary failure");
            }
            return Collections.singletonList(new Book("The Stand"));
        });
    }
    // end::programmatic-sync[]

    // tag::programmatic-reactive[]
    public Publisher<Book> streamBooks() {
        return retryOperations.executePublisher(() -> Flux.defer(() -> {
            if (reactiveCounter.incrementAndGet() < 3) {
                return Flux.error(new IllegalStateException("Temporary failure"));
            }
            return Flux.just(new Book("The Stand"));
        }));
    }
    // end::programmatic-reactive[]

    // tag::programmatic-async[]
    public CompletionStage<Book> findBook(String title) {
        return retryOperations.executeCompletionStage(() -> CompletableFuture.supplyAsync(() -> {
            if (asyncCounter.incrementAndGet() < 3) {
                throw new IllegalStateException("Temporary failure");
            }
            return new Book(title);
        }));
    }
    // end::programmatic-async[]

    // tag::programmatic-circuit[]
    public Book findBookWithCircuitBreaker(String title) {
        return circuitBreakerOperations.execute(() -> {
            if (circuitCounter.incrementAndGet() < 4) {
                throw new IllegalStateException("Circuit failure");
            }
            return new Book(title);
        });
    }
    // end::programmatic-circuit[]
}
