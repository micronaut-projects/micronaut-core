package io.micronaut.reactive.rxjava2.http.client

import groovy.transform.EqualsAndHashCode
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import io.reactivex.Maybe
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import io.micronaut.core.annotation.Introspected

class MaybePostSpec extends Specification {
    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'MaybePostSpec'])

    @Shared
    @AutoCleanup
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "test reactive maybe post retrieve request with JSON"() {
        when:
        Flowable<Book> flowable = Flowable.fromPublisher(client.retrieve(
                HttpRequest.POST("/reactive/post/maybe", Maybe.just(new Book(title: "The Stand", pages: 1000)))
                        .accept(MediaType.APPLICATION_JSON_TYPE),

                Book
        ))
        Book book = flowable.blockingFirst()

        then:
        book.title == "The Stand"
    }

    @Requires(property = 'spec.name', value = 'MaybePostSpec')
    @Controller('/reactive/post')
    static class ReactivePostController {
        @Post('/maybe')
        Maybe<Book> maybe(@Body Maybe<Book> book) {
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
