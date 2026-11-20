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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A route that keeps its error responses sends the responses of {@code 400} or more it returns
 * as they are, with their body and headers, despite the global status routes and the status
 * routes of its groups; a route that does not is answered by them; an exception still goes to
 * the error routes.
 */
class HandlerRouteKeepErrorResponsesTest {
    private static final String SPEC_NAME = "HandlerRouteKeepErrorResponsesTest";

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
    void aRouteThatKeepsItsErrorResponsesSendsThemAsTheyAre() {
        HttpClientResponseException notFound = exchange("/kept/missing");
        assertEquals(404, notFound.getStatus().getCode());
        assertEquals("upstream missing", notFound.getResponse().getBody(String.class).orElse(null));
        assertEquals("yes", notFound.getResponse().getHeaders().get("X-Upstream"));

        HttpClientResponseException failed = exchange("/kept/failed");
        assertEquals(500, failed.getStatus().getCode());
        assertEquals("upstream failed", failed.getResponse().getBody(String.class).orElse(null));
        assertEquals("yes", failed.getResponse().getHeaders().get("X-Upstream"));
    }

    @Test
    void aGroupThatKeepsItsErrorResponsesSendsThemAsTheyAre() {
        HttpClientResponseException notFound = exchange("/group/kept");
        assertEquals("upstream missing", notFound.getResponse().getBody(String.class).orElse(null));
    }

    @Test
    void withoutItTheStatusRoutesReplaceTheErrorResponses() {
        assertEquals("global not found", exchange("/replaced").getResponse().getBody(String.class).orElse(null));
        assertEquals("group not found", exchange("/group/replaced").getResponse().getBody(String.class).orElse(null));
    }

    @Test
    void aResponseFilterDoesNotLetTheStatusRoutesReplaceAKeptErrorResponse() {
        HttpClientResponseException notFound = exchange("/filtered/missing");
        assertEquals(404, notFound.getStatus().getCode());
        assertEquals("upstream missing", notFound.getResponse().getBody(String.class).orElse(null));
        assertEquals("yes", notFound.getResponse().getHeaders().get("X-Filtered"));
    }

    @Test
    void anExceptionStillGoesToTheErrorRoutes() {
        HttpClientResponseException thrown = exchange("/kept/thrown");
        assertEquals(409, thrown.getStatus().getCode());
        assertEquals("error route", thrown.getResponse().getBody(String.class).orElse(null));
    }

    private static HttpClientResponseException exchange(String path) {
        return assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.GET(path), String.class));
    }

    private static HttpResponse<?> upstream(HttpStatus status, String body) {
        return HttpResponse.status(status).body(body).contentType(MediaType.TEXT_PLAIN_TYPE).header("X-Upstream", "yes");
    }

    @ServerFilter("/filtered/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class HeaderFilter {
        @ResponseFilter
        void header(MutableHttpResponse<?> response) {
            response.header("X-Filtered", "yes");
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.status(HttpStatus.NOT_FOUND, request -> HttpResponse.notFound("global not found").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.status(HttpStatus.INTERNAL_SERVER_ERROR, request -> HttpResponse.serverError("global server error").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.error(IllegalStateException.class, (request, error) -> HttpResponse.status(HttpStatus.CONFLICT).body("error route")
                .contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.path("/kept", kept -> {
                kept.GET("/missing").keepErrorResponses().handle((request, pathVariables) -> upstream(HttpStatus.NOT_FOUND, "upstream missing"));
                kept.GET("/failed").keepErrorResponses().handle((request, pathVariables) -> upstream(HttpStatus.INTERNAL_SERVER_ERROR, "upstream failed"));
                kept.GET("/thrown").keepErrorResponses().handle((request, pathVariables) -> {
                    throw new IllegalStateException("thrown");
                });
            });
            routes.GET("/filtered/missing").keepErrorResponses().handle((request, pathVariables) -> upstream(HttpStatus.NOT_FOUND, "upstream missing"));
            routes.GET("/replaced", (request, pathVariables) -> upstream(HttpStatus.NOT_FOUND, "upstream missing"));
            routes.path("/group", group -> {
                group.status(HttpStatus.NOT_FOUND, request -> HttpResponse.notFound("group not found").contentType(MediaType.TEXT_PLAIN_TYPE));
                group.GET("/replaced", (request, pathVariables) -> upstream(HttpStatus.NOT_FOUND, "upstream missing"));
                group.path("/kept", kept -> {
                    kept.keepErrorResponses();
                    kept.GET("", (request, pathVariables) -> upstream(HttpStatus.NOT_FOUND, "upstream missing"));
                });
            });
        }
    }
}
