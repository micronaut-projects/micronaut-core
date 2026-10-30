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
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A bulkhead counts a request in flight until its response is done, not until its headers.
 */
class BulkheadStreamingTest extends GatewayTestBase {

    static final String SPEC = "BulkheadStreamingTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Test
    void aStreamingResponseHoldsItsBulkheadPermitUntilItsBodyEnds() throws Exception {
        CountDownLatch end = new CountDownLatch(1);
        scripted.on("/stream", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nfirst\r\n");
            end.await(10, TimeUnit.SECONDS);
            ScriptedUpstream.write(out, "4\r\nlast\r\n0\r\n\r\n");
            return true;
        });
        scripted.on("/quick", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok");
            return true;
        });
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("GET /bh/stream HTTP/1.1\r\nHost: localhost\r\nX-User: s\r\n\r\n");
            // the headers and the first chunk arrived: the body still streams
            readUntil(http, "first");
            assertEquals(429, get("/bh/quick", "X-User: s").status(), "the stream is in flight");
            end.countDown();
            readUntil(http, "0\r\n\r\n");
        }
        long deadline = System.currentTimeMillis() + 5000;
        int status;
        do {
            status = get("/bh/quick", "X-User: s").status();
        } while (status != 200 && System.currentTimeMillis() < deadline);
        assertEquals(200, status, "the stream ended");
    }

    private static void readUntil(RawHttp http, String marker) throws java.io.IOException {
        StringBuilder received = new StringBuilder();
        while (!received.toString().contains(marker)) {
            int c = http.in.read();
            if (c < 0) {
                throw new java.io.EOFException(received.toString());
            }
            received.append((char) c);
        }
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
            gateway.route("bh").path("/bh/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.bulkhead(1, KeyResolver.header("X-User")))
                .uri(scripted);
        }
    }
}
