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
package io.micronaut.docs.sse;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.sse.AsyncSseClient;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "spec.name", value = "AsyncSseClientSpec")
@MicronautTest
class AsyncSseClientSpec {

    @Inject
    @Client("/")
    SseClient sseClient;

    @Test
    void readEventsAsTheyArrive() throws Exception {
        // tag::async[]
        AsyncSseClient client = sseClient.toAsyncSse(); // <1>
        MutableHttpRequest<String> request = HttpRequest.POST("/mcp", "{\"method\":\"ping\"}")
            .contentType(MediaType.APPLICATION_JSON_TYPE)
            .accept(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_EVENT_STREAM_TYPE);
        List<String> messages = new CopyOnWriteArrayList<>();

        CompletionStage<String> sessionId = client.exchangeEventStream(request, String.class) // <2>
            .thenCompose(response -> response.body() // <3>
                .forEach(event -> { // <4>
                    messages.add(event.getData());
                    return CompletableFuture.completedStage(null);
                })
                .thenApply(done -> response.getHeaders().get("Mcp-Session-Id"))); // <5>
        // end::async[]

        assertEquals("abc-123", sessionId.toCompletableFuture().get(10, TimeUnit.SECONDS));
        assertEquals(List.of("progress", "done"), messages);
    }
}
