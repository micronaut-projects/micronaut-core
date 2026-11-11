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
package io.micronaut.http.client.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The publisher streams of the Netty client send the request with the propagated context of the
 * caller, as they did before they were read without Reactor, also when the publisher is
 * subscribed to later, on another thread.
 */
class LegacyStreamContextTest {
    private static final String SPEC = "LegacyStreamContextTest";
    static final AtomicReference<String> SEEN = new AtomicReference<>();

    private static EmbeddedServer server;
    private static StreamingHttpClient client;
    private static SseClient sseClient;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC));
        client = server.getApplicationContext().createBean(StreamingHttpClient.class, server.getURL());
        // the Netty client is an SSE client too
        sseClient = (SseClient) client;
    }

    @AfterAll
    static void stop() {
        client.close();
        server.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dataStream", "jsonStream", "exchangeStream", "exchangeEventStream"})
    void theRequestIsSentWithTheContextOfTheCaller(String kind) {
        SEEN.set(null);
        Publisher<?> publisher = PropagatedContext.getOrEmpty().plus(new Marker(kind)).propagate(() -> {
            HttpRequest<?> request = HttpRequest.GET(kind.equals("exchangeEventStream") ? "/legacy-context/events" : "/legacy-context/json");
            return switch (kind) {
                case "dataStream" -> (Publisher<?>) client.dataStream(request);
                case "jsonStream" -> client.jsonStream(request, Map.class);
                case "exchangeStream" -> client.exchangeStream(request);
                default -> sseClient.exchangeEventStream(request, Argument.STRING);
            };
        });

        // subscribed outside of the context, on another thread
        Flux.from(publisher).subscribeOn(Schedulers.boundedElastic()).collectList().block(Duration.ofSeconds(10));

        assertEquals(kind, SEEN.get());
    }

    record Marker(String value) implements PropagatedContextElement {
    }

    @Requires(property = "spec.name", value = SPEC)
    @ClientFilter
    static class ContextFilter {
        @RequestFilter
        void filter(MutableHttpRequest<?> request) {
            SEEN.set(PropagatedContext.getOrEmpty().find(Marker.class).map(Marker::value).orElse("none"));
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Controller("/legacy-context")
    static class StreamController {
        @Get(value = "/json", produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Map<String, String>> json() {
            return Flux.just(Map.of("a", "b"));
        }

        @Get(value = "/events", produces = MediaType.TEXT_EVENT_STREAM)
        Publisher<Event<String>> events() {
            return Flux.just(Event.of("a"));
        }
    }
}
