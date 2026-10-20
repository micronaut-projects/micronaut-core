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
import io.micronaut.http.HttpResponse;
import io.micronaut.web.router.builder.DefaultPathVariables;
import io.micronaut.web.router.builder.HandlerMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A variable of the template of a located route shadows the variable of the same name of the
 * prefix of its locator, also when it is absent from the captures of a template of another
 * language: the constraints and the handler see the same raw variables.
 */
class RouteTemplateEngineRawValuesTest {

    @Test
    void anAbsentVariableOfATemplateOfAnotherLanguageShadowsTheVariableOfThePrefix() {
        TestLocatedRoutes<String> items = TestLocatedRoutes.of(String.class, org ->
            org.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/items/:id?"))
                // the id of the prefix is not the id of the route
                .constrain(variables -> !variables.raw().contains("id") || !"acme".equals(variables.rawString("id")))
                .handle((request, variables, target) -> HttpResponse.ok(target + "|"
                    + (variables.raw().contains("id") ? variables.rawString("id") : "absent"))));
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.locate("/orgs/{id}", (request, variables) -> "org " + variables.rawString("id"), items));

        UriRouteMatch<Object, Object> absent = router.findClosest(HttpRequest.GET("/orgs/acme/items"));
        assertNotNull(absent, "the constraint accepts the absent id");
        assertFalse(absent.getRawVariableValues().containsKey("id"));
        assertFalse(absent.getVariableValues().containsKey("id") && absent.getVariableValues().get("id") != null);
        assertEquals("org acme|absent", invoke(absent, HttpRequest.GET("/orgs/acme/items")));

        UriRouteMatch<Object, Object> present = router.findClosest(HttpRequest.GET("/orgs/acme/items/7"));
        assertNotNull(present);
        assertEquals("org acme|7", invoke(present, HttpRequest.GET("/orgs/acme/items/7")));
    }

    private static Object invoke(UriRouteMatch<Object, Object> match, HttpRequest<?> request) {
        Object target = ((RouteLocator.LocatedUriMatchInfo) ((DefaultUriRouteMatch<?, ?>) match).matchInfo()).target();
        HandlerMethod<?> handler = (HandlerMethod<?>) ((DefaultUrlRouteInfo<?, ?>) match.getRouteInfo()).getTargetMethod();
        HttpResponse<?> response = (HttpResponse<?>) handler.invoke(new Object[]{request,
            new DefaultPathVariables(match.getVariableValues(), match.getRawVariableValues(), ConversionService.SHARED, target, null)});
        return response.body();
    }
}
