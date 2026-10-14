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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.TestScenario;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/**
 * Same-name uploads must be emitted in the order they arrived, even when a large part in the
 * middle takes longer to complete than the small parts around it.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class UploadArrivalOrderTest {
    private static final String SPEC_NAME = "UploadArrivalOrderTest";
    private static final int ITERATIONS = 30;
    private static final String EXPECTED = "a.txt,b.txt,c.txt";

    @Test
    public void publisherOfCompletedFileUploadKeepsArrivalOrder() throws IOException {
        assertArrivalOrder("/upload-order/publisher");
    }

    @Test
    public void multipartBodyKeepsArrivalOrder() throws IOException {
        assertArrivalOrder("/upload-order/multipart-body");
    }

    private static void assertArrivalOrder(String path) throws IOException {
        byte[] large = new byte[1024 * 1024];
        Arrays.fill(large, (byte) 'x');
        TestScenario.builder()
            .specName(SPEC_NAME)
            // write file uploads past the threshold to disk, so the large part completes later than the small ones
            .configuration(Map.of("micronaut.server.multipart.mixed", true, "micronaut.server.multipart.threshold", 1024))
            .request(request(path, large))
            .assertion((server, request) -> {
                for (int i = 0; i < ITERATIONS; i++) {
                    AssertionUtils.assertDoesNotThrow(server, request(path, large), HttpResponseAssertion.builder()
                        .status(HttpStatus.OK)
                        .body(EXPECTED)
                        .build());
                }
            })
            .run();
    }

    private static HttpRequest<MultipartBody> request(String path, byte[] large) {
        MultipartBody body = MultipartBody.builder()
            .addPart("file", "a.txt", MediaType.TEXT_PLAIN_TYPE, "a".getBytes())
            .addPart("file", "b.txt", MediaType.TEXT_PLAIN_TYPE, large)
            .addPart("file", "c.txt", MediaType.TEXT_PLAIN_TYPE, "c".getBytes())
            .build();
        return HttpRequest.POST(path, body).contentType(MediaType.MULTIPART_FORM_DATA);
    }

    private static String partName(CompletedPart part) {
        return part instanceof CompletedFileUpload upload ? upload.getFilename() : part.getName();
    }

    @Controller("/upload-order")
    @Requires(property = "spec.name", value = SPEC_NAME)
    public static class UploadOrderController {
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        @Post(value = "/publisher", produces = MediaType.TEXT_PLAIN)
        Mono<String> publisher(Publisher<CompletedFileUpload> file) {
            return Flux.from(file)
                .map(UploadArrivalOrderTest::partName)
                .collectList()
                .map(names -> String.join(",", names));
        }

        @Consumes(MediaType.MULTIPART_FORM_DATA)
        @Post(value = "/multipart-body", produces = MediaType.TEXT_PLAIN)
        Mono<String> multipartBody(@Body io.micronaut.http.server.multipart.MultipartBody body) {
            return Flux.from(body)
                .map(UploadArrivalOrderTest::partName)
                .collectList()
                .map(names -> String.join(",", names));
        }
    }
}
