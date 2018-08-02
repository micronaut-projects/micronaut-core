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
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicLong

/**
 * @author graemerocher
 * @since 1.0
 */
class ReactorCrudSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test it is possible to implement CRUD operations with Reactor"() {
        given:
        BookClient client = context.getBean(BookClient)

        when:
        Book book = client.get(99)
                          .block()
        List<Book> books = client.list().block()

        then:
        book == null
        books.size() == 0

        when:
        book = client.save("The Stand").block()

        then:
        book != null
        book.title == "The Stand"
        book.id == 1

        when:
        book = client.get(book.id).block()

        then:
        book != null
        book.title == "The Stand"
        book.id == 1

        when:
        book = client.update(book.id, "The Shining").block()

        then:
        book != null
        book.title == "The Shining"
        book.id == 1

        when:
        book = client.delete(book.id).block()

        then:
        book != null

        when:
        book = client.get(book.id)
                .block()
        then:
        book == null
    }


    @Client('/reactor/books')
    static interface BookClient extends BookApi {
    }

    @Controller("/reactor/books")
    static class BookController implements BookApi {

        Map<Long, Book> books = new LinkedHashMap<>()
        AtomicLong currentId = new AtomicLong(0)

        @Override
        Mono<Book> get(Long id) {
            Book book = books.get(id)
            if(book)
                return Mono.just(book)
            Mono.empty()
        }

        @Override
        Mono<List<Book>> list() {
            return Mono.just(books.values().toList())
        }

        @Override
        Mono<Book> delete(Long id) {
            Book book = books.remove(id)
            if(book) {
                return Mono.just(book)
            }
            return Mono.empty()
        }

        @Override
        Mono<Book> save(String title) {
            Book book = new Book(title: title, id:currentId.incrementAndGet())
            books[book.id] = book
            return Mono.just(book)
        }

        @Override
        Mono<Book> update(Long id, String title) {
            Book book = books[id]
            if(book != null) {
                book.title = title
                return Mono.just(book)
            }
            else {
                return Mono.empty()
            }
        }
    }

    static interface BookApi {

        @Get("/{id}")
        Mono<Book> get(Long id)

        @Get
        Mono<List<Book>> list()

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


