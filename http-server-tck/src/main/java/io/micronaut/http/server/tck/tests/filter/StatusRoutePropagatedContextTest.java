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
package io.micronaut.http.server.tck.tests.filter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.propagation.slf4j.MdcPropagationContext;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * A status route runs with the propagated context the filters produced, e.g. the MDC context a
 * filter method with a {@link MutablePropagatedContext} parameter added: when a route answers with
 * the status, and when no route matches the request, like the error routes and the exception
 * handlers of such a request.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class StatusRoutePropagatedContextTest {
    public static final String SPEC_NAME = "StatusRoutePropagatedContextTest";
    private static final String TRACE = "X-Trace";

    @Test
    void statusRouteForTheStatusOfARoute() throws IOException {
        assertStatusRoute(HttpRequest.GET("/status-context/not-found"), HttpStatus.NOT_FOUND);
    }

    @Test
    void statusRouteForARequestNoRouteMatches() throws IOException {
        assertStatusRoute(HttpRequest.GET("/status-context/missing"), HttpStatus.NOT_FOUND);
    }

    @Test
    void statusRouteForARequestNoRouteMatchesTheMethodOf() throws IOException {
        assertStatusRoute(HttpRequest.DELETE("/status-context/get-only"), HttpStatus.METHOD_NOT_ALLOWED);
    }

    private static void assertStatusRoute(MutableHttpRequest<?> request, HttpStatus status) throws IOException {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME)) {
            AssertionUtils.assertThrows(server, request.header(TRACE, "t1"),
                HttpResponseAssertion.builder()
                    .status(status)
                    .body("status:trace=t1,mdc=t1,request=yes")
                    .build());
        }
    }

    static String describe() {
        return "trace=" + PropagatedContext.getOrEmpty().find(Trace.class).map(Trace::id).orElse("none")
            + ",mdc=" + Objects.requireNonNullElse(MDC.get("trace"), "none")
            + ",request=" + (ServerRequestContext.currentRequest().isPresent() ? "yes" : "no");
    }

    /**
     * The element the filter adds.
     *
     * @param id The trace
     */
    record Trace(String id) implements PropagatedContextElement {
    }

    /**
     * Like the MDC filter of the documentation.
     */
    @ServerFilter("/status-context/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TraceFilter {
        @RequestFilter
        void filter(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
            String trace = Objects.requireNonNull(request.getHeaders().get(TRACE));
            propagatedContext.add(new Trace(trace));
            propagatedContext.add(new MdcPropagationContext(Map.of("trace", trace)));
        }
    }

    @Controller("/status-context")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class StatusController {
        @Get("/not-found")
        HttpResponse<?> notFound() {
            return HttpResponse.notFound();
        }

        @Get("/get-only")
        String getOnly() {
            return "get";
        }

        @Error(global = true, status = HttpStatus.NOT_FOUND)
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse<String> onNotFound(HttpRequest<?> request) {
            return HttpResponse.<String>notFound().body("status:" + describe());
        }

        @Error(global = true, status = HttpStatus.METHOD_NOT_ALLOWED)
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse<String> onNotAllowed(HttpRequest<?> request) {
            return HttpResponse.<String>status(HttpStatus.METHOD_NOT_ALLOWED).body("status:" + describe());
        }
    }
}
