/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.propagation;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MdcLegacyFilterSpec {

    @Test
    void testFilterSpec() {
        try (EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class, Map.of("mdc.example.legacy.filter.enabled", true))) {
            try (HttpClient client = HttpClient.create(embeddedServer.getURL())) {

                String tracingId = UUID.randomUUID().toString();
                HttpRequest<Object> request = HttpRequest
                    .GET("/mdc/test")
                    .header("X-TrackingId", tracingId);
                assertEquals(client.toBlocking().retrieve(request), tracingId);
            }
        }
    }

    @Controller("/mdc")
    @Requires(property = "mdc.example.legacy.filter.enabled")
    static class MDCController {

        @Get(value = "/test", produces = MediaType.TEXT_PLAIN)
        String test() {
            return MDC.get("trackingId");
        }

    }
}
