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
package io.micronaut.web.router.processor;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.web.router.DefaultRouter;
import io.micronaut.web.router.RouteAssembly;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.RouteDeclaration;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import io.micronaut.web.router.spi.PlannedRouteDeclaration;
import io.micronaut.web.router.spi.RouteSlot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The path an engine matches its routes against, see
 * {@link io.micronaut.http.uri.spi.RouteTemplateEngine#matchingPath(String)}: the parser of a
 * route plan is given that path, so a compiled slot answers exactly what the runtime matching
 * does, and the routes of the Micronaut engine in the same plan are matched with the raw path.
 */
class RoutePlanMatchingPathTest {

    private static final String MATRIX = ColonRouteTemplateEngine.Matrix.MATRIX_ID;
    private static final RouteTemplate CARS = RouteTemplate.of(MATRIX, "/cars/:id/details");
    private static final RouteTemplate ITEMS = RouteTemplate.of(MATRIX, "/items/:id");
    private static final RouteTemplate NATIVE = RouteTemplate.micronaut("/native/{id}");

    private static final CompiledRoutePlan COMPILED = new RoutePlanCompiler().plan("test.$Matrix$RoutePlan", "test:matrix", List.of(), List.of(
        GeneratedPlans.route("matrix:cars", "GET", CARS),
        GeneratedPlans.route("matrix:items", "GET", ITEMS),
        GeneratedPlans.route("matrix:post", "POST", CARS),
        GeneratedPlans.route("matrix:native", "GET", NATIVE)
    ));

    private static final List<HttpRequest<?>> REQUESTS = List.of(
        HttpRequest.GET("/cars/7/details"),
        HttpRequest.GET("/cars;color=red/7/details"),
        HttpRequest.GET("/cars;color=red/7;trim=gt/details"),
        HttpRequest.GET("/cars/7/details;x=1"),
        HttpRequest.GET("/cars;color=red/7/other"),
        HttpRequest.GET("/items/5;color=red"),
        HttpRequest.GET("/items;v=1/5"),
        HttpRequest.HEAD("/items;v=1/5"),
        HttpRequest.GET("/native/5"),
        // a Micronaut route is matched with the raw path only
        HttpRequest.GET("/native/5;x=1"),
        HttpRequest.GET("/native;x=1/5"),
        HttpRequest.DELETE("/cars;color=red/7/details"),
        HttpRequest.POST("/cars;color=red/7/details", "a").contentType(MediaType.TEXT_PLAIN_TYPE)
    );

    @Test
    void theSlotsAreCompiled() {
        for (RouteSlot slot : COMPILED.slots()) {
            assertTrue(slot.compiled(), slot.key());
        }
    }

    @Test
    void aPlanAnswersTheSameAsTheRuntimeMatching() {
        GeneratedPlans.ObservedPlan observed = new GeneratedPlans.ObservedPlan(GeneratedPlans.load(COMPILED));
        Router planned = routerOf(key -> PlannedRouteDeclaration.of(observed, key));
        Router ordinary = routerOf(key -> RouteDeclaration.of(slot(key).httpMethodName(), slot(key).template()));
        for (HttpRequest<?> request : REQUESTS) {
            assertEquals(closest(ordinary, request), closest(planned, request), request::toString);
            assertEquals(describeAll(ordinary.findAllClosest(request)), describeAll(planned.findAllClosest(request)), request::toString);
            assertEquals(describeAll(ordinary.findAny(request)), describeAll(planned.findAny(request)), request::toString);
        }
        assertTrue(observed.matches > 0);
    }

    @Test
    void theMatrixParametersAreIgnoredByTheRoutesOfTheEngineOnly() {
        GeneratedPlans.ObservedPlan observed = new GeneratedPlans.ObservedPlan(GeneratedPlans.load(COMPILED));
        Router planned = routerOf(key -> PlannedRouteDeclaration.of(observed, key));
        UriRouteMatch<Object, Object> match = planned.findClosest(HttpRequest.GET("/cars;color=red/7;trim=gt/details"));
        assertNotNull(match);
        assertEquals(CARS, match.getRouteInfo().getRouteTemplate());
        // the values the parser of the plan captured exclude the matrix parameters
        assertEquals("7", match.getVariableValues().get("id"));
        assertNull(planned.findClosest(HttpRequest.GET("/native/5;x=1")));
        // the parser of the plan was given the path the engine matches, and it reported the slot
        assertTrue(observed.reported.contains("matrix:cars"), observed.reported::toString);
        assertTrue(observed.matches > 0);
    }

    private static List<@Nullable String> describeAll(List<? extends UriRouteMatch<?, ?>> matches) {
        return matches.stream().map(RoutePlanMatchingPathTest::describe).toList();
    }

    private static @Nullable String closest(Router router, HttpRequest<?> request) {
        try {
            return describe(router.findClosest(request));
        } catch (DuplicateRouteException e) {
            return "duplicate";
        }
    }

    private static RouteSlot slot(String key) {
        for (RouteSlot slot : COMPILED.slots()) {
            if (slot.key().equals(key)) {
                return slot;
            }
        }
        throw new AssertionError(key);
    }

    private static Router routerOf(Function<String, RouteDeclaration> declarations) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, (String) null, route -> { });
        DefaultHttpRouteBuilder routes = new DefaultHttpRouteBuilder(assembly);
        routes.handle(declarations.apply("matrix:cars"), (request, variables) -> HttpResponse.ok());
        routes.handle(declarations.apply("matrix:items"), (request, variables) -> HttpResponse.ok());
        routes.handle(declarations.apply("matrix:post"), (request, variables) -> HttpResponse.ok());
        routes.handle(declarations.apply("matrix:native"), (request, variables) -> HttpResponse.ok());
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    private static @Nullable String describe(@Nullable UriRouteMatch<?, ?> match) {
        if (match == null) {
            return null;
        }
        return match.getRouteInfo().getHttpMethodName() + ' ' + match.getRouteInfo().getRouteTemplate() + ' '
            + match.getVariableValues();
    }

    @Test
    void aCustomMethodOfAnEngineIsMatchedWithItsPath() {
        GeneratedPlans.ObservedPlan observed = new GeneratedPlans.ObservedPlan(GeneratedPlans.load(COMPILED));
        Router planned = routerOf(key -> PlannedRouteDeclaration.of(observed, key));
        assertNull(planned.findClosest(HttpRequest.create(HttpMethod.CUSTOM, "/cars;color=red/7/details", "PROPFIND")));
    }
}
