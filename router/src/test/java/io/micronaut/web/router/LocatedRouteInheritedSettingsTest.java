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
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A located route inherits the settings of the groups of its locator routes, like a route
 * declared in those groups, of the closest locator route first: its attributes and its media
 * types, unless it or a group of its located routes sets them. Its URI template is the template
 * under the prefixes of its locator routes.
 */
class LocatedRouteInheritedSettingsTest {
    private static final List<MediaType> XML = List.of(MediaType.APPLICATION_XML_TYPE);
    private static final List<MediaType> TEXT = List.of(MediaType.TEXT_PLAIN_TYPE);
    private static final List<MediaType> HTML = List.of(MediaType.TEXT_HTML_TYPE);

    @Test
    void aLocatedRouteInheritsTheMediaTypesAndTheAttributesOfTheGroupsOfItsLocatorRoutes() {
        LocatedRoutes<?> lines = TestLocatedRoutes.of(line -> {
            line.GET("/inherited", LocatedRouteInheritedSettingsTest::ok);
            line.GET("/own").produces(MediaType.TEXT_HTML_TYPE).attribute("kind", "line").handle(LocatedRouteInheritedSettingsTest::ok);
        });
        LocatedRoutes<?> orders = TestLocatedRoutes.of(order -> {
            order.GET("/inherited", LocatedRouteInheritedSettingsTest::ok);
            order.group(grouped -> {
                // the group of the located routes overrides the groups of the locator routes
                grouped.produces(MediaType.TEXT_PLAIN_TYPE).attribute("kind", "grouped");
                grouped.GET("/grouped", LocatedRouteInheritedSettingsTest::ok);
                grouped.locate("/grouped-lines/{line}", (request, pathVariables) -> "line", target -> lines);
            });
            order.locate("/lines/{line}", (request, pathVariables) -> "line", target -> lines);
        });
        Router router = router(routes -> routes.path("/orders", group -> {
            group.produces(MediaType.APPLICATION_XML_TYPE).consumes(MediaType.APPLICATION_XML_TYPE).attribute("kind", "order").attribute("bean", "shop");
            group.locate("/{id}", (request, pathVariables) -> "order", target -> orders);
        }));

        UriRouteInfo<?, ?> inherited = route(router, "/orders/1/inherited");
        assertEquals(XML, inherited.getProduces());
        assertEquals(XML, inherited.getConsumes());
        assertEquals(Map.of("kind", "order", "bean", "shop"), inherited.getAttributes());

        UriRouteInfo<?, ?> grouped = route(router, "/orders/1/grouped");
        assertEquals(TEXT, grouped.getProduces());
        assertEquals(XML, grouped.getConsumes());
        assertEquals(Map.of("kind", "grouped", "bean", "shop"), grouped.getAttributes());

        // located again: what the outer locator route inherits, and what the closer one does over it
        UriRouteInfo<?, ?> line = route(router, "/orders/1/lines/2/inherited");
        assertEquals(XML, line.getProduces());
        assertEquals(Map.of("kind", "order", "bean", "shop"), line.getAttributes());
        UriRouteInfo<?, ?> groupedLine = route(router, "/orders/1/grouped-lines/2/inherited");
        assertEquals(TEXT, groupedLine.getProduces());
        assertEquals(XML, groupedLine.getConsumes());
        assertEquals(Map.of("kind", "grouped", "bean", "shop"), groupedLine.getAttributes());
        UriRouteInfo<?, ?> own = route(router, "/orders/1/grouped-lines/2/own");
        assertEquals(HTML, own.getProduces());
        assertEquals(Map.of("kind", "line", "bean", "shop"), own.getAttributes());
    }

    @Test
    void theRouteAtALocationIsBuiltOnce() {
        LocatedRoutes<?> orders = TestLocatedRoutes.of(order -> order.GET("/items", LocatedRouteInheritedSettingsTest::ok));
        Router router = router(routes -> routes.path("/orders", group -> {
            group.produces(MediaType.APPLICATION_XML_TYPE);
            group.locate("/{id}", (request, pathVariables) -> "order", target -> orders);
        }));

        assertSame(route(router, "/orders/1/items"), route(router, "/orders/2/items"));
    }

    @Test
    void theMediaTypesOfTheLocatedRouteSelectIt() {
        LocatedRoutes<?> orders = TestLocatedRoutes.of(order -> order.GET("/items", LocatedRouteInheritedSettingsTest::ok));
        Router router = router(routes -> routes.path("/orders", group -> {
            group.produces(MediaType.APPLICATION_XML_TYPE);
            group.locate("/{id}", (request, pathVariables) -> "order", target -> orders);
        }));

        assertNull(router.findClosest(HttpRequest.GET("/orders/1/items").accept(MediaType.APPLICATION_JSON_TYPE)));
        assertNotNull(router.findClosest(HttpRequest.GET("/orders/1/items").accept(MediaType.APPLICATION_XML_TYPE)));
    }

    @Test
    void theUriTemplateOfALocatedRouteIsUnderThePrefixesOfItsLocatorRoutes() {
        LocatedRoutes<?> lines = TestLocatedRoutes.of(line -> {
            line.GET("/", LocatedRouteInheritedSettingsTest::ok);
            line.GET("/name", LocatedRouteInheritedSettingsTest::ok);
        });
        LocatedRoutes<?> orders = TestLocatedRoutes.of(order -> {
            order.GET("/items/{item}", LocatedRouteInheritedSettingsTest::ok);
            order.path("/grouped", group -> group.locate("/lines/{line}", (request, pathVariables) -> "line", target -> lines));
        });
        Router router = router(routes -> {
            routes.locate("/orders/{id}", (request, pathVariables) -> "order", target -> orders);
            routes.path("/root", group -> group.locate("/", (request, pathVariables) -> "order", target -> orders));
            routes.GET("/plain/{id}", LocatedRouteInheritedSettingsTest::ok);
        });

        assertEquals("/orders/{id}/items/{item}", uriTemplate(router, "/orders/1/items/2"));
        assertEquals("/orders/{id}/grouped/lines/{line}/name", uriTemplate(router, "/orders/1/grouped/lines/2/name"));
        // a route at the prefix of its locator route
        assertEquals("/orders/{id}/grouped/lines/{line}", uriTemplate(router, "/orders/1/grouped/lines/2"));
        assertEquals("/root/items/{item}", uriTemplate(router, "/root/items/2"));
        assertEquals("/plain/{id}", uriTemplate(router, "/plain/1"));
    }

    private static String uriTemplate(Router router, String path) {
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET(path));
        assertNotNull(match, path);
        return RouteLocator.uriTemplate(match);
    }

    private static UriRouteInfo<?, ?> route(Router router, String path) {
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET(path));
        assertNotNull(match, path);
        return match.getRouteInfo();
    }

    private static HttpResponse<?> ok(HttpRequest<?> request, PathVariables pathVariables) {
        return HttpResponse.ok();
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
