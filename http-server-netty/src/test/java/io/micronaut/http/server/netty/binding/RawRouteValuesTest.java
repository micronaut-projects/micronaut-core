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
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.PathVariables;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
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
import org.jspecify.annotations.Nullable;
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

    @Test
    void aRawFormReadsTheFieldsUpToTheLimitOfTheForm() throws Exception {
        // the default form-max-fields is 128
        assertEquals(200, postStatus("/raw/form-all", "a" + "&a".repeat(127), MediaType.APPLICATION_FORM_URLENCODED));
        assertEquals("{a=" + String.join(",", java.util.Collections.nCopies(128, "")) + "}",
            post("/raw/form-all", "a" + "&a".repeat(127)));
    }

    @Test
    void aRawFormIsValidatedLikeTheDecodedForm() throws Exception {
        assertEquals(400, postStatus("/raw/form-all", "foo=ba\rr", MediaType.APPLICATION_FORM_URLENCODED));
        assertEquals(400, postStatus("/raw/decoded-form-all", "foo=ba\rr", MediaType.APPLICATION_FORM_URLENCODED));
    }

    @Test
    void theValuesOfARawFormOfACharsetThatIsNotAsciiCompatibleAreTheBytesOfTheBody() throws Exception {
        // the values are percent-encoded ASCII, whatever the charset of the decoded values
        assertEquals("{n\u00e9=%00%E9}", post("/raw/form-all", "%00n%00%E9=%00%E9", MediaType.APPLICATION_FORM_URLENCODED + ";charset=UTF-16BE"));
    }

    @Test
    void aRawFormHasTheBufferLimitOfTheDecodedForm() throws Exception {
        // like FormSmallBufferLimitTest: the limit is on the buffered values, not the whole body
        try (ApplicationContext small = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1,
            "micronaut.server.max-request-buffer-size", 16))) {
            EmbeddedServer smallServer = small.getBean(EmbeddedServer.class).start();
            java.net.http.HttpResponse<String> response = client.send(java.net.http.HttpRequest.newBuilder(
                    URI.create("http://localhost:" + smallServer.getPort() + "/raw/form-all"))
                    .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString("a=12345678&b=1234567")).build(),
                BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("{a=12345678, b=1234567}", response.body());
        }
    }

    @Test
    void aRawFormOfACopyLeavesTheBytesToTheRoute() throws Exception {
        // the decoder percent-decodes the names, and the values that end its input, in place
        String body = "n%61me=a%2Fb&v=%C3%A9";
        assertEquals("{name=a%2Fb, v=%C3%A9}|" + body, post("/raw/copied", body));
        assertEquals("{name=a/b, v=\u00e9}|" + body, post("/raw/copied-decoded", body));
    }

    @Test
    void aRawFormOfAMutatedRequest() throws Exception {
        assertEquals("{v=a%2Fb}", post("/raw/mutated", "v=a%2Fb"));
    }

    @Test
    void aRawFormOfARequestWhoseUriAFilterChanged() throws Exception {
        assertEquals("{v=a%2Fb}", post("/raw/rewritten", "v=a%2Fb"));
    }

    @Test
    void anEscapeSplitBetweenTwoChunksOfARawForm() throws Exception {
        // the body arrives in pieces: an escape split between them is read whole, and validated by
        // the decoder that refuses malformed escapes
        try (ApplicationContext strict = strict()) {
            int port = strict.getBean(EmbeddedServer.class).start().getPort();
            java.net.http.HttpResponse<String> accepted = send(port, "/raw/form-all",
                java.net.http.HttpRequest.BodyPublishers.fromPublisher(new ChunkPublisher("v=a%2", "Fb%C", "3%A9")));
            assertEquals("{v=a%2Fb%C3%A9}", accepted.body());
        }
    }

    /**
     * @return A server whose form decoder refuses the malformed percent escapes
     */
    private static ApplicationContext strict() {
        return ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1,
            "micronaut.server.netty.form-decoder-quirks", java.util.List.of("refuse-non-hex-percent-decode", "refuse-short-percent-decode")));
    }

    private static java.net.http.HttpResponse<String> send(int port, String path, java.net.http.HttpRequest.BodyPublisher body) throws Exception {
        return client.send(java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED)
                .POST(body).build(),
            BodyHandlers.ofString());
    }

    @Test
    void anAbsentOptionalVariableHasNoRawValue() throws Exception {
        assertEquals("false|[]", get("/raw/optional"));
        assertEquals("true|a%2Fb", get("/raw/optional/a%2Fb"));
    }

    private static String get(String path) throws Exception {
        return client.send(java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + path)).build(),
            BodyHandlers.ofString()).body();
    }

    private static String post(String path, String body) throws Exception {
        return post(path, body, MediaType.APPLICATION_FORM_URLENCODED);
    }

    private static String post(String path, String body, String contentType) throws Exception {
        return send(path, body, contentType).body();
    }

    private static int postStatus(String path, String body, String contentType) throws Exception {
        return send(path, body, contentType).statusCode();
    }

    private static java.net.http.HttpResponse<String> send(String path, String body, String contentType) throws Exception {
        return client.send(java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + path))
                .header("Content-Type", contentType)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.US_ASCII)).build(),
            BodyHandlers.ofString());
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

        @Post(uri = "/form-all", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> formAll(AsyncRequestBody body) {
            return body.form(FormOptions.defaults().withRawValues()).thenApply(RawController::all);
        }

        @Post(uri = "/decoded-form-all", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> decodedFormAll(AsyncRequestBody body) {
            return body.form().thenApply(RawController::all);
        }

        @Get(uri = "/optional{/page}", produces = MediaType.TEXT_PLAIN)
        String optional(HttpRequest<?> request, PathVariables pathVariables) {
            UriRouteMatch<?, ?> match = (UriRouteMatch<?, ?>) RouteAttributes.getRouteMatch(request).orElseThrow();
            Map<String, Object> raw = match.getRawVariableValues();
            return raw.containsKey("page") + "|" + (raw.containsKey("page") ? pathVariables.rawString("page") : raw.keySet().toString());
        }

        @Post(uri = "/copied", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> copied(HttpRequest<?> request, AsyncRequestBody body) {
            return body.text().thenApply(text -> request.getAttribute(COPIED_FORM, String.class).orElse("none") + "|" + text);
        }

        @Post(uri = "/copied-decoded", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> copiedDecoded(HttpRequest<?> request, AsyncRequestBody body) {
            return copied(request, body);
        }

        @Post(uri = "/rewritten", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> rewritten(HttpRequest<?> request, AsyncRequestBody body) {
            assertEquals("true", request.getParameters().get("rewritten"));
            return formAll(body);
        }

        @Post(uri = "/mutated", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> mutated(HttpRequest<?> request, AsyncRequestBody body) {
            assertEquals("1", request.getHeaders().get("X-Mutated"));
            return formAll(body);
        }

        static String all(io.micronaut.http.form.FormData form) {
            java.util.Map<String, String> all = new java.util.LinkedHashMap<>();
            for (String name : form.names()) {
                all.put(name, String.join(",", form.getValues(name)));
            }
            return all.toString();
        }

        private static String describe(io.micronaut.http.form.FormData form) {
            return form.getString("path") + "|" + form.getString("plus") + "|" + form.getString("bang") + "|"
                + form.getString("text") + "|" + form.getValues("name") + "|" + form.getValues("empty").get(0);
        }
    }

    private static final String COPIED_FORM = "raw.copied.form";

    @ServerFilter("/raw")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RawFilter {
        @RequestFilter("/copied")
        CompletionStage<@Nullable HttpResponse<?>> copied(HttpRequest<?> request, AsyncRequestBody body) {
            return copy(request, body, FormOptions.defaults().withRawValues());
        }

        @RequestFilter("/copied-decoded")
        CompletionStage<@Nullable HttpResponse<?>> copiedDecoded(HttpRequest<?> request, AsyncRequestBody body) {
            return copy(request, body, FormOptions.defaults());
        }

        private static CompletionStage<@Nullable HttpResponse<?>> copy(HttpRequest<?> request, AsyncRequestBody body, FormOptions options) {
            return body.copy().form(options).thenApply(form -> {
                request.setAttribute(COPIED_FORM, RawController.all(form));
                return null;
            });
        }

        @RequestFilter("/mutated")
        HttpRequest<?> mutated(HttpRequest<?> request) {
            return request.mutate().header("X-Mutated", "1");
        }

        @RequestFilter("/rewritten")
        void rewritten(MutableHttpRequest<?> request) {
            request.uri(URI.create("/raw/rewritten?rewritten=true"));
        }
    }

    /**
     * A body in the given pieces, each sent once the previous one was requested.
     */
    private record ChunkPublisher(String... chunks) implements java.util.concurrent.Flow.Publisher<java.nio.ByteBuffer> {
        @Override
        public void subscribe(java.util.concurrent.Flow.Subscriber<? super java.nio.ByteBuffer> subscriber) {
            subscriber.onSubscribe(new java.util.concurrent.Flow.Subscription() {
                private int next;
                private boolean done;

                @Override
                public synchronized void request(long n) {
                    for (long i = 0; i < n && !done; i++) {
                        if (next == chunks.length) {
                            done = true;
                            subscriber.onComplete();
                            return;
                        }
                        subscriber.onNext(java.nio.ByteBuffer.wrap(chunks[next++].getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
                    }
                }

                @Override
                public synchronized void cancel() {
                    done = true;
                }
            });
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
