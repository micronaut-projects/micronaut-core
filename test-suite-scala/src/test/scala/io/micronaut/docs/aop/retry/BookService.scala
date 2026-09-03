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

import io.micronaut.retry.annotation.CircuitBreaker
import io.micronaut.retry.annotation.Retryable
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

class BookService:

  // tag::simple[]
  @Retryable
  def listBooks(): List[Book] =
    // ...
  // end::simple[]
    List(Book("The Stand"))

  // tag::circuit[]
  @CircuitBreaker(reset = "30s")
  def findBooks(): List[Book] =
    // ...
  // end::circuit[]
    List(Book("The Stand"))

  // tag::attempts[]
  @Retryable(attempts = "5",
             delay = "2s")
  def findBook(title: String): Book =
    // ...
  // end::attempts[]
    Book(title)

  // tag::config[]
  @Retryable(attempts = "${book.retry.attempts:3}",
             delay = "${book.retry.delay:1s}")
  def getBook(title: String): Book =
    // ...
  // end::config[]
    Book(title)

  // tag::reactive[]
  @Retryable
  def streamBooks(): Publisher[Book] =
    // ...
  // end::reactive[]
    Flux.just(Book("The Stand"))
