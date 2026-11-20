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
package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.RouteResolution;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import io.micronaut.web.router.builder.RequestCondition;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The server resolves the route of a request whose routes have body conditions once: the other
 * conditions of the routes are evaluated, and the locators locate their targets, as many times
 * as when the router resolves the request. Conditions decided already choose the route without
 * waiting.
 */
class HandlerRouteBodyConditionsResolutionTest {
    private static final String SPEC_NAME = "HandlerRouteBodyConditionsResolutionTest";

    private static final AtomicInteger SINGLE = new AtomicInteger();
    private static final AtomicInteger AMBIGUOUS = new AtomicInteger();
    private static final AtomicInteger LOCATED = new AtomicInteger();
    private static final AtomicInteger LOCATED_ROUTE = new AtomicInteger();

    private static ApplicationContext ctx;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1));
        EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
        client = ctx.createBean(HttpClient.class, server.getURL());
    }

    @AfterAll
    static void stop() {
        if (client != null) {
            client.close();
        }
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void theConditionsOfABodyConditionedRouteAreEvaluatedOnce() {
        assertEquals(1, resolved("/res/single", SINGLE));
        assertEquals(1, served("/res/single", "express", "express", SINGLE));
        assertEquals(1, served("/res/single", "bulk", "other", SINGLE));
    }

    @Test
    void theConditionsOfEquallyCloseBodyConditionedRoutesAreEvaluatedOnce() {
        // no route is closer than the other: the body decides
        assertEquals(2, resolved("/res/ambiguous", AMBIGUOUS));
        assertEquals(2, served("/res/ambiguous", "express", "express", AMBIGUOUS));
        assertEquals(2, served("/res/ambiguous", "bulk", "bulk", AMBIGUOUS));
        assertEquals(2, served("/res/ambiguous", "none", "other", AMBIGUOUS));
    }

    @Test
    void aLocatorLocatesItsTargetOnce() {
        assertEquals(1, resolved("/res/located/shops/3/orders", LOCATED));
        assertEquals(1, served("/res/located/shops/3/orders", "express", "shop 3 express", LOCATED));
        assertEquals(1, served("/res/located/shops/3/orders", "bulk", "shop 3 other", LOCATED));
    }

    @Test
    void theConditionsOfABodyConditionedLocatedRouteAreEvaluatedOnce() {
        assertEquals(1, resolved("/res/located/shops/3/orders", LOCATED_ROUTE));
        assertEquals(1, served("/res/located/shops/3/orders", "express", "shop 3 express", LOCATED_ROUTE));
        assertEquals(1, served("/res/located/shops/3/orders", "bulk", "shop 3 other", LOCATED_ROUTE));
    }

    @Test
    void conditionsDecidedAlready() {
        assertEquals("decided", served("/res/decided", "express"));
        assertEquals("other", served("/res/decided", "bulk"));
        // a stage that failed or was cancelled is not met
        assertEquals("other", served("/res/decided", "failed"));
        assertEquals("other", served("/res/decided", "cancelled"));
    }

    private static int resolved(String path, AtomicInteger counter) {
        int before = counter.get();
        RouteResolution<Object, Object> resolution = ctx.getBean(Router.class)
            .resolve(HttpRequest.POST(path, "express").contentType(MediaType.TEXT_PLAIN_TYPE));
        assertInstanceOf(RouteResolution.Pending.class, resolution);
        return counter.get() - before;
    }

    private static int served(String path, String body, String expected, AtomicInteger counter) {
        int before = counter.get();
        assertEquals(expected, client.toBlocking().retrieve(HttpRequest.POST(path, body).contentType(MediaType.TEXT_PLAIN_TYPE)));
        return counter.get() - before;
    }

    private static String served(String path, String body) {
        return client.toBlocking().retrieve(HttpRequest.POST(path, body).contentType(MediaType.TEXT_PLAIN_TYPE));
    }

    private static HttpResponse<String> text(String text) {
        return HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    private static RequestCondition counted(AtomicInteger counter) {
        return RequestCondition.custom(request -> {
            counter.incrementAndGet();
            return true;
        });
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.POST("/res/single").where(counted(SINGLE)).where(RequestCondition.bodyText("express"::equals)).order(-1).consumesAll()
                .handle((request, pathVariables) -> text("express"));
            routes.POST("/res/single").consumesAll().handle((request, pathVariables) -> text("other"));

            // as close and of the same order: the body tells them apart
            routes.POST("/res/ambiguous").where(counted(AMBIGUOUS)).where(RequestCondition.bodyText("express"::equals)).order(-1).consumesAll()
                .handle((request, pathVariables) -> text("express"));
            routes.POST("/res/ambiguous").where(counted(AMBIGUOUS)).where(RequestCondition.bodyText("bulk"::equals)).order(-1).consumesAll()
                .handle((request, pathVariables) -> text("bulk"));
            routes.POST("/res/ambiguous").consumesAll().handle((request, pathVariables) -> text("other"));

            routes.POST("/res/decided")
                .where(RequestCondition.bodyAsync(String.class, text -> switch (text) {
                    case "failed" -> CompletableFuture.failedFuture(new IllegalStateException("lookup failed"));
                    case "cancelled" -> {
                        CompletableFuture<Boolean> cancelled = new CompletableFuture<>();
                        cancelled.cancel(false);
                        yield cancelled;
                    }
                    default -> CompletableFuture.completedFuture("express".equals(text));
                }))
                .order(-1).consumesAll()
                .handle((request, pathVariables) -> text("decided"));
            routes.POST("/res/decided").consumesAll().handle((request, pathVariables) -> text("other"));

            LocatedRoutes<String> shops = new LocatedRoutes<>() {
                @Override
                public Argument<String> targetType() {
                    return Argument.STRING;
                }

                @Override
                public void routes(LocatedHttpRouteBuilder<String> shop) {
                    shop.POST("/orders").where(counted(LOCATED_ROUTE)).where(RequestCondition.bodyText("express"::equals)).order(-1).consumesAll()
                        .handle((request, pathVariables, id) -> text("shop " + id + " express"));
                    shop.POST("/orders").consumesAll()
                        .handle((request, pathVariables, id) -> text("shop " + id + " other"));
                }
            };
            routes.path("/res/located", located -> located.locate("/shops/{id}", (request, pathVariables) -> {
                LOCATED.incrementAndGet();
                return pathVariables.getString("id");
            }, shop -> shops));
        }
    }
}
