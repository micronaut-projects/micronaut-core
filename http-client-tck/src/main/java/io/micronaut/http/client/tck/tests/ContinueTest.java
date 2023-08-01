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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.BodyAssertion;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Map;

import static io.micronaut.http.tck.ServerUnderTest.BLOCKING_CLIENT_PROPERTY;
import static io.micronaut.http.tck.TestScenario.asserts;

class ContinueTest {
    private static final String SPEC_NAME = "ContinueTest";

    @ParameterizedTest(name = "blocking={0}")
    @ValueSource(booleans = {true, false})
    public void testContinueFull(boolean blocking) throws IOException {
        asserts(SPEC_NAME,
            Map.of(BLOCKING_CLIENT_PROPERTY, blocking),
            HttpRequest.POST("/continue/plain", "1")
                .header(HttpHeaders.EXPECT, "100-continue"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body(BodyAssertion.builder().body("1").equals())
                    .build())
        );
    }

    @ParameterizedTest(name = "blocking={0}")
    @ValueSource(booleans = {true, false})
    public void testContinuePublisher(boolean blocking) throws IOException {
        asserts(SPEC_NAME,
            Map.of(BLOCKING_CLIENT_PROPERTY, blocking),
            HttpRequest.POST("/continue/plain", Flux.just("1", "2"))
                .header(HttpHeaders.EXPECT, "100-continue"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body(BodyAssertion.builder().body("[1,2]").equals())
                    .build())
        );
    }

    @Tag("multipart")
    @ParameterizedTest(name = "blocking={0}")
    @ValueSource(booleans = {true, false})
    public void testContinueMultipart(boolean blocking) throws IOException {
        asserts(SPEC_NAME,
            Map.of(BLOCKING_CLIENT_PROPERTY, blocking),
            HttpRequest.POST("/continue/part", MultipartBody.builder().addPart("foo", "bar").build())
                .header(HttpHeaders.EXPECT, "100-continue")
                .contentType(MediaType.MULTIPART_FORM_DATA),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body(BodyAssertion.builder().body("bar").equals())
                    .build())
        );
    }

    @ParameterizedTest(name = "blocking={0}")
    @ValueSource(booleans = {true, false})
    public void testContinueFormEncoded(boolean blocking) throws IOException {
        asserts(SPEC_NAME,
            Map.of(BLOCKING_CLIENT_PROPERTY, blocking),
            HttpRequest.POST("/continue/part", Map.of("foo", "bar"))
                .header(HttpHeaders.EXPECT, "100-continue")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body(BodyAssertion.builder().body("bar").equals())
                    .build())
        );
    }

    @Controller("/continue")
    @Requires(property = "spec.name", value = SPEC_NAME)
    public static class SimpleController {
        @Post("/plain")
        @ExecuteOn(TaskExecutors.BLOCKING)
        public String plain(@Body Publisher<String> data) {
            return String.join("", Flux.from(data).collectList().block());
        }

        @Post("/part")
        @Consumes({MediaType.MULTIPART_FORM_DATA, MediaType.APPLICATION_FORM_URLENCODED})
        public String part(@Part String foo) {
            return foo;
        }
    }
}
