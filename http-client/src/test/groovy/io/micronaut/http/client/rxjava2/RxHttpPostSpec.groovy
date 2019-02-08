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

import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.reactivex.Flowable
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpPostSpec
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Maybe
import io.reactivex.Single
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class RxHttpPostSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    RxHttpClient client = context.createBean(RxHttpClient, embeddedServer.getURL())

    void "test simple post exchange request with JSON"() {
        when:
        Flowable<HttpResponse<HttpPostSpec.Book>> flowable = client.exchange(
                HttpRequest.POST("/post/simple", new HttpPostSpec.Book(title: "The Stand", pages: 1000))
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                HttpPostSpec.Book
        )
        HttpResponse<HttpPostSpec.Book> response = flowable.blockingFirst()
        Optional<HttpPostSpec.Book> body = response.getBody()

        then:
        response.status == HttpStatus.OK
        response.contentType.get() == MediaType.APPLICATION_JSON_TYPE
        response.contentLength == 34
        body.isPresent()
        body.get() instanceof HttpPostSpec.Book
        body.get().title == 'The Stand'
    }

    void "test simple post retrieve request with JSON"() {
        when:
        Flowable<HttpPostSpec.Book> flowable = client.retrieve(
                HttpRequest.POST("/post/simple", new HttpPostSpec.Book(title: "The Stand", pages: 1000))
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                HttpPostSpec.Book
        )
        HttpPostSpec.Book book = flowable.blockingFirst()

        then:
        book.title == "The Stand"
    }

    void "test simple post retrieve blocking request with JSON"() {
        when:
        BlockingHttpClient blockingHttpClient = client.toBlocking()
        HttpPostSpec.Book book = blockingHttpClient.retrieve(
                HttpRequest.POST("/post/simple", new HttpPostSpec.Book(title: "The Stand", pages: 1000))
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                HttpPostSpec.Book
        )

        then:
        book.title == "The Stand"
    }

    void "test reactive single post retrieve request with JSON"() {
        when:
        Flowable<HttpPostSpec.Book> flowable = client.retrieve(
                HttpRequest.POST("/reactive/post/single", Single.just(new HttpPostSpec.Book(title: "The Stand", pages: 1000)))
                        .accept(MediaType.APPLICATION_JSON_TYPE),

                HttpPostSpec.Book
        )
        HttpPostSpec.Book book = flowable.blockingFirst()

        then:
        book.title == "The Stand"
    }

    void "test reactive maybe post retrieve request with JSON"() {
        when:
        Flowable<HttpPostSpec.Book> flowable = client.retrieve(
                HttpRequest.POST("/reactive/post/maybe", Maybe.just(new HttpPostSpec.Book(title: "The Stand", pages: 1000)))
                        .accept(MediaType.APPLICATION_JSON_TYPE),

                HttpPostSpec.Book
        )
        HttpPostSpec.Book book = flowable.blockingFirst()

        then:
        book.title == "The Stand"
    }

    void "test reactive post with unserializable data"() {
        when:
        Flowable<User> flowable = client.retrieve(
                HttpRequest.POST("/reactive/post/user", '{"userName" : "edwin","movies" : [ {"imdbId" : "tt1285016","inCollection": "true"},{"imdbId" : "tt0100502","inCollection" : "false"} ]}')
                        .accept(MediaType.APPLICATION_JSON_TYPE),

                User
        )
        User user = flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.message.contains('Cannot construct instance of `io.micronaut.http.client.rxjava2.Movie`')
    }


    @Controller('/reactive/post')
    static class ReactivePostController {
        @Post('/single')
        Single<HttpPostSpec.Book> simple(@Body Single<HttpPostSpec.Book> book) {
            return book
        }

        @Post('/maybe')
        Maybe<HttpPostSpec.Book> maybe(@Body Maybe<HttpPostSpec.Book> book) {
            return book
        }

        @Post("/user")
        Single<HttpResponse<User>> postUser(@Body Single<User> user) {
            return user.map({ User u->
                return HttpResponse.ok(u)
            })
        }

    }

}
