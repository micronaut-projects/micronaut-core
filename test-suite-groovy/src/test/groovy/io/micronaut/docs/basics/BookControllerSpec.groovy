package io.micronaut.docs.basics

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.http.HttpRequest.POST

class BookControllerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared @AutoCleanup RxHttpClient client = embeddedServer.getApplicationContext()
                                                             .createBean(RxHttpClient, embeddedServer.getURL())

    void "test post with uri template"() {

        when:
        // tag::posturitemplate[]
        Flowable<HttpResponse<Book>> call = client.exchange(
                POST("/amazon/book/{title}", new Book("The Stand")),
                Book.class
        );
        // end::posturitemplate[]

        HttpResponse<Book> response = call.blockingFirst()
        Optional<Book> message = response.getBody(Book.class) // <2>

        then:
        // check the status
        response.getStatus() == HttpStatus.CREATED // <3>
        // check the body
        message.isPresent()
        message.get().getTitle() == "The Stand"
    }

    void "test post form data"() {

        when:
        // tag::postform[]
        Flowable<HttpResponse<Book>> call = client.exchange(
                POST("/amazon/book/{title}", new Book("The Stand"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED),
                Book.class
        )
        // end::postform[]

        HttpResponse<Book> response = call.blockingFirst()
        Optional<Book> message = response.getBody(Book.class) // <2>

        then:
        // check the status
        response.getStatus() == HttpStatus.CREATED // <3>
        // check the body
        message.isPresent()
        message.get().getTitle() == "The Stand"
    }
}
