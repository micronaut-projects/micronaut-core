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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.sse.Event;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.IOException;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server-sent events: the stream is announced as {@code text/event-stream} and every event, with its name and id,
 * reaches the client.
 */
@SuppressWarnings({"java:S5960", "checkstyle:MissingJavadocType", "checkstyle:DesignForExtension"})
@Tag("sse")
public class ServerSentEventsTest {
    public static final String SPEC_NAME = "ServerSentEventsTest";

    @Test
    void eventsAreDeliveredAsAnEventStream() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/sse/events"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(response -> {
                        String contentType = response.getHeaders().get(HttpHeaders.CONTENT_TYPE);
                        assertTrue(contentType != null && contentType.startsWith(MediaType.TEXT_EVENT_STREAM), () -> "content type: " + contentType);
                        String body = response.getBody(String.class).orElse("");
                        assertTrue(body.contains("data: one"), body);
                        assertTrue(body.contains("data: two"), body);
                        assertTrue(body.contains("data: three"), body);
                    })
                    .build()));
    }

    @Test
    void eventNamesAndIdsSurvive() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/sse/rich"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(response -> {
                        String body = response.getBody(String.class).orElse("");
                        assertTrue(body.contains("event: greeting"), body);
                        assertTrue(body.contains("id: 1"), body);
                        assertTrue(body.contains("data: hello"), body);
                        assertTrue(body.contains("event: farewell"), body);
                        assertTrue(body.contains("id: 2"), body);
                        assertTrue(body.contains("data: goodbye"), body);
                    })
                    .build()));
    }

    @Controller("/sse")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class SseController {

        @Get("/events")
        @Produces(MediaType.TEXT_EVENT_STREAM)
        Publisher<Event<String>> events() {
            return Flux.just("one", "two", "three").map(Event::of);
        }

        @Get("/rich")
        @Produces(MediaType.TEXT_EVENT_STREAM)
        Publisher<Event<String>> rich() {
            return Flux.just(
                Event.of("hello").name("greeting").id("1"),
                Event.of("goodbye").name("farewell").id("2")
            );
        }
    }
}
