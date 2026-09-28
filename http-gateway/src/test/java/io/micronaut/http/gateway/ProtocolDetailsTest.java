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

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Protocol details of gateway routes: the QUERY method (RT-26), and the Date of a response
 * whose upstream sent none (HH-11).
 */
class ProtocolDetailsTest extends GatewayTestBase {

    static final String SPEC = "ProtocolDetailsTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        return Map.of("micronaut.server.idle-timeout", "5s");
    }

    @Test
    void rt26TheQueryMethodIsRelayedWithItsBody() throws IOException {
        RawHttp.Response response = send("QUERY", "/inspect/x", "select 1", "Content-Type: text/plain");
        assertEquals(200, response.status(), response.toString());
        assertEquals("QUERY", response.inspected("method"));
        assertEquals("select 1", response.inspected("body"));
    }

    @Test
    void hh11TheGatewayAddsADateWhenTheUpstreamSentNone() throws IOException {
        scripted.on("/nodate", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok");
            return true;
        });
        RawHttp.Response response = get("/raw/nodate");
        assertEquals(200, response.status(), response.toString());
        assertNotNull(response.header("Date"), response.toString());
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String upstream;
        private final String scripted;

        Routes(@Value("${test.upstream}") String upstream, @Value("${test.scripted}") String scripted) {
            this.upstream = upstream;
            this.scripted = scripted;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            gateway.route("inspect").path("/inspect/**").uri(upstream);
            gateway.route("raw").path("/raw/**").filters(GatewayFilters.stripPrefix(1)).uri(scripted);
        }
    }
}
