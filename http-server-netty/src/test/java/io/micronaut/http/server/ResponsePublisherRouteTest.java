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
package io.micronaut.http.server;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A route that returns a publisher of responses is answered by the first response, and an empty
 * publisher like a missing body, with the execution prepared for the route and with the
 * compatibility execution.
 */
class ResponsePublisherRouteTest {

    private static final String SPEC = "ResponsePublisherRouteTest";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aPublisherOfResponses(boolean compatibility) {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC, "micronaut.server.port", -1))) {
            ctx.getBean(RouteExecutor.class).forceCompatibility = compatibility;
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (HttpClient httpClient = ctx.createBean(HttpClient.class, server.getURL())) {
                BlockingHttpClient client = httpClient.toBlocking();
                HttpResponse<String> first = client.exchange(HttpRequest.GET("/response-publisher/first"), String.class);
                assertEquals(HttpStatus.ACCEPTED, first.status());
                assertEquals("a", first.body());
                HttpClientResponseException empty = assertThrows(HttpClientResponseException.class,
                    () -> client.exchange(HttpRequest.GET("/response-publisher/empty"), String.class));
                assertEquals(HttpStatus.NOT_FOUND, empty.getStatus());
            }
        }
    }

    @Controller(value = "/response-publisher", produces = MediaType.TEXT_PLAIN)
    @Requires(property = "spec.name", value = SPEC)
    static class ResponsePublisherController {

        @Get("/first")
        Publisher<HttpResponse<String>> first() {
            return Flux.just(HttpResponse.<String>status(HttpStatus.ACCEPTED).body("a"), HttpResponse.ok("b"));
        }

        @Get("/empty")
        Publisher<HttpResponse<String>> empty() {
            return Flux.empty();
        }
    }
}
