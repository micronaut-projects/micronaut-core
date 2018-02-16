/*
 * Copyright 2018 original authors
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
package org.particleframework.http.client.aop

import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single
import org.particleframework.context.ApplicationContext
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Delete
import org.particleframework.http.annotation.Get
import org.particleframework.http.annotation.Patch
import org.particleframework.http.annotation.Post
import org.particleframework.http.client.Client
import org.particleframework.http.client.Fallback
import org.particleframework.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Singleton

/**
 * @author graemerocher
 * @since 1.0
 */
class RxJavaFallbackSpec extends Specification{
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test that fallbacks are called for RxJava responses"() {
        given:
        BookClient client = context.getBean(BookClient)

        when:
        Book book = client.get(99)
                .blockingGet()
        List<Book> books = client.list().blockingGet()

        List<Book> stream = Flowable.fromPublisher(client.stream()).toList().blockingGet()

        then:
        book.title == "Fallback Book"
        books.size() == 0
        stream.size() == 1
        stream.first().title == "Fallback Book"

        when:
        book = client.save("The Stand").blockingGet()

        then:
        book != null
        book.title == "Fallback Book"
        book.id == null

        when:
        book = client.get(1).blockingGet()

        then:
        book != null
        book.title == "Fallback Book"
        book.id == null

        when:
        book = client.update(1, "The Shining").blockingGet()

        then:
        book != null
        book.title == "Fallback Book"
        book.id == null

        when:
        book = client.delete(1).blockingGet()

        then:
        book == null

        when:
        book = client.get(1)
                .blockingGet()
        then:
        book.title == "Fallback Book"
    }


    @Client('/rxjava/fallback/books')
    static interface BookClient extends BookApi {
    }

    @Fallback
    static class BookFallback implements BookApi {

        @Override
        Maybe<Book> get(Long id) {
            return Maybe.just(new Book(title: "Fallback Book"))
        }

        @Override
        Single<List<Book>> list() {
            return Single.just([])
        }

        @Override
        Publisher<Book> stream() {
            return Flowable.fromArray(new Book(title: "Fallback Book"))
        }

        @Override
        Maybe<Book> delete(Long id) {
            return Maybe.empty()
        }

        @Override
        Single<Book> save(String title) {
            return Single.just(new Book(title: "Fallback Book"))
        }

        @Override
        Maybe<Book> update(Long id, String title) {
            return Maybe.just(new Book(title: "Fallback Book"))
        }
    }

    @Controller("/rxjava/fallback/books")
    @Singleton
    static class BookController implements BookApi {

        Map<Long, Book> books = new LinkedHashMap<>()

        @Override
        Maybe<Book> get(Long id) {
            Maybe.error(new RuntimeException("bad"))
        }

        @Override
        Single<List<Book>> list() {
            Single.error(new RuntimeException("bad"))
        }

        @Override
        Publisher<Book> stream() {
            Flowable.error(new RuntimeException("bad"))
        }

        @Override
        Maybe<Book> delete(Long id) {
            Maybe.error(new RuntimeException("bad"))
        }

        @Override
        Single<Book> save(String title) {
            Single.error(new RuntimeException("bad"))
        }

        @Override
        Maybe<Book> update(Long id, String title) {
            Maybe.error(new RuntimeException("bad"))
        }
    }

    static interface BookApi {

        @Get("/{id}")
        Maybe<Book> get(Long id)

        @Get('/')
        Single<List<Book>> list()

        @Get('/stream')
        Publisher<Book> stream()

        @Delete("/{id}")
        Maybe<Book> delete(Long id)

        @Post('/')
        Single<Book> save(String title)

        @Patch("/{id}")
        Maybe<Book> update(Long id, String title)
    }


    static class Book {
        Long id
        String title
    }
}
