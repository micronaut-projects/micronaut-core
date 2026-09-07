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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Head;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.micronaut.http.tck.TestScenario.asserts;

/**
 * A {@code @Get} mapping also registers an implicit {@code HEAD} route, so that a plain {@code GET}
 * handler answers {@code HEAD} requests too. When a controller additionally declares an explicit
 * {@code @Head} route for the same URI, both routes match a {@code HEAD} request with identical
 * specificity, and the request used to be rejected as ambiguous with a {@code 400}.
 *
 * <p>The explicit declaration wins that tie. The implicit route is still registered, so a
 * {@code HEAD} request that earlier stages of resolution - such as content negotiation - can
 * attribute to the {@code @Get} route keeps reaching it.</p>
 *
 * @see <a href="https://github.com/micronaut-projects/micronaut-core/issues/13020">#13020</a>
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class ExplicitHeadRouteTest {
    public static final String SPEC_NAME = "ExplicitHeadRouteTest";

    private static final String HANDLER_HEADER = "X-Handler";

    @Test
    void headRequestPrefersTheExplicitlyDeclaredHeadRoute() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.HEAD("/explicit-head/example.dummy/availability"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .header(HANDLER_HEADER, "head")
                    .build()));
    }

    @Test
    void getRequestOnTheSameUriIsHandledByTheGetRoute() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/explicit-head/example.dummy/availability"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .header(HANDLER_HEADER, "get")
                    .body("available")
                    .build()));
    }

    @Test
    void headRequestStillReachesTheImplicitRouteOfAGetOnlyUri() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.HEAD("/explicit-head/example.dummy/stock"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .header(HANDLER_HEADER, "get")
                    .build()));
    }

    @Test
    void headRequestAcceptingTheMediaTypeOfTheHeadRouteReachesIt() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.HEAD("/explicit-head-negotiated/example.dummy/availability")
                .accept(MediaType.TEXT_PLAIN_TYPE),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .header(HANDLER_HEADER, "head")
                    .build()));
    }

    @Test
    void headRequestAcceptingTheMediaTypeOfTheGetRouteReachesTheImplicitRoute() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.HEAD("/explicit-head-negotiated/example.dummy/availability")
                .accept(MediaType.APPLICATION_JSON_TYPE),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .header(HANDLER_HEADER, "get")
                    .build()));
    }

    @Controller("/explicit-head")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ExplicitHeadController {

        @Head("/{id}/availability")
        HttpResponse<String> availabilityHead(@PathVariable String id) {
            return HttpResponse.ok("available").header(HANDLER_HEADER, "head");
        }

        @Get(uris = {"/{id}/availability", "/{id}/stock"})
        HttpResponse<String> availabilityGet(@PathVariable String id) {
            return HttpResponse.ok("available").header(HANDLER_HEADER, "get");
        }
    }

    @Controller("/explicit-head-negotiated")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class NegotiatedExplicitHeadController {

        @Produces(MediaType.TEXT_PLAIN)
        @Head("/{id}/availability")
        HttpResponse<String> availabilityHead(@PathVariable String id) {
            return HttpResponse.ok("available").header(HANDLER_HEADER, "head");
        }

        @Produces(MediaType.APPLICATION_JSON)
        @Get("/{id}/availability")
        HttpResponse<String> availabilityGet(@PathVariable String id) {
            return HttpResponse.ok("available").header(HANDLER_HEADER, "get");
        }
    }
}
