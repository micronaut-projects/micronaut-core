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
package io.micronaut.http.server.netty.multipart;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamingFileUploadWithoutFormBodyTest {

    @ParameterizedTest
    @ValueSource(strings = {"/no-form-body/completed", "/no-form-body/streaming"})
    void aRequestWithoutAFormBodyIsABadRequest(String path) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "StreamingFileUploadWithoutFormBodyTest"));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start()) {
            // no body and no content type
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(server.getURL() + path)).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(400, response.statusCode(), response.body());
        }
    }

    @Controller("/no-form-body")
    @Requires(property = "spec.name", value = "StreamingFileUploadWithoutFormBodyTest")
    static class NoFormBodyController {
        @Post(value = "/completed", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String completed(CompletedFileUpload file) {
            return "ok";
        }

        @Post(value = "/streaming", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        Mono<String> streaming(StreamingFileUpload file) {
            file.close();
            return Mono.just("ok");
        }
    }
}
