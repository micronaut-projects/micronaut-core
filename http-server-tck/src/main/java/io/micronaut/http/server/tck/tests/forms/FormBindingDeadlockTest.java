/*
 * Copyright 2017-2025 original authors
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
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.TestScenario;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class FormBindingDeadlockTest {
    private static final String SPEC_NAME = "FormBindingDeadlockTest";
    private static final String DID_NOT_FAIL = "did not fail";
    private static final String LARGE = "_".repeat(1024 * 1024);
    private static final String HALF_ASYNC_FAIL_MESSAGE = "Argument [RawFormField asyncPart] not satisfied: This argument won't consume posted data until you subscribe to it in the controller. This prevents the following arguments from being bound:\n  [String syncPart] has not yet been received.";

    private static void test(String uri, String body, String expectedResponse) throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.POST(uri, body).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))
            .assertion((server, request) ->
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(httpResponse -> {
                        Optional<String> bodyOptional = httpResponse.getBody(String.class);
                        assertTrue(bodyOptional.isPresent());
                        assertEquals(expectedResponse, bodyOptional.get());
                    })
                    .build()))
            .run();
    }

    @Test
    public void fullAsyncFail() throws IOException {
        test("/deadlock/full-async", "asyncPart=foo&syncPart=bar", """
            Argument [Publisher<PartData T> asyncPart] not satisfied: This argument won't consume posted data until you subscribe to it in the controller. This prevents the following arguments from being bound:
              [String syncPart] has not yet been received.""");
    }

    @Test
    public void fullAsyncSuccess() throws IOException {
        // succeeds because syncPart is fulfilled before asyncPart
        test("/deadlock/full-async", "syncPart=bar&asyncPart=foo", DID_NOT_FAIL);
    }

    @Test
    public void halfAsyncFail() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.POST("/deadlock/half-async", "asyncPart=foo" + LARGE + "&syncPart=bar").contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))
            .assertion((server, request) ->
                AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .assertResponse(httpResponse -> {
                        Optional<String> bodyOptional = httpResponse.getBody(String.class);
                        assertTrue(bodyOptional.isPresent());
                        String normalizedBody = bodyOptional.get().replace("\\n", "").replace("\n", "");
                        String normalizedMessage = HALF_ASYNC_FAIL_MESSAGE.replace("\n", "");
                        assertTrue(normalizedBody.contains(normalizedMessage), "error response should contain the deadlock message");
                    })
                    .build()))
            .run();
    }

    @Test
    public void halfAsyncSuccessOrder() throws IOException {
        // succeeds because syncPart is fulfilled before asyncPart
        test("/deadlock/half-async", "syncPart=bar&asyncPart=foo", DID_NOT_FAIL);
    }

    @Test
    public void halfAsyncSuccessShort() throws IOException {
        // succeeds because asyncPart is short enough that parsing happens one-shot
        test("/deadlock/half-async", "asyncPart=foo&syncPart=bar", DID_NOT_FAIL);
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/deadlock")
    static class MyController {
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Post("/full-async")
        Publisher<String> fullAsync(@Part("asyncPart") Publisher<PartData> asyncPart, @Part("syncPart") String syncPart) {
            return Mono.from(asyncPart)
                .doOnNext(PartData::close)
                .thenReturn(DID_NOT_FAIL)
                .onErrorResume(t -> Mono.just(t.getMessage()));
        }

        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Post("/half-async")
        Publisher<String> halfAsync(RawFormField asyncPart, @Part("syncPart") String syncPart) {
            return Mono.from(asyncPart.byteBody().toReadBufferPublisher())
                .doOnNext(ReadBuffer::close)
                .thenReturn(DID_NOT_FAIL)
                .onErrorResume(t -> Mono.just(t.getMessage()));
        }
    }
}
