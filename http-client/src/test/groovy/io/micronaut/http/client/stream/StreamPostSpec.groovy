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
package io.micronaut.http.client.stream

import io.micronaut.core.async.annotation.SingleResult
import groovy.transform.EqualsAndHashCode
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpPostSpec
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
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
class StreamPostSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'StreamPostSpec'])

    @Shared
    ApplicationContext context = embeddedServer.applicationContext

    @Shared
    @AutoCleanup
    HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

    @Shared
    @AutoCleanup
    StreamingHttpClient streamingHttpClient = context.createBean(StreamingHttpClient, embeddedServer.getURL())

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
        Flux<HttpPostSpec.Book> flowable = Flux.from(client.retrieve(
                HttpRequest.POST("/post/simple", new HttpPostSpec.Book(title: "The Stand", pages: 1000))
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                HttpPostSpec.Book
        ))
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

    void "test reactive post with unserializable data"() {
        when:
        Flux<User> flowable = Flux.from(client.retrieve(
                HttpRequest.POST("/reactive/post/user", '{"userName" : "edwin","movies" : [ {"imdbId" : "tt1285016","inCollection": "true"},{"imdbId" : "tt0100502","inCollection" : "false"} ]}')
                        .accept(MediaType.APPLICATION_JSON_TYPE),

                User
        ))
        User user = flowable.blockFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.getBody(Map).get()._embedded.errors[0].message.contains('Cannot construct instance of `io.micronaut.http.client.stream.Movie`')
    }

    void "test reactive post error handling"() {
        when:
        Flux<User> flowable = Flux.from(client.retrieve(
                HttpRequest.POST("/reactive/post/user-error", '{"userName":"edwin","movies":[]}')
                        .accept(MediaType.APPLICATION_JSON_TYPE),
                Argument.of(User),
                Argument.of(User)
        ))
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
        Flux<User> flowable = Flux.from(client.retrieve(
                HttpRequest.POST("/reactive/post/user-error", '{"userName":"edwin","movies":[]}')
                        .accept(MediaType.APPLICATION_JSON_TYPE),

                Argument.of(User)
        ))
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
        List<Boolean> booleans = Flux.from(streamingHttpClient.jsonStream(
                HttpRequest.POST("/reactive/post/booleans", "[true, true, false]"),
                Boolean.class
        )).collectList().block()

        expect:
        booleans[0] == true
        booleans[1] == true
        booleans[2] == false
    }

    void "test creating a person"() {
        Flux<Person> flowable = Flux.from(client.retrieve(
                HttpRequest.POST("/reactive/post/person", 'firstName=John')
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .accept(MediaType.APPLICATION_JSON_TYPE),

                Argument.of(Person)
        ))

        when:
        Person person = flowable.blockFirst()

        then:
        thrown(HttpClientResponseException)
    }

    void "test a local error handler that returns a single"() {
        Flux<Person> flowable = Flux.from(client.retrieve(
                HttpRequest.POST("/reactive/post/error", '{"firstName": "John"}'),
                Argument.of(Person),
                Argument.of(String)
        ))

        when:
        flowable.blockFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.NOT_FOUND
        ex.response.getBody(String).get() == "illegal.argument"
    }

    @EqualsAndHashCode
    @Introspected
    static class Book {
        String title
        Integer pages
    }

    @Requires(property = 'spec.name', value = 'StreamPostSpec')
    @Controller('/post')
    static class PostController {

        @Post('/simple')
        Book simple(@Body Book book, @Header String contentType, @Header long contentLength, @Header accept, @Header('X-My-Header') custom) {
            assert contentType == MediaType.APPLICATION_JSON
            assert contentLength == 34
            assert accept == MediaType.APPLICATION_JSON
            assert custom == 'Foo'
            return book
        }
    }

    @Introspected
    static class Person {
        @NotNull
        String firstName
        @NotNull
        String lastName
    }

    @Requires(property = 'spec.name', value = 'StreamPostSpec')
    @Controller('/reactive/post')
    static class ReactivePostController {

        @Post("/user")
        @SingleResult
        Publisher<HttpResponse<User>> postUser(@Body Publisher<User> user) {
            return Mono.from(user).map({ User u->
                return HttpResponse.ok(u)
            })
        }

        @Post("/user-error")
        @SingleResult
        Publisher<HttpResponse<User>> postUserError(@Body Publisher<User> user) {
            return Mono.from(user).map({ User u->
                return HttpResponse.badRequest(u)
            })
        }

        @Post(uri = "/booleans")
        Publisher<Boolean> booleans(@Body Publisher<Boolean> booleans) {
            return booleans
        }

        @Post(uri = "/person", consumes = MediaType.APPLICATION_FORM_URLENCODED)
        @SingleResult
        Publisher<HttpResponse<Person>> createPerson(@Valid @Body Person person)  {
            return Mono.just(HttpResponse.created(person))
        }

        @Post(uri = "/error")
        @SingleResult
        Publisher<HttpResponse<Person>> emitError(@Body Person person)  {
            return Mono.error(new IllegalArgumentException())
        }

        @Error(exception = IllegalArgumentException.class)
        @SingleResult
        Publisher<HttpResponse<String>> illegalArgument(HttpRequest request, IllegalArgumentException e) {
            Mono.just(HttpResponse.notFound("illegal.argument"))
        }
    }
}
