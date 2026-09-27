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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * FW-11: the forwarded headers switched off. The client's forwarded headers are still not
 * relayed: an untrusted client never sets them.
 */
class ForwardedDisabledTest extends GatewayTestBase {

    @Override
    String specName() {
        return "ForwardedDisabledTest";
    }

    @Override
    Map<String, Object> gatewayProperties() {
        return Map.of(
            "micronaut.gateway.forwarded.x-forwarded-headers", false,
            "micronaut.gateway.forwarded.forwarded-header", false,
            "micronaut.gateway.routes.inspect.uri", "${test.upstream}",
            "micronaut.gateway.routes.inspect.predicates", List.of("Path=/api/inspect/**"),
            "micronaut.gateway.routes.inspect.filters", List.of("StripPrefix=1"));
    }

    @Test
    void fw11NoForwardedHeaderIsSentAndStripPrefixSendsNoPrefix() throws IOException {
        RawHttp.Response response = get("/api/inspect/x", "X-Forwarded-For: 6.6.6.6", "Forwarded: for=6.6.6.6");
        assertEquals(200, response.status(), response.toString());
        for (String name : List.of("x-forwarded-for", "x-forwarded-proto", "x-forwarded-host", "x-forwarded-port", "x-forwarded-prefix", "forwarded")) {
            assertNull(response.inspected("h:" + name), name + " " + response);
        }
    }
}
