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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * The attributes of handler routes, {@link io.micronaut.web.router.builder.HttpRouteSpec#attribute}
 * and {@link io.micronaut.web.router.builder.HttpRouteGroup#attribute}: the handler, the route
 * filters and the server filters read them from the matched route.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteAttributesTest {
    public static final String SPEC_NAME = "HandlerRouteAttributesTest";

    @Test
    void theHandlerAndTheFiltersReadTheAttributesOfTheRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/attributes/users").header("X-Role", "admin"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("handler role=admin")
                .header("X-Route-Filter-Role", "admin")
                .header("X-Server-Filter-Role", "admin")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/attributes/audit").header("X-Role", "auditor"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("handler role=auditor")
                .header("X-Server-Filter-Role", "auditor")
                .build());
        }
    }

    @Test
    void aServerFilterEnforcesTheRoleTheRouteDeclares() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/attributes/audit").header("X-Role", "admin"), HttpResponseAssertion.builder()
                .status(HttpStatus.FORBIDDEN)
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.GET("/attributes/users"), HttpResponseAssertion.builder()
                .status(HttpStatus.FORBIDDEN)
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static String role(HttpRequest<?> request) {
        return RouteAttributes.getRouteInfo(request).flatMap(route -> route.getAttribute("role", String.class)).orElse("none");
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AttributeRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.path("/attributes", admin -> {
                admin.attribute("role", "admin");
                admin.GET("/users", (request, pathVariables) -> HttpResponse.ok("handler role=" + role(request)).contentType(MediaType.TEXT_PLAIN_TYPE))
                    .after((request, response) -> response.header("X-Route-Filter-Role", role(request)));
                admin.GET("/audit", (request, pathVariables) -> HttpResponse.ok("handler role=" + role(request)).contentType(MediaType.TEXT_PLAIN_TYPE))
                    .attribute("role", "auditor");
            });
        }
    }

    @ServerFilter("/attributes/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RoleFilter {
        @RequestFilter
        @Nullable
        HttpResponse<?> requireRole(HttpRequest<?> request, RouteInfo<?> routeInfo) {
            String required = routeInfo.getAttribute("role", String.class).orElse(null);
            if (required != null && !required.equals(request.getHeaders().get("X-Role"))) {
                return HttpResponse.status(HttpStatus.FORBIDDEN);
            }
            return null;
        }

        @ResponseFilter
        void role(RouteInfo<?> routeInfo, MutableHttpResponse<?> response) {
            routeInfo.getAttribute("role", String.class).ifPresent(role -> response.header("X-Server-Filter-Role", role));
        }
    }
}
