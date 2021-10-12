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
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.POST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HelloControllerSpec {

    private EmbeddedServer embeddedServer;
    private HttpClient client;

    @Before
    public void setup() {
        embeddedServer = ApplicationContext.run(
                EmbeddedServer.class,
                Collections.singletonMap("spec.name", getClass().getSimpleName()));
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
    public void testSimpleRetrieve() {
        // tag::simple[]
        String uri = UriBuilder.of("/hello/{name}")
                               .expand(Collections.singletonMap("name", "John"))
                               .toString();
        assertEquals("/hello/John", uri);

        String result = client.toBlocking().retrieve(uri);

        assertEquals("Hello John", result);
        // end::simple[]
    }

    @Test
    public void testRetrieveWithHeaders() {
        // tag::headers[]
        Flux<String> response = Flux.from(client.retrieve(
                GET("/hello/John")
                .header("X-My-Header", "SomeValue")
        ));
        // end::headers[]

        assertEquals("Hello John", response.blockFirst());
    }

    @Test
    public void testRetrieveWithJSON() {
        // tag::jsonmap[]
        Flux<Map> response = Flux.from(client.retrieve(
                GET("/greet/John"), Map.class
        ));
        // end::jsonmap[]

        assertEquals("Hello John", response.blockFirst().get("text"));

        // tag::jsonmaptypes[]
        response = Flux.from(client.retrieve(
                GET("/greet/John"),
                Argument.of(Map.class, String.class, String.class) // <1>
        ));
        // end::jsonmaptypes[]

        assertEquals("Hello John", response.blockFirst().get("text"));
    }

    @Test
    public void testRetrieveWithPOJO() {
        // tag::jsonpojo[]
        Flux<Message> response = Flux.from(client.retrieve(
                GET("/greet/John"), Message.class
        ));

        assertEquals("Hello John", response.blockFirst().getText());
        // end::jsonpojo[]
    }

    @Test
    public void testRetrieveWithPOJOResponse() {
        // tag::pojoresponse[]
        Flux<HttpResponse<Message>> call = Flux.from(client.exchange(
                GET("/greet/John"), Message.class // <1>
        ));

        HttpResponse<Message> response = call.blockFirst();
        Optional<Message> message = response.getBody(Message.class); // <2>
        // check the status
        assertEquals(HttpStatus.OK, response.getStatus()); // <3>
        // check the body
        assertTrue(message.isPresent());
        assertEquals("Hello John", message.get().getText());
        // end::pojoresponse[]
    }

    @Test
    public void testPostRequestWithString() {
        // tag::poststring[]
        Flux<HttpResponse<String>> call = Flux.from(client.exchange(
                POST("/hello", "Hello John") // <1>
                    .contentType(MediaType.TEXT_PLAIN_TYPE)
                    .accept(MediaType.TEXT_PLAIN_TYPE), // <2>
                String.class // <3>
        ));
        // end::poststring[]

        HttpResponse<String> response = call.blockFirst();
        Optional<String> message = response.getBody(String.class); // <2>
        // check the status
        assertEquals(HttpStatus.CREATED, response.getStatus()); // <3>
        // check the body
        assertTrue(message.isPresent());
        assertEquals("Hello John", message.get());
    }

    @Test
    public void testPostRequestWithPOJO() {
        // tag::postpojo[]
        Flux<HttpResponse<Message>> call = Flux.from(client.exchange(
                POST("/greet", new Message("Hello John")), // <1>
                Message.class // <2>
        ));
        // end::postpojo[]

        HttpResponse<Message> response = call.blockFirst();
        Optional<Message> message = response.getBody(Message.class); // <2>
        // check the status
        assertEquals(HttpStatus.CREATED, response.getStatus()); // <3>
        // check the body
        assertTrue(message.isPresent());
        assertEquals("Hello John", message.get().getText());
    }
}
