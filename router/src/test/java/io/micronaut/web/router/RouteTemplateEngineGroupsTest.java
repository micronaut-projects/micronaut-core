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

import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.web.router.builder.RouteDeclaration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Routes of route template engines in groups of handler routes: the filters of a group apply to
 * them, and the prefix of a group, which is joined to Micronaut URI templates only, rejects the
 * templates of other engines.
 */
class RouteTemplateEngineGroupsTest {


    private final TestLocatedRoutes<Object> items = TestLocatedRoutes.of(routes ->
        routes.handle(RouteDeclaration.of(HttpMethod.GET, ColonRouteTemplateEngine.template("/items/:item")), (request, variables) -> HttpResponse.ok()));

    @Test
    void aLocatorOfAnotherEngineCannotBeDeclaredInAGroupWithAPrefix() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> RouteTemplateEngineOrderTest.router(null, routes ->
            routes.path("/api", api -> api.locate(ColonRouteTemplateEngine.template("/orders/:id"), (request, variables) -> "order", target -> items))));
        assertTrue(error.getMessage().contains(ColonRouteTemplateEngine.ID), error.getMessage());
        assertTrue(error.getMessage().contains("/api"), error.getMessage());
    }

    @Test
    void aMicronautLocatorTemplateInAGroupWithAPrefixIsPrefixed() {
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.path("/api", api -> api.locate(RouteTemplate.micronaut("/orders/{id}"), (request, variables) -> "order", target -> items)));
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/api/orders/5/items/3"));
        assertNotNull(match);
        assertEquals("5", match.getVariableValues().get("id"));
        assertEquals("3", match.getVariableValues().get("item"));
    }

    @Test
    void theFiltersOfAGroupApplyToTheRoutesOfAnotherEngine() {
        List<String> trace = new ArrayList<>();
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> routes.group(group -> {
            group.before(request -> record(trace, "group"));
            group.handle(RouteDeclaration.of(HttpMethod.GET, ColonRouteTemplateEngine.template("/pets/:id")), (request, variables) -> HttpResponse.ok());
            group.locate(ColonRouteTemplateEngine.template("/orders/:id"), (request, variables) -> "order", target -> items);
        }));

        UriRouteMatch<Object, Object> pet = router.findClosest(HttpRequest.GET("/pets/1"));
        assertNotNull(pet);
        assertEquals(ColonRouteTemplateEngine.template("/pets/:id"), pet.getRouteInfo().getRouteTemplate());
        run(router, HttpRequest.GET("/pets/1"), pet, trace);
        assertEquals(List.of("group", "handler"), trace);

        trace.clear();
        HttpRequest<?> request = HttpRequest.GET("/orders/5/items/3");
        UriRouteMatch<Object, Object> located = router.findClosest(request);
        assertNotNull(located);
        assertEquals(ColonRouteTemplateEngine.template("/items/:item"), located.getRouteInfo().getRouteTemplate());
        run(router, request, located, trace);
        assertEquals(List.of("group", "handler"), trace);
    }

    private static void run(Router router, HttpRequest<?> request, RouteMatch<?> match, List<String> trace) {
        List<GenericHttpFilter> filters = router.findFilters(request, match);
        ExecutionFlow<HttpResponse<?>> flow = new FilterRunner(filters, (r, propagatedContext) -> {
            trace.add("handler");
            return ExecutionFlow.just(HttpResponse.ok());
        }).run(request);
        assertNotNull(flow.tryCompleteValue(), "the filters completed synchronously");
    }

    private static HttpResponse<?> record(List<String> trace, String step) {
        trace.add(step);
        return null;
    }
}
