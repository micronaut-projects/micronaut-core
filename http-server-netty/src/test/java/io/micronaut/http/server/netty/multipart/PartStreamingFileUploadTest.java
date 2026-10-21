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
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartStreamingFileUploadTest {

    @ParameterizedTest
    @ValueSource(strings = {"/part-streaming/unannotated", "/part-streaming/named", "/part-streaming/unnamed"})
    void partAnnotatedStreamingFileUploadIsBound(String path) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "PartStreamingFileUploadTest"));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             BlockingHttpClient client = ctx.createBean(HttpClient.class, server.getURI()).toBlocking()) {
            String body = client.retrieve(HttpRequest.POST(path, MultipartBody.builder()
                .addPart("file", "f.bin", MediaType.APPLICATION_OCTET_STREAM_TYPE, "hello".getBytes(StandardCharsets.UTF_8))
                .build()).contentType(MediaType.MULTIPART_FORM_DATA), Argument.STRING);
            assertEquals("hello", body);
        }
    }

    @Controller("/part-streaming")
    @Requires(property = "spec.name", value = "PartStreamingFileUploadTest")
    static class PartStreamingController {
        @Post(value = "/unannotated", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        Mono<String> unannotated(StreamingFileUpload file) {
            return read(file);
        }

        @Post(value = "/named", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        Mono<String> named(@Part("file") StreamingFileUpload upload) {
            return read(upload);
        }

        @Post(value = "/unnamed", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        Mono<String> unnamed(@Part StreamingFileUpload file) {
            return read(file);
        }

        private static Mono<String> read(StreamingFileUpload file) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            return Mono.from(file.transferTo(out)).then(Mono.fromSupplier(() -> out.toString(StandardCharsets.UTF_8)));
        }
    }
}
