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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settings of a group that a {@code respond} route takes: the media types it produces,
 * unless the response has a content type, but never what it consumes.
 */
class RespondRouteGroupSettingsTest {

    @Test
    void aRespondRouteProducesTheMediaTypesOfItsGroupUnlessTheResponseHasAContentType() {
        Router router = router(routes -> routes.path("/g", group -> {
            group.produces(MediaType.APPLICATION_JSON_TYPE).consumes(MediaType.APPLICATION_JSON_TYPE);
            group.GET("/plain").respond(HttpResponse.ok());
            group.GET("/typed").respond(HttpResponse.ok("x").contentType(MediaType.TEXT_PLAIN_TYPE));
        }));

        UriRouteInfo<?, ?> plain = route(router, "/g/plain");
        assertEquals(List.of(MediaType.APPLICATION_JSON_TYPE), plain.getProduces(), "the produced types of the group");
        assertTrue(plain.consumesAll(), "never the consumed types of the group");

        UriRouteInfo<?, ?> typed = route(router, "/g/typed");
        assertEquals(List.of(MediaType.TEXT_PLAIN_TYPE), typed.getProduces(), "the content type of the response");
        assertTrue(typed.consumesAll());
    }

    private static UriRouteInfo<?, ?> route(Router router, String path) {
        return router.uriRoutes()
            .filter(route -> route.getUriMatchTemplate().toString().equals(path) && !route.isImplicitHead())
            .findFirst()
            .orElseThrow();
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
