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

/**
 * DR-01, DR-02, DR-03, DR-13: routes of the YAML configuration, with the shortcut and the
 * name and args syntax, relay like the equivalent routes of the Java DSL.
 */
class YamlRoutesTest extends GatewayTestBase {

    static final String SPEC = "YamlRoutesTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        return Map.of("test.environments", "gatewayyaml");
    }

    @Test
    void yamlRouteIsEquivalentToTheDslRoute() throws IOException {
        RawHttp.Response yaml = get("/yaml/inspect/items?q=1", "X-Yaml: yes");
        RawHttp.Response dsl = get("/dsl/inspect/items?q=1", "X-Yaml: yes");
        assertEquals(200, yaml.status(), yaml.toString());
        assertEquals(200, dsl.status(), dsl.toString());
        for (String key : new String[]{"method", "path", "query", "h:x-added"}) {
            assertEquals(dsl.inspected(key), yaml.inspected(key), key);
        }
        assertEquals("/inspect/items", yaml.inspected("path"));
        assertEquals("q=1&extra=a%20b", yaml.inspected("query"));
        assertEquals("from-yaml", yaml.inspected("h:x-added"));
        assertEquals("yaml-inspect", yaml.header("X-Route"));
        // DR-02: the default filters apply to the routes of the DSL and of the configuration
        assertEquals("on", yaml.header("X-Gateway-Default"));
        assertEquals("on", dsl.header("X-Gateway-Default"));
    }

    @Test
    void aPredicateOfTheNameAndArgsSyntaxApplies() throws IOException {
        assertEquals(404, get("/yaml/inspect/items", "X-Yaml: no").status());
        assertEquals(404, get("/yaml/inspect/items").status());
    }

    @Test
    void theShortcutMethodPredicateAndRewritePathApply() throws IOException {
        RawHttp.Response response = send("POST", "/yaml/methods/a/b", "body", "Content-Type: text/plain");
        assertEquals(200, response.status(), response.toString());
        assertEquals("/inspect/a/b", response.inspected("path"));
        assertEquals(404, send("DELETE", "/yaml/methods/a/b", null).status());
    }

    @Test
    void theRetryFilterBindsItsArguments() throws IOException {
        RawHttp.Response response = get("/yaml/flaky?key=yaml&failures=2");
        assertEquals(200, response.status(), response.toString());
        assertEquals("attempt 3 0:0", response.text());
    }

    @Test
    void aDisabledRouteIsNotRegistered() throws IOException {
        assertEquals(404, get("/yaml/disabled/x").status());
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
            gateway.route("dsl-inspect")
                .path("/dsl/inspect/**")
                .where(io.micronaut.web.router.builder.RequestCondition.header("X-Yaml", io.micronaut.web.router.builder.ValueMatcher.regex("y.+")))
                .order(5)
                .filters(GatewayFilters.stripPrefix(1),
                    GatewayFilters.addRequestHeader("X-Added", "from-yaml"),
                    GatewayFilters.addRequestParameter("extra", "a b"),
                    GatewayFilters.setResponseHeader("X-Route", "dsl-inspect"))
                .uri(upstream);
        }
    }
}
