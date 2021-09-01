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
package io.micronaut.docs.server.exception;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ExceptionHandlerSpec {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeClass
    public static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class, Collections.singletonMap("spec.name", ExceptionHandlerSpec.class.getSimpleName()));
        client = server
                .getApplicationContext()
                .createBean(HttpClient.class, server.getURL());
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

    @Test
    public void testExceptionIsHandled() {
        HttpRequest request = HttpRequest.GET("/books/stock/1234");

        Argument<Map<String, Object>> errorType = Argument.mapOf(String.class, Object.class);
        HttpClientResponseException ex = Assertions.assertThrows(HttpClientResponseException.class, () -> {
                client.toBlocking().exchange(request, Argument.LONG, errorType);
        });
        HttpResponse response = ex.getResponse();
        Map<String, Object> body = (Map<String, Object>) response.getBody(errorType).get();
        Map<String, Object> embedded = (Map<String, Object>) body.get("_embedded");
        Object message = ((Map<String, Object>) ((List) embedded.get("errors")).get(0)).get("message");

        assertEquals(response.status(), HttpStatus.BAD_REQUEST);
        assertEquals(message, "No stock available");
    }
}
