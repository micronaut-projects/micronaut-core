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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A server-sent events route produces an event stream in a group that produces another media
 * type, and inherits the other settings of the group.
 */
class SseRouteGroupSettingsTest {

    @Test
    void anEventStreamRouteKeepsItsMediaTypeInAGroupThatProducesAnother() {
        Router router = router(routes -> routes.path("/g", group -> {
            group.consumes(MediaType.TEXT_PLAIN_TYPE).produces(MediaType.TEXT_PLAIN_TYPE);
            group.sse("/events", (request, pathVariables, events) -> events.complete());
            group.GET("/text", (request, pathVariables) -> HttpResponse.ok());
        }));

        UriRouteInfo<?, ?> events = route(router, "/g/events");
        assertEquals(List.of(MediaType.TEXT_EVENT_STREAM_TYPE), events.getProduces());
        // what it consumes is the group's
        assertEquals(List.of(MediaType.TEXT_PLAIN_TYPE), events.getConsumes());
        assertEquals(List.of(MediaType.TEXT_PLAIN_TYPE), route(router, "/g/text").getProduces());
    }

    private static UriRouteInfo<?, ?> route(Router router, String path) {
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET(path));
        assertNotNull(match, path);
        return match.getRouteInfo();
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
