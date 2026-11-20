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
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GatewayObserver} beans are told of every exchange once, with its route, status and
 * outcome; a failing observer changes nothing.
 */
class ObserverTest extends GatewayTestBase {

    static final String SPEC = "ObserverTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        Map<String, Object> p = new HashMap<>();
        String r = "micronaut.gateway.routes.";
        p.put(r + "observed.uri", "${test.upstream}");
        p.put(r + "observed.predicates", List.of("Path=/obs/**"));
        p.put(r + "observed.filters", List.of("StripPrefix=1"));
        p.put(r + "unreachable.uri", "http://127.0.0.1:1");
        p.put(r + "unreachable.predicates", List.of("Path=/obs-down/**"));
        return p;
    }

    private GatewayObserver.Exchange next() throws InterruptedException {
        GatewayObserver.Exchange exchange = gateway.getApplicationContext().getBean(Recorder.class).exchanges.poll(10, TimeUnit.SECONDS);
        assertNotNull(exchange, "no exchange observed");
        return exchange;
    }

    @Test
    void responsesAndFailuresAreObserved() throws IOException, InterruptedException {
        gateway.getApplicationContext().getBean(Recorder.class).exchanges.clear();
        assertEquals(200, get("/obs/inspect/a").status());
        GatewayObserver.Exchange ok = next();
        assertEquals("observed", ok.routeId());
        assertEquals("GET", ok.method());
        assertEquals(200, ok.status());
        assertEquals(GatewayObserver.Outcome.RESPONSE, ok.outcome());
        assertNotNull(ok.upstream());
        assertNull(ok.failure());
        assertTrue(!ok.duration().isNegative());

        // a status of the upstream is a response, whatever the status
        assertEquals(404, get("/obs/missing").status());
        assertEquals(404, next().status());

        assertEquals(502, get("/obs-down/x").status());
        GatewayObserver.Exchange down = next();
        assertEquals("unreachable", down.routeId());
        assertEquals(GatewayObserver.Outcome.FAILURE, down.outcome());
        assertEquals(502, down.status());
        assertNotNull(down.failure());
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Recorder implements GatewayObserver {
        final LinkedBlockingQueue<Exchange> exchanges = new LinkedBlockingQueue<>();

        @Override
        public void exchanged(Exchange exchange) {
            exchanges.add(exchange);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Failing implements GatewayObserver {
        @Override
        public void exchanged(Exchange exchange) {
            throw new IllegalStateException("an observer that fails");
        }
    }
}
