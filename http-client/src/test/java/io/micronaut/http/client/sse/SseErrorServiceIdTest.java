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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The failures to read or decode the events of a client with a service id carry that id, as the
 * other failures of the client do.
 */
class SseErrorServiceIdTest {
    private static final String SPEC = "SseErrorServiceIdTest";
    private static final String SERVICE = "sse-error-service";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static EmbeddedServer server;
    private static ApplicationContext clientContext;
    private static HttpClient httpClient;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC));
        clientContext = ApplicationContext.run(Map.of(
            "micronaut.http.services." + SERVICE + ".url", server.getURL().toString(),
            "micronaut.http.services." + SERVICE + ".max-content-length", 1024));
        @SuppressWarnings("unchecked")
        HttpClientRegistry<HttpClient> registry = clientContext.getBean(HttpClientRegistry.class);
        httpClient = registry.getClient(HttpVersion.HTTP_1_1, SERVICE, null);
    }

    @AfterAll
    static void stop() {
        clientContext.close();
        server.close();
    }

    private static Throwable cause(ExecutionException e) {
        return e.getCause();
    }

    @Test
    void asyncEventDecodeFailureHasServiceId() throws Exception {
        AsyncSseClient client = ((SseClient) httpClient).toAsyncSse();
        HttpResponse<BodyElements<Event<Map>>> response = client.exchangeEventStream(HttpRequest.GET("/sse-error/not-json"), Map.class)
            .toCompletableFuture().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        try (BodyElements<Event<Map>> events = response.body()) {
            ExecutionException e = assertThrows(ExecutionException.class, () -> events.next().toCompletableFuture().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
            HttpClientException failure = assertInstanceOf(HttpClientException.class, cause(e));
            assertEquals(SERVICE, failure.getServiceId());
        }
    }

    @Test
    void asyncLongLineFailureHasServiceId() throws Exception {
        AsyncSseClient client = ((SseClient) httpClient).toAsyncSse();
        HttpResponse<BodyElements<Event<Map>>> response = client.exchangeEventStream(HttpRequest.GET("/sse-error/long-line"), Map.class)
            .toCompletableFuture().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        try (BodyElements<Event<Map>> events = response.body()) {
            ExecutionException e = assertThrows(ExecutionException.class, () -> events.next().toCompletableFuture().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
            HttpClientException failure = assertInstanceOf(HttpClientException.class, cause(e));
            assertEquals(SERVICE, failure.getServiceId());
        }
    }

    @Test
    void reactiveExchangeEventStreamDecodeFailureHasServiceId() {
        SseClient client = (SseClient) httpClient;
        Throwable e = assertThrows(Throwable.class, () -> Flux.from(client.exchangeEventStream(HttpRequest.GET("/sse-error/not-json"), Argument.of(Map.class), HttpClient.DEFAULT_ERROR_TYPE))
            .blockLast(TIMEOUT));
        HttpClientException failure = assertInstanceOf(HttpClientException.class, e);
        assertEquals(SERVICE, failure.getServiceId());
    }

    @Test
    void reactiveEventStreamReadFailureHasServiceId() {
        SseClient client = (SseClient) httpClient;
        Throwable e = assertThrows(Throwable.class, () -> Flux.from(client.eventStream(HttpRequest.GET("/sse-error/long-line"), Map.class))
            .blockLast(TIMEOUT));
        HttpClientException failure = assertInstanceOf(HttpClientException.class, e);
        assertEquals(SERVICE, failure.getServiceId());
    }

    @Requires(property = "spec.name", value = SPEC)
    @Controller("/sse-error")
    static class SseErrorController {

        @Get(uri = "/not-json", produces = MediaType.TEXT_EVENT_STREAM)
        String notJson() {
            return "data: not json\n\n";
        }

        @Get(uri = "/long-line", produces = MediaType.TEXT_EVENT_STREAM)
        String longLine() {
            return "data: \"" + "x".repeat(4096) + "\"\n\n";
        }
    }
}
