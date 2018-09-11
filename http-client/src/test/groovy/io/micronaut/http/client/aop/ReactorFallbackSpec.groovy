/*
 * Copyright 2017-2018 original authors
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
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.Client
import io.micronaut.retry.annotation.Fallback
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicLong

/**
 * @author graemerocher
 * @since 1.0
 */
class ReactorFallbackSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test that fallbacks are called for Reactor responses"() {
        given:
        BookClient client = context.getBean(BookClient)

        when:
        Book book = client.get(99)
                .block()
        List<Book> books = client.list().block()
        List<Book> stream = client.stream().toIterable().toList()

        then:
        book.title == "Fallback Book"
        books.size() == 0
        stream.size() == 1
        stream.first().title == "Fallback Book"

        when:
        book = client.save("The Stand").block()

        then:
        book != null
        book.title == "Fallback Book"
        book.id == null

        when:
        book = client.get(1).block()

        then:
        book != null
        book.title == "Fallback Book"
        book.id == null

        when:
        book = client.update(1, "The Shining").block()

        then:
        book != null
        book.title == "Fallback Book"
        book.id == null

        when:
        book = client.delete(1).block()

        then:
        book == null

        when:
        book = client.get(1)
                .block()
        then:
        book.title == "Fallback Book"
    }


    @Client('/reactor/fallback/books')
    static interface BookClient extends BookApi {
    }

    @Fallback
    static class BookFallback implements BookApi {

        @Override
        Mono<Book> get(Long id) {
            return Mono.just(new Book(title: "Fallback Book"))
        }

        @Override
        Mono<List<Book>> list() {
            return Mono.just([])
        }

        @Override
        Flux<Book> stream() {
            Flux.just(new Book(title: "Fallback Book"))
        }

        @Override
        Mono<Book> delete(Long id) {
            return Mono.empty()
        }

        @Override
        Mono<Book> save(String title) {
            return Mono.just(new Book(title: "Fallback Book"))
        }

        @Override
        Mono<Book> update(Long id, String title) {
            return Mono.just(new Book(title: "Fallback Book"))
        }
    }

    @Controller("/reactor/fallback/books")
    static class BookController implements BookApi {

        Map<Long, Book> books = new LinkedHashMap<>()
        AtomicLong currentId = new AtomicLong(0)

        @Override
        Mono<Book> get(Long id) {
            Mono.error(new RuntimeException("bad"))
        }

        @Override
        Mono<List<Book>> list() {
            Mono.error(new RuntimeException("bad"))
        }

        @Override
        Flux<Book> stream() {
            Flux.error(new RuntimeException("bad"))
        }

        @Override
        Mono<Book> delete(Long id) {
            Mono.error(new RuntimeException("bad"))
        }

        @Override
        Mono<Book> save(String title) {
            Mono.error(new RuntimeException("bad"))
        }

        @Override
        Mono<Book> update(Long id, String title) {
            Mono.error(new RuntimeException("bad"))
        }
    }

    static interface BookApi {

        @Get("/{id}")
        Mono<Book> get(Long id)

        @Get
        Mono<List<Book>> list()

        @Get('/stream')
        Flux<Book> stream()

        @Delete("/{id}")
        Mono<Book> delete(Long id)

        @Post
        Mono<Book> save(String title)

        @Patch("/{id}")
        Mono<Book> update(Long id, String title)
    }


    static class Book {
        Long id
        String title
    }
}
