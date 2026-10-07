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
package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormPart;
import io.micronaut.http.form.FormParts;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A form is decoded as it arrives, and held to the limits of a form: a
 * {@code micronaut.server.max-request-buffer-size} smaller than the form limits each text field
 * that is read into memory, not the body. This holds for a body that arrives before the handler
 * reads the form, e.g. in the first network read with the headers.
 */
class FormSmallBufferLimitTest {
    private static final int LIMIT = 16;
    private static final String BOUNDARY = "small-buffer-boundary";

    static Stream<Arguments> cases() {
        List<Arguments> cases = new ArrayList<>();
        for (boolean multipart : new boolean[]{false, true}) {
            for (boolean chunked : new boolean[]{false, true}) {
                cases.add(Arguments.of(multipart, chunked));
            }
        }
        return cases.stream();
    }

    static Stream<Arguments> streamed() {
        List<Arguments> cases = new ArrayList<>();
        for (String path : new String[]{"/parts", "/form-parts"}) {
            for (boolean multipart : new boolean[]{false, true}) {
                for (boolean chunked : new boolean[]{false, true}) {
                    cases.add(Arguments.of(path, multipart, chunked));
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}, multipart={1}, chunked={2}")
    @MethodSource("streamed")
    void smallPartsOfABodyLargerThanTheLimitAreRead(String path, boolean multipart, boolean chunked) throws Exception {
        Map<String, String> fields = fields("12345678", "12345678", "12345678");
        try (ApplicationContext ctx = run()) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            java.net.http.HttpResponse<String> response = post(server, path, fields, null, multipart, chunked);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("a=12345678,b=12345678,c=12345678,", response.body());
        }
    }

    @ParameterizedTest(name = "{0}, multipart={1}, chunked={2}")
    @MethodSource("streamed")
    void manySmallPartsAreRead(String path, boolean multipart, boolean chunked) throws Exception {
        // many times the limit, and more than one network read
        Map<String, String> fields = new LinkedHashMap<>();
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            fields.put("f" + i, "v" + i);
            expected.append('f').append(i).append("=v").append(i).append(',');
        }
        try (ApplicationContext ctx = run()) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            java.net.http.HttpResponse<String> response = post(server, path, fields, null, multipart, chunked);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals(expected.toString(), response.body());
        }
    }

    @ParameterizedTest(name = "{0}, multipart={1}, chunked={2}")
    @MethodSource("streamed")
    void aPartLargerThanTheLimitIsRejected(String path, boolean multipart, boolean chunked) throws Exception {
        Map<String, String> fields = fields("12345678", "x".repeat(2 * LIMIT), "12345678");
        try (ApplicationContext ctx = run()) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            java.net.http.HttpResponse<String> response = post(server, path, fields, null, multipart, chunked);
            assertEquals(413, response.statusCode(), response.body());
        }
    }

    @ParameterizedTest(name = "multipart={0}, chunked={1}")
    @MethodSource("cases")
    void aStreamedPartAfterFieldsLargerThanTheLimitIsRead(boolean multipart, boolean chunked) throws Exception {
        Map<String, String> fields = fields("12345678", "12345678", "12345678");
        try (ApplicationContext ctx = run()) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            java.net.http.HttpResponse<String> response = post(server, "/form-part", fields, null, multipart, chunked);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("c=12345678", response.body());
        }
    }

    @ParameterizedTest(name = "chunked={0}")
    @MethodSource("chunked")
    void aCollectedFormLimitsItsTextFieldsNotItsFiles(boolean chunked) throws Exception {
        try (ApplicationContext ctx = run()) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            // the file is stored with the limits of the multipart configuration
            for (String path : new String[]{"/form-data", "/arguments"}) {
                java.net.http.HttpResponse<String> response = post(server, path, fields("12345678"), "y".repeat(8 * LIMIT), true, chunked);
                assertEquals(200, response.statusCode(), path + ": " + response.body());
                assertEquals("a=12345678,file=" + 8 * LIMIT, response.body());
            }
        }
    }

    @ParameterizedTest(name = "multipart={0}, chunked={1}")
    @MethodSource("cases")
    void theTextFieldsOfACollectedFormAreLimitedTogether(boolean multipart, boolean chunked) throws Exception {
        try (ApplicationContext ctx = run()) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            java.net.http.HttpResponse<String> response = post(server, "/form-data-text", fields("12345678", "12345678", "12345678"), null, multipart, chunked);
            assertEquals(413, response.statusCode(), response.body());
            response = post(server, "/form-data-text", fields("12345678", "1234567"), null, multipart, chunked);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("a=12345678,b=1234567,", response.body());
        }
    }

    static Stream<Arguments> chunked() {
        return Stream.of(Arguments.of(false), Arguments.of(true));
    }

    private static Map<String, String> fields(String... values) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            fields.put(String.valueOf((char) ('a' + i)), values[i]);
        }
        return fields;
    }

    private static java.net.http.HttpResponse<String> post(EmbeddedServer server, String path, Map<String, String> fields, String file, boolean multipart, boolean chunked) throws Exception {
        StringBuilder body = new StringBuilder();
        if (multipart) {
            fields.forEach((name, value) -> body.append("--").append(BOUNDARY).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
                .append(value).append("\r\n"));
            if (file != null) {
                body.append("--").append(BOUNDARY).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"file\"; filename=\"file.txt\"\r\n")
                    .append("Content-Type: text/plain\r\n\r\n")
                    .append(file).append("\r\n");
            }
            body.append("--").append(BOUNDARY).append("--\r\n");
        } else {
            fields.forEach((name, value) -> body.append(body.isEmpty() ? "" : "&").append(name).append('=').append(value));
        }
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        HttpRequest.BodyPublisher publisher = chunked
            // an unknown length: the body is sent chunked
            ? HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes))
            : HttpRequest.BodyPublishers.ofByteArray(bytes);
        try (HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(server.getURL() + "/small-form" + path))
                .header("Content-Type", multipart ? "multipart/form-data; boundary=" + BOUNDARY : MediaType.APPLICATION_FORM_URLENCODED)
                .POST(publisher)
                .build();
            return client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        }
    }

    private static ApplicationContext run() {
        return ApplicationContext.run(Map.of("spec.name", "FormSmallBufferLimitTest", "micronaut.server.port", -1,
            "micronaut.server.max-request-buffer-size", LIMIT,
            "micronaut.server.netty.form-max-fields", 4096));
    }

    @Controller("/small-form")
    @Requires(property = "spec.name", value = "FormSmallBufferLimitTest")
    @Consumes({MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA})
    @Produces(MediaType.TEXT_PLAIN)
    static class FormController {
        @Post("/parts")
        CompletionStage<String> parts(AsyncRequestBody body) {
            return read(body.parts());
        }

        @Post("/form-parts")
        CompletionStage<String> formParts(FormParts parts) {
            return read(parts);
        }

        @Post("/form-part")
        CompletionStage<String> formPart(FormPart c) {
            return c.text().thenApply(text -> c.name() + "=" + text);
        }

        @Post("/form-data")
        CompletionStage<String> formData(FormData form) {
            FileUpload file = form.getFile("file");
            return file.bytes(1024).thenApply(bytes -> "a=" + form.getString("a") + ",file=" + bytes.length);
        }

        @Post("/arguments")
        CompletionStage<String> arguments(String a, FileUpload file) {
            return file.bytes(1024).thenApply(bytes -> "a=" + a + ",file=" + bytes.length);
        }

        @Post("/form-data-text")
        String formDataText(FormData form) {
            StringBuilder read = new StringBuilder();
            for (String name : form.names()) {
                read.append(name).append('=').append(form.getString(name)).append(',');
            }
            return read.toString();
        }

        private static CompletionStage<String> read(FormParts parts) {
            StringBuilder read = new StringBuilder();
            return parts.forEach(part -> part.text().thenAccept(text -> read.append(part.name()).append('=').append(text).append(',')))
                .thenApply(done -> read.toString());
        }
    }
}
