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
package io.micronaut.http.server.netty.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.form.FormData;
import io.micronaut.runtime.server.EmbeddedServer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A filter that reads the body with an {@link AsyncRequestBody}, not a copy, consumes it: the
 * route that reads the body fails, the route that does not answers, and what the filter did not
 * read is released when the request ends, the files of the form on disk too. The buffers are
 * checked by the leak presence detector of the tests.
 */
class AsyncRequestBodyFilterReadTest {
    private static final String SPEC_NAME = "AsyncRequestBodyFilterReadTest";

    @TempDir
    Path uploads;

    @Test
    void whatAFilterDidNotReadIsReleased() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.multipart.disk", true,
            "micronaut.server.multipart.location", uploads.toString()));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient blocking = client.toBlocking();
            for (String read : List.of("form", "parts", "text")) {
                for (int i = 0; i < 3; i++) {
                    HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                        () -> blocking.exchange(multipart("/filter-read/" + read + "/data"), String.class));
                    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failure.getStatus(), read);
                    assertEquals("none", blocking.retrieve(multipart("/filter-read/" + read + "/none"), String.class), read);
                }
            }
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, assertThrows(HttpClientResponseException.class,
                () -> blocking.exchange(HttpRequest.POST("/filter-read/text/body", "gone").contentType(MediaType.TEXT_PLAIN_TYPE), String.class)).getStatus());
            // the files the filters did not read are deleted when the requests end
            awaitNoFiles();
        }
    }

    private void awaitNoFiles() throws IOException, InterruptedException {
        List<Path> files = List.of();
        for (int i = 0; i < 100; i++) {
            try (Stream<Path> list = Files.list(uploads)) {
                files = list.toList();
            }
            if (files.isEmpty()) {
                return;
            }
            Thread.sleep(50);
        }
        assertEquals(List.of(), files);
    }

    private static HttpRequest<?> multipart(String path) {
        byte[] content = "x".repeat(32 * 1024).getBytes(StandardCharsets.UTF_8);
        return HttpRequest.POST(path, MultipartBody.builder()
                .addPart("name", "Fred")
                .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, content)
                .addPart("other", "other.txt", MediaType.TEXT_PLAIN_TYPE, content)
                .build())
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE);
    }

    @ServerFilter("/filter-read")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ReadingFilters {

        @RequestFilter("/form/**")
        CompletionStage<@Nullable HttpResponse<?>> form(AsyncRequestBody body) {
            // the files are stored, and not read
            return body.form().thenApply(form -> null);
        }

        @RequestFilter("/parts/**")
        CompletionStage<@Nullable HttpResponse<?>> parts(AsyncRequestBody body) {
            // only the first part is read
            return body.parts().part("name", part -> part.text()).thenApply(found -> null);
        }

        @RequestFilter("/text/**")
        CompletionStage<@Nullable HttpResponse<?>> text(AsyncRequestBody body) {
            return body.text().thenApply(text -> null);
        }
    }

    @Controller("/filter-read")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ReadController {

        @Post(uri = "/{read}/data", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String data(String read, FormData form) {
            return form.getString("name");
        }

        @Post(uri = "/{read}/none", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> none(String read) {
            return CompletableFuture.completedFuture("none");
        }

        @Post(uri = "/{read}/body", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String body(String read, @Body String body) {
            return body;
        }
    }
}
