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
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.AsyncProxyHttpClient;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * The stage of an {@link AsyncProxyHttpClient} exchange completes within the context the caller
 * propagated, as the publisher of the reactive proxy client does: the stages that depend on it
 * see that context.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
})
class AsyncProxyHttpClientContextTest {
    static final String SPEC_NAME = "AsyncProxyHttpClientContextTest";

    @ParameterizedTest
    @ValueSource(strings = {"injected", "toAsyncProxy"})
    void theStageCompletesWithinTheContextOfTheCaller(String kind) throws Exception {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME)) {
            AsyncProxyHttpClient client = "injected".equals(kind)
                ? server.getApplicationContext().getBean(AsyncProxyHttpClient.class)
                : server.getApplicationContext().getBean(ProxyHttpClient.class).toAsyncProxy();
            CompletionStage<MutableHttpResponse<?>> stage = PropagatedContext.getOrEmpty().plus(new Caller("the caller"))
                .propagate(() -> client.proxy(HttpRequest.GET(server.getURL().get() + "/async-proxy-context/slow")));
            // the response is late: the stage that depends on it runs where it completes
            CompletableFuture<Optional<Caller>> seen = stage.thenApply(response -> {
                if (response instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                    byteBodyResponse.close();
                }
                return PropagatedContext.getOrEmpty().find(Caller.class);
            }).toCompletableFuture();
            Assertions.assertEquals(Optional.of(new Caller("the caller")), seen.get(10, TimeUnit.SECONDS));
        }
    }

    record Caller(String name) implements PropagatedContextElement {
    }

    @Controller("/async-proxy-context")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class SlowController {
        @Get(value = "/slow", produces = MediaType.TEXT_PLAIN)
        Mono<String> slow() {
            return Mono.delay(Duration.ofMillis(500)).map(tick -> "slow");
        }
    }
}
