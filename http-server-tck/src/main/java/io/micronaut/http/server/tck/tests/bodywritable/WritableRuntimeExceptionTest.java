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
package io.micronaut.http.server.tck.tests.bodywritable;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.Writable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960",
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class WritableRuntimeExceptionTest {
    public static final String SPEC_NAME = "WritableRuntimeExceptionTest";

    @Test
    void writableRuntimeExceptionReturns500() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/writable-runtime-exception"),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .build()));
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static final class WritableRuntimeExceptionController {
        @Get("/writable-runtime-exception")
        Writable index() {
            return out -> {
                throw new RuntimeException("Baaaaad!");
            };
        }
    }
}
