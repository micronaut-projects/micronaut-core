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
package io.micronaut.http.client.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.AsyncProxyHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import jakarta.annotation.PreDestroy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * A route that relays its own request with an injected {@link AsyncProxyHttpClient}: the body
 * bytes of the server request are claimed and relayed as they are.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
})
class AsyncProxyHttpClientRelayTest {
    static final String SPEC_NAME = "AsyncProxyHttpClientRelayTest";

    @Test
    void aServerRelaysItsRequestWithTheAsyncClient() throws Exception {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME)) {
            HttpResponse<String> response = server.exchange(
                HttpRequest.POST("/async-proxy-relay/gateway", "relayed").contentType(MediaType.TEXT_PLAIN_TYPE), String.class);
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("relayed", response.body());
        }
    }

    @Controller("/async-proxy-relay")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RelayController {
        private final AsyncProxyHttpClient client;
        // Keep response writing within this test's inherited leak-detection scope.
        private final ExecutorService responses = Executors.newSingleThreadExecutor();

        @PreDestroy
        void close() {
            responses.close();
        }

        RelayController(@Client("/") AsyncProxyHttpClient client) {
            this.client = client;
        }

        @Post(value = "/gateway", consumes = MediaType.ALL)
        CompletionStage<MutableHttpResponse<?>> gateway(HttpRequest<?> request) {
            // the body bytes of this server request are relayed as they are, to a URI resolved
            // against the URL of the client
            return client.proxy(request.mutate().uri(URI.create("/async-proxy-relay/echo"))).thenApplyAsync(Function.identity(), responses);
        }

        @Post(value = "/echo", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String echo(@Body String body) {
            return body;
        }
    }
}
