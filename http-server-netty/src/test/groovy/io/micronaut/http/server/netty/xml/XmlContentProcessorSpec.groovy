package io.micronaut.http.server.netty.xml

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class XmlContentProcessorSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [:])

    void "test sending a single book"() {
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        List<Book> books = client.retrieve(
                HttpRequest.POST("/xml/stream", '<book><title>First Book</title></book>')
                        .contentType(MediaType.TEXT_XML_TYPE), Book.class).toList().blockingGet()

        then:
        books.size() == 1
        books[0].title == "First Book"
    }

    @Controller("/xml/stream")
    static class StreamController {

        @Post(consumes = MediaType.TEXT_XML)
        Flowable<Book> stream(Flowable<Book> books) {
            return books
        }

        @Post(uri = "/list", consumes = MediaType.TEXT_XML)
        Flowable<Book> streamList(Flowable<List<Book>> books) {
            return books.flatMap({ bookList -> Flowable.fromIterable(bookList) })
        }

    }

    static class Book {
        String title
    }


}
