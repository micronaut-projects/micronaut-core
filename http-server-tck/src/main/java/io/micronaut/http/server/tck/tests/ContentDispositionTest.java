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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.ContentDisposition;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class ContentDispositionTest {
    public static final String SPEC_NAME = "ContentDispositionTest";

    @Test
    void testAttachmentWithFilename() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/content-disposition/attachment"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.csv\"; filename*=utf-8''report.csv")
                .build()));
    }

    @Test
    void testInlineWithoutFilename() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/content-disposition/inline"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .build()));
    }

    @Test
    void testAttachmentAppliesToReactiveResponses() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/content-disposition/reactive"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"data.csv\"; filename*=utf-8''data.csv")
                .build()));
    }

    @Controller("/content-disposition")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ContentDispositionController {

        @Get(uri = "/attachment", processes = MediaType.TEXT_PLAIN)
        @ContentDisposition(filename = "report.csv")
        String attachment() {
            return "one,two,three";
        }

        @Get(uri = "/inline", processes = MediaType.TEXT_PLAIN)
        @ContentDisposition(type = "inline")
        String inline() {
            return "hello";
        }

        @Get(uri = "/reactive", processes = MediaType.TEXT_PLAIN)
        @ContentDisposition(filename = "data.csv")
        Flux<String> reactive() {
            return Flux.just("a", "b", "c");
        }
    }
}
