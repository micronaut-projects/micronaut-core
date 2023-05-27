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
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.micronaut.http.server.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class MissingBodyAnnotationTest {

    public static final String SPEC_NAME = "MissingBodyAnnotationTest";

    /**
     * Test that we can use a body argument without the @Body annotation. for 3.x.x.
     * This will not work in 4.x.x as the @Body annotation is required.
     *
     * @throws IOException
     * @see <a href="https://github.com/micronaut-projects/micronaut-core/blob/37874c634202233f35b7c9376a5edfd5d49861f2/src/main/docs/guide/appendix/breaks.adoc#body-annotation-on-controller-parameters">the breaking changes</a>
     */
    @Test
    void testBodyAnnotationMissing() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/missing-body-annotation-test/absent", new Dto("tim")),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("tim")
                .build()));
    }

    @Test
    void testBodyAnnotationPresent() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/missing-body-annotation-test/present", new Dto("tim")),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("tim")
                .build()));
    }

    @Controller("/missing-body-annotation-test")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BodyController {

        @Post("/absent")
        String absent(Dto dto) {
            return dto.getValue();
        }

        @Post("/present")
        String present(@Body Dto dto) {
            return dto.getValue();
        }
    }

    @Introspected
    static class Dto {

        private final String value;

        public Dto(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
