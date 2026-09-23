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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.json.JsonSyntaxException;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asynchronous handlers read the body of the request themselves, once, with
 * {@link io.micronaut.http.AsyncServerHttpRequest}: decoded like a controller's {@code @Body}
 * argument, with the same responses for failures, or streamed, with the limits of the server for
 * buffered content applying only to what is decoded in memory.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class AsyncRequestBodyTest {
    public static final String SPEC_NAME = "AsyncRequestBodyTest";
    private static final int BUFFER_LIMIT = 256 * 1024;
    private static final String FILTER_BODY = "async-request-filter-body";

    @Test
    void theBodyDecodesLikeTheBodyOfAController() throws IOException {
        try (ServerUnderTest server = server()) {
            String json = "{\"firstName\":\"Fred\",\"lastName\":\"Flintstone\",\"age\":45}";
            Response ok = sameAsController(server, "/person", json, MediaType.APPLICATION_JSON_TYPE);
            assertEquals(HttpStatus.CREATED, ok.status());
            assertEquals(json, ok.body());
        }
    }

    @Test
    void aBodyThatDoesNotDecodeReachesTheErrorRoutesLikeForAController() throws IOException {
        try (ServerUnderTest server = server()) {
            Response malformed = sameAsController(server, "/person", "{\"", MediaType.APPLICATION_JSON_TYPE);
            assertEquals(HttpStatus.BAD_REQUEST, malformed.status());
            assertTrue(malformed.body().startsWith("Invalid JSON: "), malformed.body());
        }
    }

    @Test
    void aMissingBodyIsAnsweredLikeForAController() throws IOException {
        try (ServerUnderTest server = server()) {
            Response missing = sameAsController(server, "/person", null, MediaType.APPLICATION_JSON_TYPE);
            assertEquals(HttpStatus.BAD_REQUEST, missing.status());
            assertTrue(missing.body().contains("Required Body [body] not specified"), missing.body());
        }
    }

    @Test
    void aBodyTooLargeToDecodeIsAnsweredLikeForAController() throws IOException {
        try (ServerUnderTest server = server()) {
            String json = "{\"firstName\":\"" + "x".repeat(2 * BUFFER_LIMIT) + "\",\"lastName\":\"Flintstone\",\"age\":45}";
            Response controller = call(server, HttpRequest.POST("/ctl/async-body/person", json).contentType(MediaType.APPLICATION_JSON_TYPE));
            Response handler = call(server, HttpRequest.POST("/fn/async-body/person", json).contentType(MediaType.APPLICATION_JSON_TYPE));
            assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, controller.status());
            assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, handler.status());
            // the length in the message is what arrived when the limit was exceeded
            String limit = "exceeds the maximum allowed bufferable length [" + BUFFER_LIMIT + "]";
            assertTrue(controller.body().contains(limit), controller.body());
            assertTrue(handler.body().contains(limit), handler.body());
        }
    }

    @Test
    void anUnsupportedMediaTypeIsAnsweredLikeForAController() throws IOException {
        try (ServerUnderTest server = server()) {
            Response unsupported = sameAsController(server, "/person", "Fred", MediaType.TEXT_PLAIN_TYPE);
            assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, unsupported.status());
        }
    }

    @Test
    void theBodyIsReadOnce() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = call(server, HttpRequest.POST("/fn/async-body/one-read", "hello").contentType(MediaType.TEXT_PLAIN_TYPE));
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("hello|The body of the request was already read with text(): it can be read once", response.body());
        }
    }

    @Test
    void getBodyIsEmpty() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = call(server, HttpRequest.POST("/fn/async-body/get-body", Map.of("name", "apple")));
            assertEquals("true true apple", response.body());
        }
    }

    @Test
    void anUnreadBodyIsDiscarded() throws IOException {
        try (ServerUnderTest server = server()) {
            String large = "x".repeat(4 * BUFFER_LIMIT);
            for (int i = 0; i < 3; i++) {
                Response response = call(server, HttpRequest.POST("/fn/async-body/unread", large).contentType(MediaType.TEXT_PLAIN_TYPE));
                assertEquals(HttpStatus.ACCEPTED, response.status());
            }
            assertEquals("true OptionalLong[3]", call(server, HttpRequest.POST("/fn/async-body/size", "abc").contentType(MediaType.TEXT_PLAIN_TYPE)).body());
        }
    }

    @Test
    void theBufferLimitAppliesOnlyToWhatIsDecodedInMemory() throws IOException {
        try (ServerUnderTest server = server()) {
            String large = "x".repeat(16 * BUFFER_LIMIT);
            assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE,
                call(server, HttpRequest.POST("/fn/async-body/text", large).contentType(MediaType.TEXT_PLAIN_TYPE)).status());
            assertEquals("small", call(server, HttpRequest.POST("/fn/async-body/text", "small").contentType(MediaType.TEXT_PLAIN_TYPE)).body());
            // a file, the bytes with a larger limit of the handler, and elements are not limited by the buffer
            assertEquals(String.valueOf(large.length()),
                call(server, HttpRequest.POST("/fn/async-body/file", large).contentType(MediaType.TEXT_PLAIN_TYPE)).body());
            assertEquals(String.valueOf(large.length()),
                call(server, HttpRequest.POST("/fn/async-body/bytes/" + (32 * BUFFER_LIMIT), large).contentType(MediaType.TEXT_PLAIN_TYPE)).body());
            assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE,
                call(server, HttpRequest.POST("/fn/async-body/bytes/100", large).contentType(MediaType.TEXT_PLAIN_TYPE)).status());
            StringJoiner items = new StringJoiner(",", "[", "]");
            for (int i = 0; i < 2048; i++) {
                items.add("{\"name\":\"item-" + "x".repeat(1000) + i + "\"}");
            }
            assertTrue(items.length() > 4 * BUFFER_LIMIT);
            Response elements = call(server, HttpRequest.POST("/fn/async-body/items", items.toString()).contentType(MediaType.APPLICATION_JSON_TYPE));
            assertEquals(HttpStatus.OK, elements.status());
            assertTrue(elements.body().startsWith("2048 1 "), elements.body());
        }
    }

    @Test
    void aFilterReadsTheBodyNextToTheHandler() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = call(server, HttpRequest.POST("/fn/async-body/filtered", "shared").contentType(MediaType.TEXT_PLAIN_TYPE));
            assertEquals("shared|shared", response.body());
        }
    }

    @Test
    void aReactiveBodyTypeIsRejected() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = call(server, HttpRequest.POST("/fn/async-body/reactive", "still readable").contentType(MediaType.TEXT_PLAIN_TYPE));
            assertEquals(HttpStatus.OK, response.status());
            assertTrue(response.body().contains("elements()"), response.body());
            assertTrue(response.body().endsWith("|still readable"), response.body());
        }
    }

    @Test
    void reactiveAndBlockingElementTypesAreRejected() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = call(server, HttpRequest.POST("/fn/async-body/reactive-elements", "still readable").contentType(MediaType.TEXT_PLAIN_TYPE));
            assertEquals(HttpStatus.OK, response.status());
            String[] refusals = response.body().split("\\|", -1);
            assertEquals(4, refusals.length, response.body());
            assertTrue(refusals[0].contains("reactive or asynchronous type"), refusals[0]);
            assertTrue(refusals[1].contains("reactive or asynchronous type"), refusals[1]);
            assertTrue(refusals[2].contains("InputStream"), refusals[2]);
            // a refused type did not claim the body
            assertEquals("still readable", refusals[3]);
        }
    }

    @Test
    void elementsOfAJsonArrayAreReadOneAtATime() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = call(server, HttpRequest.POST("/fn/async-body/items",
                "[{\"name\":\"a\"},{\"name\":\"b\"},{\"name\":\"c\"}]").contentType(MediaType.APPLICATION_JSON_TYPE));
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("3 1 [a, b, c]", response.body());
        }
    }

    @Test
    void elementsOfAJsonStreamAreReadOneAtATime() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = call(server, HttpRequest.POST("/fn/async-body/items",
                "{\"name\":\"a\"}\n{\"name\":\"b\"}\n{\"name\":\"c\"}\n").contentType(MediaType.APPLICATION_JSON_STREAM_TYPE));
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("3 1 [a, b, c]", response.body());
        }
    }

    @Test
    void elementsThatAreNotReadAreDiscarded() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = call(server, HttpRequest.POST("/fn/async-body/first-item",
                "[{\"name\":\"a\"},{\"name\":\"b\"},{\"name\":\"c\"}]").contentType(MediaType.APPLICATION_JSON_TYPE));
            assertEquals("a", response.body());
        }
    }

    @Test
    void elementsAndFormsOfAnotherMediaTypeAreRefusedWith415() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                call(server, HttpRequest.POST("/fn/async-body/any-items", "a,b").contentType(MediaType.TEXT_PLAIN_TYPE)).status());
            assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                call(server, HttpRequest.POST("/fn/async-body/any-form", "a,b").contentType(MediaType.TEXT_PLAIN_TYPE)).status());
        }
    }

    @Test
    void theBodyIsTakenOrDiscarded() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("5", call(server, HttpRequest.POST("/fn/async-body/take", "taken").contentType(MediaType.TEXT_PLAIN_TYPE)).body());
            assertEquals("discarded", call(server, HttpRequest.POST("/fn/async-body/discard", "gone").contentType(MediaType.TEXT_PLAIN_TYPE)).body());
            assertEquals("false OptionalLong[0]", call(server, HttpRequest.POST("/fn/async-body/size", "").contentType(MediaType.TEXT_PLAIN_TYPE)).body());
        }
    }

    /**
     * Sends the request to the controller and to the handler route under the same path, and
     * checks that they are answered the same.
     */
    private static Response sameAsController(ServerUnderTest server, String path, String body, MediaType contentType) {
        Response controller = call(server, HttpRequest.POST("/ctl/async-body" + path, body).contentType(contentType));
        Response handler = call(server, HttpRequest.POST("/fn/async-body" + path, body).contentType(contentType));
        assertEquals(controller.status(), handler.status(), "status");
        assertEquals(controller.body().replace("/ctl/", "/fn/"), handler.body(), "body");
        return handler;
    }

    private static Response call(ServerUnderTest server, HttpRequest<?> request) {
        try {
            HttpResponse<String> response = server.exchange(request, String.class);
            return new Response(response.getStatus(), response.getBody().orElse(""));
        } catch (HttpClientResponseException e) {
            return new Response(e.getStatus(), e.getResponse().getBody(String.class).orElse(""));
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Map.of("micronaut.server.max-request-buffer-size", BUFFER_LIMIT));
    }

    private static Path temporaryFile() {
        try {
            return Files.createTempDirectory("async-body").resolve("body");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    record Response(HttpStatus status, String body) {
    }

    @Introspected
    @ReflectiveAccess
    record Person(String firstName, String lastName, int age) {
    }

    @Introspected
    @ReflectiveAccess
    record Item(String name) {
    }

    @Controller("/ctl/async-body")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PersonController {
        @Post("/person")
        HttpResponse<Person> save(@Body Person body) {
            return HttpResponse.created(body);
        }
    }

    @ServerFilter("/fn/async-body/filtered")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BodyFilter {
        @RequestFilter
        void filter(HttpRequest<?> request, @Body String body) {
            request.setAttribute(FILTER_BODY, body);
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {

        @Singleton
        @Named("async-body-consumer")
        @Bean(preDestroy = "shutdown")
        ExecutorService consumerExecutor() {
            return Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "async-body-consumer"));
        }

        @Singleton
        @Named("async-body")
        HttpRoutes asyncBodyRoutes(@Named("async-body-consumer") ExecutorService executor) {
            return routes -> {
                routes.asyncPOST("/fn/async-body/person", (request, pathVariables) -> request.body(Person.class).thenApply(HttpResponse::created));
                routes.error(JsonSyntaxException.class, (request, error) ->
                    HttpResponse.badRequest("Invalid JSON: " + error.getMessage()).contentType(MediaType.TEXT_PLAIN_TYPE));
                routes.asyncPOST("/fn/async-body/one-read", (request, pathVariables) -> {
                    var text = request.text();
                    String second;
                    try {
                        request.bytes(10);
                        second = "read twice";
                    } catch (IllegalStateException e) {
                        second = e.getMessage();
                    }
                    String message = second;
                    return text.thenApply(value -> HttpResponse.ok(value + "|" + message).contentType(MediaType.TEXT_PLAIN_TYPE));
                }).consumesAll();
                routes.asyncPOST("/fn/async-body/get-body", (request, pathVariables) -> {
                    boolean before = request.getBody().isEmpty();
                    return request.body(Argument.mapOf(String.class, String.class)).thenApply(body ->
                        HttpResponse.ok(before + " " + request.getBody().isEmpty() + " " + body.get("name")).contentType(MediaType.TEXT_PLAIN_TYPE));
                });
                routes.asyncPOST("/fn/async-body/unread", (request, pathVariables) ->
                    CompletableFuture.completedFuture(HttpResponse.accepted())).consumesAll();
                routes.asyncPOST("/fn/async-body/size", (request, pathVariables) ->
                    CompletableFuture.completedFuture(HttpResponse.ok(request.hasBody() + " " + request.expectedBodySize()).contentType(MediaType.TEXT_PLAIN_TYPE)))
                    .consumesAll();
                routes.asyncPOST("/fn/async-body/text", (request, pathVariables) ->
                    request.text().thenApply(text -> HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE))).consumesAll();
                routes.asyncPOST("/fn/async-body/file", (request, pathVariables) -> {
                    Path file = temporaryFile();
                    return request.transferTo(file).thenApply(done -> HttpResponse.ok(String.valueOf(size(file))).contentType(MediaType.TEXT_PLAIN_TYPE));
                }).consumesAll();
                routes.asyncPOST("/fn/async-body/bytes/{max}", (request, pathVariables) ->
                    request.bytes(pathVariables.getInt("max")).thenApply(bytes -> HttpResponse.ok(String.valueOf(bytes.length)).contentType(MediaType.TEXT_PLAIN_TYPE)))
                    .consumesAll();
                routes.asyncPOST("/fn/async-body/filtered", (request, pathVariables) ->
                    request.text().thenApply(text -> HttpResponse.ok(request.getAttribute(FILTER_BODY, String.class).orElse("none") + "|" + text)
                        .contentType(MediaType.TEXT_PLAIN_TYPE))).consumesAll();
                routes.asyncPOST("/fn/async-body/reactive", (request, pathVariables) -> {
                    String refused;
                    try {
                        request.body(Argument.of(Publisher.class, String.class));
                        refused = "accepted";
                    } catch (IllegalArgumentException e) {
                        refused = e.getMessage();
                    }
                    String message = refused;
                    return request.text().thenApply(text -> HttpResponse.ok(message + "|" + text).contentType(MediaType.TEXT_PLAIN_TYPE));
                }).consumesAll();
                routes.asyncPOST("/fn/async-body/reactive-elements", (request, pathVariables) -> {
                    List<String> refused = new ArrayList<>();
                    for (Argument<?> type : List.of(Argument.of(Publisher.class, String.class), Argument.of(CompletableFuture.class, String.class), Argument.of(InputStream.class))) {
                        try {
                            request.elements(type);
                            refused.add("accepted");
                        } catch (IllegalArgumentException e) {
                            refused.add(e.getMessage());
                        }
                    }
                    return request.text().thenApply(text -> HttpResponse.ok(String.join("|", refused) + "|" + text).contentType(MediaType.TEXT_PLAIN_TYPE));
                }).consumesAll();
                routes.asyncPOST("/fn/async-body/items", (request, pathVariables) -> {
                    List<String> names = new CopyOnWriteArrayList<>();
                    AtomicInteger inFlight = new AtomicInteger();
                    AtomicInteger maxInFlight = new AtomicInteger();
                    return request.elements(Item.class).forEach(item -> {
                        maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                        // the consumer completes later: the next element waits for it
                        return CompletableFuture.runAsync(() -> {
                            names.add(item.name());
                            inFlight.decrementAndGet();
                        }, executor);
                    }).thenApply(done -> HttpResponse.ok(names.size() + " " + maxInFlight.get() + " " + (names.size() > 3 ? "" : names))
                        .contentType(MediaType.TEXT_PLAIN_TYPE));
                }).consumes(MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_JSON_STREAM_TYPE);
                routes.asyncPOST("/fn/async-body/first-item", (request, pathVariables) -> {
                    BodyElements<Item> elements = request.elements(Item.class);
                    return elements.next().thenApply(first -> HttpResponse.ok(first.map(Item::name).orElse("none")).contentType(MediaType.TEXT_PLAIN_TYPE));
                });
                routes.asyncPOST("/fn/async-body/any-items", (request, pathVariables) ->
                    request.elements(Item.class).next().thenApply(first -> HttpResponse.ok("read"))).consumesAll();
                routes.asyncPOST("/fn/async-body/any-form", (request, pathVariables) ->
                    request.form().thenApply(form -> HttpResponse.ok("read"))).consumesAll();
                routes.asyncPOST("/fn/async-body/take", (request, pathVariables) -> {
                    CloseableByteBody body = request.takeBody();
                    return body.buffer().thenApply(available -> {
                        try (available) {
                            return HttpResponse.ok(String.valueOf(available.length())).contentType(MediaType.TEXT_PLAIN_TYPE);
                        }
                    });
                }).consumesAll();
                routes.asyncPOST("/fn/async-body/discard", (request, pathVariables) ->
                    request.discardBody().thenApply(done -> HttpResponse.ok("discarded").contentType(MediaType.TEXT_PLAIN_TYPE))).consumesAll();
            };
        }
    }
}
