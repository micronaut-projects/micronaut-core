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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The {@code Version} predicate: exact and baseline versions, from the default header, another
 * header or a query parameter.
 */
class VersionPredicateTest extends GatewayTestBase {

    @Override
    String specName() {
        return "VersionPredicateTest";
    }

    @Override
    Map<String, Object> gatewayProperties() {
        Map<String, Object> p = new HashMap<>();
        String r = "micronaut.gateway.routes.";
        p.put(r + "v1.uri", "${test.upstream}");
        p.put(r + "v1.predicates", List.of("Path=/ver/**", "Version=1.0"));
        p.put(r + "v1.filters", List.of("SetPath=/inspect/v1"));
        p.put(r + "v2.uri", "${test.upstream}");
        p.put(r + "v2.predicates", List.of("Path=/ver/**", "Version=2.0+"));
        p.put(r + "v2.filters", List.of("SetPath=/inspect/v2"));
        p.put(r + "v2.order", 1);
        p.put(r + "param.uri", "${test.upstream}");
        p.put(r + "param.predicates", List.of("Path=/verp/**", "Version=2.0+, parameter=v"));
        p.put(r + "param.filters", List.of("SetPath=/inspect/param"));
        p.put(r + "header.uri", "${test.upstream}");
        p.put(r + "header.predicates", List.of("Path=/verh/**", "Version=1.0, header=X-Version"));
        p.put(r + "header.filters", List.of("SetPath=/inspect/header"));
        p.put(r + "none.uri", "${test.upstream}");
        p.put(r + "none.predicates", List.of("Path=/ver/**,/verp/**,/verh/**"));
        p.put(r + "none.filters", List.of("SetPath=/inspect/none"));
        p.put(r + "none.order", 2);
        return p;
    }

    @Test
    void defaultHeader() throws IOException {
        assertEquals("/inspect/v1", get("/ver/a", "X-API-VERSION: 1.0").inspected("path"));
        assertEquals("/inspect/v2", get("/ver/a", "X-API-VERSION: 2.0").inspected("path"));
        assertEquals("/inspect/v2", get("/ver/a", "X-API-VERSION: 2.10").inspected("path"));
        assertEquals("/inspect/v2", get("/ver/a", "X-API-VERSION: 3").inspected("path"));
        assertEquals("/inspect/none", get("/ver/a", "X-API-VERSION: 1.9").inspected("path"));
        assertEquals("/inspect/none", get("/ver/a", "X-API-VERSION: two").inspected("path"));
        assertEquals("/inspect/none", get("/ver/a").inspected("path"));
    }

    @Test
    void parameterAndHeader() throws IOException {
        assertEquals("/inspect/param", get("/verp/a?v=2.1").inspected("path"));
        assertEquals("/inspect/none", get("/verp/a?v=1.1").inspected("path"));
        assertEquals("/inspect/header", get("/verh/a", "X-Version: 1.0").inspected("path"));
        assertEquals("/inspect/none", get("/verh/a", "X-API-VERSION: 1.0").inspected("path"));
    }

    @Test
    void invalidBaseline() {
        Map<String, Object> p = new HashMap<>();
        p.put("micronaut.gateway.routes.bad.uri", "${test.upstream}");
        p.put("micronaut.gateway.routes.bad.predicates", List.of("Path=/bad/**", "Version=1.x+"));
        assertThrows(Exception.class, () -> startGateway(specName(), upstream, p).getApplicationContext().close());
    }

    @Test
    void invalidSource() {
        Map<String, Object> p = new HashMap<>();
        p.put("micronaut.gateway.routes.bad.uri", "${test.upstream}");
        p.put("micronaut.gateway.routes.bad.predicates", List.of("Path=/bad/**", "Version=1.0, cookie=v"));
        assertThrows(Exception.class, () -> startGateway(specName(), upstream, p).getApplicationContext().close());
    }
}
