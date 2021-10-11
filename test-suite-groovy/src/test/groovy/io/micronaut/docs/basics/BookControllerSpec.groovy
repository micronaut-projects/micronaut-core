package io.micronaut.docs.basics

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.http.HttpRequest.POST

class BookControllerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared @AutoCleanup HttpClient client = embeddedServer.applicationContext
                                                             .createBean(HttpClient, embeddedServer.URL)

    void "test post with uri template"() {

        when:
        // tag::posturitemplate[]
        Flux<HttpResponse<Book>> call = client.exchange(
                POST("/amazon/book/{title}", new Book("The Stand")),
                Book
        );
        // end::posturitemplate[]

        HttpResponse<Book> response = call.blockFirst()
        Optional<Book> message = response.getBody(Book) // <2>

        then:
        // check the status
        response.status == HttpStatus.CREATED // <3>
        // check the body
        message.isPresent()
        message.get().title == "The Stand"
    }

    void "test post form data"() {

        when:
        // tag::postform[]
        Flux<HttpResponse<Book>> call = client.exchange(
                POST("/amazon/book/{title}", new Book("The Stand"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED),
                Book
        )
        // end::postform[]

        HttpResponse<Book> response = call.blockFirst()
        Optional<Book> message = response.getBody(Book) // <2>

        then:
        // check the status
        response.status == HttpStatus.CREATED // <3>
        // check the body
        message.isPresent()
        message.get().title == "The Stand"
    }
}
