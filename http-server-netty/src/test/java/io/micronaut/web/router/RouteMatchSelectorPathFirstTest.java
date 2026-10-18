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
package io.micronaut.web.router;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A route selector that ranks the routes by the path first, as JAX-RS does (section 3.7.2): the
 * most specific template is chosen before the media types, which then answer {@code 406} or
 * {@code 415}, and a locator route does not locate when a route of another method is more
 * specific, so that the {@code 405} status and the automatic {@code OPTIONS} apply.
 */
class RouteMatchSelectorPathFirstTest {

    private static final String SPEC_NAME = "RouteMatchSelectorPathFirstTest";

    private static ApplicationContext context;
    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        context = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1,
            "micronaut.server.dispatch-options-requests", true));
        server = context.getBean(EmbeddedServer.class).start();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stop() {
        client.close();
        context.close();
    }

    @Test
    void theMostSpecificTemplateAnswersNotAcceptable() throws Exception {
        assertEquals(406, send("GET", "/r/fixed", "Accept", "text/plain").statusCode());
        java.net.http.HttpResponse<String> fixed = send("GET", "/r/fixed", "Accept", "application/json");
        assertEquals(200, fixed.statusCode());
        assertEquals("fixed", fixed.body());
        java.net.http.HttpResponse<String> variable = send("GET", "/r/other", "Accept", "text/plain");
        assertEquals(200, variable.statusCode());
        assertEquals("x", variable.body());
    }

    @Test
    void theMostSpecificTemplateAnswersUnsupportedMediaType() throws Exception {
        assertEquals(415, send("POST", "/c/fixed", "Content-Type", "text/plain").statusCode());
        assertEquals("fixed", send("POST", "/c/fixed", "Content-Type", "application/json").body());
        assertEquals("x", send("POST", "/c/other", "Content-Type", "text/plain").body());
    }

    @Test
    void aMoreSpecificRouteOfAnotherMethodKeepsTheLocatorFromLocating() throws Exception {
        // the locator route accepts OPTIONS, but would fail to convert "sub"
        java.net.http.HttpResponse<String> options = send("OPTIONS", "/GetTest/sub", null, null);
        assertEquals(200, options.statusCode());
        assertEquals(Set.of("GET", "HEAD", "OPTIONS"), allowed(options));
        java.net.http.HttpResponse<String> post = send("POST", "/GetTest/sub", null, null);
        assertEquals(405, post.statusCode());
        assertEquals(Set.of("GET", "HEAD"), allowed(post));
        assertEquals(200, send("HEAD", "/GetTest/sub", null, null).statusCode());
        assertEquals("sub", send("GET", "/GetTest/sub", null, null).body());
        assertEquals("item 3 of 5", send("GET", "/GetTest/5/items/3", null, null).body());
    }

    @Test
    void theRejectionOfTheCustomMethodWinsOverTheRouteOfAnyMethod() throws Exception {
        // REPORT /rr/fixed is chosen and cannot produce text/plain; the route of any method alone cannot consume JSON: 415
        HttpRequest request = HttpRequest.newBuilder(URI.create(server.getURL() + "/rr/fixed"))
            .method("REPORT", HttpRequest.BodyPublishers.ofString("x"))
            .header("Accept", "text/plain")
            .header("Content-Type", "application/json")
            .build();
        assertEquals(406, client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private static Set<String> allowed(java.net.http.HttpResponse<?> response) {
        return response.headers().allValues("Allow").stream()
            .flatMap(allow -> Arrays.stream(allow.split(",")))
            .map(String::trim)
            .filter(method -> !method.isEmpty())
            .collect(Collectors.toSet());
    }

    private static java.net.http.HttpResponse<String> send(String method, String path, String header, String value) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher body = "POST".equals(method) ? HttpRequest.BodyPublishers.ofString("{}") : HttpRequest.BodyPublishers.noBody();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server.getURL() + path)).method(method, body);
        if (header != null) {
            request.header(header, value);
        }
        return client.send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            LocatedRoutes<Integer> items = new LocatedRoutes<>() {
                @Override
                public Argument<Integer> targetType() {
                    return Argument.INT;
                }

                @Override
                public void routes(LocatedHttpRouteBuilder<Integer> located) {
                    located.GET("/items/{item}").handle((request, pathVariables, target) ->
                        HttpResponse.ok("item " + pathVariables.getString("item") + " of " + target).contentType(MediaType.TEXT_PLAIN_TYPE));
                }
            };
            routes.group(group -> {
                group.templates(TestPathFirstColonRouteTemplateEngine.INSTANCE).selectedBy(TestPathFirstColonRouteTemplateEngine.INSTANCE);
                group.GET("/r/fixed").produces(MediaType.APPLICATION_JSON_TYPE).handle((request, pathVariables) -> HttpResponse.ok("fixed").contentType(MediaType.TEXT_PLAIN_TYPE));
                group.GET("/r/:x").produces(MediaType.TEXT_PLAIN_TYPE).handle((request, pathVariables) -> HttpResponse.ok("x"));
                group.POST("/c/fixed").consumes(MediaType.APPLICATION_JSON_TYPE).handle((request, pathVariables) -> HttpResponse.ok("fixed").contentType(MediaType.TEXT_PLAIN_TYPE));
                group.POST("/c/:x").consumes(MediaType.TEXT_PLAIN_TYPE).handle((request, pathVariables) -> HttpResponse.ok("x").contentType(MediaType.TEXT_PLAIN_TYPE));
                group.GET("/GetTest/sub").handle((request, pathVariables) -> HttpResponse.ok("sub").contentType(MediaType.TEXT_PLAIN_TYPE));
                group.route("REPORT", "/rr/fixed").produces(MediaType.APPLICATION_JSON_TYPE).handle((request, pathVariables) -> HttpResponse.ok("fixed"));
                group.any("/rr/:x").consumes(MediaType.TEXT_XML_TYPE).handle((request, pathVariables) -> HttpResponse.ok("x"));
                // the locator converts the id to an int, as a JAX-RS sub-resource locator of an int parameter
                group.locate("/GetTest/:id", (request, pathVariables) -> Integer.valueOf(pathVariables.getString("id")), target -> items);
            });
        }
    }
}
