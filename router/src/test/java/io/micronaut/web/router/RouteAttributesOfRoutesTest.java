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
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.http.PathVariables;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The attributes of handler routes and of groups of handler routes, read from the matched route.
 */
class RouteAttributesOfRoutesTest {

    @Test
    void theMatchedRouteHasTheAttributesOfItsGroupsAndItsOwn() {
        Router router = router(routes -> routes.path("/admin", admin -> {
            admin.GET("/users", RouteAttributesOfRoutesTest::ok);
            admin.GET("/audit", RouteAttributesOfRoutesTest::ok).attribute("role", "auditor").attribute("audited", true);
            admin.path("/nested", nested -> {
                nested.attribute("tier", 2);
                nested.GET("/route", RouteAttributesOfRoutesTest::ok);
            });
            // declared after the routes
            admin.attribute("role", "admin");
            admin.attribute("tier", 1);
        }));

        assertEquals(Map.of("role", "admin", "tier", 1), attributes(router, "/admin/users"));
        assertEquals(Map.of("role", "auditor", "tier", 1, "audited", true), attributes(router, "/admin/audit"));
        assertEquals(Map.of("role", "admin", "tier", 2), attributes(router, "/admin/nested/route"));

        RouteInfo<?> audit = route(router, HttpRequest.GET("/admin/audit"));
        assertEquals(Optional.of("auditor"), audit.getAttribute("role"));
        assertEquals(Optional.of("auditor"), audit.getAttribute("role", String.class));
        assertEquals(Optional.empty(), audit.getAttribute("role", Integer.class));
        assertEquals(Optional.empty(), audit.getAttribute("missing"));
        // the implicit HEAD route has the attributes of its GET route
        assertEquals("auditor", route(router, HttpRequest.HEAD("/admin/audit")).getAttributes().get("role"));
    }

    @Test
    void aRouteWithoutAttributesHasNone() {
        Router router = router(routes -> routes.GET("/plain", RouteAttributesOfRoutesTest::ok));
        assertTrue(attributes(router, "/plain").isEmpty());
    }

    @Test
    void theAttributesAreReadOnly() {
        Router router = router(routes -> routes.GET("/x", RouteAttributesOfRoutesTest::ok).attribute("a", 1));
        Map<String, Object> attributes = attributes(router, "/x");
        assertThrows(UnsupportedOperationException.class, () -> attributes.put("b", 2));
        assertThrows(NullPointerException.class, () -> router(routes -> routes.GET("/y", RouteAttributesOfRoutesTest::ok).attribute("a", null)));
    }

    private static Map<String, Object> attributes(Router router, String path) {
        return route(router, HttpRequest.GET(path)).getAttributes();
    }

    private static RouteInfo<?> route(Router router, HttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getPath());
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
