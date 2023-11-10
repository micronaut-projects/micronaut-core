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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MdcServiceSpec {

    @Test
    void testFilterSpec() {
        try (EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class, Map.of("mdc.example.service.enabled", true))) {
            try (HttpClient client = HttpClient.create(embeddedServer.getURL())) {
                HttpRequest<Object> request = HttpRequest
                    .GET("/mdc/test");
                String response = client.toBlocking().retrieve(request);
                assertTrue(response.startsWith("New user id: "));
                assertTrue(response.endsWith(" name: Denis"));
            }
        }
    }

    @Controller("/mdc")
    @Requires(property = "mdc.example.service.enabled")
    static class MDCController {

        private final MdcService mdcService;

        MDCController(MdcService mdcService) {
            this.mdcService = mdcService;
        }

        @Get(value = "/test", produces = MediaType.TEXT_PLAIN)
        String test() {
            return mdcService.createUser("Denis");
        }

    }
}
