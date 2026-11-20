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
package io.micronaut.http.server;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpApplicationTest {

    private static final String SPEC_NAME = "HttpApplicationTest";

    @Test
    void routesDeclaredInMain() throws Exception {
        try (ApplicationContext context = HttpApplication.build()
            .port(-1)
            .property("spec.name", SPEC_NAME)
            .routes(routes -> routes.GET("/hello/{name}", (request, vars) ->
                HttpResponse.ok("Hello " + vars.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE)))
            .routes(routes -> routes.path("/api", api ->
                api.GET("/ping").respond(HttpResponse.ok("pong").contentType(MediaType.TEXT_PLAIN_TYPE))))
            .start()) {

            assertTrue(context.getBean(EmbeddedServer.class).isRunning());
            assertEquals("Hello Fred", get(context, "/hello/Fred"));
            assertEquals("pong", get(context, "/api/ping"));
        }
    }

    @Test
    void routesUsingBeans() throws Exception {
        try (ApplicationContext context = HttpApplication.build()
            .port(-1)
            .property("spec.name", SPEC_NAME)
            .routes((routes, beans) -> {
                Greeter greeter = beans.getBean(Greeter.class);
                routes.GET("/greet/{name}", (request, vars) ->
                    HttpResponse.ok(greeter.greet(vars.getString("name"))).contentType(MediaType.TEXT_PLAIN_TYPE));
            })
            .start()) {

            assertEquals("Hi Fred", get(context, "/greet/Fred"));
        }
    }

    @Test
    void runWithArguments() throws Exception {
        try (ApplicationContext context = HttpApplication.run(
            new String[] {"-micronaut.server.port=-1", "-spec.name=" + SPEC_NAME},
            routes -> routes.GET("/", (request, vars) -> HttpResponse.ok("root").contentType(MediaType.TEXT_PLAIN_TYPE)))) {

            assertEquals("root", get(context, "/"));
        }
    }

    private static String get(ApplicationContext context, String path) throws Exception {
        URI uri = context.getBean(EmbeddedServer.class).getURI().resolve(path);
        try (HttpClient client = HttpClient.newHttpClient()) {
            java.net.http.HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri).build(), BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response::body);
            return response.body();
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Greeter {
        String greet(String name) {
            return "Hi " + name;
        }
    }
}
