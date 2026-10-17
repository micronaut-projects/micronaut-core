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
package io.micronaut.http.server.netty.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.PathVariables;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.FormOptions;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The raw values of the path variables and of the fields of a url-encoded form, as they appear
 * in the request, not percent-decoded: for controller, functional and located routes. A simple
 * variable does not match a {@code +}: the routes with one take a regular expression variable.
 */
class RawRouteValuesTest {
    private static final String SPEC_NAME = "RawRouteValuesTest";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1));
        server = ctx.getBean(EmbeddedServer.class).start();
        client = HttpClient.newHttpClient();
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

    @ParameterizedTest
    @ValueSource(strings = {"a%2Fb", "a+b", "x!y", "%C3%A9t%C3%A9", "~'()"})
    void aControllerReadsTheRawPathVariable(String raw) throws Exception {
        assertEquals(raw + "|" + raw, get("/raw/controller/" + raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a%2Fb", "a+b", "x!y", "%C3%A9t%C3%A9"})
    void aHandlerFunctionReadsTheRawPathVariable(String raw) throws Exception {
        assertEquals(raw, get("/raw/fn/" + raw));
    }

    @Test
    void theDecodedValueIsStillDecoded() throws Exception {
        assertEquals("a/b été", get("/raw/decoded/a%2Fb%20%C3%A9t%C3%A9"));
    }

    @Test
    void aLocatedRouteReadsTheRawValuesOfThePrefixAndOfTheRoute() throws Exception {
        assertEquals("p%2Fq!|a+b%C3%A9|p/q!", get("/raw/located/p%2Fq!/items/a+b%C3%A9"));
    }

    @Test
    void theRawValuesOfTheRawValuesAreThemselves() throws Exception {
        assertEquals("a%2Fb", get("/raw/twice/a%2Fb"));
    }

    @Test
    void aUrlEncodedFormKeepsTheRawValues() throws Exception {
        String body = "path=a%2Fb&plus=a+b&bang=x!y&text=%C3%A9t%C3%A9&n%61me=1&n%61me=2&empty";
        assertEquals("a%2Fb|a+b|x!y|%C3%A9t%C3%A9|[1, 2]|", post("/raw/form", body));
        assertEquals("a/b|a b|x!y|été|[1, 2]|", post("/raw/decoded-form", body));
    }

    private static String get(String path) throws Exception {
        return client.send(java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + path)).build(),
            BodyHandlers.ofString()).body();
    }

    private static String post(String path, String body) throws Exception {
        return client.send(java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + path))
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),
            BodyHandlers.ofString()).body();
    }

    @Controller("/raw")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RawController {
        @Get(uri = "/controller/{name:[^/]+}", produces = MediaType.TEXT_PLAIN)
        String controller(HttpRequest<?> request, PathVariables pathVariables) {
            UriRouteMatch<?, ?> match = (UriRouteMatch<?, ?>) RouteAttributes.getRouteMatch(request).orElseThrow();
            return pathVariables.rawString("name") + "|" + match.getRawVariableValues().get("name");
        }

        @Get(uri = "/decoded/{name}", produces = MediaType.TEXT_PLAIN)
        String decoded(PathVariables pathVariables) {
            return pathVariables.getString("name");
        }

        @Get(uri = "/twice/{name}", produces = MediaType.TEXT_PLAIN)
        String twice(PathVariables pathVariables) {
            return pathVariables.raw().raw().getString("name");
        }

        @Post(uri = "/form", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> form(AsyncRequestBody body) {
            return body.form(FormOptions.defaults().withRawValues()).thenApply(RawController::describe);
        }

        @Post(uri = "/decoded-form", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> decodedForm(AsyncRequestBody body) {
            return body.form(FormOptions.defaults()).thenApply(RawController::describe);
        }

        private static String describe(io.micronaut.http.form.FormData form) {
            return form.getString("path") + "|" + form.getString("plus") + "|" + form.getString("bang") + "|"
                + form.getString("text") + "|" + form.getValues("name") + "|" + form.getValues("empty").get(0);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RawRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/raw/fn/{name:[^/]+}", (request, pathVariables) ->
                HttpResponse.ok(pathVariables.rawString("name")).contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.locate("/raw/located/{prefix}", (request, pathVariables) -> pathVariables.raw().getString("prefix"), new ItemRoutes());
        }
    }

    static final class ItemRoutes implements LocatedRoutes<String> {
        @Override
        public Argument<String> targetType() {
            return Argument.STRING;
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<String> located) {
            located.GET("/items/{name:[^/]+}", (request, pathVariables) ->
                HttpResponse.ok(pathVariables.rawString("prefix") + "|" + pathVariables.rawString("name") + "|" + pathVariables.getString("prefix"))
                    .contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }
}
