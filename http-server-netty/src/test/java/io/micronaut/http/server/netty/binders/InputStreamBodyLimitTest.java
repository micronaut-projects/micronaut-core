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
package io.micronaut.http.server.netty.binders;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InputStreamBodyLimitTest {
    private static final int MAX_REQUEST_SIZE = 1024 * 1024;

    @ParameterizedTest
    @ValueSource(strings = {"/input-stream-limit/bytes", "/input-stream-limit/input-stream"})
    void aBodyOverTheLimitIsAnsweredWith413(String path) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "InputStreamBodyLimitTest",
            "micronaut.server.max-request-size", MAX_REQUEST_SIZE
        ));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start()) {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(server.getURL() + path))
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[MAX_REQUEST_SIZE + 200_000])).build(),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(413, response.statusCode(), response.body());
        }
    }

    @Test
    void aTruncatedMultipartBodyReadAsAStreamIsABadRequest() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "InputStreamBodyLimitTest"));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start()) {
            // no closing boundary
            String body = "--bb\r\nContent-Disposition: form-data; name=\"file\"; filename=\"f.bin\"\r\n\r\n" + "x".repeat(300_000);
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(server.getURL() + "/input-stream-limit/streaming-upload"))
                    .header("Content-Type", "multipart/form-data; boundary=bb")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(400, response.statusCode(), response.body());
        }
    }

    @Controller("/input-stream-limit")
    @Requires(property = "spec.name", value = "InputStreamBodyLimitTest")
    static class InputStreamLimitController {
        @Post(value = "/bytes", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        String bytes(@Body byte[] body) {
            return Integer.toString(body.length);
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Post(value = "/input-stream", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        String inputStream(@Body InputStream body) throws IOException {
            return Integer.toString(body.readAllBytes().length);
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Post(value = "/streaming-upload", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String streamingUpload(StreamingFileUpload file) throws IOException {
            try (InputStream in = file.asInputStream()) {
                return Integer.toString(in.readAllBytes().length);
            }
        }
    }
}
