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
            "micronaut.server.dispatch-options-requests", true,
            "micronaut.router.static-resources.static.paths", "classpath:public",
            "micronaut.router.static-resources.static.mapping", "/static/**"));
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

    @Test
    void aRejectionIsAnsweredInsteadOfAStaticFileOfThePath() throws Exception {
        // the file public/index.html is at /static/index.html, and so is a route that produces JSON
        assertEquals(406, send("GET", "/static/index.html", "Accept", "text/html").statusCode());
        assertEquals("index", send("GET", "/static/index.html", "Accept", "application/json").body());
        // without a route of the path, the file
        assertEquals(200, send("GET", "/static/foo/index.html", "Accept", "text/html").statusCode());
    }

    @Test
    void aRejectionIsAnsweredByTheStatusRouteOfTheGroupOfTheRejectedRoute() throws Exception {
        java.net.http.HttpResponse<String> response = send("GET", "/local/fixed", "Accept", "text/plain");
        assertEquals(406, response.statusCode());
        assertEquals("local", response.body());
        // a request no route of the group matches: the global status route
        java.net.http.HttpResponse<String> global = send("GET", "/r/fixed", "Accept", "text/plain");
        assertEquals(406, global.statusCode());
        assertEquals("global", global.body());
    }

    @Test
    void aRejectionOfALocatedRouteIsAnsweredByTheStatusRouteOfItsGroup() throws Exception {
        java.net.http.HttpResponse<String> response = send("GET", "/loc/1/fixed", "Accept", "text/plain");
        assertEquals(406, response.statusCode());
        assertEquals("located", response.body());
    }

    @Test
    void aRejectionOfALocatedRouteIsAnsweredByTheStatusRouteAroundItsLocator() throws Exception {
        // the located routes have no status route: the one of the group of the locator route answers
        java.net.http.HttpResponse<String> response = send("GET", "/anc/1/fixed", "Accept", "text/plain");
        assertEquals(406, response.statusCode());
        assertEquals("ancestor", response.body());
    }

    @Test
    void aLocatedCustomMethodIsAnsweredWithTheRejectionOfItsRouting() throws Exception {
        // REPORT /fixed of any method is the most specific, and cannot consume JSON: 415, not the 406 of REPORT /{x}
        HttpRequest request = HttpRequest.newBuilder(URI.create(server.getURL() + "/cm/1/fixed"))
            .method("REPORT", HttpRequest.BodyPublishers.ofString("{}"))
            .header("Accept", "text/plain")
            .header("Content-Type", "application/json")
            .build();
        assertEquals(415, client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void theRejectionOfTheSelectorOfTheCandidatesWinsOverTheTypesOfOtherSelectors() throws Exception {
        // the route of the other selector cannot consume JSON; the selector of the candidate rejects q=0
        assertEquals(406, send("POST", "/mx/fixed", Map.of("Content-Type", "application/json", "Accept", "text/plain;q=0")).statusCode());
    }

    @Test
    void aRejectionIsNotLostToAWebSocketOfThePath() throws Exception {
        // a plain GET: the WebSocket does not match it, and the selector rejects the HTTP route
        assertEquals(406, send("GET", "/ws/fixed", "Accept", "text/plain").statusCode());
    }

    private static Set<String> allowed(java.net.http.HttpResponse<?> response) {
        return response.headers().allValues("Allow").stream()
            .flatMap(allow -> Arrays.stream(allow.split(",")))
            .map(String::trim)
            .filter(method -> !method.isEmpty())
            .collect(Collectors.toSet());
    }

    private static java.net.http.HttpResponse<String> send(String method, String path, String header, String value) throws IOException, InterruptedException {
        return send(method, path, header == null ? Map.of() : Map.of(header, value));
    }

    private static java.net.http.HttpResponse<String> send(String method, String path, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher body = "POST".equals(method) ? HttpRequest.BodyPublishers.ofString("{}") : HttpRequest.BodyPublishers.noBody();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server.getURL() + path)).method(method, body);
        headers.forEach(request::header);
        return client.send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> status406(String body) {
        return HttpResponse.<String>status(io.micronaut.http.HttpStatus.NOT_ACCEPTABLE).body(body).contentType(MediaType.TEXT_PLAIN_TYPE);
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
            LocatedRoutes<String> located = new LocatedRoutes<>() {
                @Override
                public Argument<String> targetType() {
                    return Argument.STRING;
                }

                @Override
                public void routes(LocatedHttpRouteBuilder<String> routes) {
                    routes.group(group -> {
                        group.status(io.micronaut.http.HttpStatus.NOT_ACCEPTABLE, request -> status406("located"));
                        group.GET("/fixed").produces(MediaType.APPLICATION_JSON_TYPE).handle((request, pathVariables) -> HttpResponse.ok("fixed"));
                    });
                }
            };
            routes.status(io.micronaut.http.HttpStatus.NOT_ACCEPTABLE, request -> status406("global"));
            LocatedRoutes<String> unhandled = new LocatedRoutes<>() {
                @Override
                public Argument<String> targetType() {
                    return Argument.STRING;
                }

                @Override
                public void routes(LocatedHttpRouteBuilder<String> routes) {
                    routes.GET("/fixed").produces(MediaType.APPLICATION_JSON_TYPE).handle((request, pathVariables) -> HttpResponse.ok("fixed"));
                    routes.route("REPORT", "/{x}").produces(MediaType.APPLICATION_JSON_TYPE).handle((request, pathVariables) -> HttpResponse.ok("x"));
                    routes.any("/fixed").consumes(MediaType.TEXT_XML_TYPE).handle((request, pathVariables) -> HttpResponse.ok("any"));
                }
            };
            routes.group(group -> {
                group.templates(TestPathFirstColonRouteTemplateEngine.INSTANCE).selectedBy(TestPathFirstColonRouteTemplateEngine.INSTANCE);
                group.status(io.micronaut.http.HttpStatus.NOT_ACCEPTABLE, request -> status406("ancestor"));
                group.locate("/anc/:id", (request, pathVariables) -> "located", target -> unhandled);
                group.locate("/cm/:id", (request, pathVariables) -> "located", target -> unhandled);
                group.GET("/ws/:x").produces(MediaType.APPLICATION_JSON_TYPE).handle((request, pathVariables) -> HttpResponse.ok("x"));
                group.POST("/mx/:x").consumes(MediaType.APPLICATION_JSON_TYPE).produces(MediaType.TEXT_PLAIN_TYPE)
                    .handle((request, pathVariables) -> HttpResponse.ok("x").contentType(MediaType.TEXT_PLAIN_TYPE));
            });
            // a WebSocket has a URI template, and the selector of the routes of its path
            routes.GET("/ws/fixed").selectedBy(TestPathFirstColonRouteTemplateEngine.INSTANCE)
                .webSocket(ws -> ws.onMessage(Argument.STRING, (session, message) -> session.sendAsync(message)));
            routes.route(io.micronaut.http.HttpMethod.POST, TestPathFirstColonRouteTemplateEngine.template("/mx/fixed"))
                .selectedBy(TestPathFirstColonRouteTemplateEngine.OTHER).consumes(MediaType.TEXT_XML_TYPE)
                .handle((request, pathVariables) -> HttpResponse.ok("fixed"));
            routes.group(group -> {
                group.templates(TestPathFirstColonRouteTemplateEngine.INSTANCE).selectedBy(TestPathFirstColonRouteTemplateEngine.INSTANCE);
                group.status(io.micronaut.http.HttpStatus.NOT_ACCEPTABLE, request -> status406("local"));
                group.GET("/local/fixed").produces(MediaType.APPLICATION_JSON_TYPE).handle((request, pathVariables) -> HttpResponse.ok("fixed"));
                group.locate("/loc/:id", (request, pathVariables) -> "located", target -> located);
            });
            routes.group(group -> {
                group.templates(TestPathFirstColonRouteTemplateEngine.INSTANCE).selectedBy(TestPathFirstColonRouteTemplateEngine.INSTANCE);
                group.GET("/static/index.html").produces(MediaType.APPLICATION_JSON_TYPE).handle((request, pathVariables) -> HttpResponse.ok("index").contentType(MediaType.TEXT_PLAIN_TYPE));
            });
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
