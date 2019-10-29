package io.micronaut.docs.basics;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.reactivex.Flowable;
import org.junit.Test;

import java.util.Optional;

import static io.micronaut.http.HttpRequest.POST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BookControllerSpec {
    @Test
    public void testPostWithURITemplate() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient.class, embeddedServer.getURL());

        // tag::posturitemplate[]
        Flowable<HttpResponse<Book>> call = client.exchange(
                POST("/amazon/book/{title}", new Book("The Stand")),
                Book.class
        );
        // end::posturitemplate[]

        HttpResponse<Book> response = call.blockingFirst();
        Optional<Book> message = response.getBody(Book.class); // <2>
        // check the status
        assertEquals(
                HttpStatus.CREATED,
                response.getStatus() // <3>
        );
        // check the body
        assertTrue(message.isPresent());
        assertEquals(
                "The Stand",
                message.get().getTitle()
        );

        embeddedServer.stop();
        client.stop();
    }

    @Test
    public void testPostFormData() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient.class, embeddedServer.getURL());

        // tag::postform[]
        Flowable<HttpResponse<Book>> call = client.exchange(
                POST("/amazon/book/{title}", new Book("The Stand"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED),
                Book.class
        );
        // end::postform[]

        HttpResponse<Book> response = call.blockingFirst();
        Optional<Book> message = response.getBody(Book.class); // <2>
        // check the status
        assertEquals(
                HttpStatus.CREATED,
                response.getStatus() // <3>
        );
        // check the body
        assertTrue(message.isPresent());
        assertEquals(
                "The Stand",
                message.get().getTitle()
        );


        embeddedServer.stop();
        client.stop();
    }
}
