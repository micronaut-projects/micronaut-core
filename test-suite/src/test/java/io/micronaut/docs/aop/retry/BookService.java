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

import io.micronaut.retry.annotation.CircuitBreaker;
import io.micronaut.retry.annotation.Retryable;
import io.reactivex.Flowable;

import java.util.Collections;
import java.util.List;

public class BookService {

    // tag::simple[]
    @Retryable
    public List<Book> listBooks() {
        // ...
    // end::simple[]
        return Collections.singletonList(
                new Book("The Stand")
        );
    }

    // tag::circuit[]
    @CircuitBreaker(reset = "30s")
    public List<Book> findBooks() {
        // ...
    // end::circuit[]
        return Collections.singletonList(
                new Book("The Stand")
        );
    }

    // tag::attempts[]
    @Retryable( attempts = "5",
                delay = "2s" )
    public Book findBook(String title) {
        // ...
    // end::attempts[]
        return new Book(title);
    }


    // tag::config[]
    @Retryable( attempts = "${book.retry.attempts:3}",
                delay = "${book.retry.delay:1s}" )
    public Book getBook(String title) {
        // ...
    // end::config[]
        return new Book(title);
    }

    // tag::reactive[]
    @Retryable
    public Flowable<Book> streamBooks() {
        // ...
    // end::reactive[]
        return Flowable.just(
                new Book("The Stand")
        );
    }
}
