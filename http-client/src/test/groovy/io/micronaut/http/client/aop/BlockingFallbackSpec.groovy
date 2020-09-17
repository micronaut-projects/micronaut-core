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
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Fallback
import io.micronaut.retry.annotation.Recoverable
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicLong

/**
 * @author graemerocher
 * @since 1.0
 */
class BlockingFallbackSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test that fallback is called when an exception happens invoking the server"() {
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
        book.id != null

        when:
        client.delete(book.id)

        then:
        client.get(book.id) == null
    }

    @Client('/blocking/fallback/books')
    static interface BookClient extends BookApi {
        @Override
        @Recoverable(api = BookApi)
        Book get(Long id)
    }

    @Fallback
    static class BookFallback implements BookApi {

        Map<Long, Book> books = new LinkedHashMap<>()
        AtomicLong currentId = new AtomicLong(0)

        @Override
        Book get(Long id) {
            return books.get(id)
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

    @Controller("/blocking/fallback/books")
    static class BookController implements BookApi {

        @Override
        Book get(Long id) {
            throw new RuntimeException("bad")
        }

        @Override
        List<Book> list() {
            throw new RuntimeException("bad")
        }

        @Override
        void delete(Long id) {
            throw new RuntimeException("bad")
        }

        @Override
        Book save(String title) {
            throw new RuntimeException("bad")
        }

        @Override
        Book update(Long id, String title) {
            throw new RuntimeException("bad")
        }
    }

    static interface BookApi {

        @Get("/{id}")
        Book get(Long id)

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
}
