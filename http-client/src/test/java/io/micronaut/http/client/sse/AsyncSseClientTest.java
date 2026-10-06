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
package io.micronaut.http.client.sse;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.sse.Event;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AsyncSseClient}: the Netty implementation without Reactor, and the adapter of the
 * reactive {@link SseClient#exchangeEventStream}, against a server that answers like the MCP
 * Streamable HTTP transport.
 */
class AsyncSseClientTest {
    private static final String SPEC = "AsyncSseClientTest";
    private static final String ACCEPT = MediaType.APPLICATION_JSON + ", " + MediaType.TEXT_EVENT_STREAM;
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

    static Stream<Named<AsyncSseClient>> clients() {
        SseClient sseClient = (SseClient) httpClient;
        return Stream.of(
            Named.of("netty", sseClient.toAsyncSse()),
            Named.of("reactive adapter", new DefaultAsyncOverReactiveSseClient(sseClient))
        );
    }

    private static MutableHttpRequest<String> post(String uri) {
        return HttpRequest.POST(uri, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}")
            .contentType(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.ACCEPT, ACCEPT);
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

    private static <B> Event<B> nextEvent(BodyElements<Event<B>> events) throws Exception {
        Optional<Event<B>> event = await(events.next());
        assertTrue(event.isPresent(), "No event");
        return event.get();
    }

    @Test
    void nettyClientIsNotTheAdapter() {
        assertInstanceOf(io.micronaut.http.client.netty.DefaultHttpClient.class, httpClient);
        assertFalse(((SseClient) httpClient).toAsyncSse() instanceof DefaultAsyncOverReactiveSseClient);
    }

    @ParameterizedTest(autoCloseArguments = false) // closing the view closes the shared client
    @MethodSource("clients")
    void jsonResponseIsOneEvent(AsyncSseClient client) throws Exception {
        HttpResponse<BodyElements<Event<Message>>> response = await(client.exchangeEventStream(post("/async-mcp/json"), Message.class));

        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getContentType().orElseThrow());
        // the Accept header of the caller reached the server unchanged
        assertEquals(ACCEPT, response.getHeaders().get("X-Accept"));
        try (BodyElements<Event<Message>> events = response.body()) {
            assertEquals(new Message("2.0", 1, "pong"), nextEvent(events).getData());
            assertTrue(await(events.next()).isEmpty());
        }
    }

    @ParameterizedTest(autoCloseArguments = false) // closing the view closes the shared client
    @MethodSource("clients")
    void jsonResponseAsString(AsyncSseClient client) throws Exception {
        HttpResponse<BodyElements<Event<String>>> response = await(client.exchangeEventStream(post("/async-mcp/json"), Argument.STRING));

        assertEquals("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"pong\"}", nextEvent(response.body()).getData());
    }

    @ParameterizedTest(autoCloseArguments = false) // closing the view closes the shared client
    @MethodSource("clients")
    void eventsArriveAsTheyAreSent(AsyncSseClient client) throws Exception {
        CompletionStage<HttpResponse<BodyElements<Event<Message>>>> exchange = client.exchangeEventStream(post("/async-mcp/events"), Message.class);
        Sinks.Many<Event<Message>> sink = AsyncMcpController.awaitSink();
        sink.tryEmitNext(Event.of(new Message("2.0", 1, "progress")).id("1").name("message"));

        HttpResponse<BodyElements<Event<Message>>> response = await(exchange);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertTrue(MediaType.TEXT_EVENT_STREAM_TYPE.matches(response.getContentType().orElseThrow()));
        assertEquals("abc-123", response.getHeaders().get("Mcp-Session-Id"));
        BodyElements<Event<Message>> events = response.body();
        Event<Message> first = nextEvent(events);
        assertEquals(new Message("2.0", 1, "progress"), first.getData());
        assertEquals("1", first.getId());
        assertEquals("message", first.getName());

        // the next read waits for the server
        CompletableFuture<Optional<Event<Message>>> second = events.next().toCompletableFuture();
        assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
        sink.tryEmitNext(Event.of(new Message("2.0", 1, "pong")).id("2"));
        Event<Message> event = second.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).orElseThrow();
        assertEquals(new Message("2.0", 1, "pong"), event.getData());
        assertEquals("2", event.getId());

        CompletableFuture<Optional<Event<Message>>> end = events.next().toCompletableFuture();
        sink.tryEmitComplete();
        assertTrue(end.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).isEmpty());
    }

    @ParameterizedTest(autoCloseArguments = false) // closing the view closes the shared client
    @MethodSource("clients")
    void customHeaderOfResponseWithoutBody(AsyncSseClient client) throws Exception {
        HttpResponse<BodyElements<Event<Message>>> response = await(client.exchangeEventStream(post("/async-mcp/accepted"), Message.class));

        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        assertEquals("abc-123", response.getHeaders().get("Mcp-Session-Id"));
        assertTrue(await(response.body().next()).isEmpty());
    }

    @ParameterizedTest(autoCloseArguments = false) // closing the view closes the shared client
    @MethodSource("clients")
    void errorStatusCarriesTheBody(AsyncSseClient client) {
        CompletionStage<HttpResponse<BodyElements<Event<Message>>>> exchange = client.exchangeEventStream(post("/async-mcp/error"), Argument.of(Message.class), Argument.of(ErrorMessage.class));
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> await(exchange));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals("abc-123", e.getResponse().getHeaders().get("Mcp-Session-Id"));
        ErrorMessage error = e.getResponse().getBody(ErrorMessage.class).orElseThrow();
        assertEquals(new ErrorMessage("2.0", 1, Map.of("code", -32600, "message", "Invalid request")), error);
    }

    @ParameterizedTest(autoCloseArguments = false) // closing the view closes the shared client
    @MethodSource("clients")
    void errorStatusCarriesTheBodyWithDefaultErrorType(AsyncSseClient client) {
        CompletionStage<HttpResponse<BodyElements<Event<Message>>>> exchange = client.exchangeEventStream(post("/async-mcp/error"), Message.class);
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> await(exchange));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        String body = e.getResponse().getBody(String.class).orElseThrow();
        assertTrue(body.contains("Invalid request"), body);
    }

    @ParameterizedTest(autoCloseArguments = false) // closing the view closes the shared client
    @MethodSource("clients")
    void notAcceptableIsAnError(AsyncSseClient client) {
        // the route only produces JSON, and the client only accepts an event stream
        MutableHttpRequest<String> request = HttpRequest.POST("/async-mcp/json", "{}")
            .contentType(MediaType.APPLICATION_JSON_TYPE)
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE);
        CompletionStage<HttpResponse<BodyElements<Event<Message>>>> exchange = client.exchangeEventStream(request, Argument.of(Message.class), Argument.STRING);
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> await(exchange));

        assertEquals(HttpStatus.NOT_ACCEPTABLE, e.getStatus());
        assertTrue(e.getResponse().getBody(String.class).isPresent());
    }

    @ParameterizedTest(autoCloseArguments = false) // closing the view closes the shared client
    @MethodSource("clients")
    void eventStreamReplacesTheAcceptHeader(AsyncSseClient client) {
        // eventStream sets Accept: text/event-stream, so the JSON only route answers 406
        MutableHttpRequest<String> request = HttpRequest.POST("/async-mcp/json", "{}")
            .contentType(MediaType.APPLICATION_JSON_TYPE)
            .accept(MediaType.APPLICATION_JSON_TYPE);
        CompletionStage<BodyElements<Event<String>>> events = client.eventStream(request, Argument.STRING, Argument.STRING);
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> await(events));

        assertEquals(HttpStatus.NOT_ACCEPTABLE, e.getStatus());
        assertTrue(e.getResponse().getBody(String.class).isPresent());
    }

    @ParameterizedTest(autoCloseArguments = false) // closing the view closes the shared client
    @MethodSource("clients")
    void eventStreamJoinsMultilineData(AsyncSseClient client) throws Exception {
        MutableHttpRequest<String> request = HttpRequest.POST("/async-mcp/multiline", "{}").contentType(MediaType.APPLICATION_JSON_TYPE);
        BodyElements<Event<String>> events = await(client.eventStream(request, String.class));

        assertEquals("second line\nthird", nextEvent(events).getData());
        assertTrue(await(events.next()).isEmpty());
    }

    @ParameterizedTest(autoCloseArguments = false) // closing the view closes the shared client
    @MethodSource("clients")
    void acceptIsAddedWhenMissing(AsyncSseClient client) throws Exception {
        HttpRequest<String> request = HttpRequest.POST("/async-mcp/events-accept", "{}").contentType(MediaType.APPLICATION_JSON_TYPE);
        HttpResponse<BodyElements<Event<String>>> response = await(client.exchangeEventStream(request, Argument.STRING));

        assertEquals(MediaType.TEXT_EVENT_STREAM, nextEvent(response.body()).getData());
    }

    @ParameterizedTest(autoCloseArguments = false) // closing the view closes the shared client
    @MethodSource("clients")
    void forEachReadsEveryEvent(AsyncSseClient client) throws Exception {
        // many events arrive in few pieces, and are consumed in a loop rather than recursively
        MutableHttpRequest<String> request = HttpRequest.POST("/async-mcp/many", "{}").contentType(MediaType.APPLICATION_JSON_TYPE);
        BodyElements<Event<Integer>> events = await(client.eventStream(request, Integer.class));
        List<Integer> received = new ArrayList<>();
        await(events.forEach(event -> {
            received.add(event.getData());
            return CompletableFuture.completedStage(null);
        }));

        assertEquals(IntStream.range(0, AsyncMcpController.MANY).boxed().toList(), received);
    }

    @ParameterizedTest(autoCloseArguments = false) // closing the view closes the shared client
    @MethodSource("clients")
    void oneOperationAtATime(AsyncSseClient client) throws Exception {
        CompletionStage<HttpResponse<BodyElements<Event<Message>>>> exchange = client.exchangeEventStream(post("/async-mcp/events"), Message.class);
        Sinks.Many<Event<Message>> sink = AsyncMcpController.awaitSink();
        sink.tryEmitNext(Event.of(new Message("2.0", 1, "progress")));
        BodyElements<Event<Message>> events = await(exchange).body();
        nextEvent(events);

        CompletableFuture<Optional<Event<Message>>> waiting = events.next().toCompletableFuture();
        assertThrows(IllegalStateException.class, events::next);

        // closing ends the waiting read, and later reads are refused
        events.close();
        ExecutionException e = assertThrows(ExecutionException.class, () -> waiting.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
        assertInstanceOf(CancellationException.class, e.getCause());
        assertThrows(IllegalStateException.class, events::next);
        sink.tryEmitComplete();

        // the client still works
        HttpResponse<BodyElements<Event<Message>>> response = await(client.exchangeEventStream(post("/async-mcp/json"), Message.class));
        assertEquals(new Message("2.0", 1, "pong"), nextEvent(response.body()).getData());
    }

    record Message(String jsonrpc, int id, String result) {
    }

    record ErrorMessage(String jsonrpc, int id, Map<String, Object> error) {
    }

    @Requires(property = "spec.name", value = SPEC)
    @Controller("/async-mcp")
    static class AsyncMcpController {
        static final int MANY = 10_000;

        private static final AtomicReference<CompletableFuture<Sinks.Many<Event<Message>>>> SINK =
            new AtomicReference<>(new CompletableFuture<>());

        static Sinks.Many<Event<Message>> awaitSink() {
            try {
                return SINK.get().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new AssertionError(e);
            } finally {
                SINK.set(new CompletableFuture<>());
            }
        }

        @Post(uri = "/json", produces = MediaType.APPLICATION_JSON)
        HttpResponse<String> json(@Body String body, @Header(HttpHeaders.ACCEPT) String accept) {
            return HttpResponse.ok("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"pong\"}").header("X-Accept", accept);
        }

        @Post(uri = "/events", produces = MediaType.TEXT_EVENT_STREAM)
        HttpResponse<Flux<Event<Message>>> events(@Body String body) {
            Sinks.Many<Event<Message>> sink = Sinks.many().unicast().onBackpressureBuffer();
            SINK.get().complete(sink);
            return HttpResponse.ok(sink.asFlux()).header("Mcp-Session-Id", "abc-123");
        }

        @Post(uri = "/events-accept", produces = MediaType.TEXT_EVENT_STREAM)
        Publisher<Event<String>> eventsAccept(@Body String body, @Header(HttpHeaders.ACCEPT) String accept) {
            return Flux.just(Event.of(accept));
        }

        @Post(uri = "/multiline", produces = MediaType.TEXT_EVENT_STREAM)
        Publisher<Event<String>> multiline(@Body String body) {
            return Flux.just(Event.of("second line\nthird"));
        }

        @Post(uri = "/many", produces = MediaType.TEXT_EVENT_STREAM)
        Publisher<Event<Integer>> many(@Body String body) {
            return Flux.range(0, MANY).map(Event::of);
        }

        @Post(uri = "/accepted", produces = {MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM})
        HttpResponse<?> accepted(@Body String body) {
            return HttpResponse.accepted().header("Mcp-Session-Id", "abc-123");
        }

        @Post(uri = "/error", produces = MediaType.APPLICATION_JSON)
        HttpResponse<String> error(@Body String body) {
            return HttpResponse.<String>badRequest()
                .body("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32600,\"message\":\"Invalid request\"}}")
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .header("Mcp-Session-Id", "abc-123");
        }
    }
}
