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
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.TestScenario;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class UploadTest {
    private static final String SPEC_NAME = "UploadTest";

    @Test
    public void inputStream() throws Exception {
        Path tmp = Files.createTempFile("UploadTest", ".txt");
        try {
            Files.writeString(tmp, "foo");
            TestScenario.builder()
                .specName(SPEC_NAME)
                .request(HttpRequest.POST("/upload/input-stream", MultipartBody.builder().addPart("file", tmp.toFile()).build()).contentType(MediaType.MULTIPART_FORM_DATA))
                .assertion((server, request) ->
                    AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                        .status(HttpStatus.OK)
                        .assertResponse(httpResponse -> {
                            Optional<String> bodyOptional = httpResponse.getBody(String.class);
                            assertTrue(bodyOptional.isPresent());
                            assertEquals("foo", bodyOptional.get());
                        })
                        .build()))
                .run();
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Controller("/upload")
    @Requires(property = "spec.name", value = SPEC_NAME)
    public static class UploadController {
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        @Post("/input-stream")
        @ExecuteOn(TaskExecutors.BLOCKING)
        byte[] inputStream(StreamingFileUpload file) throws Exception {
            try (InputStream stream = file.asInputStream()) {
                return stream.readAllBytes();
            }
        }
    }
}
