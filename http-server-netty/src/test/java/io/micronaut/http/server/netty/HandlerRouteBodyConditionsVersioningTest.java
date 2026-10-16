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
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import io.micronaut.web.router.builder.RequestCondition;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With API versioning, the router filters the routes: a body that does not meet the condition
 * of a route of a located target still falls through to its less specific route.
 */
class HandlerRouteBodyConditionsVersioningTest {
    private static final String SPEC_NAME = "HandlerRouteBodyConditionsVersioningTest";

    @Test
    void aBodyFallsThroughToTheLessSpecificRouteOfALocatedTargetWithVersioning() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1,
            "micronaut.router.versioning.enabled", true))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
                assertEquals("special", client.toBlocking().retrieve(HttpRequest.POST("/review/items/special", "express").contentType(MediaType.TEXT_PLAIN_TYPE)));
                assertEquals("by name", client.toBlocking().retrieve(HttpRequest.POST("/review/items/special", "ordinary").contentType(MediaType.TEXT_PLAIN_TYPE)));
            }
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            LocatedRoutes<String> review = new LocatedRoutes<>() {
                @Override
                public Argument<String> targetType() {
                    return Argument.STRING;
                }

                @Override
                public void routes(LocatedHttpRouteBuilder<String> located) {
                    located.POST("/items/special").where(RequestCondition.bodyText("express"::equals)).consumesAll()
                        .handle((request, pathVariables, target) -> HttpResponse.ok("special").contentType(MediaType.TEXT_PLAIN_TYPE));
                    located.POST("/items/{name}").consumesAll()
                        .handle((request, pathVariables, target) -> HttpResponse.ok("by name").contentType(MediaType.TEXT_PLAIN_TYPE));
                }
            };
            routes.locate("/review", (request, pathVariables) -> "review", target -> review);
        }
    }
}
