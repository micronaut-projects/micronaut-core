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
package io.micronaut.http.server.tck.tests.forms;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormPart;
import io.micronaut.http.form.FormParts;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The types of {@code io.micronaut.http.form} as arguments of a controller: {@link FileUpload}
 * (by the name of its {@link Part} or its own name, a {@code List} of them, nullable or
 * {@code Optional}), {@link FormPart}, {@link FormData} and {@link FormParts}. Each request is
 * sent to the controller, under {@code /ctl}, and to a controller that reads the form with
 * {@link AsyncRequestBody#form()} or {@link AsyncRequestBody#parts()}, under {@code /fn}, and where it applies to a
 * controller that binds a {@link CompletedFileUpload}, under {@code /legacy}; the responses must
 * be the same.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class FormArgumentsTest {
    public static final String SPEC_NAME = "FormArgumentsTest";
    private static final String CTL = "/form-args/ctl";
    private static final String FN = "/form-args/fn";
    private static final String LEGACY = "/form-args/legacy";

    @Test
    @Tag("multipart")
    void fileByTheNameOfItsPart() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = same(server, prefix -> multipart(prefix + "/file", avatarForm()), FN, LEGACY);
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("Fred avatar.txt 7 picture", response.body());
            // the content type of the part
            assertEquals("avatar text/plain; charset=UTF-8", call(server, multipart(CTL + "/file-type", avatarForm())).body());
        }
    }

    @Test
    @Tag("multipart")
    void fileByTheNameOfTheParameter() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = same(server, prefix -> multipart(prefix + "/by-name", avatarForm()), FN, LEGACY);
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("Fred avatar.txt 7 picture", response.body());
        }
    }

    @Test
    @Tag("multipart")
    void missingRequiredFileIsABadRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = sameStatus(server, prefix -> multipart(prefix + "/file", MultipartBody.builder().addPart("name", "Fred").build()), FN, LEGACY);
            assertEquals(HttpStatus.BAD_REQUEST, response.status());
            // the same for a file bound by the name of the parameter
            assertEquals(HttpStatus.BAD_REQUEST, call(server, multipart(CTL + "/by-name", MultipartBody.builder().addPart("name", "Fred").build())).status());
        }
    }

    @Test
    @Tag("multipart")
    void textFieldAskedForAsAFileIsABadRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            MultipartBody body = MultipartBody.builder().addPart("name", "Fred").addPart("avatar", "not a file").build();
            Response response = sameStatus(server, prefix -> multipart(prefix + "/file", body), FN, LEGACY);
            assertEquals(HttpStatus.BAD_REQUEST, response.status());
            // the same when the file is taken from the FormData of the route
            assertEquals(HttpStatus.BAD_REQUEST, call(server, multipart(CTL + "/shared", body)).status());
            // and when it is asked for as a list of files
            assertEquals(HttpStatus.BAD_REQUEST, call(server, multipart(CTL + "/text-as-files", body)).status());
        }
    }

    @Test
    @Tag("multipart")
    void nullableAndOptionalFilesThatAreMissing() throws IOException {
        try (ServerUnderTest server = server()) {
            Response missing = same(server, prefix -> multipart(prefix + "/optional", MultipartBody.builder().addPart("name", "Fred").build()), FN);
            assertEquals(HttpStatus.OK, missing.status());
            assertEquals("none empty no-docs", missing.body());
            Response present = same(server, prefix -> multipart(prefix + "/optional", MultipartBody.builder()
                .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, bytes("picture"))
                .addPart("cover", "cover.txt", MediaType.TEXT_PLAIN_TYPE, bytes("front"))
                .addPart("docs", "a.txt", MediaType.TEXT_PLAIN_TYPE, bytes("A"))
                .build()), FN);
            assertEquals("avatar.txt cover.txt 1", present.body());
        }
    }

    @Test
    @Tag("multipart")
    void severalFilesOfOneName() throws IOException {
        try (ServerUnderTest server = server()) {
            MultipartBody body = MultipartBody.builder()
                .addPart("docs", "a.txt", MediaType.TEXT_PLAIN_TYPE, bytes("first"))
                .addPart("name", "Fred")
                .addPart("docs", "b.txt", MediaType.TEXT_PLAIN_TYPE, bytes("second"))
                .addPart("docs", "c.txt", MediaType.TEXT_PLAIN_TYPE, bytes("third"))
                .build();
            Response response = same(server, prefix -> multipart(prefix + "/files", body), FN);
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("Fred a.txt=first;b.txt=second;c.txt=third;", response.body());
        }
    }

    @Test
    @Tag("multipart")
    void fileLargerThanTheMaximumFileSizeIsRejected() throws IOException {
        byte[] large = new byte[4096];
        Arrays.fill(large, (byte) 'x');
        MultipartBody body = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, large)
            .build();
        try (ServerUnderTest server = server(Map.of("micronaut.server.multipart.max-file-size", "1024"))) {
            Response response = sameStatus(server, prefix -> multipart(prefix + "/file", body), FN, LEGACY);
            assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, response.status());
            assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, call(server, multipart(CTL + "/shared", body)).status());
        }
    }

    @Test
    @Tag("multipart")
    void aTakenFileIsHeldToTheMaximumFileSize() throws IOException {
        byte[] large = new byte[16];
        Arrays.fill(large, (byte) 'x');
        MultipartBody body = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, large)
            .build();
        try (ServerUnderTest server = server(Map.of("micronaut.server.multipart.max-file-size", "8"))) {
            // taken like it is read
            for (String path : List.of("/part-text", "/part-taken", "/part-file-taken")) {
                Response response = call(server, multipart(CTL + path, body));
                assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, response.status(), () -> path + ": " + response.body());
            }
            for (String path : List.of("/part-taken", "/part-file-taken")) {
                assertEquals("Fred avatar.txt 7", call(server, multipart(CTL + path, avatarForm())).body(), path);
            }
        }
    }

    @Test
    @Tag("multipart")
    void uploadsTheApplicationDidNotConsumeAreReleasedWhenTheRequestEnds() throws IOException {
        Path location = Files.createTempDirectory("form-args");
        MultipartBody body = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, bytes("picture"))
            .addPart("docs", "a.txt", MediaType.TEXT_PLAIN_TYPE, bytes("first"))
            .addPart("docs", "b.txt", MediaType.TEXT_PLAIN_TYPE, bytes("second"))
            .build();
        try (ServerUnderTest server = server(Map.of(
            "micronaut.server.multipart.disk", true,
            "micronaut.server.multipart.location", location.toString()))) {
            for (String path : List.of("/unread", "/unread-form", "/file")) {
                Response response = call(server, multipart(CTL + path, body));
                assertEquals(HttpStatus.OK, response.status(), response.body());
                assertEmptySoon(location, path);
            }
        } finally {
            try (Stream<Path> files = Files.list(location)) {
                for (Path file : files.toList()) {
                    Files.deleteIfExists(file);
                }
            }
            Files.deleteIfExists(location);
        }
    }

    @Test
    @Tag("multipart")
    void formStoredAfterTheRouteFailedIsReleased() throws IOException {
        byte[] large = new byte[256 * 1024];
        Arrays.fill(large, (byte) 'x');
        MultipartBody body = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("archive", "archive.bin", MediaType.APPLICATION_OCTET_STREAM_TYPE, large)
            .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, bytes("picture"))
            .build();
        try (ServerUnderTest server = server()) {
            for (int i = 0; i < 2; i++) {
                // the form is being read when the header fails the route: the files stored after
                // the request ended are released
                Response response = call(server, HttpRequest.POST(CTL + "/failing-form", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE).header("X-Count", "many"));
                assertEquals(HttpStatus.BAD_REQUEST, response.status(), response.body());
            }
            assertEquals("Fred avatar.txt 7 picture", call(server, multipart(CTL + "/file", avatarForm())).body());
        }
    }

    @Test
    @Tag("multipart")
    void formDataAndFileArgumentsShareTheForm() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String path : List.of("/shared", "/shared-reversed")) {
                Response response = same(server, prefix -> multipart(prefix + path, avatarForm()), FN);
                assertEquals(HttpStatus.OK, response.status());
                assertEquals("same Fred Fred picture", response.body());
            }
        }
    }

    @Test
    @Tag("multipart")
    void formDataIsReadWhole() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = same(server, prefix -> multipart(prefix + "/data", avatarForm()), FN);
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("Fred [avatar] picture", response.body());
        }
    }

    @Test
    void urlEncodedFormDataAndTextFieldsShareTheForm() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String path : List.of("/text", "/text-reversed")) {
                Response response = same(server, prefix -> HttpRequest.POST(prefix + path, "name=Fred&city=Prague&age=41")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), FN);
                assertEquals(HttpStatus.OK, response.status());
                assertEquals("Fred Prague 42 Fred", response.body());
            }
        }
    }

    @Test
    void anOptionalCollectionOfATextFieldGetsEveryValue() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = call(server, HttpRequest.POST(CTL + "/tags", "tags=one&name=Fred&tags=two")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
            assertEquals(HttpStatus.OK, response.status(), response.body());
            assertEquals("[one, two] [one, two] [one, two] [one, two]", response.body());
            Response missing = call(server, HttpRequest.POST(CTL + "/tags", "name=Fred")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
            assertEquals(HttpStatus.OK, missing.status(), missing.body());
            assertEquals("null empty null empty", missing.body());
        }
    }

    @Test
    @Tag("multipart")
    void anOptionalCollectionOfATextFieldOfAMultipartFormGetsEveryValue() throws IOException {
        try (ServerUnderTest server = server()) {
            MultipartBody tags = MultipartBody.builder().addPart("tags", "one").addPart("name", "Fred").addPart("tags", "two").build();
            Response response = call(server, multipart(CTL + "/tags", tags));
            assertEquals(HttpStatus.OK, response.status(), response.body());
            assertEquals("[one, two] [one, two] [one, two] [one, two]", response.body());
            Response missing = call(server, multipart(CTL + "/tags", MultipartBody.builder().addPart("name", "Fred").build()));
            assertEquals(HttpStatus.OK, missing.status(), missing.body());
            assertEquals("null empty null empty", missing.body());
        }
    }

    @Test
    void aCollectionOfATextFieldWithAValueThatDoesNotConvertIsABadRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String path : List.of("/integers", "/integers-argument", "/integers-optional")) {
                Response converted = call(server, HttpRequest.POST(CTL + path, "tags=1&name=Fred&tags=2")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
                assertEquals(HttpStatus.OK, converted.status(), converted.body());
                assertEquals("[1, 2]", converted.body(), path);
                // not the values that convert, without the others
                Response rejected = call(server, HttpRequest.POST(CTL + path, "tags=1&name=Fred&tags=bad")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
                assertEquals(HttpStatus.BAD_REQUEST, rejected.status(), path + ": " + rejected.body());
            }
        }
    }

    @Test
    @Tag("multipart")
    void aCollectionOfATextFieldOfAMultipartFormWithAValueThatDoesNotConvertIsABadRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String path : List.of("/integers", "/integers-argument", "/integers-optional")) {
                Response converted = call(server, multipart(CTL + path, MultipartBody.builder().addPart("tags", "1").addPart("tags", "2").build()));
                assertEquals(HttpStatus.OK, converted.status(), converted.body());
                assertEquals("[1, 2]", converted.body(), path);
                Response rejected = call(server, multipart(CTL + path, MultipartBody.builder().addPart("tags", "1").addPart("tags", "bad").build()));
                assertEquals(HttpStatus.BAD_REQUEST, rejected.status(), path + ": " + rejected.body());
            }
        }
    }

    @Test
    @Tag("multipart")
    void fileIsReadAsText() throws IOException {
        try (ServerUnderTest server = server()) {
            MultipartBody form = MultipartBody.builder()
                .addPart("name", "Fred")
                .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, bytes("pictüre"))
                .build();
            Response stored = same(server, prefix -> multipart(prefix + "/file-text", form), FN);
            assertEquals(HttpStatus.OK, stored.status());
            assertEquals("Fred avatar.txt pictüre", stored.body());
            Response streamed = same(server, prefix -> multipart(prefix + "/part-text", form), FN);
            assertEquals(HttpStatus.OK, streamed.status());
            assertEquals("Fred avatar.txt pictüre", streamed.body());

            // the charset of the part, not of the request
            MultipartBody latin1 = MultipartBody.builder()
                .addPart("name", "Fred")
                .addPart("avatar", "avatar.txt", MediaType.of("text/plain; charset=ISO-8859-1"), "pictüre".getBytes(StandardCharsets.ISO_8859_1))
                .build();
            Response storedLatin1 = same(server, prefix -> multipart(prefix + "/file-text", latin1), FN);
            assertEquals(HttpStatus.OK, storedLatin1.status());
            assertEquals("Fred avatar.txt pictüre", storedLatin1.body());
            Response streamedLatin1 = same(server, prefix -> multipart(prefix + "/part-text", latin1), FN);
            assertEquals(HttpStatus.OK, streamedLatin1.status());
            assertEquals("Fred avatar.txt pictüre", streamedLatin1.body());

            // a charset given to the read wins over the charset of the part
            MultipartBody mislabelled = MultipartBody.builder()
                .addPart("name", "Fred")
                .addPart("avatar", "avatar.txt", MediaType.of("text/plain; charset=UTF-8"), "pictüre".getBytes(StandardCharsets.ISO_8859_1))
                .build();
            Response storedOverride = same(server, prefix -> multipart(prefix + "/file-text-latin1", mislabelled), FN);
            assertEquals(HttpStatus.OK, storedOverride.status());
            assertEquals("Fred avatar.txt pictüre", storedOverride.body());
            Response streamedOverride = same(server, prefix -> multipart(prefix + "/part-text-latin1", mislabelled), FN);
            assertEquals(HttpStatus.OK, streamedOverride.status());
            assertEquals("Fred avatar.txt pictüre", streamedOverride.body());
        }
    }

    @Test
    @Tag("multipart")
    void formPartIsStreamedByName() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = same(server, prefix -> multipart(prefix + "/part", avatarForm()), FN);
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("Fred avatar.txt picture", response.body());
            Response text = same(server, prefix -> multipart(prefix + "/text-part", avatarForm()), FN);
            assertEquals("name=Fred false", text.body());
            Response missing = same(server, prefix -> multipart(prefix + "/optional-part", avatarForm()), FN);
            assertEquals("no cover", missing.body());
        }
    }

    @Test
    @Tag("multipart")
    @Tag("streaming-multipart") // a runner whose server reads the whole multipart body before the route runs can exclude this tag
    void aFieldSentAfterAStreamedFormPartIsNotBound() throws IOException {
        try (ServerUnderTest server = server()) {
            // like a StreamingFileUpload: the route runs once the part starts, so a field sent
            // after a part that is still arriving cannot be bound
            byte[] large = new byte[256 * 1024];
            Arrays.fill(large, (byte) 'x');
            MultipartBody fieldAfterThePart = MultipartBody.builder()
                .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, large)
                .addPart("name", "Fred")
                .build();
            assertEquals(HttpStatus.BAD_REQUEST, call(server, multipart(CTL + "/part", fieldAfterThePart)).status());
        }
    }

    @Test
    @Tag("multipart")
    void formPartsAreStreamedByAnAsynchronousController() throws IOException {
        try (ServerUnderTest server = server()) {
            MultipartBody body = MultipartBody.builder()
                .addPart("name", "Fred")
                .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, bytes("picture"))
                .addPart("age", "42")
                .build();
            Response response = same(server, prefix -> multipart(prefix + "/parts", body), FN);
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("name=Fred;avatar.txt=picture;age=42;", response.body());
            // a part found by name
            assertEquals("true picture", call(server, multipart(CTL + "/parts-named", body)).body());
        }
    }

    @Test
    void formPartsCannotBeCombinedWithOtherFormArguments() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String path : List.of("/refused/parts-and-field", "/refused/data-and-parts")) {
                Response response = call(server, HttpRequest.POST(CTL + path, "name=Fred")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.status(), path);
                assertTrue(response.body().contains("cannot be combined"), response.body());
            }
            Response field = call(server, HttpRequest.POST(CTL + "/refused/parts-and-field", "name=Fred")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
            assertTrue(field.body().contains("reads a form field"), field.body());
        }
    }

    @Test
    @Tag("multipart")
    void formDataCannotBeCombinedWithArgumentsThatReadAFieldThemselves() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String path : List.of("/refused/data-and-completed", "/refused/data-and-form-part", "/refused/data-and-streaming",
                "/refused/parts-and-file", "/refused/parts-and-form-part")) {
                Response response = call(server, multipart(CTL + path, avatarForm()));
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.status(), path);
                assertTrue(response.body().contains("cannot be combined"), response.body());
                if (path.startsWith("/refused/parts-and-")) {
                    assertTrue(response.body().contains("reads a form field"), path + ": " + response.body());
                } else if (!path.equals("/refused/data-and-completed")) {
                    assertTrue(response.body().contains("reads its form field by itself"), path + ": " + response.body());
                }
            }
            // the refused requests leave the server usable
            assertEquals("Fred avatar.txt 7 picture", call(server, multipart(CTL + "/file", avatarForm())).body());
        }
    }

    @Test
    @Tag("multipart")
    void thePartsOfAFormLargerThanTheBufferLimitAreLimitedOneByOne() throws IOException {
        int limit = 1024;
        MultipartBody.Builder small = MultipartBody.builder();
        StringBuilder expected = new StringBuilder();
        // the form is several times the limit, each part is below it
        for (int i = 0; i < 8; i++) {
            String value = String.valueOf((char) ('a' + i)).repeat(limit / 2);
            small.addPart("field" + i, value);
            expected.append("field").append(i).append('=').append(value).append(';');
        }
        MultipartBody large = MultipartBody.builder()
            .addPart("field0", "a")
            .addPart("field1", "b".repeat(2 * limit))
            .build();
        try (ServerUnderTest server = server(Map.of("micronaut.server.max-request-buffer-size", limit))) {
            MultipartBody body = small.build();
            Response read = same(server, prefix -> multipart(prefix + "/parts", body), FN);
            assertEquals(HttpStatus.OK, read.status(), read.body());
            assertEquals(expected.toString(), read.body());
            // a part that is read into memory is held to the limit
            Response rejected = sameStatus(server, prefix -> multipart(prefix + "/parts", large), FN);
            assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, rejected.status(), rejected.body());
        }
    }

    private static void assertEmptySoon(Path location, String path) throws IOException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        List<Path> left;
        do {
            try (Stream<Path> files = Files.list(location)) {
                left = files.toList();
            }
            if (left.isEmpty()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        } while (System.nanoTime() < deadline);
        assertTrue(left.isEmpty(), path + " left " + left);
    }

    private static MultipartBody avatarForm() {
        return MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, bytes("picture"))
            .build();
    }

    private static HttpRequest<?> multipart(String path, MultipartBody body) {
        return HttpRequest.POST(path, body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static Response same(ServerUnderTest server, Function<String, HttpRequest<?>> request, String... prefixes) {
        Response controller = call(server, request.apply(CTL));
        for (String prefix : prefixes) {
            HttpRequest<?> other = request.apply(prefix);
            Response response = call(server, other);
            assertEquals(controller.status(), response.status(), () -> other.getPath() + ": " + response.body());
            assertEquals(controller.body(), response.body(), other.getPath());
        }
        return controller;
    }

    private static Response sameStatus(ServerUnderTest server, Function<String, HttpRequest<?>> request, String... prefixes) {
        Response controller = call(server, request.apply(CTL));
        for (String prefix : prefixes) {
            HttpRequest<?> other = request.apply(prefix);
            Response response = call(server, other);
            assertEquals(controller.status(), response.status(), () -> other.getPath() + ": " + response.body());
        }
        return controller;
    }

    private static Response call(ServerUnderTest server, HttpRequest<?> request) {
        try {
            HttpResponse<String> response = server.exchange(request, String.class);
            return new Response(response.getStatus(), response.getBody().orElse(""));
        } catch (HttpClientResponseException e) {
            return new Response(e.getStatus(), e.getResponse().getBody(String.class).orElse(""));
        }
    }

    private static ServerUnderTest server() {
        return server(Map.of());
    }

    private static ServerUnderTest server(Map<String, Object> properties) {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, properties);
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static CompletionStage<String> describe(String name, FileUpload file) {
        return file.bytes(1024).thenApply(bytes -> name + " " + file.fileName() + " " + file.size().orElse(-1) + " " + text(bytes));
    }

    /**
     * The files in order, each as {@code fileName=content;}.
     */
    private static CompletionStage<String> contents(List<FileUpload> files) {
        CompletionStage<StringBuilder> result = CompletableFuture.completedFuture(new StringBuilder());
        for (FileUpload file : files) {
            result = result.thenCompose(builder -> file.bytes(1024).thenApply(bytes -> builder.append(file.fileName()).append('=').append(text(bytes)).append(';')));
        }
        return result.thenApply(StringBuilder::toString);
    }

    private static CompletionStage<String> forEach(FormParts parts) {
        StringBuilder result = new StringBuilder();
        return parts.forEach(part -> {
            if (part.isFile()) {
                return part.bytes(1024).thenAccept(bytes -> result.append(part.fileName()).append('=').append(text(bytes)).append(';'));
            }
            return part.text().thenAccept(value -> result.append(part.name()).append('=').append(value).append(';'));
        }).thenApply(done -> result.toString());
    }

    /**
     * The length of a taken body, once all of it arrived.
     */
    private static CompletionStage<String> taken(String prefix, CloseableByteBody body) {
        return body.buffer().thenApply(available -> {
            try (available) {
                return prefix + " " + available.length();
            }
        });
    }

    private static HttpResponse<String> ok(String body) {
        return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    record Response(HttpStatus status, String body) {
    }

    @Controller(CTL)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FormArgumentsController {

        @Post(uri = "/file", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> file(@Part("avatar") FileUpload file, @Part("name") String name) {
            return describe(name, file);
        }

        @Post(uri = "/by-name", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> byName(FileUpload avatar, String name) {
            return describe(name, avatar);
        }

        @Post(uri = "/file-type", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String fileType(@Part("avatar") FileUpload file) {
            return file.name() + " " + file.contentType().map(MediaType::toString).orElse("none");
        }

        @Post(uri = "/text-as-files", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String textAsFiles(FormData form, @Part("avatar") List<FileUpload> avatar) {
            return "not reached";
        }

        @Post(uri = "/optional", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String optional(@Nullable FileUpload avatar, @Part("cover") Optional<FileUpload> cover, @Nullable List<FileUpload> docs) {
            return (avatar == null ? "none" : avatar.fileName())
                + " " + cover.map(FileUpload::fileName).orElse("empty")
                + " " + (docs == null ? "no-docs" : docs.size());
        }

        @Post(uri = "/files", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> files(List<FileUpload> docs, @Part("name") String name) {
            return contents(docs).thenApply(contents -> name + " " + contents);
        }

        @Post(uri = "/unread", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String unread(FileUpload avatar, List<FileUpload> docs) {
            // not consumed: released when the request ends
            return avatar.fileName() + " " + docs.size();
        }

        @Post(uri = "/unread-form", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String unreadForm(FormData form, FileUpload avatar) {
            return form.getString("name") + " " + avatar.fileName();
        }

        @Post(uri = "/shared", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> shared(FormData form, @Part("avatar") FileUpload avatar, @Part("name") String name) {
            return sharedResult(form, avatar, name);
        }

        @Post(uri = "/shared-reversed", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> sharedReversed(@Part("name") String name, @Part("avatar") FileUpload avatar, FormData form) {
            return sharedResult(form, avatar, name);
        }

        @SuppressWarnings("ReferenceEquality") // the same handle
        private static CompletionStage<String> sharedResult(FormData form, FileUpload avatar, String name) {
            String same = form.getFile("avatar") == avatar ? "same" : "different";
            return avatar.bytes(1024).thenApply(bytes -> same + " " + name + " " + form.getString("name") + " " + text(bytes));
        }

        @Post(uri = "/failing-form", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String failingForm(FormData form, @Header("X-Count") int count) {
            return "not reached";
        }

        @Post(uri = "/data", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> data(FormData form) {
            return form.getFile("avatar").bytes(1024).thenApply(bytes -> form.getString("name") + " [avatar] " + text(bytes));
        }

        @Post(uri = "/text", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        String textFields(FormData form, @Part("name") String name, String city, int age) {
            return name + " " + city + " " + (age + 1) + " " + form.getString("name");
        }

        @Post(uri = "/text-reversed", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        String textFieldsReversed(@Part("name") String name, String city, int age, FormData form) {
            return name + " " + city + " " + (age + 1) + " " + form.getString("name");
        }

        @Post(uri = "/tags", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        String tags(FormData form,
                    @Nullable @Part("tags") List<String> list,
                    @Part("tags") Optional<List<String>> optionalList,
                    @Part("tags") String @Nullable [] array,
                    @Part("tags") Optional<String[]> optionalArray) {
            return tagsResult(list, optionalList, array, optionalArray);
        }

        private static String tagsResult(@Nullable List<String> list, Optional<List<String>> optionalList, String @Nullable [] array, Optional<String[]> optionalArray) {
            return list + " " + optionalList.map(List::toString).orElse("empty")
                + " " + (array == null ? "null" : Arrays.toString(array)) + " " + optionalArray.map(Arrays::toString).orElse("empty");
        }

        @Post(uri = "/integers", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        String integers(FormData form) {
            return form.get("tags", Argument.listOf(Integer.class)).toString();
        }

        @Post(uri = "/integers-argument", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        String integersArgument(FormData form, @Part("tags") List<Integer> tags) {
            return tags.toString();
        }

        @Post(uri = "/integers-optional", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        String integersOptional(FormData form, @Part("tags") Optional<List<Integer>> tags) {
            return tags.orElseThrow().toString();
        }

        @Post(uri = "/part", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> part(@Part("name") String name, @Part("avatar") FormPart avatar) {
            // the part is read as it arrives
            return avatar.file().bytes(1024).thenApply(bytes -> name + " " + avatar.fileName() + " " + text(bytes));
        }

        @Post(uri = "/part-taken", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> partTaken(@Part("name") String name, @Part("avatar") FormPart avatar) {
            return taken(name + " " + avatar.fileName(), avatar.takeBody());
        }

        @Post(uri = "/part-file-taken", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> partFileTaken(@Part("name") String name, @Part("avatar") FormPart avatar) {
            return taken(name + " " + avatar.fileName(), avatar.file().takeBody());
        }

        @Post(uri = "/file-text", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> fileText(FileUpload avatar, String name) {
            return avatar.text(1024).thenApply(text -> name + " " + avatar.fileName() + " " + text);
        }

        @Post(uri = "/part-text", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> partText(@Part("name") String name, @Part("avatar") FormPart avatar) {
            // the part is read as it arrives
            return avatar.file().text(1024).thenApply(text -> name + " " + avatar.fileName() + " " + text);
        }

        @Post(uri = "/file-text-latin1", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> fileTextLatin1(FileUpload avatar, String name) {
            return avatar.text(1024, StandardCharsets.ISO_8859_1).thenApply(text -> name + " " + avatar.fileName() + " " + text);
        }

        @Post(uri = "/part-text-latin1", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> partTextLatin1(@Part("name") String name, @Part("avatar") FormPart avatar) {
            return avatar.text(1024, StandardCharsets.ISO_8859_1).thenApply(text -> name + " " + avatar.fileName() + " " + text);
        }

        @Post(uri = "/text-part", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> textPart(FormPart name) {
            return name.text().thenApply(value -> name.name() + "=" + value + " " + name.isFile());
        }

        @Post(uri = "/optional-part", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String optionalPart(Optional<FormPart> cover) {
            return cover.map(part -> "cover " + part.fileName()).orElse("no cover");
        }

        @Post(uri = "/parts", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> parts(FormParts parts) {
            return forEach(parts);
        }

        @Post(uri = "/parts-named", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> partsNamed(FormParts parts) {
            StringBuilder result = new StringBuilder();
            return parts.part("avatar", part -> part.text(1024).thenAccept(result::append))
                .thenApply(found -> found + " " + result);
        }

        @Error(exception = IllegalStateException.class)
        HttpResponse<String> refused(IllegalStateException e) {
            // the message of a refused combination, answered with 500
            return HttpResponse.<String>serverError().body(e.getMessage()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }

        @Post(uri = "/refused/parts-and-field", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        String partsAndField(FormParts parts, @Part("name") String name) {
            return "not reached";
        }

        @Post(uri = "/refused/data-and-parts", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        String dataAndParts(FormData form, FormParts parts) {
            return "not reached";
        }

        @Post(uri = "/refused/data-and-completed", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String dataAndCompleted(FormData form, @Part("avatar") CompletedFileUpload avatar) {
            return "not reached";
        }

        @Post(uri = "/refused/data-and-form-part", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String dataAndFormPart(FormData form, @Part("avatar") FormPart avatar) {
            return "not reached";
        }

        @Post(uri = "/refused/parts-and-file", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String partsAndFile(@Part("avatar") FileUpload avatar, FormParts parts) {
            return "not reached";
        }

        @Post(uri = "/refused/data-and-streaming", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String dataAndStreaming(@Part("avatar") StreamingFileUpload avatar, FormData form) {
            return "not reached";
        }

        @Post(uri = "/refused/parts-and-form-part", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String partsAndFormPart(FormParts parts, @Part("avatar") FormPart avatar) {
            return "not reached";
        }
    }

    @Controller(LEGACY)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CompletedFileUploadController {

        @Post(uri = "/file", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String file(@Part("avatar") CompletedFileUpload file, @Part("name") String name) throws IOException {
            return describe(name, file);
        }

        @Post(uri = "/by-name", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String byName(CompletedFileUpload avatar, String name) throws IOException {
            return describe(name, avatar);
        }

        private static String describe(String name, CompletedFileUpload file) throws IOException {
            try (file) {
                return name + " " + file.getFilename() + " " + file.getSize() + " " + text(file.getBytes());
            }
        }
    }

    @Controller(FN)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AsyncBodyController {

        @Post(uri = "/file", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> file(AsyncRequestBody body) {
            return body.form().thenCompose(form -> describe(form.getString("name"), form.getFile("avatar")).thenApply(FormArgumentsTest::ok));
        }

        @Post(uri = "/by-name", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> byName(AsyncRequestBody body) {
            return file(body);
        }

        @Post(uri = "/optional", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> optional(AsyncRequestBody body) {
            return body.form().thenApply(form -> {
                List<FileUpload> docs = form.getFiles("docs");
                return ok(form.findFile("avatar").map(FileUpload::fileName).orElse("none")
                    + " " + form.findFile("cover").map(FileUpload::fileName).orElse("empty")
                    + " " + (docs.isEmpty() ? "no-docs" : docs.size()));
            });
        }

        @Post(uri = "/files", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> files(AsyncRequestBody body) {
            return body.form().thenCompose(form -> contents(form.getFiles("docs")).thenApply(contents -> ok(form.getString("name") + " " + contents)));
        }

        @Post(uri = "/shared", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> shared(AsyncRequestBody body) {
            return body.form().thenCompose(form -> {
                FileUpload avatar = form.getFile("avatar");
                return avatar.bytes(1024).thenApply(bytes -> ok("same " + form.getString("name") + " " + form.getString("name") + " " + text(bytes)));
            });
        }

        @Post(uri = "/shared-reversed", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> sharedReversed(AsyncRequestBody body) {
            return shared(body);
        }

        @Post(uri = "/data", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> data(AsyncRequestBody body) {
            return body.form().thenCompose(form ->
                form.getFile("avatar").bytes(1024).thenApply(bytes -> ok(form.getString("name") + " [avatar] " + text(bytes))));
        }

        @Post(uri = "/text", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> textFields(AsyncRequestBody body) {
            return body.form().thenApply(form ->
                ok(form.getString("name") + " " + form.getString("city") + " " + (form.getInt("age") + 1) + " " + form.getString("name")));
        }

        @Post(uri = "/text-reversed", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> textReversed(AsyncRequestBody body) {
            return textFields(body);
        }

        @Post(uri = "/part", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> part(AsyncRequestBody body) {
            FormParts parts = body.parts();
            List<String> result = new ArrayList<>();
            return parts.part("name", part -> part.text().thenAccept(result::add))
                .thenCompose(found -> parts.part("avatar", part -> part.file().bytes(1024)
                    .thenAccept(bytes -> result.add(part.fileName() + " " + text(bytes)))))
                .thenApply(found -> ok(String.join(" ", result)));
        }

        @Post(uri = "/file-text", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> fileText(AsyncRequestBody body) {
            return body.form().thenCompose(form -> {
                FileUpload avatar = form.getFile("avatar");
                return avatar.text(1024).thenApply(text -> ok(form.getString("name") + " " + avatar.fileName() + " " + text));
            });
        }

        @Post(uri = "/part-text", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> partText(AsyncRequestBody body) {
            FormParts parts = body.parts();
            List<String> result = new ArrayList<>();
            return parts.part("name", part -> part.text().thenAccept(result::add))
                .thenCompose(found -> parts.part("avatar", part -> part.file().text(1024)
                    .thenAccept(text -> result.add(part.fileName() + " " + text))))
                .thenApply(found -> ok(String.join(" ", result)));
        }

        @Post(uri = "/file-text-latin1", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> fileTextLatin1(AsyncRequestBody body) {
            return body.form().thenCompose(form -> {
                FileUpload avatar = form.getFile("avatar");
                return avatar.text(1024, StandardCharsets.ISO_8859_1).thenApply(text -> ok(form.getString("name") + " " + avatar.fileName() + " " + text));
            });
        }

        @Post(uri = "/part-text-latin1", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> partTextLatin1(AsyncRequestBody body) {
            FormParts parts = body.parts();
            List<String> result = new ArrayList<>();
            return parts.part("name", part -> part.text().thenAccept(result::add))
                .thenCompose(found -> parts.part("avatar", part -> part.text(1024, StandardCharsets.ISO_8859_1)
                    .thenAccept(text -> result.add(part.fileName() + " " + text))))
                .thenApply(found -> ok(String.join(" ", result)));
        }

        @Post(uri = "/text-part", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> textPart(AsyncRequestBody body) {
            FormParts parts = body.parts();
            List<String> result = new ArrayList<>();
            return parts.part("name", part -> part.text().thenAccept(value -> result.add(part.name() + "=" + value + " " + part.isFile())))
                .thenApply(found -> ok(String.join(" ", result)));
        }

        @Post(uri = "/optional-part", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> optionalPart(AsyncRequestBody body) {
            FormParts parts = body.parts();
            List<String> result = new ArrayList<>();
            return parts.part("cover", part -> {
                result.add("cover " + part.fileName());
                return CompletableFuture.completedFuture(null);
            }).thenApply(found -> ok(found ? String.join(" ", result) : "no cover"));
        }

        @Post(uri = "/parts", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> parts(AsyncRequestBody body) {
            return forEach(body.parts()).thenApply(FormArgumentsTest::ok);
        }
    }
}
