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
package io.micronaut.http.server.tck.tests.body;

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
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.json.JsonSyntaxException;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A controller method and a request filter method read the body of the request themselves,
 * once, with an {@link AsyncRequestBody} parameter: decoded like a {@code @Body} argument, with
 * the same responses for failures, or streamed, with the limits of the server for buffered
 * content applying only to what is decoded in memory.
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
    private static final String BASE = "/async-body";

    @Test
    void theBodyDecodesLikeABodyArgument() throws IOException {
        try (ServerUnderTest server = server()) {
            String json = "{\"firstName\":\"Fred\",\"lastName\":\"Flintstone\",\"age\":45}";
            Response ok = sameAsBodyArgument(server, json, MediaType.APPLICATION_JSON_TYPE);
            assertEquals(HttpStatus.CREATED, ok.status());
            assertEquals(json, ok.body());
        }
    }

    @Test
    void aBodyThatDoesNotDecodeReachesTheErrorRoutesLikeABodyArgument() throws IOException {
        try (ServerUnderTest server = server()) {
            Response malformed = sameAsBodyArgument(server, "{\"", MediaType.APPLICATION_JSON_TYPE);
            assertEquals(HttpStatus.BAD_REQUEST, malformed.status());
            assertTrue(malformed.body().startsWith("Invalid JSON: "), malformed.body());
        }
    }

    @Test
    void aBodyTooLargeToDecodeIsAnsweredLikeABodyArgument() throws IOException {
        try (ServerUnderTest server = server()) {
            String json = "{\"firstName\":\"" + "x".repeat(2 * BUFFER_LIMIT) + "\",\"lastName\":\"Flintstone\",\"age\":45}";
            Response argument = call(server, HttpRequest.POST(BASE + "/person-argument", json).contentType(MediaType.APPLICATION_JSON_TYPE));
            Response async = call(server, HttpRequest.POST(BASE + "/person", json).contentType(MediaType.APPLICATION_JSON_TYPE));
            assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, argument.status());
            assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, async.status());
            // the length in the message is what arrived when the limit was exceeded
            String limit = "exceeds the maximum allowed bufferable length [" + BUFFER_LIMIT + "]";
            assertTrue(argument.body().contains(limit), argument.body());
            assertTrue(async.body().contains(limit), async.body());
        }
    }

    @Test
    void theBodyIsReadOnce() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = call(server, text("/one-read", "hello"));
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("hello|The body of the request was already read with text(): it can be read once", response.body());
        }
    }

    @Test
    void getBodyIsEmpty() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = call(server, HttpRequest.POST(BASE + "/get-body", Map.of("name", "apple")));
            assertEquals("true true apple", response.body());
        }
    }

    @Test
    void anUnreadBodyIsDiscarded() throws IOException {
        try (ServerUnderTest server = server()) {
            String large = "x".repeat(4 * BUFFER_LIMIT);
            for (int i = 0; i < 3; i++) {
                assertEquals(HttpStatus.ACCEPTED, call(server, text("/unread", large)).status());
            }
            assertEquals("true OptionalLong[3]", call(server, text("/size", "abc")).body());
        }
    }

    @Test
    void theBufferLimitAppliesOnlyToWhatIsDecodedInMemory() throws IOException {
        try (ServerUnderTest server = server()) {
            String large = "x".repeat(16 * BUFFER_LIMIT);
            assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, call(server, text("/text", large)).status());
            assertEquals("small", call(server, text("/text", "small")).body());
            assertEquals(String.valueOf(large.length()), call(server, text("/file", large)).body());
            assertEquals(String.valueOf(large.length()), call(server, text("/bytes/" + (32 * BUFFER_LIMIT), large)).body());
            assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, call(server, text("/bytes/100", large)).status());
            StringJoiner items = new StringJoiner(",", "[", "]");
            for (int i = 0; i < 2048; i++) {
                items.add("{\"name\":\"item-" + "x".repeat(1000) + i + "\"}");
            }
            assertTrue(items.length() > 4 * BUFFER_LIMIT);
            Response elements = call(server, HttpRequest.POST(BASE + "/items", items.toString()).contentType(MediaType.APPLICATION_JSON_TYPE));
            assertEquals(HttpStatus.OK, elements.status());
            assertTrue(elements.body().startsWith("2048 1 "), elements.body());
        }
    }

    @Test
    void aFilterReadsABodyArgumentNextToTheController() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("shared|shared", call(server, text("/filtered", "shared")).body());
        }
    }

    @Test
    void aFilterReadsTheBodyItself() throws IOException {
        try (ServerUnderTest server = server()) {
            // the filter reads the body with an AsyncRequestBody before the controller runs
            assertEquals("filter:read by filter", call(server, text("/filter-reads", "read by filter")).body());
        }
    }

    @Test
    void aClearedBodyIsNoBody() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("false OptionalLong[0] []", call(server, text("/cleared", "secret")).body());
        }
    }

    @Test
    void aReplacedBodyIsReadDecoded() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("replaced", call(server, text("/replaced", "secret")).body());
        }
    }

    @Test
    void reactiveAndBlockingTypesAreRejected() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = call(server, text("/reactive", "still readable"));
            assertEquals(HttpStatus.OK, response.status());
            String[] refusals = response.body().split("\\|", -1);
            assertEquals(5, refusals.length, response.body());
            assertTrue(refusals[0].contains("elements()"), refusals[0]);
            assertTrue(refusals[1].contains("reactive or asynchronous type"), refusals[1]);
            assertTrue(refusals[2].contains("reactive or asynchronous type"), refusals[2]);
            assertTrue(refusals[3].contains("InputStream"), refusals[3]);
            // a refused type did not claim the body
            assertEquals("still readable", refusals[4]);
        }
    }

    @Test
    void elementsOfAJsonArrayAreReadOneAtATime() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = call(server, HttpRequest.POST(BASE + "/items",
                "[{\"name\":\"a\"},{\"name\":\"b\"},{\"name\":\"c\"}]").contentType(MediaType.APPLICATION_JSON_TYPE));
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("3 1 [a, b, c]", response.body());
        }
    }

    @Test
    void elementsOfAJsonStreamAreReadOneAtATime() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = call(server, HttpRequest.POST(BASE + "/items",
                "{\"name\":\"a\"}\n{\"name\":\"b\"}\n{\"name\":\"c\"}\n").contentType(MediaType.APPLICATION_JSON_STREAM_TYPE));
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("3 1 [a, b, c]", response.body());
        }
    }

    @Test
    void elementsThatAreNotReadAreDiscarded() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = call(server, HttpRequest.POST(BASE + "/first-item",
                "[{\"name\":\"a\"},{\"name\":\"b\"},{\"name\":\"c\"}]").contentType(MediaType.APPLICATION_JSON_TYPE));
            assertEquals("a", response.body());
        }
    }

    @Test
    void elementsAndFormsOfAnotherMediaTypeAreRefusedWith415() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, call(server, text("/any-items", "a,b")).status());
            assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, call(server, text("/any-form", "a,b")).status());
        }
    }

    @Test
    void theBodyIsTakenOrDiscarded() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("5", call(server, text("/take", "taken")).body());
            assertEquals("discarded", call(server, text("/discard", "gone")).body());
            assertEquals("false OptionalLong[0]", call(server, text("/size", "")).body());
        }
    }

    /**
     * Sends the request to a method with a {@code @Body} argument and to one that reads the body
     * with an {@link AsyncRequestBody}, and checks that they are answered the same.
     */
    private static Response sameAsBodyArgument(ServerUnderTest server, String body, MediaType contentType) {
        Response argument = call(server, HttpRequest.POST(BASE + "/person-argument", body).contentType(contentType));
        Response async = call(server, HttpRequest.POST(BASE + "/person", body).contentType(contentType));
        assertEquals(argument.status(), async.status(), "status");
        assertEquals(argument.body(), async.body(), "body");
        return async;
    }

    private static HttpRequest<?> text(String path, String body) {
        return HttpRequest.POST(BASE + path, body).contentType(MediaType.TEXT_PLAIN_TYPE);
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

    private static long fileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static HttpResponse<String> ok(String body) {
        return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
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

    @ServerFilter(BASE + "/filtered")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BodyFilter {
        @RequestFilter
        void filter(HttpRequest<?> request, @Body String body) {
            request.setAttribute(FILTER_BODY, body);
        }
    }

    @ServerFilter(BASE + "/filter-reads")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AsyncBodyFilter {
        @RequestFilter
        CompletionStage<@Nullable HttpResponse<?>> filter(HttpRequest<?> request, AsyncRequestBody body) {
            return body.text().thenApply(text -> {
                request.setAttribute(FILTER_BODY, "filter:" + text);
                return null;
            });
        }
    }

    @ServerFilter(BASE + "/cleared")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ClearingFilter {
        @RequestFilter
        MutableHttpRequest<?> filter(MutableHttpRequest<?> request) {
            return request.body(null);
        }
    }

    @ServerFilter(BASE + "/replaced")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ReplacingFilter {
        @RequestFilter
        MutableHttpRequest<?> filter(MutableHttpRequest<?> request) {
            return request.body("replaced");
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Executors0 {
        @Singleton
        @Named("async-body-consumer")
        @Bean(preDestroy = "shutdown")
        ExecutorService consumerExecutor() {
            return Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "async-body-consumer"));
        }
    }

    @Controller(BASE)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AsyncBodyController {
        private final ExecutorService executor;

        AsyncBodyController(@Named("async-body-consumer") ExecutorService executor) {
            this.executor = executor;
        }

        @Post("/person-argument")
        HttpResponse<Person> saveArgument(@Body Person body) {
            return HttpResponse.created(body);
        }

        @Post("/person")
        CompletionStage<HttpResponse<Person>> save(AsyncRequestBody body) {
            return body.body(Person.class).thenApply(HttpResponse::created);
        }

        @Error(exception = JsonSyntaxException.class)
        HttpResponse<String> invalidJson(JsonSyntaxException error) {
            return HttpResponse.<String>badRequest("Invalid JSON: " + error.getMessage()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }

        @Post(uri = "/one-read", consumes = MediaType.ALL)
        CompletionStage<HttpResponse<String>> oneRead(AsyncRequestBody body) {
            var text = body.text();
            String second;
            try {
                body.bytes(10);
                second = "read twice";
            } catch (IllegalStateException e) {
                second = e.getMessage();
            }
            String message = second;
            return text.thenApply(value -> ok(value + "|" + message));
        }

        @Post(uri = "/get-body", consumes = MediaType.APPLICATION_JSON)
        CompletionStage<HttpResponse<String>> getBody(HttpRequest<?> request, AsyncRequestBody body) {
            boolean before = request.getBody().isEmpty();
            return body.body(Argument.mapOf(String.class, String.class)).thenApply(value ->
                ok(before + " " + request.getBody().isEmpty() + " " + value.get("name")));
        }

        @Post(uri = "/unread", consumes = MediaType.ALL)
        HttpResponse<?> unread(AsyncRequestBody body) {
            return HttpResponse.accepted();
        }

        @Post(uri = "/size", consumes = MediaType.ALL)
        HttpResponse<String> bodySize(AsyncRequestBody body) {
            return ok(body.hasBody() + " " + body.expectedBodySize());
        }

        @Post(uri = "/text", consumes = MediaType.ALL)
        CompletionStage<HttpResponse<String>> readText(AsyncRequestBody body) {
            return body.text().thenApply(AsyncRequestBodyTest::ok);
        }

        @Post(uri = "/file", consumes = MediaType.ALL)
        CompletionStage<HttpResponse<String>> file(AsyncRequestBody body) {
            Path file = temporaryFile();
            return body.transferTo(file).thenApply(done -> ok(String.valueOf(fileSize(file))));
        }

        @Post(uri = "/bytes/{max}", consumes = MediaType.ALL)
        CompletionStage<HttpResponse<String>> bytes(@PathVariable int max, AsyncRequestBody body) {
            return body.bytes(max).thenApply(bytes -> ok(String.valueOf(bytes.length)));
        }

        @Post(uri = "/filtered", consumes = MediaType.ALL)
        CompletionStage<HttpResponse<String>> filtered(HttpRequest<?> request, AsyncRequestBody body) {
            return body.text().thenApply(text -> ok(request.getAttribute(FILTER_BODY, String.class).orElse("none") + "|" + text));
        }

        @Post(uri = "/filter-reads", consumes = MediaType.ALL)
        HttpResponse<String> filterReads(HttpRequest<?> request) {
            return ok(request.getAttribute(FILTER_BODY, String.class).orElse("none"));
        }

        @Post(uri = "/cleared", consumes = MediaType.ALL)
        CompletionStage<HttpResponse<String>> cleared(AsyncRequestBody body) {
            String state = body.hasBody() + " " + body.expectedBodySize();
            return body.text().thenApply(text -> ok(state + " [" + text + "]"));
        }

        @Post(uri = "/replaced", consumes = MediaType.ALL)
        CompletionStage<HttpResponse<String>> replaced(AsyncRequestBody body) {
            return body.body(String.class).thenApply(AsyncRequestBodyTest::ok);
        }

        @Post(uri = "/reactive", consumes = MediaType.ALL)
        CompletionStage<HttpResponse<String>> reactive(AsyncRequestBody body) {
            List<String> refused = new ArrayList<>();
            try {
                body.body(Argument.of(Publisher.class, String.class));
                refused.add("accepted");
            } catch (IllegalArgumentException e) {
                refused.add(e.getMessage());
            }
            for (Argument<?> type : List.of(Argument.of(Publisher.class, String.class), Argument.of(CompletableFuture.class, String.class), Argument.of(InputStream.class))) {
                try {
                    body.elements(type);
                    refused.add("accepted");
                } catch (IllegalArgumentException e) {
                    refused.add(e.getMessage());
                }
            }
            return body.text().thenApply(text -> ok(String.join("|", refused) + "|" + text));
        }

        @Post(uri = "/items", consumes = {MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON_STREAM})
        CompletionStage<HttpResponse<String>> items(AsyncRequestBody body) {
            List<String> names = new CopyOnWriteArrayList<>();
            AtomicInteger inFlight = new AtomicInteger();
            AtomicInteger maxInFlight = new AtomicInteger();
            return body.elements(Item.class).forEach(item -> {
                maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                // the consumer completes later: the next element waits for it
                return CompletableFuture.runAsync(() -> {
                    names.add(item.name());
                    inFlight.decrementAndGet();
                }, executor);
            }).thenApply(done -> ok(names.size() + " " + maxInFlight.get() + " " + (names.size() > 3 ? "" : names)));
        }

        @Post(uri = "/first-item", consumes = MediaType.APPLICATION_JSON)
        CompletionStage<HttpResponse<String>> firstItem(AsyncRequestBody body) {
            BodyElements<Item> elements = body.elements(Item.class);
            return elements.next().thenApply(first -> ok(first.map(Item::name).orElse("none")));
        }

        @Post(uri = "/any-items", consumes = MediaType.ALL)
        CompletionStage<HttpResponse<String>> anyItems(AsyncRequestBody body) {
            return body.elements(Item.class).next().thenApply(first -> ok("read"));
        }

        @Post(uri = "/any-form", consumes = MediaType.ALL)
        CompletionStage<HttpResponse<String>> anyForm(AsyncRequestBody body) {
            return body.form().thenApply(form -> ok("read"));
        }

        @Post(uri = "/take", consumes = MediaType.ALL)
        CompletionStage<HttpResponse<String>> take(AsyncRequestBody body) {
            CloseableByteBody taken = body.takeBody();
            return taken.buffer().thenApply(available -> {
                try (available) {
                    return ok(String.valueOf(available.length()));
                }
            });
        }

        @Post(uri = "/discard", consumes = MediaType.ALL)
        CompletionStage<HttpResponse<String>> discard(AsyncRequestBody body) {
            return body.discardBody().thenApply(done -> ok("discarded"));
        }
    }
}
