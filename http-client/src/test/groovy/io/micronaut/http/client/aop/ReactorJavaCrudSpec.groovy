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
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.*
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicLong

/**
 * @author graemerocher
 * @since 1.0
 */
class ReactorJavaCrudSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test it is possible to implement CRUD operations with Reactor"() {
        given:
        BookClient client = context.getBean(BookClient)

        when:
        Book book = client.get(99)
                .onErrorResume(t -> { Mono.empty()})
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


        when:'the full response is resolved'
        HttpResponse<Book> bookAndResponse = client.getResponse(book.id).block()

        then:"The response is valid"
        bookAndResponse.status() == HttpStatus.OK
        bookAndResponse.body().title == "The Stand"

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
                .onErrorResume(t -> { Mono.empty()})
                .block()
        then:
        book == null
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/2905')
    void "test maybe of number"() {
        given:
        BookClient client = context.getBean(BookClient)

        expect:
        client.getPrice("good").block() == 10
        client.getPrice("empty").onErrorResume(throwable -> {
            if (throwable instanceof HttpClientResponseException) {
                return Mono.empty()
            }
            throw throwable
        }).block() == null
    }

    @Client('/rxjava/books')
    static interface BookClient extends BookApi, PriceApi {
    }

    @Controller("/rxjava/books")
    static class BookController implements BookApi, PriceApi {

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
        Mono<HttpResponse<Book>> getResponse(Long id) {
            Book book = books.get(id)
            if(book) {
                return Mono.just(HttpResponse.ok(book))
            }
            return Mono.just(HttpResponse.notFound())
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

        @Override
        Mono<Integer> getPrice(String title) {
            if (title == 'empty') {
                return Mono.empty()
            }
            return Mono.just(10)
        }
    }

    static interface BookApi {

        @Get("/{id}")
        Mono<Book> get(Long id)

        @Get("/res/{id}")
        Mono<HttpResponse<Book>> getResponse(Long id)

        @Get
        Mono<List<Book>> list()

        @Delete("/{id}")
        Mono<Book> delete(Long id)

        @Post
        Mono<Book> save(String title)

        @Patch("/{id}")
        Mono<Book> update(Long id, String title)

    }

    static interface PriceApi {
        @Get(uri = "/price/{title}")
        Mono<Integer> getPrice(@PathVariable String title)
    }


    static class Book {
        Long id
        String title
    }
}

