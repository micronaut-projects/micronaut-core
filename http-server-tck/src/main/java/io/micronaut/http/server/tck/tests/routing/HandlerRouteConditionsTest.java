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

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.RouteCondition;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RequestPredicates;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.function.Predicate;

/**
 * The conditions of handler routes, {@link io.micronaut.web.router.builder.HttpRouteSpec#where}
 * and {@link io.micronaut.web.router.builder.HttpRouteGroup#where}, built with
 * {@link RequestPredicates} or not, and the
 * {@link RouteCondition} of the bean method a handler route implements: a request that does not
 * meet the conditions of a route is answered by another route, or as if the route did not exist.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteConditionsTest {
    public static final String SPEC_NAME = "HandlerRouteConditionsTest";

    @Test
    void aConditionSelectsAmongTheRoutesOfTheSameUriAndMethod() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/conditions/reports/1").header("X-Export", "csv"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("csv 1")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/conditions/reports/1"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("json 1")
                .build());
        }
    }

    @Test
    void aRequestTheConditionRejectsIsNotFoundAndNotAMethodNotAllowed() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/conditions/csv-only"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/conditions/csv-only").header("X-Export", "csv"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("csv only")
                .build());
        }
    }

    @Test
    void theRoutesOfAGroupMeetTheConditionsOfTheGroupAndTheirOwn() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/conditions/beta/search").header("X-Beta", "1").header("X-Search", "1"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("beta search")
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.GET("/conditions/beta/search").header("X-Search", "1"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.GET("/conditions/beta/search").header("X-Beta", "1"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
        }
    }

    @Test
    void theRouteConditionOfTheImplementedBeanMethodAppliesLikeOnAControllerMethod() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/conditions/variant").header("X-Variant", "b"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("b")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/conditions/variant"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("a")
                .build());
        }
    }

    @Test
    void theRequestPredicatesSelectByQueryParameterHeaderAndAcceptedType() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/conditions/export?format=csv"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("csv export")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/conditions/export").accept(MediaType.TEXT_CSV_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("csv export")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/conditions/export?format=json").accept(MediaType.APPLICATION_JSON_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("default export")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/conditions/export").header("X-Mode", "debug"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("debug export")
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static HttpResponse<?> text(String body) {
        return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    private static boolean csv(HttpRequest<?> request) {
        return "csv".equals(request.getHeaders().get("X-Export"));
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class VariantTarget {
        @Executable
        @RouteCondition("#{request.headers.getFirst('X-Variant').orElse(null) == 'b'}")
        String variantB() {
            return "b";
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ConditionRoutes implements HttpRoutes {
        private final BeanContext beanContext;
        private final VariantTarget target;

        ConditionRoutes(BeanContext beanContext, VariantTarget target) {
            this.beanContext = beanContext;
            this.target = target;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/conditions/reports/{id}", (request, pathVariables) -> text("csv " + pathVariables.getLong("id")))
                .where(HandlerRouteConditionsTest::csv);
            routes.GET("/conditions/reports/{id}", (request, pathVariables) -> text("json " + pathVariables.getLong("id")))
                .where(request -> !csv(request));
            routes.GET("/conditions/csv-only", (request, pathVariables) -> text("csv only"))
                .where(HandlerRouteConditionsTest::csv);
            routes.path("/conditions/beta", beta -> {
                beta.GET("/search", (request, pathVariables) -> text("beta search"))
                    .where(request -> request.getHeaders().contains("X-Search"));
                beta.where(request -> request.getHeaders().contains("X-Beta"));
            });
            routes.GET("/conditions/variant", (request, pathVariables) -> text(target.variantB()))
                .implementing(beanContext.getBeanDefinition(VariantTarget.class).getRequiredMethod("variantB"));
            routes.GET("/conditions/variant", (request, pathVariables) -> text("a"))
                .where(request -> !"b".equals(request.getHeaders().get("X-Variant")));
            Predicate<HttpRequest<?>> csv = RequestPredicates.queryParam("format", "csv")
                .or(RequestPredicates.all(RequestPredicates.accept(MediaType.TEXT_CSV_TYPE), RequestPredicates.header(HttpHeaders.ACCEPT)));
            Predicate<HttpRequest<?>> debug = RequestPredicates.header("X-Mode", value -> value.startsWith("debug"));
            routes.GET("/conditions/export", (request, pathVariables) -> text("csv export")).where(csv);
            routes.GET("/conditions/export", (request, pathVariables) -> text("debug export")).where(debug.and(csv.negate()));
            routes.GET("/conditions/export", (request, pathVariables) -> text("default export")).where(RequestPredicates.any(csv, debug).negate());
        }
    }
}
