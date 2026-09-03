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
package io.micronaut.docs.aop.retry

import io.micronaut.retry.CircuitBreakerOperations
import io.micronaut.retry.CircuitBreakerOperationsFactory
import io.micronaut.retry.CircuitBreakerPolicy
import io.micronaut.retry.RetryOperations
import io.micronaut.retry.RetryOperationsFactory
import io.micronaut.retry.RetryPolicy
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger

@Singleton
class ProgrammaticBookService(
    retryOperationsFactory: RetryOperationsFactory,
    circuitBreakerOperationsFactory: CircuitBreakerOperationsFactory
):

  // tag::programmatic-policy[]
  private val retryPolicy = RetryPolicy.builder()
    .maxAttempts(5)
    .delay(Duration.ofMillis(5))
    .build()
  private val circuitBreakerPolicy = CircuitBreakerPolicy.builder()
    .maxAttempts(3)
    .delay(Duration.ofMillis(5))
    .resetTimeout(Duration.ofMillis(100))
    .build()
  // end::programmatic-policy[]

  private val retryOperations: RetryOperations =
    retryOperationsFactory.createRetryOperations(retryPolicy)
  private val circuitBreakerOperations: CircuitBreakerOperations =
    circuitBreakerOperationsFactory.createCircuitBreakerOperations(circuitBreakerPolicy)
  private val syncCounter = AtomicInteger()
  private val reactiveCounter = AtomicInteger()
  private val asyncCounter = AtomicInteger()
  private val circuitCounter = AtomicInteger()

  def reset(): Unit =
    syncCounter.set(0)
    reactiveCounter.set(0)
    asyncCounter.set(0)
    circuitCounter.set(0)

  // tag::programmatic-sync[]
  def listBooks(): List[Book] =
    retryOperations.execute(() =>
      if syncCounter.incrementAndGet() < 3 then
        throw IllegalStateException("Temporary failure")
      List(Book("The Stand"))
    )
  // end::programmatic-sync[]

  // tag::programmatic-reactive[]
  def streamBooks(): Publisher[Book] =
    retryOperations.executePublisher(() =>
      Flux.defer(() =>
        if reactiveCounter.incrementAndGet() < 3 then
          Flux.error(IllegalStateException("Temporary failure"))
        else
          Flux.just(Book("The Stand"))
      )
    )
  // end::programmatic-reactive[]

  // tag::programmatic-async[]
  def findBook(title: String): CompletionStage[Book] =
    retryOperations.executeCompletionStage(() =>
      CompletableFuture.supplyAsync(() =>
        if asyncCounter.incrementAndGet() < 3 then
          throw IllegalStateException("Temporary failure")
        Book(title)
      )
    )
  // end::programmatic-async[]

  // tag::programmatic-circuit[]
  def findBookWithCircuitBreaker(title: String): Book =
    circuitBreakerOperations.execute(() =>
      if circuitCounter.incrementAndGet() < 4 then
        throw IllegalStateException("Circuit failure")
      Book(title)
    )
  // end::programmatic-circuit[]
