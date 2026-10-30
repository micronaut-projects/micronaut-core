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
import io.micronaut.http.annotation.PathVariable;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @ParameterizedTest
    @ValueSource(strings = {"/raw/decoded-plus/", "/raw/fn-decoded/"})
    void aDecodedPathVariableKeepsAPlusAsAPlus(String path) throws Exception {
        // only form and query decoding treat a + as a space, RFC 3986 section 3.3
        assertEquals("a+b c+", get(path + "a+b%20c%2B"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/raw/decoded-plus/", "/raw/fn-decoded/"})
    void anEncodedPlusIsAPlusAndAnEncodedSpaceASpace(String path) throws Exception {
        // %2B on its own, and %20 in the same segment, without a literal +
        assertEquals("+", get(path + "%2B"));
        assertEquals("a b", get(path + "a%20b"));
        assertEquals("+ +", get(path + "%2B%20%2B"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/raw/decoded-plus/", "/raw/fn-decoded/"})
    void aPlusInAPathVariableIsASpaceWhenConfigured(String path) throws Exception {
        // the decoding before 5.3, like a form value
        try (ApplicationContext plusAsSpace = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1,
            "micronaut.router.path-variable-plus-as-space", true))) {
            int port = plusAsSpace.getBean(EmbeddedServer.class).start().getPort();
            java.net.http.HttpResponse<String> response = client.send(java.net.http.HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + path + "a+b%20c%2B")).build(), BodyHandlers.ofString());
            assertEquals("a b c+", response.body());
        }
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

    @ParameterizedTest
    @ValueSource(strings = {"/raw/form-all", "/raw/decoded-form-all", "/raw/form-data-arg"})
    void aFormOverTheFieldLimitIsRejected(String path) throws Exception {
        // the default form-max-fields is 128. The last fields of a body that arrived whole are
        // decoded at its end: the failure releases the bytes, which the leak check of the class checks
        assertEquals(413, postStatus(path, "a" + "&a".repeat(128), MediaType.APPLICATION_FORM_URLENCODED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"v=%rr", "v=%a", "v=a%", "v=a%2", "%rr=v"})
    void aMalformedEscapeIsRejectedByAStrictDecoder(String body) throws Exception {
        try (ApplicationContext strict = strict()) {
            int port = strict.getBean(EmbeddedServer.class).start().getPort();
            for (String path : java.util.List.of("/raw/form-all", "/raw/decoded-form-all", "/raw/form-data-arg")) {
                assertEquals(400, send(port, path, java.net.http.HttpRequest.BodyPublishers.ofString(body)).statusCode(), path);
            }
        }
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

    @ParameterizedTest
    @ValueSource(strings = {"/raw/after-form-data", "/raw/after-text", "/raw/after-replaced"})
    void aRawFormNeedsTheBytes(String path) throws Exception {
        // an earlier read consumed the bytes, or a filter replaced the body: the leak check of
        // the class checks the bytes were released
        assertEquals("IllegalStateException", post(path, "v=a%2Fb"));
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
    void anAbsentOptionalVariableOfALocatorHasNoRawValue() throws Exception {
        assertEquals("located:false|absent", get("/raw/shops/items"));
        assertEquals("located:true|7", get("/raw/shops/7/items"));
    }

    @Test
    void anAbsentVariableOfALocatedRouteShadowsTheVariableOfThePrefix() throws Exception {
        // the constraint and the handler see the same raw variables: the absent id of the route
        assertEquals("tenant abc|false", get("/raw/tenants/abc/items"));
        assertEquals("tenant abc|7", get("/raw/tenants/abc/items/7"));
    }

    @Test
    void anAbsentVariableOfANestedLocatorShadowsTheVariableOfTheOuterPrefix() throws Exception {
        assertEquals("false|null", get("/raw/orgs/acme/shops/items"));
        assertEquals("7|7", get("/raw/orgs/acme/shops/7/items"));
    }

    @Test
    void aRawValueKeepsTheAsciiCharactersACharsetMapsElsewhere() throws Exception {
        // x-IBM943 decodes 0x7E as U+203E
        assertEquals("{v=~\\}", post("/raw/form-all", "v=~\\", MediaType.APPLICATION_FORM_URLENCODED + ";charset=x-IBM943"));
    }

    @Test
    void aPendingRawFormIsCancelledWhenTheHandlerCompleted() throws Exception {
        PENDING.set(null);
        try (java.net.Socket socket = partialForm("/raw/pending-early")) {
            // the body has not arrived: the response proves the handler completed while the form was pending
            assertTrue(readUntil(socket.getInputStream(), "early").contains("early"));
            java.util.concurrent.CompletableFuture<?> form = awaitPending();
            java.util.concurrent.ExecutionException failure = org.junit.jupiter.api.Assertions.assertThrows(
                java.util.concurrent.ExecutionException.class, () -> form.get(10, java.util.concurrent.TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertInstanceOf(java.util.concurrent.CancellationException.class, failure.getCause());
        }
        // the leak check of the class checks the bytes that arrived were released
        assertEquals("{v=a%2Fb}", post("/raw/form-all", "v=a%2Fb"));
    }

    @Test
    void aPendingRawFormFailsWhenTheClientDisconnects() throws Exception {
        PENDING.set(null);
        java.util.concurrent.CompletableFuture<?> form;
        try (java.net.Socket ignored = partialForm("/raw/pending")) {
            form = awaitPending();
            org.junit.jupiter.api.Assertions.assertFalse(form.isDone(), "the form waits for the rest of the body");
        }
        org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
            () -> form.get(10, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals("{v=a%2Fb}", post("/raw/form-all", "v=a%2Fb"));
    }

    /**
     * @param path The path
     * @return A connection that sent the start of a form of 1000 bytes
     */
    private static java.net.Socket partialForm(String path) throws java.io.IOException {
        java.net.Socket socket = new java.net.Socket(java.net.InetAddress.getLoopbackAddress(), server.getPort());
        socket.setSoTimeout(10_000);
        java.io.OutputStream out = socket.getOutputStream();
        out.write(("POST " + path + " HTTP/1.1\r\nHost: localhost\r\nContent-Type: " + MediaType.APPLICATION_FORM_URLENCODED
            + "\r\nContent-Length: 1000\r\n\r\nv=a%2Fb&w=").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.flush();
        return socket;
    }

    private static java.util.concurrent.CompletableFuture<?> awaitPending() throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (PENDING.get() == null) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the route did not start the form");
            }
            Thread.sleep(10);
        }
        return PENDING.get();
    }

    private static String readUntil(java.io.InputStream in, String expected) throws java.io.IOException {
        java.io.ByteArrayOutputStream read = new java.io.ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            read.write(b);
            if (read.toString(java.nio.charset.StandardCharsets.US_ASCII).contains(expected)) {
                break;
            }
        }
        return read.toString(java.nio.charset.StandardCharsets.US_ASCII);
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

        @Get(uri = "/decoded-plus/{name:[^/]+}", produces = MediaType.TEXT_PLAIN)
        String decodedPlus(@PathVariable String name, PathVariables pathVariables) {
            assertEquals(name, pathVariables.getString("name"));
            return name;
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

        @Post(uri = "/form-data-arg", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        String formDataArg(io.micronaut.http.form.FormData form) {
            return all(form);
        }

        @Post(uri = "/pending-early", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> pendingEarly(AsyncRequestBody body) {
            // the form is not waited for: the read is cancelled when the handler completed
            PENDING.set(body.form(FormOptions.defaults().withRawValues()).toCompletableFuture());
            return java.util.concurrent.CompletableFuture.completedStage("early");
        }

        @Post(uri = "/pending", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> pending(AsyncRequestBody body) {
            java.util.concurrent.CompletableFuture<io.micronaut.http.form.FormData> form = body.form(FormOptions.defaults().withRawValues()).toCompletableFuture();
            PENDING.set(form);
            return form.thenApply(RawController::all);
        }

        @Post(uri = "/after-form-data", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> afterFormData(AsyncRequestBody body) {
            return rawFailure(body);
        }

        @Post(uri = "/after-text", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> afterText(AsyncRequestBody body) {
            return body.text().thenCompose(text -> rawFailure(body));
        }

        @Post(uri = "/after-replaced", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> afterReplaced(AsyncRequestBody body) {
            return rawFailure(body);
        }

        private static CompletionStage<String> rawFailure(AsyncRequestBody body) {
            try {
                return body.form(FormOptions.defaults().withRawValues())
                    .handle((form, error) -> error == null ? "read " + all(form) : unwrap(error).getClass().getSimpleName());
            } catch (RuntimeException e) {
                return java.util.concurrent.CompletableFuture.completedStage(e.getClass().getSimpleName());
            }
        }

        private static Throwable unwrap(Throwable error) {
            return error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
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
    // the raw form a route started and did not wait for, or waits for
    private static final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<?>> PENDING =
        new java.util.concurrent.atomic.AtomicReference<>();

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

        @RequestFilter("/after-form-data")
        void afterFormData(io.micronaut.http.form.FormData form) {
            // the filter reads the form of the request, which consumes the body
            assertEquals("a/b", form.getString("v"));
        }

        @RequestFilter("/after-replaced")
        HttpRequest<?> afterReplaced(HttpRequest<?> request) {
            return request.mutate().body(Map.of("v", "a/b"));
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
            routes.GET("/raw/fn-decoded/{name:[^/]+}", (request, pathVariables) ->
                HttpResponse.ok(pathVariables.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/raw/fn/{name:[^/]+}", (request, pathVariables) ->
                HttpResponse.ok(pathVariables.rawString("name")).contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.locate("/raw/orgs/{id}", (request, pathVariables) -> pathVariables.rawString("id"), new OrgRoutes());
            routes.locate("/raw/tenants/{id}", (request, pathVariables) -> "tenant " + pathVariables.rawString("id"), new TenantRoutes());
            routes.locate("/raw/shops{/shop:[0-9]+}", (request, pathVariables) -> "located:" + pathVariables.raw().contains("shop"), new ShopRoutes());
            routes.locate("/raw/located/{prefix}", (request, pathVariables) -> pathVariables.raw().getString("prefix"), new ItemRoutes());
        }
    }

    static final class OrgRoutes implements LocatedRoutes<String> {
        @Override
        public Argument<String> targetType() {
            return Argument.STRING;
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<String> located) {
            // the absent id of the nested prefix shadows the id of the outer one
            located.locate("/shops{/id:[0-9]+}", (request, pathVariables, org) -> org, new OrgShopRoutes());
        }
    }

    static final class OrgShopRoutes implements LocatedRoutes<String> {
        @Override
        public Argument<String> targetType() {
            return Argument.STRING;
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<String> located) {
            located.GET("/items", (request, pathVariables) -> HttpResponse.ok(
                    (pathVariables.raw().contains("id") ? pathVariables.rawString("id") : "false") + "|" + pathVariables.find("id", String.class).orElse("null"))
                .contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }

    static final class TenantRoutes implements LocatedRoutes<String> {
        @Override
        public Argument<String> targetType() {
            return Argument.STRING;
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<String> located) {
            // the id of the route shadows the id of the prefix, also when it is absent
            located.GET("/items{/id}")
                .constrain(pathVariables -> !pathVariables.raw().contains("id") || !"abc".equals(pathVariables.rawString("id")))
                .handle((request, pathVariables, tenant) -> HttpResponse.ok(tenant + "|" + (pathVariables.raw().contains("id") ? pathVariables.rawString("id") : "false"))
                    .contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }

    static final class ShopRoutes implements LocatedRoutes<String> {
        @Override
        public Argument<String> targetType() {
            return Argument.STRING;
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<String> located) {
            // the constraint sees the raw variables of the prefix, without the absent one
            located.GET("/items")
                .constrain(pathVariables -> pathVariables.raw().contains("shop") == pathVariables.raw().names().contains("shop"))
                .handle((request, pathVariables, shop) -> HttpResponse.ok(shop + "|" + (pathVariables.raw().contains("shop") ? pathVariables.rawString("shop") : "absent"))
                    .contentType(MediaType.TEXT_PLAIN_TYPE));
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
