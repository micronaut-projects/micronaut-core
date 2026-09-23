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
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.web.router.DefaultRouter;
import io.micronaut.web.router.RouteAssembly;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteSpec;
import io.micronaut.web.router.builder.RequestPredicates;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import io.micronaut.web.router.spi.PlannedRouteDeclaration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The conditions, the order and the attributes of handler routes apply to the routes a route plan
 * finds exactly like to the routes matched at runtime: the parser of a plan only finds the
 * candidates, the router then applies the same conditions, the same selection and the same
 * tie-break by order.
 */
class RoutePlanRouteSettingsTest {

    private static final RouteTemplate REPORT = RouteTemplate.micronaut("/reports/{id}");
    private static final RouteTemplate TIE = RouteTemplate.micronaut("/tie/{id}");

    private static final CompiledRoutePlan COMPILED = new RoutePlanCompiler().plan("test.$Settings$RoutePlan", "test:settings", List.of(), List.of(
        GeneratedPlans.route("test:csv", "GET", REPORT),
        GeneratedPlans.route("test:any", "GET", REPORT),
        GeneratedPlans.route("test:tie-a", "GET", TIE),
        GeneratedPlans.route("test:tie-b", "GET", TIE)
    ));

    private static final List<HttpRequest<?>> REQUESTS = List.of(
        HttpRequest.GET("/reports/1").header("X-Csv", "1"),
        HttpRequest.GET("/reports/1"),
        HttpRequest.HEAD("/reports/1").header("X-Csv", "1"),
        HttpRequest.GET("/reports/1").header("X-Csv", "1").header("X-Beta", "1"),
        HttpRequest.GET("/tie/1"),
        HttpRequest.GET("/tie/1").header("X-Beta", "1")
    );

    @Test
    void aPlanSelectsTheSameRoutesAsTheRuntimeWithConditionsAndOrders() {
        GeneratedPlans.ObservedPlan observed = new GeneratedPlans.ObservedPlan(GeneratedPlans.load(COMPILED));
        Router planned = router(routes -> declare(routes, (key, template) -> PlannedRouteDeclaration.of(observed, key), true));
        Router runtime = router(routes -> declare(routes, (key, template) -> null, false));

        List<String> plannedOutcomes = new ArrayList<>();
        List<String> runtimeOutcomes = new ArrayList<>();
        for (HttpRequest<?> request : REQUESTS) {
            observed.reported.clear();
            plannedOutcomes.add(outcome(planned, request));
            assertTrue(!observed.reported.isEmpty(), "The plan found the routes of " + request.getPath());
            runtimeOutcomes.add(outcome(runtime, request));
        }
        assertEquals(List.of(
            "csv role=reader",
            "any role=admin",
            "csv role=reader",
            // the conditions of both routes are met: the lower order wins
            "csv role=reader",
            // the same order: ambiguous
            "ambiguous",
            // the condition of the group rejects tie-b: tie-a
            "tie-a role=admin"
        ), plannedOutcomes);
        assertEquals(runtimeOutcomes, plannedOutcomes);
    }

    private static void declare(HttpRouteBuilder routes, BiFunction<String, RouteTemplate, PlannedRouteDeclaration> plan, boolean planned) {
        routes.group(group -> {
            group.attribute("role", "admin");
            route(group, planned, plan, "test:csv", REPORT, "csv")
                .where(RequestPredicates.header("X-Csv"))
                .order(-1)
                .attribute("role", "reader");
            route(group, planned, plan, "test:any", REPORT, "any");
            route(group, planned, plan, "test:tie-a", TIE, "tie-a");
            group.group(inner -> {
                inner.where(RequestPredicates.header("X-Beta").negate());
                route(inner, planned, plan, "test:tie-b", TIE, "tie-b");
            });
        });
    }

    private static HttpRouteSpec route(HttpRouteBuilder routes, boolean planned, BiFunction<String, RouteTemplate, PlannedRouteDeclaration> plan,
                                       String key, RouteTemplate template, String name) {
        return planned
            ? routes.handle(plan.apply(key, template), (request, variables) -> HttpResponse.ok(name))
            : routes.GET(template.expression(), (request, variables) -> HttpResponse.ok(name));
    }

    private static String outcome(Router router, HttpRequest<?> request) {
        UriRouteMatch<Object, Object> match;
        try {
            match = router.findClosest(request);
        } catch (DuplicateRouteException e) {
            return "ambiguous";
        }
        if (match == null) {
            return "none";
        }
        Object handler = ((io.micronaut.web.router.builder.HandlerMethod<?>) match.getRouteInfo().getTargetMethod()).getTarget();
        HttpResponse<?> response;
        try {
            response = ((io.micronaut.web.router.builder.RequestHandler) handler).handle(request, null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return response.body() + " role=" + match.getRouteInfo().getAttribute("role", String.class).orElse("none");
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, (String) null, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
