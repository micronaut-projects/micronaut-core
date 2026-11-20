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
package io.micronaut.http.gateway;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpResponse;
import io.micronaut.retry.CircuitBreakerRegistry;
import io.micronaut.retry.CircuitState;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A client that goes away is not a failure of the upstream: the circuit is not charged and no
 * fallback answers.
 */
class AbandonedRequestTest extends GatewayTestBase {

    static final String SPEC = "AbandonedRequestTest";
    static final AtomicInteger FALLBACKS = new AtomicInteger();

    @Override
    String specName() {
        return SPEC;
    }

    @Test
    void anAbandonedRequestChargesNoCircuitAndCallsNoFallback() throws Exception {
        CountDownLatch closed = new CountDownLatch(4);
        scripted.on("/silent", (request, in, out, socket) -> {
            try {
                while (in.read() >= 0) {
                    // never answers
                }
            } catch (IOException e) {
                // reset
            }
            closed.countDown();
            return false;
        });
        for (int i = 0; i < 4; i++) {
            RawHttp http = new RawHttp(gateway.getPort());
            http.send("GET /abandoned/silent HTTP/1.1\r\nHost: localhost\r\n\r\n");
            Thread.sleep(200);
            http.close();
        }
        assertTrue(closed.await(5, TimeUnit.SECONDS), "the exchanges were cancelled");
        Thread.sleep(200);
        assertEquals(0, FALLBACKS.get());
        CircuitBreakerRegistry circuits = gateway.getApplicationContext().getBean(CircuitBreakerRegistry.class);
        assertEquals(CircuitState.CLOSED, circuits.findState("abandoned").orElse(CircuitState.CLOSED));
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String scripted;

        Routes(@Value("${test.scripted}") String scripted) {
            this.scripted = scripted;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            gateway.route("abandoned").path("/abandoned/**")
                .filters(GatewayFilters.stripPrefix(1))
                .circuitBreaker(GatewayCircuitBreaker.of("abandoned", (request, pathVariables) -> {
                    FALLBACKS.incrementAndGet();
                    return CompletableFuture.completedFuture(HttpResponse.ok("fallback"));
                }))
                .uri(scripted);
        }
    }
}
