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
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SseClient#exchangeEventStream} reads a response that is either a single JSON body or an event stream, as the
 * MCP Streamable HTTP transport answers, with the status and the headers of the response.
 */
class ExchangeEventStreamTest {
    private static final String SPEC = "ExchangeEventStreamTest";
    private static final String ACCEPT = MediaType.APPLICATION_JSON + ", " + MediaType.TEXT_EVENT_STREAM;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static EmbeddedServer server;
    private static HttpClient httpClient;
    private static SseClient client;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC));
        httpClient = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
        client = (SseClient) httpClient;
    }

    @AfterAll
    static void stop() {
        httpClient.close();
        server.close();
    }

    private static MutableHttpRequest<String> post(String uri) {
        return HttpRequest.POST(uri, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}")
            .contentType(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.ACCEPT, ACCEPT);
    }

    @Test
    void jsonResponseIsOneEvent() {
        List<HttpResponse<Event<Message>>> responses = Flux.from(client.exchangeEventStream(post("/mcp/json"), Message.class))
            .collectList().block(TIMEOUT);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        HttpResponse<Event<Message>> response = responses.get(0);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getContentType().orElseThrow());
        // the Accept header of the caller reached the server unchanged
        assertEquals(ACCEPT, response.getHeaders().get("X-Accept"));
        assertEquals(new Message("2.0", 1, "pong"), response.body().getData());
    }

    @Test
    void jsonResponseAsString() {
        HttpResponse<Event<String>> response = Flux.from(client.exchangeEventStream(post("/mcp/json"), Argument.STRING))
            .blockLast(TIMEOUT);

        assertNotNull(response);
        assertEquals("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"pong\"}", response.body().getData());
    }

    @Test
    void eventsArriveAsTheyAreSent() throws Exception {
        BlockingQueue<HttpResponse<Event<Message>>> received = new LinkedBlockingQueue<>();
        CompletableFuture<Void> completed = new CompletableFuture<>();
        Flux.from(client.exchangeEventStream(post("/mcp/events"), Message.class))
            .subscribe(received::add, completed::completeExceptionally, () -> completed.complete(null));

        Sinks.Many<Event<Message>> sink = McpController.awaitSink();
        sink.tryEmitNext(Event.of(new Message("2.0", 1, "progress")).id("1").name("message"));

        HttpResponse<Event<Message>> first = received.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertNotNull(first, "The first event did not arrive before the stream completed");
        assertFalse(completed.isDone());
        assertEquals(HttpStatus.OK, first.getStatus());
        assertTrue(MediaType.TEXT_EVENT_STREAM_TYPE.matches(first.getContentType().orElseThrow()));
        assertEquals("abc-123", first.getHeaders().get("Mcp-Session-Id"));
        Event<Message> event = first.body();
        assertEquals(new Message("2.0", 1, "progress"), event.getData());
        assertEquals("1", event.getId());
        assertEquals("message", event.getName());

        sink.tryEmitNext(Event.of(new Message("2.0", 1, "pong")).id("2"));
        HttpResponse<Event<Message>> second = received.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertNotNull(second);
        assertFalse(completed.isDone());
        assertEquals(new Message("2.0", 1, "pong"), second.body().getData());
        assertEquals("2", second.body().getId());

        sink.tryEmitComplete();
        completed.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertTrue(received.isEmpty());
    }

    @Test
    void customHeaderOfResponseWithoutBody() {
        List<HttpResponse<Event<Message>>> responses = Flux.from(client.exchangeEventStream(post("/mcp/accepted"), Message.class))
            .collectList().block(TIMEOUT);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        HttpResponse<Event<Message>> response = responses.get(0);
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        assertEquals("abc-123", response.getHeaders().get("Mcp-Session-Id"));
        assertNull(response.body());
    }

    @Test
    void errorStatusCarriesTheBody() {
        Flux<HttpResponse<Event<Message>>> responses = Flux.from(client.exchangeEventStream(post("/mcp/error"), Argument.of(Message.class), Argument.of(ErrorMessage.class)));
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> responses.blockLast(TIMEOUT));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals("abc-123", e.getResponse().getHeaders().get("Mcp-Session-Id"));
        ErrorMessage error = e.getResponse().getBody(ErrorMessage.class).orElseThrow();
        assertEquals(new ErrorMessage("2.0", 1, Map.of("code", -32600, "message", "Invalid request")), error);
    }

    @Test
    void errorStatusCarriesTheBodyWithDefaultErrorType() {
        Flux<HttpResponse<Event<Message>>> responses = Flux.from(client.exchangeEventStream(post("/mcp/error"), Message.class));
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> responses.blockLast(TIMEOUT));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        String body = e.getResponse().getBody(String.class).orElseThrow();
        assertTrue(body.contains("Invalid request"), body);
    }

    @Test
    void notAcceptableIsAnError() {
        // the route only produces JSON, and the client only accepts an event stream
        MutableHttpRequest<String> request = HttpRequest.POST("/mcp/json", "{}")
            .contentType(MediaType.APPLICATION_JSON_TYPE)
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE);
        Flux<HttpResponse<Event<Message>>> responses = Flux.from(client.exchangeEventStream(request, Argument.of(Message.class), Argument.STRING));
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> responses.blockLast(TIMEOUT));

        assertEquals(HttpStatus.NOT_ACCEPTABLE, e.getStatus());
        assertTrue(e.getResponse().getBody(String.class).isPresent());
    }

    @Test
    void eventStreamKeepsTheErrorBody() {
        // eventStream replaces an Accept header without text/event-stream, so the JSON only route answers 406, and the
        // error body is not split into event stream lines, which lost it
        MutableHttpRequest<String> request = HttpRequest.POST("/mcp/json", "{}")
            .contentType(MediaType.APPLICATION_JSON_TYPE)
            .accept(MediaType.APPLICATION_JSON_TYPE);
        Flux<Event<String>> events = Flux.from(client.eventStream(request, Argument.STRING, Argument.STRING));
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> events.blockLast(TIMEOUT));

        assertEquals(HttpStatus.NOT_ACCEPTABLE, e.getStatus());
        assertTrue(e.getResponse().getBody(String.class).isPresent());
    }

    @Test
    void acceptIsAddedWhenMissing() {
        HttpRequest<String> request = HttpRequest.POST("/mcp/events-accept", "{}").contentType(MediaType.APPLICATION_JSON_TYPE);
        HttpResponse<Event<String>> response = Flux.from(client.exchangeEventStream(request, Argument.STRING)).blockLast(TIMEOUT);

        assertNotNull(response);
        assertEquals(MediaType.TEXT_EVENT_STREAM, response.body().getData());
    }

    @Test
    void eventStreamIsUnchanged() {
        CompletableFuture<List<Event<Message>>> events = Flux.from(client.eventStream(post("/mcp/events"), Message.class))
            .collectList().toFuture();
        Sinks.Many<Event<Message>> serverSink = McpController.awaitSink();
        serverSink.tryEmitNext(Event.of(new Message("2.0", 1, "pong")));
        serverSink.tryEmitComplete();

        List<Event<Message>> list = events.join();
        assertEquals(1, list.size());
        assertEquals(new Message("2.0", 1, "pong"), list.get(0).getData());
    }

    @Test
    void multilineDataIsJoinedWithLineFeed() {
        MutableHttpRequest<String> request = HttpRequest.POST("/mcp/multiline", "{}").contentType(MediaType.APPLICATION_JSON_TYPE);
        Event<String> legacy = Flux.from(client.eventStream(request, String.class)).blockLast(TIMEOUT);
        HttpResponse<Event<String>> exchanged = Flux.from(client.exchangeEventStream(request, Argument.STRING)).blockLast(TIMEOUT);

        assertNotNull(legacy);
        assertNotNull(exchanged);
        assertEquals("second line\nthird", legacy.getData());
        assertEquals("second line\nthird", exchanged.body().getData());
    }

    @Test
    void dataStreamWithSeveralAcceptedTypesIsNotSplit() {
        // only an Accept of exactly text/event-stream splits the body of the existing streaming methods into lines
        MutableHttpRequest<String> request = HttpRequest.POST("/mcp/multiline", "{}")
            .contentType(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.ACCEPT, ACCEPT);
        String body = Flux.from(((io.micronaut.http.client.StreamingHttpClient) httpClient).dataStream(request))
            .map(buffer -> buffer.toString(java.nio.charset.StandardCharsets.UTF_8))
            .collect(java.util.stream.Collectors.joining())
            .block(TIMEOUT);

        assertEquals("data: second line\ndata: third\n\n", body);
    }

    record Message(String jsonrpc, int id, String result) {
    }

    record ErrorMessage(String jsonrpc, int id, Map<String, Object> error) {
    }

    @Requires(property = "spec.name", value = SPEC)
    @Controller("/mcp")
    static class McpController {
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
