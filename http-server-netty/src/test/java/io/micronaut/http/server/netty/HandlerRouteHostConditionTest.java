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
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RouteCondition;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code where(host(...))} reads the host as the host resolver of the server resolves it, here
 * from a configured header, not the {@code Host} header of the request.
 */
class HandlerRouteHostConditionTest {

    @Test
    void aHostConditionReadsTheHostTheServerResolves() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "HandlerRouteHostConditionTest", "micronaut.server.port", -1,
            "micronaut.server.host-resolution.host-header", "X-Site"))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
                assertEquals("example", client.toBlocking().retrieve(HttpRequest.GET("/site").header("X-Site", "example.com")));
                assertEquals("other", client.toBlocking().retrieve(HttpRequest.GET("/site")), "the Host header is not the resolved host");
            }
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "HandlerRouteHostConditionTest")
    static class Routes {
        @Singleton
        HttpRoutes routes() {
            return routes -> {
                routes.GET("/site").where(RouteCondition.host("example.com")).order(-1)
                    .handle((request, pathVariables) -> HttpResponse.ok("example").contentType(MediaType.TEXT_PLAIN_TYPE));
                routes.GET("/site", (request, pathVariables) -> HttpResponse.ok("other").contentType(MediaType.TEXT_PLAIN_TYPE));
            };
        }
    }
}
