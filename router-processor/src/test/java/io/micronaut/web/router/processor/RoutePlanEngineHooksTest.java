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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The order of specificity and the route selector of an engine apply to the routes of the compiled
 * slots of a route plan exactly as to routes matched at runtime.
 */
class RoutePlanEngineHooksTest {

    private static final RouteTemplate ORDERED_VAR = RouteTemplate.of(ColonRouteTemplateEngine.Ordered.ORDERED_ID, "/o/:id");
    private static final RouteTemplate ORDERED_LITERAL = RouteTemplate.of(ColonRouteTemplateEngine.Ordered.ORDERED_ID, "/o/special");
    private static final RouteTemplate SELECTED = RouteTemplate.of(ColonRouteTemplateEngine.Selecting.SELECTING_ID, "/s/:id");
    private static final RouteTemplate SELECTED_SINGLE = RouteTemplate.of(ColonRouteTemplateEngine.Selecting.SELECTING_ID, "/one/:id");

    private static final CompiledRoutePlan COMPILED = new RoutePlanCompiler().plan("test.$Hooks$RoutePlan", "test:hooks", List.of(), List.of(
        GeneratedPlans.route("hooks:var", "GET", ORDERED_VAR),
        GeneratedPlans.route("hooks:literal", "GET", ORDERED_LITERAL),
        GeneratedPlans.route("hooks:selected", "GET", SELECTED),
        GeneratedPlans.route("hooks:single", "GET", SELECTED_SINGLE)
    ));

    private static final List<HttpRequest<?>> REQUESTS = List.of(
        HttpRequest.GET("/o/special"),
        HttpRequest.GET("/o/5"),
        HttpRequest.HEAD("/o/special"),
        HttpRequest.GET("/s/5"),
        HttpRequest.GET("/s/5").accept(MediaType.TEXT_PLAIN_TYPE),
        HttpRequest.GET("/s/5").accept(MediaType.APPLICATION_JSON_TYPE),
        HttpRequest.GET("/s/5").header("Accept", "text/plain;q=0.2, application/json"),
        HttpRequest.GET("/s/5").accept(MediaType.IMAGE_PNG_TYPE),
        HttpRequest.HEAD("/s/5").accept(MediaType.APPLICATION_JSON_TYPE),
        HttpRequest.GET("/one/5").header("Accept", "text/html, text/plain;q=0.5"),
        HttpRequest.GET("/one/5")
    );

    @Test
    void theSlotsAreCompiled() {
        for (RouteSlot slot : COMPILED.slots()) {
            assertTrue(slot.compiled(), slot.key());
        }
    }

    @Test
    void theOrderOfTheEngineSelectsTheSameRouteWithAndWithoutThePlan() {
        GeneratedPlans.ObservedPlan observed = new GeneratedPlans.ObservedPlan(GeneratedPlans.load(COMPILED));
        Router planned = routerOf(key -> PlannedRouteDeclaration.of(observed, key));
        Router ordinary = routerOf(key -> RouteDeclaration.of(slot(key).httpMethodName(), slot(key).template()));
        // the engine prefers fewer literal characters: the variable wins, unlike the Micronaut policy
        assertEquals(ORDERED_VAR, planned.findClosest(HttpRequest.GET("/o/special")).getRouteInfo().getRouteTemplate());
        assertEquals(ORDERED_VAR, ordinary.findClosest(HttpRequest.GET("/o/special")).getRouteInfo().getRouteTemplate());
        assertTrue(observed.matches > 0);
    }

    @Test
    void theRouterSelectsAndNegotiatesTheSameWithAndWithoutThePlan() {
        GeneratedPlans.ObservedPlan observed = new GeneratedPlans.ObservedPlan(GeneratedPlans.load(COMPILED));
        Router planned = routerOf(key -> PlannedRouteDeclaration.of(observed, key));
        Router ordinary = routerOf(key -> RouteDeclaration.of(slot(key).httpMethodName(), slot(key).template()));
        for (HttpRequest<?> request : REQUESTS) {
            assertEquals(closest(ordinary, request), closest(planned, request), request::toString);
            assertEquals(ordinary.findAllClosest(request).stream().map(RoutePlanEngineHooksTest::describe).toList(),
                planned.findAllClosest(request).stream().map(RoutePlanEngineHooksTest::describe).toList(), request::toString);
        }
        assertEquals("GET test.colon-selecting:/s/:id [text/plain] {id=5} text/plain",
            closest(planned, HttpRequest.GET("/s/5").accept(MediaType.TEXT_PLAIN_TYPE)));
        assertEquals("GET test.colon-selecting:/s/:id [application/json] {id=5} application/json",
            closest(planned, HttpRequest.GET("/s/5").header("Accept", "text/plain;q=0.2, application/json")));
        // a single route of a compiled slot is negotiated too
        assertEquals("GET test.colon-selecting:/one/:id [application/json, text/plain] {id=5} text/plain",
            closest(planned, HttpRequest.GET("/one/5").header("Accept", "text/html, text/plain;q=0.5")));
        assertTrue(observed.matches > 0);
    }

    private static String closest(Router router, HttpRequest<?> request) {
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
        routes.handle(declarations.apply("hooks:var"), (request, variables) -> HttpResponse.ok());
        routes.handle(declarations.apply("hooks:literal"), (request, variables) -> HttpResponse.ok());
        routes.handle(declarations.apply("hooks:selected"), (request, variables) -> HttpResponse.ok()).produces(MediaType.TEXT_PLAIN_TYPE);
        routes.handle(declarations.apply("hooks:selected"), (request, variables) -> HttpResponse.ok()).produces(MediaType.APPLICATION_JSON_TYPE);
        routes.handle(declarations.apply("hooks:single"), (request, variables) -> HttpResponse.ok())
            .produces(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_PLAIN_TYPE);
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    private static @Nullable String describe(@Nullable UriRouteMatch<?, ?> match) {
        if (match == null) {
            return null;
        }
        return match.getRouteInfo().getHttpMethodName() + ' ' + match.getRouteInfo().getRouteTemplate() + ' '
            + match.getRouteInfo().getProduces() + ' ' + match.getVariableValues() + ' '
            + match.getSelectedMediaType().map(MediaType::toString).orElse("-");
    }
}
