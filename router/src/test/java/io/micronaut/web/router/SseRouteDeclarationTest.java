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
package io.micronaut.web.router;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A server-sent events route is declared on any method, with or without a body stage, and
 * produces {@code text/event-stream} only.
 */
class SseRouteDeclarationTest {

    @Test
    void anEventStreamRouteMayUseAnyMethod() {
        Router router = router(routes -> routes.POST("/trigger").sse((request, pathVariables, events) -> events.send("started")));
        UriRouteInfo<?, ?> route = route(router, HttpRequest.POST("/trigger", ""));
        assertEquals(HttpMethod.POST, route.getHttpMethod());
        assertEquals(List.of(MediaType.TEXT_EVENT_STREAM_TYPE), route.getProduces());
    }

    @Test
    void anEventStreamRouteReceivesTheBodyOfItsBodyStage() {
        Router router = router(routes -> {
            routes.POST("/completions").body(String.class).sse((request, pathVariables, prompt, events) -> events.send(prompt));
            routes.POST("/form").form().sse((request, pathVariables, form, events) -> events.send(form.getString("name")));
        });
        UriRouteInfo<?, ?> completions = route(router, HttpRequest.POST("/completions", ""));
        assertEquals(List.of(MediaType.TEXT_EVENT_STREAM_TYPE), completions.getProduces());
        UriRouteInfo<?, ?> form = route(router, HttpRequest.POST("/form", ""));
        assertEquals(List.of(MediaType.TEXT_EVENT_STREAM_TYPE), form.getProduces());
        assertTrue(form.getConsumes().contains(MediaType.APPLICATION_FORM_URLENCODED_TYPE), form.getConsumes().toString());
    }

    @Test
    void anEventStreamRouteThatProducesAnotherTypeFails() {
        Router router = router(routes -> {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> routes.GET("/json")
                .produces(MediaType.APPLICATION_JSON_TYPE)
                .sse((request, pathVariables, events) -> events.send("never")));
            assertTrue(failure.getMessage().contains("remove produces"), failure.getMessage());
            // the event stream type itself is allowed
            routes.GET("/events").produces(MediaType.TEXT_EVENT_STREAM_TYPE).sse((request, pathVariables, events) -> events.send("one"));
        });
        // the failed route was dropped, and does not fail the startup
        assertNull(router.findClosest(HttpRequest.GET("/json")));
        assertEquals(List.of(MediaType.TEXT_EVENT_STREAM_TYPE), route(router, HttpRequest.GET("/events")).getProduces());
    }

    @Test
    void anEventStreamRouteMayProduceOtherTypesForItsOtherResponses() {
        Router router = router(routes -> routes.POST("/messages")
            .produces(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
            .sse((request, pathVariables, events) -> events.send("one")));
        assertEquals(List.of(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE),
            route(router, HttpRequest.POST("/messages", "")).getProduces());
    }

    private static UriRouteInfo<?, ?> route(Router router, HttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getPath());
        return match.getRouteInfo();
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
