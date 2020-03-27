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

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Retryable
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import io.reactivex.Single
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.util.concurrent.atomic.AtomicLong

/**
 * TODO: Javadoc description
 *
 * @author graemerocher
 * @since 1.0
 */
class BlockingCrudSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test configured client"() {
        given:
        ApplicationContext anotherContext = ApplicationContext.run(
                'book.service.uri':"${embeddedServer.URL}/blocking"
        )
        ConfiguredBookClient bookClient = anotherContext.getBean(ConfiguredBookClient)

        expect:
        bookClient.list().size() == 0
    }

    void "test CRUD operations on generated client that returns blocking responses"() {
        given:
        BookClient client = context.getBean(BookClient)

        when:
        Book book = client.get(99)
        Optional<Book> opt = client.getOptional(99)
        List<Book> books = client.list()

        then:
        book == null
        !opt.isPresent()
        books.size() == 0

        when:
        book = client.save("The Stand")

        then:
        book != null
        book.title == "The Stand"
        book.id == 1

        when:
        book = client.get(book.id)
        opt = client.getOptional(book.id)

        then:
        book != null
        book.title == "The Stand"
        book.id == 1
        opt.isPresent()
        opt.get().title == book.title

        when:'the full response is resolved'
        HttpResponse<Book> bookAndResponse = client.getResponse(book.id)

        then:"The response is valid"
        bookAndResponse.status() == HttpStatus.OK
        bookAndResponse.body().title == "The Stand"

        when:'the full response returns 404'
        bookAndResponse = client.getResponse(-1)

        then:
        noExceptionThrown()
        bookAndResponse.status() == HttpStatus.NOT_FOUND

        when:
        book = client.update(book.id, "The Shining")
        books = client.list()

        then:
        book != null
        book.title == "The Shining"
        book.id == 1
        books.size() == 1
        books.first() instanceof Book

        when:
        client.delete(book.id)

        then:
        client.get(book.id) == null
    }

    void "test DELETE operation with null values"() {
        given:
        BookClient client = context.getBean(BookClient)

        when:
        client.delete(null)

        then:
        thrown(IllegalArgumentException)
    }

    void "test POST operation with null values"() {
        given:
        BookClient client = context.getBean(BookClient)

        when:
        Book book = client.save(null)

        then:
        thrown(IllegalArgumentException)
    }

    void "test PUT operation with null values"() {
        given:
        BookClient client = context.getBean(BookClient)

        when:
        Book book = client.update(5, null)

        then:
        thrown(IllegalArgumentException)
    }

    void "test GET operation with null values"() {
        given:
        BookClient client = context.getBean(BookClient)

        when:
        Book book = client.get(null)

        then:
        thrown(IllegalArgumentException)
    }

    void "test a declarative client void method and 404 response"() {
        given:
        VoidNotFoundClient client = context.getBean(VoidNotFoundClient)

        when:
        client.call()

        then:
        noExceptionThrown()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/2959')
    void "test annotation stereotype"() {
        given:
        def client = context.getBean(StereotypeClient)

        expect:
        client.list().size() == 0
    }

    @Client('/blocking/books')
    static interface BookClient extends BookApi {
    }

    @Client('${book.service.uri}/books')
    static interface ConfiguredBookClient extends BookApi {
    }

    @Controller("/blocking/books")
    static class BookController implements BookApi {

        Map<Long, Book> books = new LinkedHashMap<>()
        AtomicLong currentId = new AtomicLong(0)

        @Override
        Book get(Long id) {
            return books.get(id)
        }

        @Override
        Optional<Book> getOptional(Long id) {
            return Optional.ofNullable(get(id))
        }

        @Override
        HttpResponse<Book> getResponse(Long id) {
            def book = books.get(id)
            if(book) {
                return HttpResponse.ok(book)
            }
            return HttpResponse.notFound()
        }

        @Override
        List<Book> list() {
            return books.values().toList()
        }

        @Override
        void delete(Long id) {
            books.remove(id)
        }

        @Override
        Book save(String title) {
            Book book = new Book(title: title, id:currentId.incrementAndGet())
            books[book.id] = book
            return book
        }

        @Override
        Book update(Long id, String title) {
            Book book = books[id]
            if(book != null) {
                book.title = title
            }
            return book
        }
    }

    static interface BookApi {

        @Get("/{id}")
        Book get(Long id)

        @Get("/optional/{id}")
        Optional<Book> getOptional(Long id)

        @Get("/res/{id}")
        HttpResponse<Book> getResponse(Long id)

        @Get
        List<Book> list()

        @Delete("/{id}")
        void delete(Long id)

        @Post
        Book save(String title)

        @Patch("/{id}")
        Book update(Long id, String title)
    }


    static class Book {
        Long id
        String title
    }

    @Client("/void/404")
    static interface VoidNotFoundClient {

        @Get
        void call()
    }


    @BookRetryClient
    static interface StereotypeClient extends BookApi {
    }


}
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Client("/blocking/books")
@Retryable
@interface BookRetryClient {
    // ...
}