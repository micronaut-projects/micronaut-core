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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
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
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormPart;
import io.micronaut.http.form.FormParts;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
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
 * sent to the controller, under {@code /ctl}, and to handler functions that read the form with
 * {@code request.form()} or {@code request.parts()}, under {@code /fn}, and where it applies to a
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
        }
    }

    @Test
    @Tag("multipart")
    void formDataCannotBeCombinedWithArgumentsThatReadAFieldThemselves() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String path : List.of("/refused/data-and-completed", "/refused/data-and-form-part", "/refused/parts-and-file")) {
                Response response = call(server, multipart(CTL + path, avatarForm()));
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.status(), path);
                assertTrue(response.body().contains("cannot be combined"), response.body());
            }
            // the refused requests leave the server usable
            assertEquals("Fred avatar.txt 7 picture", call(server, multipart(CTL + "/file", avatarForm())).body());
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

        @Post(uri = "/part", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> part(@Part("name") String name, @Part("avatar") FormPart avatar) {
            // the part is read as it arrives
            return avatar.file().bytes(1024).thenApply(bytes -> name + " " + avatar.fileName() + " " + text(bytes));
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

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class HandlerRoutes {
        private static final MediaType[] FORM_MEDIA_TYPES = {MediaType.APPLICATION_FORM_URLENCODED_TYPE, MediaType.MULTIPART_FORM_DATA_TYPE};

        @Singleton
        @Named("form-args")
        HttpRoutes formArgumentsRoutes() {
            return routes -> {
                for (String path : List.of("/file", "/by-name")) {
                    routes.asyncPOST(FN + path, (request, pathVariables, body) -> body.form().thenCompose(form ->
                            describe(form.getString("name"), form.getFile("avatar")).thenApply(FormArgumentsTest::ok)))
                        .consumes(FORM_MEDIA_TYPES);
                }
                routes.POST(FN + "/optional", (request, pathVariables, form) -> {
                    List<FileUpload> docs = form.getFiles("docs");
                    return ok(form.findFile("avatar").map(FileUpload::fileName).orElse("none")
                        + " " + form.findFile("cover").map(FileUpload::fileName).orElse("empty")
                        + " " + (docs.isEmpty() ? "no-docs" : docs.size()));
                });
                routes.asyncPOST(FN + "/files", (request, pathVariables, body) -> body.form().thenCompose(form ->
                        contents(form.getFiles("docs")).thenApply(contents -> ok(form.getString("name") + " " + contents))))
                    .consumes(FORM_MEDIA_TYPES);
                for (String path : List.of("/shared", "/shared-reversed")) {
                    routes.asyncPOST(FN + path, (request, pathVariables, body) -> body.form().thenCompose(form -> {
                            FileUpload avatar = form.getFile("avatar");
                            return avatar.bytes(1024).thenApply(bytes -> ok("same " + form.getString("name") + " " + form.getString("name") + " " + text(bytes)));
                        }))
                        .consumes(FORM_MEDIA_TYPES);
                }
                routes.asyncPOST(FN + "/data", (request, pathVariables, body) -> body.form().thenCompose(form ->
                        form.getFile("avatar").bytes(1024).thenApply(bytes -> ok(form.getString("name") + " [avatar] " + text(bytes)))))
                    .consumes(FORM_MEDIA_TYPES);
                for (String path : List.of("/text", "/text-reversed")) {
                    routes.POST(FN + path, (request, pathVariables, form) ->
                        ok(form.getString("name") + " " + form.getString("city") + " " + (form.getInt("age") + 1) + " " + form.getString("name")));
                }
                routes.asyncPOST(FN + "/part", (request, pathVariables, body) -> {
                    FormParts parts = body.parts();
                    List<String> result = new ArrayList<>();
                    return parts.part("name", part -> part.text().thenAccept(result::add))
                        .thenCompose(found -> parts.part("avatar", part -> part.file().bytes(1024)
                            .thenAccept(bytes -> result.add(part.fileName() + " " + text(bytes)))))
                        .thenApply(found -> ok(String.join(" ", result)));
                })
                    .consumes(FORM_MEDIA_TYPES);
                routes.asyncPOST(FN + "/text-part", (request, pathVariables, body) -> {
                    FormParts parts = body.parts();
                    List<String> result = new ArrayList<>();
                    return parts.part("name", part -> part.text().thenAccept(value -> result.add(part.name() + "=" + value + " " + part.isFile())))
                        .thenApply(found -> ok(String.join(" ", result)));
                })
                    .consumes(FORM_MEDIA_TYPES);
                routes.asyncPOST(FN + "/optional-part", (request, pathVariables, body) -> {
                    FormParts parts = body.parts();
                    List<String> result = new ArrayList<>();
                    return parts.part("cover", part -> {
                        result.add("cover " + part.fileName());
                        return CompletableFuture.completedFuture(null);
                    }).thenApply(found -> ok(found ? String.join(" ", result) : "no cover"));
                })
                    .consumes(FORM_MEDIA_TYPES);
                routes.asyncPOST(FN + "/parts", (request, pathVariables, body) -> forEach(body.parts()).thenApply(FormArgumentsTest::ok))
                    .consumes(FORM_MEDIA_TYPES);
            };
        }
    }
}
