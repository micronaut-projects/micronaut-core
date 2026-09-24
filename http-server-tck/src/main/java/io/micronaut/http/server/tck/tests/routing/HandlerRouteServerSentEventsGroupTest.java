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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A server-sent events route of a group that produces another media type still answers an event
 * stream: the event stream is the route's own media type, not one it inherits from its group.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteServerSentEventsGroupTest {
    public static final String SPEC_NAME = "HandlerRouteServerSentEventsGroupTest";

    @Test
    void anEventStreamRouteOfAGroupProducingTextAnswersAnEventStream() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> events = server.exchange(HttpRequest.GET("/sse-group/events"), String.class);
            assertEquals(HttpStatus.OK, events.getStatus());
            String contentType = events.getHeaders().get(HttpHeaders.CONTENT_TYPE);
            assertTrue(contentType != null && contentType.startsWith(MediaType.TEXT_EVENT_STREAM), contentType);
            assertEquals("data: one\n\n", events.body());

            // the other routes of the group produce what it produces
            HttpResponse<String> text = server.exchange(HttpRequest.GET("/sse-group/text"), String.class);
            String textType = text.getHeaders().get(HttpHeaders.CONTENT_TYPE);
            assertTrue(textType != null && textType.startsWith(MediaType.TEXT_PLAIN), textType);
            assertEquals("text", text.body());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {

        @Singleton
        HttpRoutes sseGroupRoutes() {
            return routes -> routes.group(group -> {
                group.produces(MediaType.TEXT_PLAIN_TYPE);
                group.sse("/sse-group/events", (request, pathVariables, events) -> {
                    events.send("one");
                    events.complete();
                });
                group.GET("/sse-group/text", (request, pathVariables) -> HttpResponse.ok("text"));
            });
        }
    }
}
