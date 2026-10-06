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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.sse.AsyncSseClient;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The async clients injected with {@link Client}, and declarative client methods that return
 * {@code CompletionStage<BodyElements<T>>} or {@code CompletionStage<HttpResponse<BodyElements<T>>>}.
 */
class AsyncClientsTest {
    private static final String SPEC = "AsyncClientsTest";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC));
    }

    @AfterAll
    static void stop() {
        server.close();
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

    private static <T> List<T> readAll(BodyElements<T> elements) throws Exception {
        List<T> all = new ArrayList<>();
        await(elements.forEach(element -> {
            all.add(element);
            return CompletableFuture.completedStage(null);
        }));
        return all;
    }

    private static String text(List<ByteBuffer<?>> pieces) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        pieces.forEach(piece -> bytes.writeBytes(piece.toByteArray()));
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void asyncClientsAreInjected() throws Exception {
        InjectedClients clients = server.getApplicationContext().getBean(InjectedClients.class);

        assertEquals("pong", await(clients.asyncHttpClient.retrieve(HttpRequest.GET("/async-clients/ping"))));
        List<Event<Message>> events = readAll(await(clients.asyncSseClient.eventStream(HttpRequest.GET("/async-clients/events"), Message.class)));
        assertEquals(List.of(new Message("one"), new Message("two")), events.stream().map(Event::getData).toList());
        assertEquals("abc", text(readAll(await(clients.asyncStreamingHttpClient.dataStream(HttpRequest.GET("/async-clients/bytes"))))));
    }

    @Test
    void declarativeEvents() throws Exception {
        AsyncElementsClient client = server.getApplicationContext().getBean(AsyncElementsClient.class);

        List<Event<Message>> events = readAll(await(client.events()));
        assertEquals(List.of(new Message("one"), new Message("two")), events.stream().map(Event::getData).toList());
        assertEquals("1", events.get(0).getId());
        assertEquals(List.of(new Message("one"), new Message("two")), readAll(await(client.eventData())));
    }

    @Test
    void declarativeEventsWithTheResponse() throws Exception {
        AsyncElementsClient client = server.getApplicationContext().getBean(AsyncElementsClient.class);

        HttpResponse<BodyElements<Event<Message>>> response = await(client.exchangeEvents());
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals("abc-123", response.getHeaders().get("Mcp-Session-Id"));
        assertEquals(2, readAll(response.body()).size());
    }

    @Test
    void declarativeJsonStream() throws Exception {
        AsyncElementsClient client = server.getApplicationContext().getBean(AsyncElementsClient.class);

        assertEquals(List.of(new Message("one"), new Message("two")), readAll(await(client.messages())));
    }

    @Test
    void declarativeBytes() throws Exception {
        AsyncElementsClient client = server.getApplicationContext().getBean(AsyncElementsClient.class);

        assertEquals("abc", text(readAll(await(client.bytes()))));
        HttpResponse<BodyElements<ByteBuffer<?>>> response = await(client.exchangeBytes());
        assertEquals("yes", response.getHeaders().get("X-Stream"));
        assertEquals("abc", text(readAll(response.body())));
    }

    @Test
    void declarativeErrorStatus() {
        AsyncElementsClient client = server.getApplicationContext().getBean(AsyncElementsClient.class);

        CompletionStage<BodyElements<Message>> messages = client.error();
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> await(messages));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertTrue(e.getResponse().getBody(String.class).orElseThrow().contains("Invalid request"));
    }

    record Message(String text) {
    }

    @Requires(property = "spec.name", value = SPEC)
    @Singleton
    static class InjectedClients {
        @Inject
        @Client("/")
        AsyncHttpClient asyncHttpClient;

        @Inject
        @Client("/")
        AsyncSseClient asyncSseClient;

        @Inject
        @Client("/")
        AsyncStreamingHttpClient asyncStreamingHttpClient;
    }

    @Requires(property = "spec.name", value = SPEC)
    @Client("/async-clients")
    interface AsyncElementsClient {

        @Get(value = "/events", processes = MediaType.TEXT_EVENT_STREAM)
        CompletionStage<BodyElements<Event<Message>>> events();

        @Get(value = "/events", processes = MediaType.TEXT_EVENT_STREAM)
        CompletionStage<BodyElements<Message>> eventData();

        @Get(value = "/events", processes = MediaType.TEXT_EVENT_STREAM)
        CompletionStage<HttpResponse<BodyElements<Event<Message>>>> exchangeEvents();

        @Get(value = "/messages", processes = MediaType.APPLICATION_JSON_STREAM)
        CompletionStage<BodyElements<Message>> messages();

        @Get(value = "/bytes", processes = MediaType.APPLICATION_OCTET_STREAM)
        CompletionStage<BodyElements<ByteBuffer<?>>> bytes();

        @Get(value = "/bytes", processes = MediaType.APPLICATION_OCTET_STREAM)
        CompletionStage<HttpResponse<BodyElements<ByteBuffer<?>>>> exchangeBytes();

        @Get(value = "/error", processes = MediaType.APPLICATION_JSON)
        CompletionStage<BodyElements<Message>> error();
    }

    @Requires(property = "spec.name", value = SPEC)
    @Controller("/async-clients")
    static class AsyncClientsController {

        @Get(value = "/ping", produces = MediaType.TEXT_PLAIN)
        String ping() {
            return "pong";
        }

        @Get(value = "/events", produces = MediaType.TEXT_EVENT_STREAM)
        HttpResponse<Publisher<Event<Message>>> events() {
            return HttpResponse.<Publisher<Event<Message>>>ok(Flux.just(
                Event.of(new Message("one")).id("1"),
                Event.of(new Message("two")).id("2")
            )).header("Mcp-Session-Id", "abc-123");
        }

        @Get(value = "/messages", produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Message> messages() {
            return Flux.just(new Message("one"), new Message("two"));
        }

        @Get(value = "/bytes", produces = MediaType.APPLICATION_OCTET_STREAM)
        HttpResponse<Publisher<byte[]>> bytes() {
            return HttpResponse.<Publisher<byte[]>>ok(Flux.just("a", "b", "c").map(s -> s.getBytes(StandardCharsets.UTF_8)))
                .header("X-Stream", "yes");
        }

        @Get(value = "/error", produces = MediaType.APPLICATION_JSON)
        HttpResponse<String> error() {
            return HttpResponse.<String>badRequest()
                .body("{\"message\":\"Invalid request\"}")
                .contentType(MediaType.APPLICATION_JSON_TYPE);
        }
    }
}
