/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client.docs.basics;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.util.Optional;
import static io.micronaut.http.HttpRequest.POST;

/**
 * @author graemerocher
 * @since 1.0
 */
public class BookControllerTest {

    @Test
    public void testPostWithURITemplate() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        HttpClient client = embeddedServer.getApplicationContext().createBean(HttpClient.class, embeddedServer.getURL());

        // tag::posturitemplate[]
        Flux<HttpResponse<Book>> call = Flux.from(client.exchange(
                POST("/amazon/book/{title}", new Book("The Stand")),
                Book.class
        ));
        // end::posturitemplate[]

        HttpResponse<Book> response = call.blockFirst();
        Optional<Book> message = response.getBody(Book.class); // <2>
        // check the status
        Assertions.assertEquals(
                HttpStatus.CREATED,
                response.getStatus() // <3>
        );
        // check the body
        Assertions.assertTrue(message.isPresent());
        Assertions.assertEquals(
                "The Stand",
                message.get().getTitle()
        );

        embeddedServer.stop();
        client.stop();
    }

    @Test
    public void testPostFormData() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        HttpClient client = embeddedServer.getApplicationContext().createBean(HttpClient.class, embeddedServer.getURL());

        // tag::postform[]
        Flux<HttpResponse<Book>> call = Flux.from(client.exchange(
                POST("/amazon/book/{title}", new Book("The Stand"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED),
                Book.class
        ));
        // end::postform[]

        HttpResponse<Book> response = call.blockFirst();
        Optional<Book> message = response.getBody(Book.class); // <2>
        // check the status
        Assertions.assertEquals(
                HttpStatus.CREATED,
                response.getStatus() // <3>
        );
        // check the body
        Assertions.assertTrue(message.isPresent());
        Assertions.assertEquals(
                "The Stand",
                message.get().getTitle()
        );


        embeddedServer.stop();
        client.stop();
    }
}
