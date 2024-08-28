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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.BodyAssertion;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.TestScenario;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class RootRoutingTest {
    public static final String SPEC_NAME = "RootRoutingTest";

    @Test
    void testRouting() throws IOException {
        TestScenario.asserts(SPEC_NAME,
            HttpRequest.GET("/"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(BodyAssertion.builder().body("""
                    {"key":"hello","value":"world"}""").equals())
                .build()));
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller
    @ExecuteOn(TaskExecutors.BLOCKING)
    static class MyController {

        @Post
        public KeyValue createRoot(@Body KeyValue body) {
            return body;
        }

        @Get
        public KeyValue root() {
            return new KeyValue("hello", "world");
        }

        @Get("/{id}")
        public KeyValue id(String id) {
            return new KeyValue("hello", id);
        }

        @Get("/{id}/items")
        public List<KeyValue> items(String id) {
            return List.of(new KeyValue("hello", id), new KeyValue("foo", "bar"));
        }
    }

    @Introspected
    private record KeyValue(String key, String value) {
    }

}
