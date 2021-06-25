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
package io.micronaut.docs.basics;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Flux;

import java.util.Optional;

import static io.micronaut.http.HttpRequest.POST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BookControllerSpec {

    private EmbeddedServer embeddedServer;
    private HttpClient client;

    @Before
    public void setup() {
        embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        client = embeddedServer.getApplicationContext().createBean(
                HttpClient.class,
                embeddedServer.getURL());
    }

    @After
    public void cleanup() {
        embeddedServer.stop();
        client.stop();
    }

    @Test
    public void testPostWithURITemplate() {
        // tag::posturitemplate[]
        Flux<HttpResponse<Book>> call = Flux.from(client.exchange(
                POST("/amazon/book/{title}", new Book("The Stand")),
                Book.class
        ));
        // end::posturitemplate[]

        HttpResponse<Book> response = call.blockFirst();
        Optional<Book> message = response.getBody(Book.class); // <2>
        // check the status
        assertEquals(HttpStatus.CREATED, response.getStatus()); // <3>
        // check the body
        assertTrue(message.isPresent());
        assertEquals("The Stand", message.get().getTitle());
    }

    @Test
    public void testPostFormData() {
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
        assertEquals(HttpStatus.CREATED, response.getStatus()); // <3>
        // check the body
        assertTrue(message.isPresent());
        assertEquals("The Stand", message.get().getTitle());
    }
}
