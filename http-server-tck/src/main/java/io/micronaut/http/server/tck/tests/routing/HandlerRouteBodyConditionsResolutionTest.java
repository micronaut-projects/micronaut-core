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

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.filter.ConditionalFilter;
import io.micronaut.http.server.annotation.PreMatching;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedRoutes;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.micronaut.web.router.builder.RequestCondition.bodyAsync;
import static io.micronaut.web.router.builder.RequestCondition.bodyText;

/**
 * How the route chosen by the body of a request is resolved: a route locator chosen by the
 * body locates its target, whose routes are chosen by the body too, also when the locator is
 * asynchronous; a body a filter set is held to the limits of the conditions; and the filters
 * after the route match see the propagated context of the request after an asynchronous
 * condition completed on another thread.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteBodyConditionsResolutionTest {
    public static final String SPEC_NAME = "HandlerRouteBodyConditionsResolutionTest";
    private static final String TRACE = "X-Trace";

    @Test
    void aLocatorChosenByTheBodyChoosesTheRoutesOfItsTargetByTheBody() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String group : new String[]{"/bcr/vip", "/bcr/vip-async"}) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST(group + "/shops/7/orders", "vip express").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("shop 7 express vip express")
                    .build());
                // the body meets the condition of the group, not the one of the route of the target
                AssertionUtils.assertThrows(server, HttpRequest.POST(group + "/shops/7/orders", "vip lamp").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                    .status(HttpStatus.NOT_FOUND)
                    .build());
                // the body does not meet the condition of the group
                AssertionUtils.assertThrows(server, HttpRequest.POST(group + "/shops/7/orders", "express").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                    .status(HttpStatus.NOT_FOUND)
                    .build());
            }
        }
    }

    @Test
    void anAmbiguityAmongTheRoutesOfALocatedTargetIsAnsweredByTheErrorRoutesOfItsGroup() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.POST("/bcr/dup/shops/7/orders", "x").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.CONFLICT)
                .body("ambiguous in the group")
                .build());
        }
    }

    @Test
    void aLocatedTargetWithoutARouteForTheMethodIsNotAllowed() throws IOException {
        try (ServerUnderTest server = server()) {
            // the body meets the condition of the group, the target has a GET route only
            AssertionUtils.assertThrows(server, HttpRequest.POST("/bcr/vip/shops/7/report", "vip").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .build());
        }
    }

    @Test
    void aBodyAFilterSetIsHeldToTheLimitOfTheCondition() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bcr/set-body", "x").header("X-Body", "12345678").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("limited")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bcr/set-body", "x").header("X-Body", "123456789").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("fallback")
                .build());
        }
    }

    @Test
    void theFiltersAfterTheRouteMatchSeeThePropagatedContextAfterAnAsynchronousCondition() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bcr/traced", "x").header(TRACE, "t1").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("traced")
                .headers(Map.of("X-Traced", "t1"))
                .build());
        }
    }

    @Test
    void theFiltersAfterTheRouteMatchSeeTheRequestAfterADelayedConditionWithoutPreMatchingFilters() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bcr/gated", "x").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("gated")
                .headers(Map.of("X-Gated", "POST /bcr/gated"))
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static HttpResponse<?> text(String body) {
        return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    record Trace(String id) implements PropagatedContextElement {
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Lookups {
        /**
         * The executor the asynchronous locator and condition complete on.
         */
        @Singleton
        @Named("body-resolution-lookups")
        @Bean(preDestroy = "shutdown")
        ExecutorService lookups() {
            return Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "body-resolution-lookup"));
        }
    }

    /**
     * Adds the trace of the request to the propagated context before the route is matched.
     */
    @ServerFilter("/bcr/traced")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TraceFilter {
        @PreMatching
        @RequestFilter
        void trace(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
            String trace = request.getHeaders().get(TRACE);
            if (trace != null) {
                propagatedContext.add(new Trace(trace));
            }
        }
    }

    /**
     * A filter after the route match, enabled by the propagated context of the request.
     */
    @ServerFilter("/bcr/traced")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TracedOnlyFilter implements ConditionalFilter {
        @Override
        public boolean isEnabled(HttpRequest<?> request) {
            return PropagatedContext.getOrEmpty().find(Trace.class).isPresent();
        }

        @ResponseFilter
        void traced(MutableHttpResponse<?> response) {
            response.header("X-Traced", PropagatedContext.getOrEmpty().find(Trace.class).map(Trace::id).orElse("none"));
        }
    }

    /**
     * A filter after the route match, without a pre-matching filter on its path, enabled when
     * the request is in the propagated context.
     */
    @ServerFilter("/bcr/gated")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class GatedFilter implements ConditionalFilter {
        @Override
        public boolean isEnabled(HttpRequest<?> request) {
            return ServerRequestContext.currentRequest().isPresent();
        }

        @ResponseFilter
        void gated(HttpRequest<?> request, MutableHttpResponse<?> response) {
            response.header("X-Gated", request.getMethodName() + " " + request.getPath());
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        private final ExecutorService lookups;

        Routes(@Named("body-resolution-lookups") ExecutorService lookups) {
            this.lookups = lookups;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            LocatedRoutes<String> shops = TckLocatedRoutes.of(String.class, shop -> {
                shop.POST("/orders").where(bodyText(text -> text.contains("express"))).consumesAll()
                    .body(String.class).handle((request, pathVariables, id, body) -> text("shop " + id + " express " + body));
                shop.GET("/report").handle((request, pathVariables, id) -> text("report " + id));
            });
            LocatedRoutes<String> ambiguous = TckLocatedRoutes.of(String.class, shop -> {
                shop.POST("/orders").where(bodyText(text -> true)).consumesAll().handle((request, pathVariables, id) -> text("first"));
                shop.POST("/orders").where(bodyText(text -> true)).consumesAll().handle((request, pathVariables, id) -> text("second"));
            });
            routes.path("/bcr/dup", dup -> {
                dup.error(DuplicateRouteException.class, (request, error) -> HttpResponse.status(HttpStatus.CONFLICT).body("ambiguous in the group").contentType(MediaType.TEXT_PLAIN_TYPE));
                dup.locate("/shops/{id}", (request, pathVariables) -> pathVariables.getString("id"), shop -> ambiguous);
            });
            routes.path("/bcr/vip", vip -> {
                vip.where(bodyText(text -> text.startsWith("vip")));
                vip.locate("/shops/{id}", (request, pathVariables) -> pathVariables.getString("id"), shop -> shops);
            });
            routes.path("/bcr/vip-async", vip -> {
                vip.where(bodyText(text -> text.startsWith("vip")));
                vip.locateAsync("/shops/{id}", (request, pathVariables) ->
                    CompletableFuture.supplyAsync(() -> pathVariables.getString("id"), lookups), shop -> shops);
            });

            routes.serverFilter("/bcr/set-body").preMatching().beforeReplacing(request -> request.body(request.getHeaders().get("X-Body")));
            routes.POST("/bcr/set-body").where(bodyText(text -> true).maxBytes(8)).order(-1).consumesAll()
                .handle((request, pathVariables) -> text("limited"));
            routes.POST("/bcr/set-body").consumesAll()
                .handle((request, pathVariables) -> text("fallback"));

            // completes after the route match waits for it: the continuation runs on the lookup thread
            routes.POST("/bcr/gated").where(bodyAsync(String.class, body -> CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return true;
            }, lookups))).consumesAll()
                .handle((request, pathVariables) -> text("gated"));
            routes.POST("/bcr/traced").where(bodyAsync(String.class, body -> CompletableFuture.supplyAsync(() -> true, lookups))).consumesAll()
                .handle((request, pathVariables) -> text("traced"));
        }
    }
}
