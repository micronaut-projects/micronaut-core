package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.netty.DefaultHttpClient
import io.micronaut.http.client.sse.SseClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketClient
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

class StaticClientSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer, [
                    'spec.name': 'StaticClientSpec',
            ])

    void "test clients can be created outside the context"() {
        URL url = new URL("https://foo_bar")

        expect:
        HttpClient.create(url) instanceof DefaultHttpClient
        StreamingHttpClient.create(url) instanceof DefaultHttpClient
        SseClient.create(url) instanceof DefaultHttpClient
        ProxyHttpClient.create(url) instanceof DefaultHttpClient
        WebSocketClient.create(url) instanceof DefaultHttpClient
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6323')
    void "deserialization with context-less http client"() {
        given:
        BlockingHttpClient client = HttpClient.create(embeddedServer.URL).toBlocking()

        when:
        Book book = client.retrieve(HttpRequest.GET('/book'), Book)

        then:
        book
        "Building Microservices" == book.title
    }

    @Introspected
    static class Book {
        String title
    }

    @Requires(property = 'spec.name', value = 'StaticClientSpec')
    @Controller("/book")
    static class BookController {
        @Get
        Publisher<Book> index() {
            return Publishers.just(new Book(title: "Building Microservices"));
        }
    }
}
