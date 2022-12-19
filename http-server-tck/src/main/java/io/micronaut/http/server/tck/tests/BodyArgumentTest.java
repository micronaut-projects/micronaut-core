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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import io.micronaut.http.server.tck.TestScenario;
import org.junit.jupiter.api.Test;
import java.io.IOException;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public interface BodyArgumentTest {
    /**
     * @see <a href="https://github.com/micronaut-projects/micronaut-aws/issues/1164">micronaut-aws #1164</a>
     */
    @Test
    default void testBodyArguments() throws IOException {
        TestScenario.builder()
            .specName("BodyArgumentTest")
            .request(HttpRequest.POST("/body-arguments-test/getA", "{\"a\":\"A\",\"b\":\"B\"}").header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN))
            .assertion(((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("A")
                .build())))
            .run();
    }

    @Controller("/body-arguments-test")
    @Requires(property = "spec.name", value = "BodyArgumentTest")
    static class BodyController {

        @Post(uri = "/getA")
        @Produces(MediaType.TEXT_PLAIN)
        String getA(String a) {
            return a;
        }
    }
}
