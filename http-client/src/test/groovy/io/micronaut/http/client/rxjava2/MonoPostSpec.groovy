package io.micronaut.http.client.rxjava2

import groovy.transform.EqualsAndHashCode
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpPostSpec
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import io.micronaut.core.annotation.Introspected

class MonoPostSpec extends Specification {

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'MonoPostSpec'])

    @Shared
    @AutoCleanup
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "test reactive Mono post retrieve request with JSON"() {
        when:
        Flux<Book> flowable = Flux.from(client.retrieve(
                HttpRequest.POST("/reactive/post/mono", Mono.just(new Book(title: "The Stand", pages: 1000)))
                        .accept(MediaType.APPLICATION_JSON_TYPE),

                Book
        ))
        Book book = flowable.blockFirst()

        then:
        book.title == "The Stand"
    }

    @Requires(property = 'spec.name', value = 'MonoPostSpec')
    @Controller('/reactive/post')
    static class ReactivePostController {

        @Post('/mono')
        Mono<Book> simple(@Body Mono<Book> book) {
            return book
        }
    }

    @EqualsAndHashCode
    @Introspected
    static class Book {
        String title
        Integer pages
    }
}

