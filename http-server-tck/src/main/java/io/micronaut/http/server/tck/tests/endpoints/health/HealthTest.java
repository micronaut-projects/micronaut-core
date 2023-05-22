/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.server.tck.tests.endpoints.health;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.micronaut.http.tck.TestScenario.asserts;


@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HealthTest {
    private static final String SPEC_NAME = "HealthTest";

    /**
     * This test verifies health endpoint is exposed. The server under test needs to publish the {@link io.micronaut.discovery.event.ServiceReadyEvent} or {@link io.micronaut.runtime.server.event.ServerStartupEvent} for {@link io.micronaut.management.health.indicator.service.ServiceReadyHealthIndicator} to be UP.
     * @throws IOException Exception thrown while getting the server under test.
     */
    @Test
    public void healthEndpointExposed() throws IOException {
        // standard header name with mixed case
        asserts(SPEC_NAME,
            HttpRequest.GET("/health"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"status\":\"UP\"}")
                .build()));
    }
}
