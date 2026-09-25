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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.annotation.Requires;
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
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Streaming responses from handler routes: a {@link Publisher} body is written as server-sent events or as a JSON
 * array, as it is for a controller that returns a {@link Publisher}.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteStreamingTest {
    public static final String SPEC_NAME = "HandlerRouteStreamingTest";

    @Test
    @Tag("sse")
    void aPublisherOfEventsIsAnEventStream() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String uri : List.of("/ctl-stream/events", "/ctl-stream/events-response", "/ctl-stream/events-response-async", "/fn-stream/events", "/fn-stream/events-async", "/fn-stream/events-response-type")) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(uri), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(response -> {
                        String contentType = response.getHeaders().get(HttpHeaders.CONTENT_TYPE);
                        assertTrue(contentType != null && contentType.startsWith(MediaType.TEXT_EVENT_STREAM), () -> uri + " content type: " + contentType);
                        String body = response.getBody(String.class).orElse("");
                        assertTrue(body.contains("data: one"), body);
                        assertTrue(body.contains("data: two"), body);
                        assertTrue(body.contains("data: three"), body);
                    })
                    .build());
            }
        }
    }

    @Test
    @Tag("sse")
    void eventNamesAndIdsSurvive() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String uri : List.of("/ctl-stream/rich", "/fn-stream/rich")) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(uri), HttpResponseAssertion.builder()
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
                    .build());
            }
        }
    }

    @Test
    void aPublisherOfObjectsIsAJsonArray() throws IOException {
        try (ServerUnderTest server = server()) {
            String expected = null;
            for (String uri : List.of("/ctl-stream/json", "/ctl-stream/json-response", "/fn-stream/json", "/fn-stream/json-async")) {
                String[] body = new String[1];
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(uri), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(response -> {
                        String contentType = response.getHeaders().get(HttpHeaders.CONTENT_TYPE);
                        assertTrue(contentType != null && contentType.startsWith(MediaType.APPLICATION_JSON), () -> uri + " content type: " + contentType);
                        body[0] = response.getBody(String.class).orElse("");
                    })
                    .build());
                assertTrue(body[0].startsWith("[") && body[0].contains("\"name\":\"a\"") && body[0].contains("\"name\":\"b\""), body[0]);
                if (expected == null) {
                    expected = body[0];
                } else {
                    assertEquals(expected, body[0], uri);
                }
            }
        }
    }

    @Test
    void anErrorBeforeTheFirstElementIsAnErrorResponse() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String uri : List.of("/ctl-stream/failing", "/fn-stream/failing")) {
                AssertionUtils.assertThrows(server, HttpRequest.GET(uri), HttpResponseAssertion.builder()
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build());
            }
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static Publisher<Event<String>> events() {
        return Flux.just("one", "two", "three").map(Event::of);
    }

    private static Publisher<Event<String>> rich() {
        return Flux.just(
            Event.of("hello").name("greeting").id("1"),
            Event.of("goodbye").name("farewell").id("2")
        );
    }

    private static Publisher<Map<String, String>> json() {
        return Flux.just(Map.of("name", "a"), Map.of("name", "b"));
    }

    private static Publisher<Map<String, String>> failing() {
        return Flux.error(new IllegalStateException("broken stream"));
    }

    @Controller("/ctl-stream")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class StreamController {

        @Get("/events")
        @Produces(MediaType.TEXT_EVENT_STREAM)
        Publisher<Event<String>> events() {
            return HandlerRouteStreamingTest.events();
        }

        @Get("/events-response")
        @Produces(MediaType.TEXT_EVENT_STREAM)
        HttpResponse<?> eventsResponse() {
            return HttpResponse.ok(HandlerRouteStreamingTest.events());
        }

        @Get("/events-response-async")
        @Produces(MediaType.TEXT_EVENT_STREAM)
        CompletableFuture<HttpResponse<?>> eventsResponseAsync() {
            return CompletableFuture.completedFuture(HttpResponse.ok(HandlerRouteStreamingTest.events()));
        }

        @Get("/json-response")
        HttpResponse<?> jsonResponse() {
            return HttpResponse.ok(HandlerRouteStreamingTest.json());
        }

        @Get("/rich")
        @Produces(MediaType.TEXT_EVENT_STREAM)
        Publisher<Event<String>> rich() {
            return HandlerRouteStreamingTest.rich();
        }

        @Get("/json")
        Publisher<Map<String, String>> json() {
            return HandlerRouteStreamingTest.json();
        }

        @Get("/failing")
        Publisher<Map<String, String>> failing() {
            return HandlerRouteStreamingTest.failing();
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class StreamRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.path("/fn-stream", group -> {
                group.GET("/events", (request, pathVariables) -> HttpResponse.ok(events()))
                    .produces(MediaType.TEXT_EVENT_STREAM_TYPE);
                group.asyncGET("/events-async", (request, pathVariables) -> CompletableFuture.completedFuture(HttpResponse.ok(events())))
                    .produces(MediaType.TEXT_EVENT_STREAM_TYPE);
                group.GET("/events-response-type", (request, pathVariables) -> HttpResponse.ok(events()).contentType(MediaType.TEXT_EVENT_STREAM_TYPE));
                group.GET("/rich", (request, pathVariables) -> HttpResponse.ok(rich()))
                    .produces(MediaType.TEXT_EVENT_STREAM_TYPE);
                group.GET("/json", (request, pathVariables) -> HttpResponse.ok(json()));
                group.asyncGET("/json-async", (request, pathVariables) -> CompletableFuture.completedFuture(HttpResponse.ok(json())));
                group.GET("/failing", (request, pathVariables) -> HttpResponse.ok(failing()));
            });
        }
    }
}
