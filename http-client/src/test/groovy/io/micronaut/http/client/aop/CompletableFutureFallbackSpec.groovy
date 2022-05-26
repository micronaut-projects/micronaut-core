/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.*
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Fallback
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong

/**
 * @author graemerocher
 * @since 1.0
 */
class CompletableFutureFallbackSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test that fallbacks are called for CompletableFuture responses"() {
        given:
        BookClient client = context.getBean(BookClient)

        when:
        Book book = client.get(99)
                .get()
        List<Book> books = client.list().get()

        then:
        book.title == "Fallback Book"
        books.size() == 0

        when:
        book = client.save("The Stand").get()

        then:
        book != null
        book.title == "Fallback Book"
        book.id == null

        when:
        book = client.get(1).get()

        then:
        book != null
        book.title == "Fallback Book"
        book.id == null

        when:
        book = client.update(1, "The Shining").get()

        then:
        book != null
        book.title == "Fallback Book"
        book.id == null

        when:
        book = client.delete(1).get()

        then:
        book == null

        when:
        book = client.get(1)
                .get()
        then:
        book.title == "Fallback Book"
    }


    @Client('/future/fallback/books')
    static interface BookClient extends BookApi {
    }

    @Fallback
    static class BookFallback implements BookApi {

        @Override
        CompletableFuture<Book> get(Long id) {
            return CompletableFuture.completedFuture(new Book(title: "Fallback Book"))
        }

        @Override
        CompletableFuture<List<Book>> list() {
            return CompletableFuture.completedFuture([])
        }

        @Override
        CompletableFuture<Book> delete(Long id) {
            return CompletableFuture.completedFuture(null)
        }

        @Override
        CompletableFuture<Book> save(String title) {
            return CompletableFuture.completedFuture(new Book(title: "Fallback Book"))
        }

        @Override
        CompletableFuture<Book> update(Long id, String title) {
            return CompletableFuture.completedFuture(new Book(title: "Fallback Book"))
        }
    }

    @Controller("/future/fallback/books")
    static class BookController implements BookApi {

        Map<Long, Book> books = new LinkedHashMap<>()
        AtomicLong currentId = new AtomicLong(0)

        @Override
        CompletableFuture<Book> get(Long id) {
            CompletableFuture f = new CompletableFuture()
            f.completeExceptionally(new RuntimeException("bad"))
            return f
        }

        @Override
        CompletableFuture<List<Book>> list() {
            CompletableFuture f = new CompletableFuture()
            f.completeExceptionally(new RuntimeException("bad"))
            return f
        }

        @Override
        CompletableFuture<Book> delete(Long id) {
            CompletableFuture f = new CompletableFuture()
            f.completeExceptionally(new RuntimeException("bad"))
            return f
        }

        @Override
        CompletableFuture<Book> save(String title) {
            CompletableFuture f = new CompletableFuture()
            f.completeExceptionally(new RuntimeException("bad"))
            return f
        }

        @Override
        CompletableFuture<Book> update(Long id, String title) {
            CompletableFuture f = new CompletableFuture()
            f.completeExceptionally(new RuntimeException("bad"))
            return f
        }
    }

    static interface BookApi {

        @Get("/{id}")
        CompletableFuture<Book> get(Long id)

        @Get
        CompletableFuture<List<Book>> list()

        @Delete("/{id}")
        CompletableFuture<Book> delete(Long id)

        @Post
        CompletableFuture<Book> save(String title)

        @Patch("/{id}")
        CompletableFuture<Book> update(Long id, String title)
    }


    static class Book {
        Long id
        String title
    }
}

