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
package io.micronaut.docs.aop.retry;

import io.micronaut.context.annotation.Requires;
import io.micronaut.retry.annotation.Retryable;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
@Requires(property = "spec.name", value = "NamedRetrySpec")
public class NamedRetryBookService {

    private final AtomicInteger calls = new AtomicInteger();

    // tag::named[]
    @Retryable(name = "books") // <1>
    public Book findBook(String title) {
        // ...
    // end::named[]
        return failTwice(title);
    }

    // tag::override[]
    @Retryable(name = "books", attempts = "1") // <1>
    public Book getBook(String title) {
        // ...
    // end::override[]
        return failTwice(title);
    }

    int reset() {
        return calls.getAndSet(0);
    }

    private Book failTwice(String title) {
        if (calls.incrementAndGet() < 3) {
            throw new UncheckedIOException(new IOException("unavailable"));
        }
        return new Book(title);
    }
}
