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
package io.micronaut.http.server.tck.tests.forms;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.TestScenario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({
        "java:S5960", // We're allowed assertions, as these are used in tests only
        "checkstyle:MissingJavadocType",
        "checkstyle:DesignForExtension"
})
public class FormsSubmissionsWithListsTest {
    private static final String SPEC_NAME = "FormsSubmissionsWithListsTest";
    @Test
    public void formWithListOfOneItem() throws IOException {
        String body = "question=en+que+trabajas&usersId=1";
        String expectedJson = "{\"question\":\"en que trabajas\",\"usersId\":[1]}";
        assertWithBody(body, expectedJson);
    }

    @Test
    public void formWithListOfMoreThanOne() throws IOException {
        String body = "question=en+que+trabajas&usersId=1&usersId=2";
        String expectedJson = "{\"question\":\"en que trabajas\",\"usersId\":[1,2]}";
        assertWithBody(body, expectedJson);
    }

    private static void assertWithBody(String body, String expectedJson) throws IOException {
        TestScenario.builder()
                .specName(SPEC_NAME)
                .request(HttpRequest.POST("/questions/save", body).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))
                .assertion((server, request) ->
                        AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                                .status(HttpStatus.OK)
                                .assertResponse(httpResponse -> {
                                    Optional<String> bodyOptional = httpResponse.getBody(String.class);
                                    assertTrue(bodyOptional.isPresent());
                                    assertEquals(expectedJson, bodyOptional.get());
                                })
                                .build()))
                .run();
    }

    @Controller("/questions")
    @Requires(property = "spec.name", value = SPEC_NAME)
    public static class QuestionController {
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Post("/save")
        QuestionSave save(@Body QuestionSave questionSave) {
            return questionSave;
        }
    }

    @Introspected
    public record QuestionSave(String question,
                               List<Long> usersId) {
    }

}
