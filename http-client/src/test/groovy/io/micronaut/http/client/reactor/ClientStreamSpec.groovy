package io.micronaut.http.client.reactor

import io.micronaut.context.ApplicationContext
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.Client
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ClientStreamSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared BookClient bookClient = embeddedServer.applicationContext.getBean(BookClient)

    void "test stream array of json objects"() {
        when:
        List<Book> books = bookClient.arrayStream().collectList().block()

        then:
        books.size() == 2
        books[0].title == "The Stand"
        books[1].title == "The Shining"
    }

    void "test stream json stream of objects"() {
        when:
        List<Book> books = bookClient.jsonStream().collectList().block()

        then:
        books.size() == 2
        books[0].title == "The Stand"
        books[1].title == "The Shining"

    }


    @Client('/reactor/stream')
    static interface BookClient extends BookApi {

    }

    @Controller("/reactor/stream")
    static class StreamController implements BookApi {

        @Override
        Flux<Book> arrayStream() {
            return Flux.just(
                    new Book(title: "The Stand"),
                    new Book(title: "The Shining"),
            )
        }

        @Override
        Flux<Book> jsonStream() {
            return Flux.just(
                    new Book(title: "The Stand"),
                    new Book(title: "The Shining"),
            )
        }
    }

    static interface BookApi {
        @Get("/array")
        Flux<Book> arrayStream()

        @Get(uri = "/json", processes = MediaType.APPLICATION_JSON_STREAM)
        Flux<Book> jsonStream()
    }

    static class Book {
        String title
    }
}

