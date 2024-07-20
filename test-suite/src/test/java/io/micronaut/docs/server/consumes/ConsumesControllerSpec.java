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
package io.micronaut.docs.server.consumes;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsumesControllerSpec {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class, Collections.singletonMap("spec.name", "consumesspec"));
        client = server
                .getApplicationContext()
                .createBean(HttpClient.class, server.getURL());
    }

    @AfterAll
    static void stopServer() {
        if(server != null) {
            server.stop();
        }
        if(client != null) {
            client.stop();
        }
    }

    @Test
    void testConsumes() {
        Book book = new Book();
        book.title = "The Stand";
        book.pages = 1000;

        assertThrows(HttpClientResponseException.class, () ->
            client.toBlocking().exchange(HttpRequest.POST("/consumes", book)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)));

        assertDoesNotThrow(() ->
                client.toBlocking().exchange(HttpRequest.POST("/consumes", book)
                        .contentType(MediaType.APPLICATION_JSON)));

        assertDoesNotThrow(() ->
                client.toBlocking().exchange(HttpRequest.POST("/consumes/multiple", book)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)));

        assertDoesNotThrow(() ->
                client.toBlocking().exchange(HttpRequest.POST("/consumes/multiple", book)
                .contentType(MediaType.APPLICATION_JSON)));

        assertDoesNotThrow(() ->
                client.toBlocking().exchange(HttpRequest.POST("/consumes/member", book)
                .contentType(MediaType.TEXT_PLAIN)));
    }

    @Introspected
    static class Book {
        public String title;
        public Integer pages;
    }
}
