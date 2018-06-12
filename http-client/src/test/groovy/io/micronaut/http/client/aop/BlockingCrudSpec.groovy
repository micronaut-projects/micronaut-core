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

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

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
    @AutoCleanup
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
        List<Book> books = client.list()

        then:
        book == null
        books.size() == 0

        when:
        book = client.save("The Stand")

        then:
        book != null
        book.title == "The Stand"
        book.id == 1

        when:
        book = client.get(book.id)

        then:
        book != null
        book.title == "The Stand"
        book.id == 1

        when:'the full response is resolved'
        HttpResponse<Book> bookAndResponse = client.getResponse(book.id)

        then:"The response is valid"
        bookAndResponse.status() == HttpStatus.OK
        bookAndResponse.body().title == "The Stand"


        when:
        book = client.update(book.id, "The Shining")

        then:
        book != null
        book.title == "The Shining"
        book.id == 1

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

        @Get("/res/{id}")
        HttpResponse<Book> getResponse(Long id)

        @Get('/')
        List<Book> list()

        @Delete("/{id}")
        void delete(Long id)

        @Post('/')
        Book save(String title)

        @Patch("/{id}")
        Book update(Long id, String title)
    }


    static class Book {
        Long id
        String title
    }
}
