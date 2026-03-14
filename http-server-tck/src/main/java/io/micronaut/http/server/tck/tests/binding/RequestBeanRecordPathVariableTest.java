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
package io.micronaut.http.server.tck.tests.binding;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestBean;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class RequestBeanRecordPathVariableTest {
    public static final String SPEC_NAME = "RequestBeanRecordPathVariableTest";

    @Test
    void bindsPathVariableInsideRecordRequestBean() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/requestbean/record/1", ""),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("1")
                .build()));
    }

    @Controller("/requestbean/record")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RequestBeanRecordController {

        @Post("/{id}")
        String handle(@Valid @RequestBean RequestBeanRecordRequest request) {
            return Integer.toString(request.id());
        }
    }

    @Introspected
    record RequestBeanRecordRequest(HttpRequest<?> httpRequest, @PathVariable @Positive Integer id) {
    }
}
