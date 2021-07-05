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
import io.reactivex.Single
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import io.micronaut.core.annotation.Introspected

class SinglePostSpec extends Specification {
    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'SinglePostSpec'])
    @Shared
    @AutoCleanup
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "test reactive single post retrieve request with JSON"() {
        when:
        Flowable<Book> flowable = Flowable.fromPublisher(client.retrieve(
                HttpRequest.POST("/reactive/post/single", Single.just(new Book(title: "The Stand", pages: 1000)))
                        .accept(MediaType.APPLICATION_JSON_TYPE),

                Book
        ))
        Book book = flowable.blockingFirst()

        then:
        book.title == "The Stand"
    }

    @Requires(property = 'spec.name', value = 'SinglePostSpec')
    @Controller('/reactive/post')
    static class ReactivePostController {

        @Post('/single')
        Single<Book> simple(@Body Single<Book> book) {
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
