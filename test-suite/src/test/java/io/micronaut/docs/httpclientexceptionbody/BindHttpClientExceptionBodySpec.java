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
package io.micronaut.docs.httpclientexceptionbody;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class BindHttpClientExceptionBodySpec {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeClass
    public static void setupServer() {
        Map<String, Object> map = new HashMap<>();
        map.put("spec.name", BindHttpClientExceptionBodySpec.class.getSimpleName());
        map.put("spec.lang", "java");

        server = ApplicationContext.run(EmbeddedServer.class, map, Environment.TEST);
        client = server
                .getApplicationContext()
                .createBean(RxHttpClient.class, server.getURL());
    }

    @AfterClass
    public static void stopServer() {
        if(server != null) {
            server.stop();
        }
        if(client != null) {
            client.stop();
        }
    }

    //tag::test[]
    @Test
    public void afterAnHttpClientExceptionTheResponseBodyCanBeBoundToAPOJO() {
        try {
            client.toBlocking().exchange(HttpRequest.GET("/books/1680502395"),
                    Argument.of(Book.class), // <1>
                    Argument.of(CustomError.class)); // <2>
        } catch (HttpClientResponseException e) {
            assertEquals(HttpStatus.UNAUTHORIZED, e.getResponse().getStatus());
            Optional<CustomError> jsonError = e.getResponse().getBody(CustomError.class);
            assertTrue(jsonError.isPresent());
            assertEquals(401, jsonError.get().status);
            assertEquals("Unauthorized", jsonError.get().error);
            assertEquals("No message available", jsonError.get().message);
            assertEquals("/books/1680502395", jsonError.get().path);
        }
    }
    //end::test[]

    @Test
    public void testExceptionBindingErrorResponse() {
        try {
            client.toBlocking().exchange(HttpRequest.GET("/books/1680502395"),
                    Argument.of(Book.class), // <1>
                    Argument.of(OtherError.class)); // <2>
        } catch (HttpClientResponseException e) {
            assertEquals(HttpStatus.UNAUTHORIZED, e.getResponse().getStatus());
            Optional<OtherError> jsonError = e.getResponse().getBody(OtherError.class);

            assertNotNull(jsonError);
            assertTrue(!jsonError.isPresent());
        }
    }

    @Test
    public void verifyBindErrorIsThrown() {
        try {
            client.toBlocking().exchange(HttpRequest.GET("/books/1491950358"),
                    Argument.of(Book.class),
                    Argument.of(CustomError.class));
        } catch (HttpClientResponseException e) {
            assertEquals(HttpStatus.OK, e.getResponse().getStatus());
            assertTrue(e.getMessage().startsWith("Error decoding HTTP response body"));
            assertTrue(e.getMessage().contains("cannot deserialize from Object value")); // the jackson error
        }
    }
}