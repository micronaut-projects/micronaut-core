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

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.RouteCaptures;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.RouteTemplateParser;
import io.micronaut.http.uri.RouteTemplateVariable;
import io.micronaut.http.uri.UriRouteTemplate;
import io.micronaut.http.uri.UriTypeMatchTemplate;
import io.micronaut.web.router.spi.RouteMatchSelector;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The routes of a route selector at a location, the locator routes of a selector, and the
 * prefixes a parser of a group validates.
 */
class RouteTemplateSelectionTest {

    @Test
    void theMatchesOfACustomTemplateShareItsVariableMap() {
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/items/:item")).handle((request, variables) -> HttpResponse.ok()));
        UriRouteMatch<Object, Object> first = router.findClosest(HttpRequest.GET("/items/1"));
        UriRouteMatch<Object, Object> second = router.findClosest(HttpRequest.GET("/items/2"));
        assertNotNull(first);
        assertNotNull(second);
        assertSame(first.getVariableMap(), second.getVariableMap());
        assertEquals(List.of("item"), List.copyOf(first.getVariableMap().keySet()));
        assertEquals("1", first.getVariableValues().get("item"));
        assertEquals("2", second.getVariableValues().get("item"));
    }

    @Test
    void theParserOfAGroupValidatesALocatorPrefix() {
        TestLocatedRoutes<Object> items = TestLocatedRoutes.of(routes ->
            routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/items/:item")).handle((request, variables) -> HttpResponse.ok()));
        // a '?' of a regular expression, which a URI template prefix cannot have
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> routes.group(group ->
            group.templates(ColonRouteTemplateEngine.INSTANCE).locate("/orders/:id((?:[0-9]+))", (request, variables) -> "order", target -> items)));
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/orders/5/items/3"));
        assertNotNull(match);
        assertEquals("5", match.getVariableValues().get("id"));
        assertNull(router.findClosest(HttpRequest.GET("/orders/x/items/3")));
    }

    @Test
    void aRouteSelectorSeesTheRoutesWithTheSettingsTheyInheritAtALocation() {
        // the located route produces what the group of its locator route produces
        TestLocatedRoutes<Object> items = TestLocatedRoutes.of(routes ->
            routes.route(HttpMethod.GET, SelectingColonRouteTemplateEngine.template("/items/:item"))
                .selectedBy(SelectingColonRouteTemplateEngine.INSTANCE)
                .handle((request, variables) -> HttpResponse.ok()));
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> routes.group(group -> {
            group.produces(MediaType.TEXT_PLAIN_TYPE);
            group.locate("/orders/{id}", (request, variables) -> "order", target -> items);
        }));
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/orders/5/items/3").accept(MediaType.TEXT_PLAIN_TYPE));
        assertNotNull(match);
        assertEquals(MediaType.TEXT_PLAIN_TYPE, match.getSelectedMediaType().orElseThrow());
        assertEquals(List.of(MediaType.TEXT_PLAIN_TYPE), match.getRouteInfo().getProduces());
        assertTrue(methods(router.findAny(HttpRequest.GET("/orders/5/items/3").accept(MediaType.TEXT_PLAIN_TYPE))).contains("GET"));
    }

    @Test
    void theAllowedMethodsAreTheOnesOfTheLocatorTheSelectorOrders() {
        TestLocatedRoutes<Object> gets = TestLocatedRoutes.of(routes ->
            routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/items/:item")).handle((request, variables) -> HttpResponse.ok()));
        TestLocatedRoutes<Object> posts = TestLocatedRoutes.of(routes ->
            routes.route(HttpMethod.POST, ColonRouteTemplateEngine.template("/items/:item")).handle((request, variables) -> HttpResponse.ok()));
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> routes.group(group -> {
            group.templates(OrderedColonRouteTemplateEngine.INSTANCE).selectedBy(OrderedColonRouteTemplateEngine.INSTANCE);
            group.locate("/o/:id", (request, variables) -> "plain", target -> gets);
            // more variables with a regular expression: more specific for the selector
            group.locate("/o/:id([0-9]+)", (request, variables) -> "pattern", target -> posts);
        }));
        assertNotNull(router.findClosest(HttpRequest.POST("/o/1/items/2", "")));
        assertNull(router.findClosest(HttpRequest.GET("/o/1/items/2")));
        // the 405 and the CORS preflight request see the routes of the same locator
        assertEquals(Set.of("POST"), methods(router.findAny(HttpRequest.GET("/o/1/items/2"))));
    }

    @Test
    void theOrderOfASelectorChoosesALocatorRouteBeforeTheMediaTypes() {
        TestLocatedRoutes<Object> root = TestLocatedRoutes.of(routes ->
            routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/")).handle((request, variables) -> HttpResponse.ok()));
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> routes.group(group -> {
            group.templates(OrderedColonRouteTemplateEngine.INSTANCE).selectedBy(OrderedColonRouteTemplateEngine.INSTANCE);
            // more literal text: the locator route is more specific for the selector
            group.locate("/r/x", (request, variables) -> "x", target -> root);
            group.GET("/r/:id").produces(MediaType.TEXT_PLAIN_TYPE).handle((request, variables) -> HttpResponse.ok());
        }));
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/r/x").accept(MediaType.TEXT_PLAIN_TYPE));
        assertNotNull(match);
        assertEquals(ColonRouteTemplateEngine.template("/"), match.getRouteInfo().getRouteTemplate());
    }

    @Test
    void aRouteSelectorIsGivenARequestOfTheMethodOfTheRoutesForTheAllowedMethods() {
        RouteMatchSelector byMethod = (request, matches) -> matches.stream()
            .filter(match -> match.getRouteInfo().getHttpMethodName().equals(request.getMethodName()))
            .map(RouteMatchSelector.Selection::of)
            .toList();
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/m/:id")).selectedBy(byMethod).handle((request, variables) -> HttpResponse.ok()));
        assertNotNull(router.findClosest(HttpRequest.GET("/m/1")));
        // a PUT request: the GET route is selected for a GET request
        assertTrue(methods(router.findAny(HttpRequest.PUT("/m/1", ""))).contains("GET"));
        assertTrue(methods(router.findAny(HttpRequest.OPTIONS("/m/1"))).contains("GET"));
    }

    @Test
    void anExplicitHeadRouteOfATypedTemplateDoesNotHideTheImplicitHeadRouteOfAnotherTypedTemplate() {
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> {
            routes.route(HttpMethod.GET, UriRouteTemplate.of(new UriTypeMatchTemplate("/items/{id}", String.class))).handle((request, variables) -> HttpResponse.ok());
            routes.route(HttpMethod.HEAD, UriRouteTemplate.of(new UriTypeMatchTemplate("/items/{id}", Integer.class))).handle((request, variables) -> HttpResponse.ok());
        });
        // the explicit HEAD route and the implicit one of the GET route
        assertEquals(2, router.uriRoutes().filter(route -> route.getHttpMethod() == HttpMethod.HEAD).count());
    }

    @Test
    void theSelectorSelectsAmongItsRoutesLeftOnceItsLocatorRoutesAreLessSpecific() {
        TestLocatedRoutes<Object> table = TestLocatedRoutes.of(routes ->
            routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/:rest")).handle((request, variables) -> HttpResponse.ok()));
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> routes.group(group -> {
            group.templates(SelectingColonRouteTemplateEngine.INSTANCE).selectedBy(SelectingColonRouteTemplateEngine.INSTANCE);
            group.GET("/c/:id/summary").produces(MediaType.APPLICATION_JSON_TYPE).handle((request, variables) -> HttpResponse.ok());
            group.GET("/c/:id/summary").produces(MediaType.APPLICATION_XML_TYPE).handle((request, variables) -> HttpResponse.ok());
            group.locate("/c/:id", (request, variables) -> "c", target -> table);
        }));
        // the two routes are more specific than the locator route: the selector negotiates between them
        UriRouteMatch<Object, Object> any = router.findClosest(HttpRequest.GET("/c/5/summary"));
        assertNotNull(any);
        assertEquals(MediaType.APPLICATION_JSON_TYPE, any.getSelectedMediaType().orElseThrow());
        UriRouteMatch<Object, Object> xml = router.findClosest(HttpRequest.GET("/c/5/summary").accept(MediaType.APPLICATION_XML_TYPE));
        assertNotNull(xml);
        assertEquals(MediaType.APPLICATION_XML_TYPE, xml.getSelectedMediaType().orElseThrow());
        assertEquals(1, router.findAllClosest(HttpRequest.GET("/c/5/summary")).size());
    }

    @Test
    void theAllowedMethodsLocateWithTheOrderOfEachMethod() {
        TestLocatedRoutes<Object> gets = TestLocatedRoutes.of(routes ->
            routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/items/:item")).handle((request, variables) -> HttpResponse.ok()));
        TestLocatedRoutes<Object> posts = TestLocatedRoutes.of(routes ->
            routes.route(HttpMethod.POST, ColonRouteTemplateEngine.template("/items/:item")).handle((request, variables) -> HttpResponse.ok()));
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> {
            routes.group(group -> {
                group.templates(OrderedColonRouteTemplateEngine.INSTANCE).selectedBy(OrderedColonRouteTemplateEngine.INSTANCE);
                group.locate("/o/:id", (request, variables) -> "plain", target -> gets);
                group.locate("/o/:id([0-9]+)", (request, variables) -> "pattern", target -> posts);
            });
            // a route of PUT without the selector: the Micronaut policy locates a PUT request, which prefers fewer regular expressions
            routes.route(HttpMethod.PUT, ColonRouteTemplateEngine.template("/o/:id/items/:item")).handle((request, variables) -> HttpResponse.ok());
        });
        // the order of the selector locates a request of GET or POST: the routes of POST
        assertNull(router.findClosest(HttpRequest.GET("/o/1/items/2")));
        assertNotNull(router.findClosest(HttpRequest.POST("/o/1/items/2", "")));
        assertNotNull(router.findClosest(HttpRequest.PUT("/o/1/items/2", "")));
        // the allowed methods agree with routing, method by method
        assertEquals(Set.of("POST", "PUT"), methods(router.findAny(HttpRequest.GET("/o/1/items/2"))));
    }

    @Test
    void theRoutesASelectorOrdersEquallyAreNarrowedByTheirMediaTypes() {
        TestLocatedRoutes<Object> table = TestLocatedRoutes.of(routes -> routes.GET("/", (request, variables) -> HttpResponse.ok()));
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> routes.group(group -> {
            group.templates(OrderedColonRouteTemplateEngine.INSTANCE).selectedBy(OrderedColonRouteTemplateEngine.INSTANCE);
            group.locate("/r/:id", (request, variables) -> "r", target -> table);
            group.GET("/r/:id").produces(MediaType.TEXT_PLAIN_TYPE).handle((request, variables) -> HttpResponse.ok());
        }));
        // the locator route and the route are equally specific by the order: the route produces the accepted type
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/r/x").accept(MediaType.TEXT_PLAIN_TYPE));
        assertNotNull(match);
        assertEquals(List.of(MediaType.TEXT_PLAIN_TYPE), match.getRouteInfo().getProduces());
    }

    @Test
    void theOrderOfASelectorComparesTheRoutesWithTheSettingsTheyInheritAtALocation() {
        RouteMatchSelector ranked = new RouteMatchSelector() {
            @Override
            public List<Selection> select(HttpRequest<?> request, List<UriRouteMatch<?, ?>> matches) {
                return matches.stream().map(Selection::of).toList();
            }

            @Override
            public Optional<Comparator<UriRouteInfo<?, ?>>> order() {
                // the rank the routes inherit from the group of the outer locator route
                return Optional.of(Comparator.<UriRouteInfo<?, ?>>comparingInt(route -> route.getAttribute("rank", Integer.class).orElseThrow())
                    .thenComparing(OrderedColonRouteTemplateEngine.ROUTE_ORDER));
            }
        };
        TestLocatedRoutes<Object> plain = TestLocatedRoutes.of(routes -> { });
        TestLocatedRoutes<Object> pattern = TestLocatedRoutes.of(routes ->
            routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/items/:item")).handle((request, variables) -> HttpResponse.ok()));
        TestLocatedRoutes<Object> table = TestLocatedRoutes.of(routes -> routes.group(group -> {
            group.templates(ColonRouteTemplateEngine.INSTANCE).selectedBy(ranked);
            group.locate("/a/:id", (request, variables) -> "plain", target -> plain);
            group.locate("/a/:id([0-9]+)", (request, variables) -> "pattern", target -> pattern);
        }));
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> routes.group(group -> {
            group.attribute("rank", 1);
            group.locate("/o/{id}", (request, variables) -> "o", target -> table);
        }));
        // the two locator routes of the table rank equally: the one with a regular expression is more specific
        assertNotNull(router.findClosest(HttpRequest.GET("/o/1/a/2/items/3")));
        assertEquals(Set.of("GET"), methods(router.findAny(HttpRequest.GET("/o/1/a/2/items/3"))).stream()
            .filter(method -> !"HEAD".equals(method)).collect(Collectors.toSet()));
    }

    @Test
    void theAllowedMethodsOfACustomMethodCountItsRoutesOfAnyMethodToo() {
        RouteMatchSelector rejecting = (request, matches) -> List.of();
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> {
            routes.any("/x", (request, variables) -> HttpResponse.ok());
            routes.route("PROPFIND", "/x").selectedBy(rejecting).handle((request, variables) -> HttpResponse.ok());
        });
        // a route of any method among its matches: the Micronaut policy, not the selector
        UriRouteMatch<Object, Object> propfind = router.findClosest(HttpRequest.create(HttpMethod.CUSTOM, "/x", "PROPFIND"));
        assertNotNull(propfind);
        assertEquals("PROPFIND", propfind.getRouteInfo().getHttpMethodName());
        assertTrue(methods(router.findAny(HttpRequest.OPTIONS("/x"))).contains("PROPFIND"));
    }

    @Test
    void aTemplateOfAnotherLanguageIsMountedOnceUnderTheContextPathAndThePrefixOfItsGroup() {
        List<String> mounts = new ArrayList<>();
        RouteTemplateParser parser = expression -> new MountRecording(ColonRouteTemplateEngine.template(expression), mounts);
        Router router = RouteTemplateEngineOrderTest.router("/ctx", routes -> routes.group(outer -> outer.templates(parser).path("/api", group -> {
            group.GET("/items/:id").handle((request, variables) -> HttpResponse.ok());
            group.locate("/orders/:id", (request, variables) -> "order", target -> TestLocatedRoutes.of(located ->
                located.GET("/lines", (request, variables) -> HttpResponse.ok())));
        })));
        assertEquals(List.of("/ctx/api", "/ctx/api"), mounts);
        assertNotNull(router.findClosest(HttpRequest.GET("/ctx/api/items/1")));
        assertNotNull(router.findClosest(HttpRequest.GET("/ctx/api/orders/1/lines")));
    }

    @Test
    void theAllowedMethodsAgreeWithRoutingWhenALocatorRouteIsAmongTheMatches() {
        TestLocatedRoutes<Object> posts = TestLocatedRoutes.of(routes ->
            routes.route(HttpMethod.POST, ColonRouteTemplateEngine.template("/:rest")).handle((request, variables) -> HttpResponse.ok()));
        RouteMatchSelector rejecting = (request, matches) -> List.of();
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> routes.group(group -> {
            group.selectedBy(rejecting).produces(MediaType.APPLICATION_JSON_TYPE);
            group.locate("/o", (request, variables) -> "o", target -> posts);
            group.GET("/o/x").produces(MediaType.TEXT_PLAIN_TYPE).handle((request, variables) -> HttpResponse.ok());
        }));
        HttpRequest<?> get = HttpRequest.GET("/o/x").accept(MediaType.TEXT_PLAIN_TYPE);
        // the selector rejects the route of the request, and the allowed methods agree
        assertNull(router.findClosest(get));
        assertTrue(!methods(router.findAny(get)).contains("GET"));
    }

    @Test
    void aGetRouteOfAUriTemplateIsAWebSocketRouteAndARouteOfAnotherLanguageIsNot() {
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.route(HttpMethod.GET, UriRouteTemplate.of("/ws")).webSocket(ws -> ws.onOpen((session, request) -> null)));
        assertNotNull(router);
        IllegalStateException error = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
            RouteTemplateEngineOrderTest.router(null, routes -> routes.group(group -> {
                group.templates(ColonRouteTemplateEngine.INSTANCE);
                group.GET("/ws/:id").webSocket(ws -> ws.onOpen((session, request) -> null));
            })));
        assertTrue(error.getMessage().contains("not a template of another language"), error.getMessage());
    }

    @Test
    void theIndexDoesNotSkipATemplateWhoseRequiredPrefixEndsWithASlash() {
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> {
            for (int i = 0; i < 20; i++) {
                routes.GET("/other" + i, (request, variables) -> HttpResponse.ok());
            }
            routes.route(HttpMethod.GET, new ExactTemplate("/a/")).handle((request, variables) -> HttpResponse.ok());
        });
        assertNotNull(router.findClosest(HttpRequest.GET("/a/")));
    }

    @Test
    void aTemplateOfASubclassOfUriMatchTemplateIsNotMounted() {
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> RouteTemplateEngineOrderTest.router("/ctx", routes ->
            routes.route(HttpMethod.GET, UriRouteTemplate.of(new UriTypeMatchTemplate("/items/{id}", Integer.class))).handle((request, variables) -> HttpResponse.ok())));
        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> RouteTemplateEngineOrderTest.router(null, routes ->
            routes.path("/api", api -> api.route(HttpMethod.GET, UriRouteTemplate.of(new UriTypeMatchTemplate("/items/{id}", Integer.class))))));
        assertTrue(error.getMessage().contains("/api"), error.getMessage());
    }

    @Test
    void theRoutesOfALocationInheritTheSelectorOfTheGroupsOfTheLocatorRoute() {
        TestLocatedRoutes<Object> items = TestLocatedRoutes.of(routes ->
            routes.route(HttpMethod.GET, SelectingColonRouteTemplateEngine.template("/items/:item")).handle((request, variables) -> HttpResponse.ok()));
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> routes.group(group -> {
            group.selectedBy(SelectingColonRouteTemplateEngine.INSTANCE);
            group.locate("/orders/{id}", (request, variables) -> "order", target -> items);
        }));
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/orders/5/items/3"));
        assertNotNull(match);
        // selected by the selector: it negotiated the media type
        assertTrue(match.getSelectedMediaType().isPresent());
    }

    /**
     * A template that matches its expression only, with its expression as the required prefix.
     *
     * @param expression The expression
     */
    private record ExactTemplate(String expression) implements RouteTemplate {
        @Override
        public List<RouteTemplateVariable> variables() {
            return List.of();
        }

        @Override
        public @Nullable RouteCaptures match(String rawPath) {
            return rawPath.equals(expression) ? RouteCaptures.copyOf(rawPath, List.of(), List.of()) : null;
        }

        @Override
        public RouteTemplate mount(String prefix) {
            return new ExactTemplate(prefix + expression);
        }

        @Override
        public String requiredPrefix() {
            return expression;
        }

        @Override
        public int rawLength() {
            return expression.length();
        }
    }

    @Test
    void aWebSocketRouteOfAParserGroupIsParsedOnce() {
        java.util.concurrent.atomic.AtomicInteger parsed = new java.util.concurrent.atomic.AtomicInteger();
        RouteTemplateParser counting = expression -> {
            parsed.incrementAndGet();
            return UriRouteTemplate.of(expression);
        };
        RouteTemplateEngineOrderTest.router(null, routes -> routes.group(group -> {
            group.templates(counting);
            group.GET("/ws").webSocket(ws -> ws.onOpen((session, request) -> null));
        }));
        assertEquals(1, parsed.get());
    }

    @Test
    void theOrderOfASelectorIsReadOnceForEachRoute() {
        java.util.concurrent.atomic.AtomicInteger orders = new java.util.concurrent.atomic.AtomicInteger();
        RouteMatchSelector selector = new RouteMatchSelector() {
            @Override
            public List<Selection> select(HttpRequest<?> request, List<UriRouteMatch<?, ?>> matches) {
                return List.of(Selection.of(matches.get(0)));
            }

            @Override
            public Optional<Comparator<UriRouteInfo<?, ?>>> order() {
                orders.incrementAndGet();
                return Optional.empty();
            }
        };
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/o/:id")).selectedBy(selector).handle((request, variables) -> HttpResponse.ok()));
        int built = orders.get();
        for (int i = 0; i < 5; i++) {
            assertNotNull(router.findClosest(HttpRequest.GET("/o/" + i)));
            router.findAny(HttpRequest.GET("/o/" + i));
        }
        assertEquals(built, orders.get());
    }

    @Test
    void aTemplateIsMatchedWithThePathWithoutItsQuery() {
        List<String> paths = new ArrayList<>();
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.route(HttpMethod.GET, new RecordingTemplate("/a", paths)).handle((request, variables) -> HttpResponse.ok()));
        assertTrue(router.route(HttpMethod.GET, "/a?x=1").isPresent());
        assertEquals(List.of("/a"), paths);
    }

    @Test
    void aTemplateOfAParserGroupIsMountedOnceForTheRoutesOfAllItsMethods() {
        java.util.concurrent.atomic.AtomicInteger mounted = new java.util.concurrent.atomic.AtomicInteger();
        RouteTemplateParser counting = expression -> new CountingMountTemplate(ColonRouteTemplateEngine.template(expression), mounted);
        Router router = RouteTemplateEngineOrderTest.router("/ctx", routes -> routes.group(group -> {
            group.templates(counting);
            group.any("/items/:id", (request, variables) -> HttpResponse.ok());
        }));
        assertEquals(1, mounted.get());
        assertNotNull(router.findClosest(HttpRequest.POST("/ctx/items/1", "")));
    }

    @Test
    void theRoutesOfTheMethodsOfAStringDeclarationShareOneTemplate() {
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> routes.any("/shared/{id}", (request, variables) -> HttpResponse.ok()));
        List<RouteTemplate> templates = router.uriRoutes()
            .filter(route -> route.getRouteTemplate().expression().equals("/shared/{id}"))
            .map(UriRouteInfo::getRouteTemplate)
            .toList();
        assertTrue(templates.size() > 2, templates.toString());
        assertTrue(templates.stream().allMatch(template -> template == templates.get(0)));
    }

    /**
     * A template that records the paths it is given and matches its expression only.
     *
     * @param expression The expression
     * @param paths      The paths given to match
     */
    private record RecordingTemplate(String expression, List<String> paths) implements RouteTemplate {
        @Override
        public List<RouteTemplateVariable> variables() {
            return List.of();
        }

        @Override
        public @Nullable RouteCaptures match(String rawPath) {
            paths.add(rawPath);
            return rawPath.equals(expression) ? RouteCaptures.of(rawPath, List.of(), List.of()) : null;
        }

        @Override
        public RouteTemplate mount(String prefix) {
            return new RecordingTemplate(prefix + expression, paths);
        }

        @Override
        public String requiredPrefix() {
            return expression;
        }

        @Override
        public int rawLength() {
            return expression.length();
        }
    }

    /**
     * A template that counts how many times it is mounted.
     *
     * @param delegate The template
     * @param mounted  The count
     */
    private record CountingMountTemplate(RouteTemplate delegate,
                                         java.util.concurrent.atomic.AtomicInteger mounted) implements RouteTemplate {
        @Override
        public String expression() {
            return delegate.expression();
        }

        @Override
        public List<RouteTemplateVariable> variables() {
            return delegate.variables();
        }

        @Override
        public @Nullable RouteCaptures match(String rawPath) {
            return delegate.match(rawPath);
        }

        @Override
        public RouteTemplate mount(String prefix) {
            mounted.incrementAndGet();
            return new CountingMountTemplate(delegate.mount(prefix), mounted);
        }

        @Override
        public String requiredPrefix() {
            return delegate.requiredPrefix();
        }

        @Override
        public int rawLength() {
            return delegate.rawLength();
        }
    }

    private static Set<String> methods(List<? extends UriRouteMatch<?, ?>> matches) {
        return matches.stream().map(match -> match.getRouteInfo().getHttpMethodName()).collect(Collectors.toSet());
    }

    /**
     * A template that records the prefixes it is mounted at.
     */
    private record MountRecording(RouteTemplate delegate, List<String> mounts) implements RouteTemplate {
        @Override
        public String expression() {
            return delegate.expression();
        }

        @Override
        public List<RouteTemplateVariable> variables() {
            return delegate.variables();
        }

        @Override
        public @Nullable RouteCaptures match(String rawPath) {
            return delegate.match(rawPath);
        }

        @Override
        public RouteTemplate mount(String prefix) {
            mounts.add(prefix);
            return new MountRecording(delegate.mount(prefix), mounts);
        }

        @Override
        public int rawLength() {
            return delegate.rawLength();
        }

        @Override
        public String requiredPrefix() {
            return delegate.requiredPrefix();
        }
    }
}
