/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.sse.Event;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@link Publisher} body the route did not declare, returned inside an {@link HttpResponse} (or a
 * {@link CompletableFuture} of one), is announced with the content type it is written in, as it is when the route
 * declares a {@link Publisher} return type.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class UndeclaredPublisherBodyContentTypeTest {
    public static final String SPEC_NAME = "UndeclaredPublisherBodyContentTypeTest";

    @Test
    @Tag("sse")
    void aPublisherOfEventsIsAnnouncedAsAnEventStream() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String uri : List.of("/undeclared-publisher/events", "/undeclared-publisher/events-response", "/undeclared-publisher/events-response-async")) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(uri), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(response -> {
                        String contentType = response.getHeaders().get(HttpHeaders.CONTENT_TYPE);
                        assertTrue(contentType != null && contentType.startsWith(MediaType.TEXT_EVENT_STREAM), () -> uri + " content type: " + contentType);
                        String body = response.getBody(String.class).orElse("");
                        assertTrue(body.contains("data: one"), body);
                        assertTrue(body.contains("data: two"), body);
                    })
                    .build());
            }
        }
    }

    @Test
    void aPublisherOfObjectsIsAnnouncedAsJson() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String uri : List.of("/undeclared-publisher/json", "/undeclared-publisher/json-response", "/undeclared-publisher/json-response-async")) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(uri), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(response -> {
                        String contentType = response.getHeaders().get(HttpHeaders.CONTENT_TYPE);
                        assertTrue(contentType != null && contentType.startsWith(MediaType.APPLICATION_JSON), () -> uri + " content type: " + contentType);
                        String body = response.getBody(String.class).orElse("");
                        assertTrue(body.contains("\"name\":\"a\"") && body.contains("\"name\":\"b\""), body);
                    })
                    .build());
            }
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static Publisher<Event<String>> events() {
        return Flux.just("one", "two").map(Event::of);
    }

    private static Publisher<Item> items() {
        return Flux.just(new Item("a"), new Item("b"));
    }

    @Introspected
    @ReflectiveAccess
    static class Item {

        private final String name;

        Item(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    @Controller("/undeclared-publisher")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class UndeclaredPublisherController {

        @Get("/events")
        @Produces(MediaType.TEXT_EVENT_STREAM)
        Publisher<Event<String>> events() {
            return UndeclaredPublisherBodyContentTypeTest.events();
        }

        @Get("/events-response")
        @Produces(MediaType.TEXT_EVENT_STREAM)
        HttpResponse<?> eventsResponse() {
            return HttpResponse.ok(UndeclaredPublisherBodyContentTypeTest.events());
        }

        @Get("/events-response-async")
        @Produces(MediaType.TEXT_EVENT_STREAM)
        CompletableFuture<HttpResponse<?>> eventsResponseAsync() {
            return CompletableFuture.completedFuture(HttpResponse.ok(UndeclaredPublisherBodyContentTypeTest.events()));
        }

        @Get("/json")
        Publisher<Item> json() {
            return items();
        }

        @Get("/json-response")
        HttpResponse<?> jsonResponse() {
            return HttpResponse.ok(items());
        }

        @Get("/json-response-async")
        CompletableFuture<HttpResponse<?>> jsonResponseAsync() {
            return CompletableFuture.completedFuture(HttpResponse.ok(items()));
        }
    }
}
