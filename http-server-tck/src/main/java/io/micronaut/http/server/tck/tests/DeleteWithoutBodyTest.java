/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.http.HttpHeaderValues;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Status;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class DeleteWithoutBodyTest {
    public static final String SPEC_NAME = "DeleteWithoutBodyTest";

    @Test
    void verifiesItIsPossibleToExposesADeleteEndpointWhichIsInvokedWithoutABody() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.DELETE("/sessions/sergio").header(HttpHeaders.AUTHORIZATION, HttpHeaderValues.AUTHORIZATION_PREFIX_BEARER + " xxx"),
            (server, request) -> {
                HttpResponse<?> response = assertDoesNotThrow(() -> server.exchange(request));
                assertEquals(HttpStatus.OK, response.getStatus());
            });
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/sessions")
    static class SessionsController {
        @Status(HttpStatus.OK)
        @Delete("/{username}")
        void delete(@PathVariable String username,
                    @Header(HttpHeaders.AUTHORIZATION) String authorization) {
        }
    }
}
