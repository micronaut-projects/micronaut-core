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
package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamedFluxRequestContextTest {

    @Test
    void multiElementFluxSeesServerRequestContextOnSubsequentRequests() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "StreamedFluxRequestContextTest"
        )); HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURI())) {
            List<Map<String, String>> result = client.toBlocking().retrieve(
                HttpRequest.GET("/streamed-flux-context/items").header("X-Req-Id", "abc-123"),
                Argument.listOf(Argument.mapOf(String.class, String.class))
            );
            assertEquals(List.of(
                Map.of("item", "1:abc-123"),
                Map.of("item", "2:abc-123"),
                Map.of("item", "3:abc-123"),
                Map.of("item", "4:abc-123"),
                Map.of("item", "5:abc-123")
            ), result);
        }
    }

    @Requires(property = "spec.name", value = "StreamedFluxRequestContextTest")
    @Controller("/streamed-flux-context")
    static class TestController {

        @Get(value = "/items", produces = MediaType.APPLICATION_JSON)
        Flux<Map<String, String>> items() {
            return Flux.range(1, 5).map(i -> {
                String header = ServerRequestContext.currentRequest()
                    .map(req -> req.getHeaders().get("X-Req-Id"))
                    .orElse("missing");
                return Map.of("item", i + ":" + header);
            });
        }
    }
}
