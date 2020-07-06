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
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.runtime.server.EmbeddedServer;
import io.reactivex.Flowable;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.POST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author graemerocher
 * @since 1.0
 */
public class HelloControllerTest {

    @Test
    public void testSimpleRetrieve() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient.class, embeddedServer.getURL());

        // tag::simple[]
        String uri = UriBuilder.of("/hello/{name}")
                               .expand(Collections.singletonMap("name", "John"))
                               .toString();
        assertEquals("/hello/John", uri);

        String result = client.toBlocking().retrieve(uri);

        assertEquals(
                "Hello John",
                result
        );
        // end::simple[]

        embeddedServer.stop();
        client.stop();
    }

    @Test
    public void testRetrieveWithHeaders() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient.class, embeddedServer.getURL());

        // tag::headers[]
        Flowable<String> response = client.retrieve(
                GET("/hello/John")
                .header("X-My-Header", "SomeValue")
        );
        // end::headers[]

        assertEquals(
                "Hello John",
                response.blockingFirst()
        );

        embeddedServer.stop();
        client.stop();
    }

    @Test
    public void testRetrieveWithJSON() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient.class, embeddedServer.getURL());

        // tag::jsonmap[]
        Flowable<Map> response = client.retrieve(
                GET("/greet/John"), Map.class
        );
        // end::jsonmap[]

        assertEquals(
                "Hello John",
                response.blockingFirst().get("text")
        );

        // tag::jsonmaptypes[]
        response = client.retrieve(
                GET("/greet/John"),
                Argument.of(Map.class, String.class, String.class) // <1>
        );
        // end::jsonmaptypes[]

        assertEquals(
                "Hello John",
                response.blockingFirst().get("text")
        );
        embeddedServer.stop();
        client.stop();
    }

    @Test
    public void testRetrieveWithPOJO() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient.class, embeddedServer.getURL());

        // tag::jsonpojo[]
        Flowable<Message> response = client.retrieve(
                GET("/greet/John"), Message.class
        );

        assertEquals(
                "Hello John",
                response.blockingFirst().getText()
        );
        // end::jsonpojo[]

        embeddedServer.stop();
        client.stop();
    }

    @Test
    public void testRetrieveWithPOJOResponse() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient.class, embeddedServer.getURL());

        // tag::pojoresponse[]
        Flowable<HttpResponse<Message>> call = client.exchange(
                GET("/greet/John"), Message.class // <1>
        );

        HttpResponse<Message> response = call.blockingFirst();
        Optional<Message> message = response.getBody(Message.class); // <2>
        // check the status
        assertEquals(
                HttpStatus.OK,
                response.getStatus() // <3>
        );
        // check the body
        assertTrue(message.isPresent());
        assertEquals(
                "Hello John",
                message.get().getText()
        );
        // end::pojoresponse[]

        embeddedServer.stop();
        client.stop();
    }

    @Test
    public void testPostRequestWithString() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient.class, embeddedServer.getURL());

        // tag::poststring[]
        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/hello", "Hello John") // <1>
                    .contentType(MediaType.TEXT_PLAIN_TYPE)
                    .accept(MediaType.TEXT_PLAIN_TYPE), // <2>
                String.class // <3>
        );
        // end::poststring[]

        HttpResponse<String> response = call.blockingFirst();
        Optional<String> message = response.getBody(String.class); // <2>
        // check the status
        assertEquals(
                HttpStatus.CREATED,
                response.getStatus() // <3>
        );
        // check the body
        assertTrue(message.isPresent());
        assertEquals(
                "Hello John",
                message.get()
        );

        embeddedServer.stop();
        client.stop();
    }

    @Test
    public void testPostRequestWithPOJO() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient.class, embeddedServer.getURL());

        // tag::postpojo[]
        Flowable<HttpResponse<Message>> call = client.exchange(
                POST("/greet", new Message("Hello John")), // <1>
                Message.class // <2>
        );
        // end::postpojo[]

        HttpResponse<Message> response = call.blockingFirst();
        Optional<Message> message = response.getBody(Message.class); // <2>
        // check the status
        assertEquals(
                HttpStatus.CREATED,
                response.getStatus() // <3>
        );
        // check the body
        assertTrue(message.isPresent());
        assertEquals(
                "Hello John",
                message.get().getText()
        );


        embeddedServer.stop();
        client.stop();
    }
}
