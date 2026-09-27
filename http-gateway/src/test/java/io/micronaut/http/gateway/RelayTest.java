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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Relaying requests and responses: methods, paths, queries and bodies.
 */
class RelayTest extends GatewayTestBase {

    static final String SPEC = "RelayTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Test
    void getIsRelayedWithThePathAndQueryByteForByte() throws IOException {
        RawHttp.Response response = get("/inspect/a%20b/c+d/%E2%80%93?q=a+b&p=%2B%20%26&&x=%2F&empty=");
        assertEquals(200, response.status(), response.toString());
        assertEquals("GET", response.inspected("method"));
        assertEquals("/inspect/a%20b/c+d/%E2%80%93", response.inspected("path"));
        assertEquals("q=a+b&p=%2B%20%26&&x=%2F&empty=", response.inspected("query"));
    }

    @Test
    void postBodyIsRelayedByteForByte() throws IOException {
        String json = "{ \"a\" :  1 ,\n \"b\":[1, 2]\t}";
        RawHttp.Response response = send("POST", "/inspect/items", json, "Content-Type: application/json");
        assertEquals(200, response.status(), response.toString());
        assertEquals("POST", response.inspected("method"));
        assertEquals(json.replace("\n", "\\n"), response.inspected("body"));
        assertTrue(response.inspected("h:content-length").equals(String.valueOf(json.length())), response.toString());
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String upstream;

        Routes(@Value("${test.upstream}") String upstream) {
            this.upstream = upstream;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            gateway.route("inspect").path("/inspect/**").uri(upstream);
            gateway.route("up").path("/gw/up/**").filters(GatewayFilters.stripPrefix(1)).uri(upstream);
        }
    }
}
