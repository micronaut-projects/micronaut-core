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
package io.micronaut.http.client.rxjava2

import io.micronaut.context.ApplicationContext
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ClientStreamSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared BookClient bookClient = embeddedServer.applicationContext.getBean(BookClient)

    void "test stream array of json objects"() {
        when:
        List<Book> books = bookClient.arrayStream().toList().blockingGet()

        then:
        books.size() == 2
        books[0].title == "The Stand"
        books[1].title == "The Shining"

    }

    void "test stream json stream of objects"() {
        when:
        List<Book> books = bookClient.jsonStream().toList().blockingGet()

        then:
        books.size() == 2
        books[0].title == "The Stand"
        books[1].title == "The Shining"

    }


    @Client('/rxjava/stream')
    static interface BookClient extends BookApi {

    }

    @Controller("/rxjava/stream")
    static class StreamController implements BookApi {

        @Override
        Flowable<Book> arrayStream() {
            return Flowable.just(
                    new Book(title: "The Stand"),
                    new Book(title: "The Shining"),
            )
        }

        @Override
        Flowable<Book> jsonStream() {
            return Flowable.just(
                    new Book(title: "The Stand"),
                    new Book(title: "The Shining"),
            )
        }
    }

    static interface BookApi {
        @Get("/array")
        Flowable<Book> arrayStream()

        @Get(value = "/json", processes = MediaType.APPLICATION_JSON_STREAM)
        Flowable<Book> jsonStream()
    }

    static class Book {
        String title
    }
}
