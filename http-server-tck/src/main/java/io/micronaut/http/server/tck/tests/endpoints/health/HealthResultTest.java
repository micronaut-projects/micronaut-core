/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.context.annotation.Requires;
import io.micronaut.health.HealthStatus;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.server.tck.tests.forms.FormsSubmissionsWithListsTest;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.json.JsonMapper;
import io.micronaut.management.health.indicator.HealthResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
        "java:S5960", // We're allowed assertions, as these are used in tests only
        "checkstyle:MissingJavadocType",
        "checkstyle:DesignForExtension"
})
public class HealthResultTest {
    private static final String SPEC_NAME = "HealthResultTest";

    /**
     * This test verifies the available JSON Mapper is able to serialize a {@link HealthResult}.
     * @throws IOException Exception thrown while getting the server under test.
     */
    @Test
    public void healthResultSerialization() throws IOException {
        // standard header name with mixed case
        asserts(SPEC_NAME,
                HttpRequest.GET("/healthresult"),
                (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                        .status(HttpStatus.OK)
                        .body("{\"name\":\"db\",\"status\":\"DOWN\",\"details\":{\"foo\":\"bar\"}}")
                        .build()));
    }

    @Controller("/healthresult")
    @Requires(property = "spec.name", value = SPEC_NAME)
    public static class QuestionController {
        private final JsonMapper jsonMapper;

        public QuestionController(JsonMapper jsonMapper) {
            this.jsonMapper = jsonMapper;
        }

        @Get
        String index() throws IOException {
            return jsonMapper.writeValueAsString(HealthResult.builder("db", HealthStatus.DOWN)
                    .details(Collections.singletonMap("foo", "bar"))
                    .build());
        }
    }
}
