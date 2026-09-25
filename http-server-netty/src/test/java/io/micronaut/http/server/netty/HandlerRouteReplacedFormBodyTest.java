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
import io.micronaut.http.form.FormData;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The form a form handler route receives is the body of the request the route is invoked with:
 * a filter that cleared the body leaves a form without fields, not the form that was sent, like
 * for the other readers of the body.
 */
class HandlerRouteReplacedFormBodyTest {

    @Test
    void aClearedBodyIsAFormWithoutFields() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "HandlerRouteReplacedFormBodyTest", "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
                assertEquals("missing", post(client, "/form/cleared"));
            }
        }
    }

    @Test
    void aFilterThatDoesNotSetTheBodyKeepsTheForm() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "HandlerRouteReplacedFormBodyTest", "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
                assertEquals("original", post(client, "/form/header"));
            }
        }
    }

    private static String post(HttpClient client, String uri) {
        return client.toBlocking().retrieve(HttpRequest.POST(uri, "secret=original").contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
    }

    private static HttpResponse<String> secret(FormData form) {
        return HttpResponse.ok(form.getString("secret", "missing")).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    @Factory
    @Requires(property = "spec.name", value = "HandlerRouteReplacedFormBodyTest")
    static class Routes {
        @Singleton
        HttpRoutes routes() {
            return routes -> {
                routes.POST("/form/cleared", (request, variables, form) -> secret(form))
                    .beforeReplacing(request -> request.body(null));
                routes.POST("/form/header", (request, variables, form) -> secret(form))
                    .beforeReplacing(request -> request.header("X-Filtered", "true"));
            };
        }
    }
}
