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
package io.micronaut.http.client.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static io.micronaut.http.tck.TestScenario.asserts;

class ExceptionOnErrorStatusTest {

    private static final String SPEC_NAME = "ExceptionOnErrorStatusTest";

    @Test
    void exceptionOnErrorStatus() throws IOException {
        asserts(SPEC_NAME,
            Collections.singletonMap("micronaut.http.client.exception-on-error-status", StringUtils.FALSE),
            HttpRequest.GET("/unprocessable"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body("{\"message\":\"Cannot make it\"}")
                .build()));
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/unprocessable")
    @SuppressWarnings("checkstyle:MissingJavadocType")
    static class RedirectTestController {

        @Get
        HttpResponse<?> index() {
            return HttpResponse.unprocessableEntity().body("{\"message\":\"Cannot make it\"}");
        }
    }
}
