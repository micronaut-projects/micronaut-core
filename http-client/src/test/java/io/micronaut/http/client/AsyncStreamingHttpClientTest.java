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
package io.micronaut.http.client;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AsyncStreamingHttpClient}: the Netty implementation without Reactor, and the adapter of
 * the reactive {@link StreamingHttpClient}.
 */
class AsyncStreamingHttpClientTest {
    private static final String SPEC = "AsyncStreamingHttpClientTest";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static EmbeddedServer server;
    private static HttpClient httpClient;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC));
        httpClient = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
    }

    @AfterAll
    static void stop() {
        httpClient.close();
        server.close();
    }

    static Stream<Named<AsyncStreamingHttpClient>> clients() {
        StreamingHttpClient streamingHttpClient = (StreamingHttpClient) httpClient;
        return Stream.of(
            Named.of("netty", streamingHttpClient.toAsync()),
            Named.of("reactive adapter", new DefaultAsyncOverReactiveStreamingHttpClient(streamingHttpClient))
        );
    }

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        try {
            return stage.toCompletableFuture().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static String read(BodyElements<ByteBuffer<?>> pieces) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        await(pieces.forEach(piece -> {
            assertFalse(piece instanceof ReferenceCounted, "The buffers are not reference counted");
            bytes.writeBytes(piece.toByteArray());
            return CompletableFuture.completedStage(null);
        }));
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void nettyClientIsNotTheAdapter() {
        assertFalse(((StreamingHttpClient) httpClient).toAsync() instanceof DefaultAsyncOverReactiveStreamingHttpClient);
        assertInstanceOf(AsyncStreamingHttpClient.class, httpClient.toAsync());
    }

    @ParameterizedTest(autoCloseArguments = false) // closing the view closes the shared client
    @MethodSource("clients")
    void dataStreamReadsTheBody(AsyncStreamingHttpClient client) throws Exception {
        BodyElements<ByteBuffer<?>> pieces = await(client.dataStream(HttpRequest.GET("/async-stream/bytes")));

        assertEquals("abc", read(pieces));
    }

    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("clients")
    void piecesArriveAsTheyAreSent(AsyncStreamingHttpClient client) throws Exception {
        CompletionStage<BodyElements<ByteBuffer<?>>> stage = client.dataStream(HttpRequest.GET("/async-stream/sink"));
        Sinks.Many<byte[]> sink = StreamController.awaitSink();
        sink.tryEmitNext("first".getBytes(StandardCharsets.UTF_8));
        BodyElements<ByteBuffer<?>> pieces = await(stage);

        assertEquals("first", await(pieces.next()).orElseThrow().toString(StandardCharsets.UTF_8));
        CompletableFuture<Optional<ByteBuffer<?>>> second = pieces.next().toCompletableFuture();
        assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
        sink.tryEmitNext("second".getBytes(StandardCharsets.UTF_8));
        assertEquals("second", second.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).orElseThrow().toString(StandardCharsets.UTF_8));

        CompletableFuture<Optional<ByteBuffer<?>>> end = pieces.next().toCompletableFuture();
        sink.tryEmitComplete();
        assertTrue(end.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).isEmpty());
    }

    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("clients")
    void exchangeStreamHasTheResponse(AsyncStreamingHttpClient client) throws Exception {
        HttpResponse<BodyElements<ByteBuffer<?>>> response = await(client.exchangeStream(HttpRequest.GET("/async-stream/headers")));

        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals("yes", response.getHeaders().get("X-Stream"));
        assertEquals("abc", read(response.body()));
    }

    @Test
    void exchangeStreamOfAnEmptyBody() throws Exception {
        // the reactive exchangeStream emits no response for an empty body, the Netty client has it
        AsyncStreamingHttpClient client = ((StreamingHttpClient) httpClient).toAsync();
        HttpResponse<BodyElements<ByteBuffer<?>>> response = await(client.exchangeStream(HttpRequest.GET("/async-stream/empty")));

        assertEquals("empty", response.getHeaders().get("X-Stream"));
        assertTrue(await(response.body().next()).isEmpty());
    }

    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("clients")
    void dataStreamOfAnEmptyBody(AsyncStreamingHttpClient client) throws Exception {
        assertEquals("", read(await(client.dataStream(HttpRequest.GET("/async-stream/empty")))));
    }

    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("clients")
    void jsonStreamReadsEachElement(AsyncStreamingHttpClient client) throws Exception {
        BodyElements<Book> books = await(client.jsonStream(HttpRequest.GET("/async-stream/books"), Book.class));

        assertEquals(new Book("The Stand", 1153), await(books.next()).orElseThrow());
        assertEquals(new Book("It", 1138), await(books.next()).orElseThrow());
        assertTrue(await(books.next()).isEmpty());
    }

    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("clients")
    void jsonStreamReadsTheElementsOfAnArray(AsyncStreamingHttpClient client) throws Exception {
        BodyElements<Book> books = await(client.jsonStream(HttpRequest.GET("/async-stream/books-array"), Book.class));
        List<Book> received = new ArrayList<>();
        await(books.forEach(book -> {
            received.add(book);
            return CompletableFuture.completedStage(null);
        }));

        assertEquals(List.of(new Book("The Stand", 1153), new Book("It", 1138)), received);
    }

    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("clients")
    void jsonStreamAsMaps(AsyncStreamingHttpClient client) throws Exception {
        BodyElements<Map<String, Object>> books = await(client.jsonStream(HttpRequest.GET("/async-stream/books")));

        assertEquals("The Stand", await(books.next()).orElseThrow().get("title"));
        books.close();
    }

    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("clients")
    void forEachReadsManyElements(AsyncStreamingHttpClient client) throws Exception {
        BodyElements<Book> books = await(client.jsonStream(HttpRequest.GET("/async-stream/many"), Book.class));
        List<Integer> received = new ArrayList<>();
        await(books.forEach(book -> {
            received.add(book.pages());
            return CompletableFuture.completedStage(null);
        }));

        assertEquals(IntStream.range(0, StreamController.MANY).boxed().toList(), received);
    }

    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("clients")
    void errorStatusCarriesTheBody(AsyncStreamingHttpClient client) {
        CompletionStage<BodyElements<Book>> books = client.jsonStream(HttpRequest.GET("/async-stream/error"), Argument.of(Book.class), Argument.STRING);
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> await(books));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals("{\"message\":\"Invalid request\"}", e.getResponse().getBody(String.class).orElseThrow());
    }

    @Test
    void errorStatusCarriesTheBodyWithTheDefaultErrorType() {
        // the reactive streaming methods discard the error body with the default error type
        CompletionStage<BodyElements<ByteBuffer<?>>> pieces = ((StreamingHttpClient) httpClient).toAsync().dataStream(HttpRequest.GET("/async-stream/error"));
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> await(pieces));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertTrue(e.getResponse().getBody(String.class).orElseThrow().contains("Invalid request"));
    }

    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("clients")
    void closingDiscardsTheRest(AsyncStreamingHttpClient client) throws Exception {
        CompletionStage<BodyElements<ByteBuffer<?>>> stage = client.dataStream(HttpRequest.GET("/async-stream/sink"));
        Sinks.Many<byte[]> sink = StreamController.awaitSink();
        sink.tryEmitNext("first".getBytes(StandardCharsets.UTF_8));
        BodyElements<ByteBuffer<?>> pieces = await(stage);
        await(pieces.next());

        pieces.close();
        assertThrows(IllegalStateException.class, pieces::next);
        sink.tryEmitNext("second".getBytes(StandardCharsets.UTF_8));
        sink.tryEmitComplete();

        // the client still works
        assertEquals("abc", read(await(client.dataStream(HttpRequest.GET("/async-stream/bytes")))));
    }

    record Book(String title, int pages) {
    }

    @Requires(property = "spec.name", value = SPEC)
    @Controller("/async-stream")
    static class StreamController {
        static final int MANY = 10_000;

        private static final AtomicReference<CompletableFuture<Sinks.Many<byte[]>>> SINK =
            new AtomicReference<>(new CompletableFuture<>());

        static Sinks.Many<byte[]> awaitSink() {
            try {
                return SINK.get().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new AssertionError(e);
            } finally {
                SINK.set(new CompletableFuture<>());
            }
        }

        @Get(value = "/bytes", produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<byte[]> bytes() {
            return Flux.just("a", "b", "c").map(s -> s.getBytes(StandardCharsets.UTF_8));
        }

        @Get(value = "/sink", produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<byte[]> sink() {
            Sinks.Many<byte[]> sink = Sinks.many().unicast().onBackpressureBuffer();
            SINK.get().complete(sink);
            return sink.asFlux();
        }

        @Get(value = "/headers", produces = MediaType.APPLICATION_OCTET_STREAM)
        HttpResponse<Publisher<byte[]>> headers() {
            return HttpResponse.<Publisher<byte[]>>ok(bytes()).header("X-Stream", "yes");
        }

        @Get(value = "/empty", produces = MediaType.APPLICATION_OCTET_STREAM)
        HttpResponse<?> empty() {
            return HttpResponse.ok().header("X-Stream", "empty");
        }

        @Get(value = "/books", produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Book> books() {
            return Flux.just(new Book("The Stand", 1153), new Book("It", 1138));
        }

        @Get(value = "/books-array", produces = MediaType.APPLICATION_JSON)
        Publisher<Book> booksArray() {
            return books();
        }

        @Get(value = "/many", produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Book> many() {
            return Flux.range(0, MANY).map(i -> new Book("Book " + i, i));
        }

        @Get(value = "/error", produces = MediaType.APPLICATION_JSON)
        HttpResponse<String> error() {
            return HttpResponse.<String>badRequest()
                .body("{\"message\":\"Invalid request\"}")
                .contentType(MediaType.APPLICATION_JSON_TYPE);
        }
    }
}
