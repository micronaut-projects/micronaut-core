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
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The filters of a group apply to the routes of the group only, also to a route of another group
 * or of the builder on a path under the same prefix; a server filter of the builder applies to
 * every request its patterns match.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteGroupFilterScopeTest {
    public static final String SPEC_NAME = "HandlerRouteGroupFilterScopeTest";

    @Test
    void aGroupFilterAppliesToTheRoutesOfItsGroupOnly() throws IOException {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME)) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/scope/a/x"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .header("X-Group", "a")
                .header("X-Server", "yes")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/scope/b/x"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .header("X-Server", "yes")
                .assertResponse(response -> assertFalse(response.getHeaders().contains("X-Group"), "not a route of the group"))
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/scope/a/outside"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> assertFalse(response.getHeaders().contains("X-Group"), "under the prefix, not in the group"))
                .build());
        }
    }

    private static HttpResponse<?> text(String body) {
        return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ScopeRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.path("/scope/a", a -> {
                a.after((request, response) -> response.header("X-Group", "a"));
                a.GET("/x", (request, pathVariables) -> text("a"));
            });
            routes.path("/scope/b", b -> b.GET("/x", (request, pathVariables) -> text("b")));
            routes.GET("/scope/a/outside", (request, pathVariables) -> text("outside"));
            routes.serverFilter("/scope/**").after((request, response) -> response.header("X-Server", "yes"));
        }
    }
}
