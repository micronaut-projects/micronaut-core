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
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpPostSpec
import io.micronaut.http.client.ReactorHttpClient
import io.micronaut.http.client.ReactorStreamingHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

import javax.validation.Valid
import javax.validation.constraints.NotNull
import java.util.function.Function

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class RxHttpPostSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    ReactorHttpClient client = context.createBean(ReactorHttpClient, embeddedServer.getURL())

    @Shared
    @AutoCleanup
    ReactorStreamingHttpClient streamingHttpClient = context.createBean(ReactorStreamingHttpClient, embeddedServer.getURL())

    void "test simple post exchange request with JSON"() {
        when:
        Flux<HttpResponse<HttpPostSpec.Book>> flowable = client.exchange(
                HttpRequest.POST("/post/simple", new HttpPostSpec.Book(title: "The Stand", pages: 1000))
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                HttpPostSpec.Book
        )
        HttpResponse<HttpPostSpec.Book> response = flowable.blockFirst()
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
        Flux<HttpPostSpec.Book> flowable = client.retrieve(
                HttpRequest.POST("/post/simple", new HttpPostSpec.Book(title: "The Stand", pages: 1000))
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                HttpPostSpec.Book
        )
        HttpPostSpec.Book book = flowable.blockFirst()

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

    void "test reactive Mono post retrieve request with JSON"() {
        when:
        Flux<HttpPostSpec.Book> flowable = client.retrieve(
                HttpRequest.POST("/reactive/post/single", Mono.just(new HttpPostSpec.Book(title: "The Stand", pages: 1000)))
                        .accept(MediaType.APPLICATION_JSON_TYPE),

                HttpPostSpec.Book
        )
        HttpPostSpec.Book book = flowable.blockFirst()

        then:
        book.title == "The Stand"
    }

    void "test reactive maybe post retrieve request with JSON"() {
        when:
        Flux<HttpPostSpec.Book> flowable = client.retrieve(
                HttpRequest.POST("/reactive/post/maybe", Mono.just(new HttpPostSpec.Book(title: "The Stand", pages: 1000)))
                        .accept(MediaType.APPLICATION_JSON_TYPE),

                HttpPostSpec.Book
        )
        HttpPostSpec.Book book = flowable.blockFirst()

        then:
        book.title == "The Stand"
    }

    void "test reactive post with unserializable data"() {
        when:
        Flux<User> flowable = client.retrieve(
                HttpRequest.POST("/reactive/post/user", '{"userName" : "edwin","movies" : [ {"imdbId" : "tt1285016","inCollection": "true"},{"imdbId" : "tt0100502","inCollection" : "false"} ]}')
                        .accept(MediaType.APPLICATION_JSON_TYPE),

                User
        )
        User user = flowable.blockFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.message.contains('Cannot construct instance of `io.micronaut.http.client.rxjava2.Movie`')
    }

    void "test reactive post error handling"() {
        when:
        Flux<User> flowable = client.retrieve(
                HttpRequest.POST("/reactive/post/user-error", '{"userName":"edwin","movies":[]}')
                        .accept(MediaType.APPLICATION_JSON_TYPE),
                Argument.of(User),
                Argument.of(User)
        )
        User user = flowable.onErrorResume((Function){ t ->
            Flux.just(((HttpClientResponseException) t).response.getBody(User).get())
        }).blockFirst()

        then:
        user.userName == "edwin"
    }

    @IgnoreIf({env["GITHUB_WORKFLOW"]})
    // investigate intermitten issues with this test on Github Actions
    void "test reactive post error handling without specifying error body type"() {
        when:
        Flux<User> flowable = client.retrieve(
                HttpRequest.POST("/reactive/post/user-error", '{"userName":"edwin","movies":[]}')
                        .accept(MediaType.APPLICATION_JSON_TYPE),

                Argument.of(User)
        )
        User user = flowable.onErrorResume((Function){ t ->
            if (t instanceof HttpClientResponseException) {
                try {
                    return Flux.just(((HttpClientResponseException) t).response.getBody(User).get())
                } catch (e) {
                    return Flux.error(e)
                }
            } else {
                return Flux.error(t)
            }
        }).blockFirst()

        then:
        user.userName == "edwin"
    }

    void "test posting an array of simple types"() {
        List<Boolean> booleans = streamingHttpClient.jsonStream(
                HttpRequest.POST("/reactive/post/booleans", "[true, true, false]"),
                Boolean.class
        ).collectList().block()

        expect:
        booleans[0] == true
        booleans[1] == true
        booleans[2] == false
    }

    void "test creating a person"() {
        Flux<Person> flowable = client.retrieve(
                HttpRequest.POST("/reactive/post/person", 'firstName=John')
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .accept(MediaType.APPLICATION_JSON_TYPE),

                Argument.of(Person)
        )

        when:
        Person person = flowable.blockFirst()

        then:
        thrown(HttpClientResponseException)
    }

    void "test a local error handler that returns a single"() {
        Flux<Person> flowable = client.retrieve(
                HttpRequest.POST("/reactive/post/error", '{"firstName": "John"}'),
                Argument.of(Person),
                Argument.of(String)
        )

        when:
        flowable.blockFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.NOT_FOUND
        ex.response.getBody(String).get() == "illegal.argument"
    }

    @Introspected
    static class Person {

        @NotNull
        String firstName
        @NotNull
        String lastName
    }

    @Controller('/reactive/post')
    static class ReactivePostController {

        @Post('/single')
        Mono<HttpPostSpec.Book> simple(@Body Mono<HttpPostSpec.Book> book) {
            return book
        }

        @Post('/maybe')
        Mono<HttpPostSpec.Book> maybe(@Body Mono<HttpPostSpec.Book> book) {
            return book
        }

        @Post("/user")
        Mono<HttpResponse<User>> postUser(@Body Mono<User> user) {
            return user.map({ User u->
                return HttpResponse.ok(u)
            })
        }

        @Post("/user-error")
        Mono<HttpResponse<User>> postUserError(@Body Mono<User> user) {
            return user.map({ User u->
                return HttpResponse.badRequest(u)
            })
        }

        @Post(uri = "/booleans")
        Flux<Boolean> booleans(@Body Flux<Boolean> booleans) {
            return booleans
        }

        @Post(uri = "/person", consumes = MediaType.APPLICATION_FORM_URLENCODED)
        Mono<HttpResponse<Person>> createPerson(@Valid @Body Person person)  {
            return Mono.just(HttpResponse.created(person))
        }

        @Post(uri = "/error")
        Mono<HttpResponse<Person>> emitError(@Body Person person)  {
            return Mono.error(new IllegalArgumentException())
        }

        @Error(exception = IllegalArgumentException.class)
        Mono<HttpResponse<String>> illegalArgument(HttpRequest request, IllegalArgumentException e) {
            Mono.just(HttpResponse.notFound("illegal.argument"))
        }
    }
}
