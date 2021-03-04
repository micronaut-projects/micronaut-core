/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.reactivex.Flowable

open class BookService {

    // tag::simple[]
    @Retryable
    open fun listBooks(): List<Book> {
        // ...
        // end::simple[]
        return listOf(Book("The Stand"))
    }

    // tag::circuit[]
    @CircuitBreaker(reset = "30s")
    open fun findBooks(): List<Book> {
        // ...
        // end::circuit[]
        return listOf(Book("The Stand"))
    }

    // tag::attempts[]
    @Retryable(attempts = "5",
               delay = "2s")
    open fun findBook(title: String): Book {
        // ...
        // end::attempts[]
        return Book(title)
    }

    // tag::config[]
    @Retryable(attempts = "\${book.retry.attempts:3}",
               delay = "\${book.retry.delay:1s}")
    open fun getBook(title: String): Book {
        // ...
        // end::config[]
        return Book(title)
    }

    // tag::reactive[]
    @Retryable
    open fun streamBooks(): Flowable<Book> {
        // ...
        // end::reactive[]
        return Flowable.just(
                Book("The Stand")
        )
    }
}
