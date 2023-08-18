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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.*;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.micronaut.http.tck.TestScenario.asserts;

public class LocalErrorReadingBodyTest {
    public static final String SPEC_NAME = "LocalErrorReadingBodyTest";

    @Test
    void jsonSyntaxErrorBodyAccessible() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/json/jsonBody", "{\"numberField\": \"textInsteadOf"),
            (server, request) -> AssertionUtils.assertThrows(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Syntax error: {\"numberField\": \"textInsteadOf")
                    .build()
            ));
    }


    @Introspected
    static class RequestObject {
        @Min(1L)
        private Integer numberField;

        public RequestObject(Integer numberField) {
            this.numberField = numberField;
        }

        public Integer getNumberField() {
            return numberField;
        }
    }

    @Controller("/json")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class JsonController {
        @Post("/jsonBody")
        String jsonBody(@Valid @Body RequestObject data) {
            return "blah";
        }

        @Error
        @Produces(MediaType.APPLICATION_JSON) // it's a lie!
        @Status(HttpStatus.BAD_REQUEST)
        String syntaxErrorHandler(@Body @Nullable String body) {
            return "Syntax error: " + body;
        }
    }
}
