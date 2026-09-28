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

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A body filter of an application, written against the public {@link GatewayBodyFilter}.
 */
class CustomBodyFilterTest extends GatewayTestBase {

    static final String SPEC = "CustomBodyFilterTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Test
    void anApplicationBodyFilterRewritesTheResponse() throws Exception {
        RawHttp.Response response = get("/shout/up/text");
        assertEquals(200, response.status(), response.toString());
        assertEquals(response.text().toUpperCase(Locale.ROOT), response.text());
        assertEquals(String.valueOf(response.body().length), response.header("Content-Length"));
    }

    static final class Shout implements GatewayBodyFilter {
        @Override
        public Target target() {
            return Target.RESPONSE;
        }

        @Override
        public long maxBytes() {
            return 64 * 1024;
        }

        @Override
        public BodyRewriter rewriter() {
            return BodyRewriter.of(body -> new String(body, StandardCharsets.UTF_8).toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        }
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
            gateway.route("shout").path("/shout/**").filters(GatewayFilters.stripPrefix(1), new Shout()).uri(upstream);
        }
    }
}
