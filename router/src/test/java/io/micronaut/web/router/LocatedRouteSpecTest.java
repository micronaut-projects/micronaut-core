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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.DefaultPathVariables;
import io.micronaut.web.router.builder.LocatedBodyFilterSpec;
import io.micronaut.web.router.builder.LocatedFilterSpec;
import io.micronaut.web.router.builder.LocatedHttpBodyRouteSpec;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedHttpRouteSpec;
import io.micronaut.web.router.builder.LocatedRoutes;
import io.micronaut.web.router.builder.RequestPredicates;
import io.micronaut.web.router.builder.ValueMatcher;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settings, the constraints, the filters and the handlers of a located route, with a body or
 * without, reach the route like the ones of a route that is not located.
 */
class LocatedRouteSpecTest {

    private static final String EXECUTOR = "blocking";
    private static final String NO_EXECUTOR = "No executor selector to find executor: " + EXECUTOR;
    private static final String ORDER = "order";
    private static final String HEADER = "X-Located";
    private static final Predicate<HttpRequest<?>> HAS_HEADER = request -> request.getHeaders().contains(HEADER);
    private static final AnnotationMetadataProvider ELEMENT = new AnnotationMetadataProvider() {
        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return AnnotationMetadata.EMPTY_METADATA;
        }
    };

    @Test
    void everySettingAndConstraintOfALocatedRouteReachesTheRoute() {
        Router router = locatedRouter(located -> {
            configure(located.GET("/plain/{item}")).handle((request, pathVariables, order) -> HttpResponse.ok());
            configure(located.POST("/body/{item}").body(String.class)).handle((request, pathVariables, order, text) -> HttpResponse.ok());
        });
        for (String path : List.of("/orders/1/plain/1", "/orders/1/body/1")) {
            HttpMethod method = path.contains("body") ? HttpMethod.POST : HttpMethod.GET;
            // the condition of the settings
            assertNull(router.findClosest(request(method, path)), path);
            UriRouteMatch<Object, Object> match = router.findClosest(request(method, path).header(HEADER, "yes"));
            assertNotNull(match, path);
            UriRouteInfo<Object, Object> route = match.getRouteInfo();
            assertEquals(List.of(MediaType.TEXT_PLAIN_TYPE), route.getConsumes(), path);
            assertEquals(List.of(MediaType.TEXT_HTML_TYPE), route.getProduces(), path);
            assertEquals(7, route.getOrder(), path);
            assertEquals("value", route.getAttribute("name", String.class).orElseThrow(), path);
            assertEquals(String.class, route.getResponseBodyType().getType(), path);
            AnnotationMetadata annotations = route.getAnnotationMetadata();
            assertTrue(annotations.hasAnnotation(Marker.class), path);
            assertTrue(annotations.hasAnnotation(Other.class), path);
            assertTrue(annotations.hasAnnotation(ByName.class), path);
            assertEquals("b", annotations.stringValue(Tagged.class).orElseThrow(), path);
            // every kind of constraint accepts the item 1 only
            for (String item : List.of("3", "4")) {
                String rejected = path.substring(0, path.length() - 1) + item;
                assertNull(router.findClosest(request(method, rejected).header(HEADER, "yes")), rejected);
            }
        }
    }

    @Test
    void aLocatedRouteCannotExposeAPort() {
        List<Consumer<LocatedHttpRouteBuilder<Object>>> declarations = List.of(
            located -> located.GET("/number").port(9090).handle((request, pathVariables) -> HttpResponse.ok()),
            located -> located.GET("/property").port("9091").handle((request, pathVariables) -> HttpResponse.ok()),
            located -> located.POST("/body-number").body(String.class).port(9090).handle((request, pathVariables, text) -> HttpResponse.ok()),
            located -> located.POST("/body-property").body(String.class).port("9091").handle((request, pathVariables, text) -> HttpResponse.ok()));
        for (Consumer<LocatedHttpRouteBuilder<Object>> declaration : declarations) {
            Router router = locatedRouter(declaration);
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> router.findClosest(HttpRequest.GET("/orders/1/number")));
            assertTrue(error.getMessage().startsWith("Located routes cannot expose ports: "), error.getMessage());
        }
    }

    @Test
    void everyFilterOfALocatedRouteRuns() {
        List<Function<LocatedHttpRouteSpec<Object>, LocatedFilterSpec<Object>>> filters = List.of(
            spec -> spec.before(request -> { }),
            spec -> spec.before((request, propagatedContext) -> { }),
            spec -> spec.beforeAsync(request -> CompletableFuture.completedFuture(null)),
            spec -> spec.beforeAsync((request, propagatedContext) -> CompletableFuture.completedFuture(null)),
            spec -> spec.beforeReplacing(request -> null),
            spec -> spec.beforeReplacing((request, propagatedContext) -> null),
            spec -> spec.beforeReplacingAsync(request -> CompletableFuture.completedFuture(null)),
            spec -> spec.beforeReplacingAsync((request, propagatedContext) -> CompletableFuture.completedFuture(null)),
            spec -> spec.after((request, response) -> { }),
            spec -> spec.after((request, response, propagatedContext) -> { }),
            spec -> spec.afterAsync((request, response) -> CompletableFuture.completedFuture(null)),
            spec -> spec.afterAsync((request, response, propagatedContext) -> CompletableFuture.completedFuture(null)),
            spec -> spec.afterReplacing((request, response) -> null),
            spec -> spec.afterReplacing((request, response, propagatedContext) -> null),
            spec -> spec.afterReplacingAsync((request, response) -> CompletableFuture.completedFuture(null)),
            spec -> spec.afterReplacingAsync((request, response, propagatedContext) -> CompletableFuture.completedFuture(null)));
        List<Function<LocatedHttpBodyRouteSpec<Object, String>, LocatedBodyFilterSpec<Object, String>>> bodyFilters = List.of(
            spec -> spec.before(request -> { }),
            spec -> spec.before((request, propagatedContext) -> { }),
            spec -> spec.beforeAsync(request -> CompletableFuture.completedFuture(null)),
            spec -> spec.beforeAsync((request, propagatedContext) -> CompletableFuture.completedFuture(null)),
            spec -> spec.beforeReplacing(request -> null),
            spec -> spec.beforeReplacing((request, propagatedContext) -> null),
            spec -> spec.beforeReplacingAsync(request -> CompletableFuture.completedFuture(null)),
            spec -> spec.beforeReplacingAsync((request, propagatedContext) -> CompletableFuture.completedFuture(null)),
            spec -> spec.after((request, response) -> { }),
            spec -> spec.after((request, response, propagatedContext) -> { }),
            spec -> spec.afterAsync((request, response) -> CompletableFuture.completedFuture(null)),
            spec -> spec.afterAsync((request, response, propagatedContext) -> CompletableFuture.completedFuture(null)),
            spec -> spec.afterReplacing((request, response) -> null),
            spec -> spec.afterReplacing((request, response, propagatedContext) -> null),
            spec -> spec.afterReplacingAsync((request, response) -> CompletableFuture.completedFuture(null)),
            spec -> spec.afterReplacingAsync((request, response, propagatedContext) -> CompletableFuture.completedFuture(null)));
        List<String> paths = new ArrayList<>();
        Router router = locatedRouter(located -> {
            for (int i = 0; i < filters.size(); i++) {
                LocatedHttpRouteSpec<Object> route = located.GET("/filter/" + i);
                assertSame(route, filters.get(i).apply(route).nonBlocking().executeOn(EXECUTOR).and());
                route.handle((request, pathVariables, order) -> HttpResponse.ok());
                paths.add("/orders/1/filter/" + i);
            }
            for (int i = 0; i < bodyFilters.size(); i++) {
                LocatedHttpBodyRouteSpec<Object, String> route = located.GET("/body-filter/" + i).body(String.class);
                assertSame(route, bodyFilters.get(i).apply(route).nonBlocking().executeOn(EXECUTOR).and());
                route.handle((request, pathVariables, order, text) -> HttpResponse.ok());
                paths.add("/orders/1/body-filter/" + i);
            }
        });
        for (String path : paths) {
            assertEquals(NO_EXECUTOR, failure(router, path), path);
        }
    }

    @Test
    void theHandlersOfALocatedRouteReceiveTheTarget() throws Exception {
        Router router = locatedRouter(located -> {
            located.GET("/sync").handle((request, pathVariables) -> HttpResponse.ok(LocatedRoutes.locatedTarget(pathVariables)));
            located.GET("/async").handleAsync((request, pathVariables, order) -> CompletableFuture.completedFuture(HttpResponse.ok(order)));
            located.GET("/async-plain").handleAsync((request, pathVariables) ->
                CompletableFuture.completedFuture(HttpResponse.ok(LocatedRoutes.locatedTarget(pathVariables))));
            located.DELETE("/fixed").respond(HttpResponse.accepted());
            located.GET("/supplied").respond(() -> HttpResponse.accepted());
            located.GET("/function").respond(pathVariables -> HttpResponse.ok(pathVariables.get("id", String.class)));
            located.POST("/body").body(String.class).handle((request, pathVariables, text) -> HttpResponse.ok(LocatedRoutes.locatedTarget(pathVariables)));
            located.POST("/body-async").body(String.class).handleAsync((request, pathVariables, order, text) ->
                CompletableFuture.completedFuture(HttpResponse.ok(order)));
            located.POST("/form").form().handle((request, pathVariables, order, form) -> HttpResponse.ok(order));
        });
        assertEquals(ORDER, execute(router, HttpRequest.GET("/orders/1/sync")).body());
        assertEquals(ORDER, execute(router, HttpRequest.GET("/orders/1/async")).body());
        assertEquals(ORDER, execute(router, HttpRequest.GET("/orders/1/async-plain")).body());
        assertEquals(HttpStatus.ACCEPTED, execute(router, HttpRequest.DELETE("/orders/1/fixed")).status());
        assertEquals(HttpStatus.ACCEPTED, execute(router, HttpRequest.GET("/orders/1/supplied")).status());
        assertEquals("1", execute(router, HttpRequest.GET("/orders/1/function")).body());
        assertEquals(ORDER, execute(router, HttpRequest.POST("/orders/1/body", "x")).body());
        assertEquals(ORDER, execute(router, HttpRequest.POST("/orders/1/body-async", "x")).body());
        UriRouteMatch<Object, Object> form = router.findClosest(HttpRequest.POST("/orders/1/form", "")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
        assertNotNull(form);
        assertTrue(form.getRouteInfo().getConsumes().contains(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
    }

    @Test
    void aNestedLocatorGivenItsRoutesReceivesTheTargetOfTheOuterLocator() {
        AtomicReference<@Nullable Object> outer = new AtomicReference<>();
        TestLocatedRoutes<Object> lines = TestLocatedRoutes.of(line -> line.GET("/", (request, pathVariables) -> HttpResponse.ok()));
        Router router = locatedRouter(located -> {
            located.locate("/lines/{line}", (request, pathVariables, order) -> {
                outer.set(order);
                return order + "-line";
            }, lines);
            located.locateAsync("/async-lines/{line}", (request, pathVariables, order) -> {
                outer.set(order);
                return CompletableFuture.completedFuture(order + "-async");
            }, lines);
        });
        for (String path : List.of("/orders/1/lines/2", "/orders/1/async-lines/2")) {
            outer.set(null);
            assertNotNull(router.findClosest(HttpRequest.GET(path)), path);
            assertEquals(ORDER, outer.get(), path);
        }
    }

    private static MutableHttpRequest<?> request(HttpMethod method, String path) {
        return HttpRequest.create(method, path).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    private static LocatedHttpRouteSpec<Object> configure(LocatedHttpRouteSpec<Object> spec) {
        return spec.consumesAll()
            .consumes(MediaType.TEXT_PLAIN_TYPE)
            .produces(MediaType.TEXT_HTML_TYPE)
            .nonBlocking()
            .executeOn(EXECUTOR)
            .annotationMetadata(ELEMENT)
            .annotate(AnnotationValue.builder(Marker.class).build())
            .annotate(Other.class.getName())
            .annotate(ByName.class.getName(), builder -> { })
            .annotate(Tagged.class.getName(), tagged -> tagged.value("a"))
            .annotate(Tagged.class, tagged -> tagged.value("b"))
            .annotate(Marker.class)
            .attribute("name", "value")
            .where(HAS_HEADER)
            .where(RequestPredicates.header(HEADER))
            .order(7)
            .responseType(String.class)
            .constrain(pathVariables -> "1".equals(pathVariables.get("id", String.class)))
            .constrain("item", value -> !value.isEmpty())
            .constrain("item", Integer.class, value -> value < 4)
            .constrain("item", List.of("1", "4"))
            .constrain("item", ValueMatcher.oneOf("1", "3", "4"))
            .constrain(Map.of("item", ValueMatcher.equalTo("1")));
    }

    private static LocatedHttpBodyRouteSpec<Object, String> configure(LocatedHttpBodyRouteSpec<Object, String> spec) {
        return spec.consumesAll()
            .consumes(MediaType.TEXT_PLAIN_TYPE)
            .produces(MediaType.TEXT_HTML_TYPE)
            .nonBlocking()
            .executeOn(EXECUTOR)
            .annotationMetadata(ELEMENT)
            .annotate(AnnotationValue.builder(Marker.class).build())
            .annotate(Other.class.getName())
            .annotate(ByName.class.getName(), builder -> { })
            .annotate(Tagged.class.getName(), tagged -> tagged.value("a"))
            .annotate(Tagged.class, tagged -> tagged.value("b"))
            .annotate(Marker.class)
            .attribute("name", "value")
            .where(HAS_HEADER)
            .where(RequestPredicates.header(HEADER))
            .order(7)
            .responseType(String.class)
            .constrain(pathVariables -> "1".equals(pathVariables.get("id", String.class)))
            .constrain("item", value -> !value.isEmpty())
            .constrain("item", Integer.class, value -> value < 4)
            .constrain("item", List.of("1", "4"))
            .constrain("item", ValueMatcher.oneOf("1", "3", "4"))
            .constrain(Map.of("item", ValueMatcher.equalTo("1")));
    }

    /**
     * Bind the arguments of a handler like the server does, and run it.
     */
    private static HttpResponse<?> execute(Router router, HttpRequest<?> request) throws Exception {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getPath());
        Object target = ((AbstractRouteMatch<?, ?>) match).resolvedTarget();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("request", request);
        arguments.put("pathVariables", new DefaultPathVariables(match.getVariableValues(), ConversionService.SHARED, target));
        request.getBody().ifPresent(body -> arguments.put("body", body));
        match.fulfill(arguments);
        Object result = match.execute();
        if (result instanceof CompletionStage<?> stage) {
            result = stage.toCompletableFuture().get();
        }
        return assertInstanceOf(HttpResponse.class, result);
    }

    private static @Nullable String failure(Router router, String path) {
        HttpRequest<?> request = HttpRequest.GET(path);
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, path);
        ExecutionFlow<HttpResponse<?>> flow = new FilterRunner(router.findFilters(request, match),
            (r, propagatedContext) -> ExecutionFlow.just(HttpResponse.ok()))
            .run(request);
        AtomicReference<@Nullable Throwable> error = new AtomicReference<>();
        flow.onComplete((value, throwable) -> error.set(throwable));
        Throwable failure = error.get();
        return failure == null ? null : failure.getMessage();
    }

    private static Router locatedRouter(Consumer<LocatedHttpRouteBuilder<Object>> located) {
        TestLocatedRoutes<Object> routes = TestLocatedRoutes.of(located);
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        builder.locate("/orders/{id}", (request, pathVariables) -> ORDER, target -> routes);
        builder.close();
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Marker {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Other {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface ByName {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Tagged {
        String value();
    }
}
