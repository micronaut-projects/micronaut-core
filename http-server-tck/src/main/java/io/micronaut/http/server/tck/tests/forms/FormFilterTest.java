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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormPart;
import io.micronaut.http.form.FormParts;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The types of {@code io.micronaut.http.form} and {@link AsyncRequestBody} as parameters of the
 * request filter methods of a {@link ServerFilter}: the form is read once for the request, and a
 * filter that reads it leaves it readable for the controller. A body a filter cleared or replaced
 * is the form of the controller, and the bytes of the request are not read then.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class FormFilterTest {
    public static final String SPEC_NAME = "FormFilterTest";
    private static final String BASE = "/form-filter";
    private static final String SEEN = "form-filter-seen";

    @Test
    void aFilterReadsTheFormDataAndTheControllerReadsItAgain() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("filter:Fred|Fred Prague same", call(server, urlEncoded("/data", "name=Fred&city=Prague")).body());
        }
    }

    @Test
    void aFilterReadsATextFieldAndTheControllerReadsTheForm() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("filter:Fred|Fred Prague", call(server, urlEncoded("/field", "name=Fred&city=Prague")).body());
        }
    }

    @Test
    void aFilterReadsTheFormOfTheAsyncBodyAndTheControllerReadsTheFormData() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("filter:Fred|Fred", call(server, urlEncoded("/async-form", "name=Fred")).body());
        }
    }

    @Test
    void aClearedBodyIsAFormWithoutFields() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("[] missing null", call(server, urlEncoded("/cleared", "name=Fred&city=Prague")).body());
            assertEquals("[]", call(server, urlEncoded("/cleared-async", "name=Fred")).body());
        }
    }

    @Test
    void aFormSetByAFilterIsTheForm() throws IOException {
        try (ServerUnderTest server = server()) {
            // the filter replaces the form it read with another one: the controller never sees the original
            assertEquals("Wilma Wilma [name]", call(server, urlEncoded("/replaced", "name=Fred")).body());
        }
    }

    @Test
    void aFilterThatDoesNotSetTheBodyKeepsTheForm() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("Fred", call(server, urlEncoded("/header", "name=Fred")).body());
        }
    }

    @Test
    @Tag("multipart")
    void aFilterReadsTheFilesAndTheControllerReadsThemAgain() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("filter:avatar.txt Fred|Fred avatar.txt picture same", call(server, multipart("/files", avatarForm())).body());
        }
    }

    @Test
    @Tag("multipart")
    void aFilterReadsTheFormDataOfAMultipartForm() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("filter:Fred picture|Fred picture", call(server, multipart("/multipart-data", avatarForm())).body());
        }
    }

    @Test
    @Tag("multipart")
    void aClearedMultipartBodyHasNoFilesNorParts() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("null null", call(server, multipart("/cleared-files", avatarForm())).body());
            assertEquals("parts:", call(server, multipart("/cleared-parts", avatarForm())).body());
        }
    }

    @Test
    @Tag("multipart")
    void aFilterStreamsTheParts() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("name=Fred;avatar.txt=7;", call(server, multipart("/filter-parts", avatarForm())).body());
        }
    }

    @Test
    @Tag("multipart")
    void aLargeUploadIsStreamedToTheController() throws IOException {
        byte[] large = new byte[8 * 1024 * 1024];
        java.util.Arrays.fill(large, (byte) 'x');
        MultipartBody body = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("archive", "archive.bin", MediaType.APPLICATION_OCTET_STREAM_TYPE, large)
            .build();
        try (ServerUnderTest server = server(Map.of(
            "micronaut.server.max-request-size", 16 * 1024 * 1024,
            "micronaut.server.multipart.max-file-size", 16 * 1024 * 1024))) {
            Response response = call(server, multipart("/stream", body));
            assertEquals(HttpStatus.OK, response.status(), response.body());
            assertEquals("Fred archive.bin " + large.length, response.body());
        }
    }

    private static MultipartBody avatarForm() {
        return MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, "picture".getBytes(StandardCharsets.UTF_8))
            .build();
    }

    private static HttpRequest<?> urlEncoded(String path, String body) {
        return HttpRequest.POST(BASE + path, body).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE);
    }

    private static HttpRequest<?> multipart(String path, MultipartBody body) {
        return HttpRequest.POST(BASE + path, body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE);
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

    private static String seen(HttpRequest<?> request) {
        return request.getAttribute(SEEN, String.class).orElse("none");
    }

    record Response(HttpStatus status, String body) {
    }

    @ServerFilter(BASE)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FormFilters {

        @RequestFilter("/data")
        void data(HttpRequest<?> request, FormData form) {
            request.setAttribute(SEEN, "filter:" + form.getString("name"));
            request.setAttribute(SEEN + "-form", form);
        }

        @RequestFilter("/field")
        void field(HttpRequest<?> request, @Part("name") String name) {
            request.setAttribute(SEEN, "filter:" + name);
        }

        @RequestFilter("/async-form")
        CompletionStage<@Nullable HttpResponse<?>> asyncForm(HttpRequest<?> request, AsyncRequestBody body) {
            return body.form().thenApply(form -> {
                request.setAttribute(SEEN, "filter:" + form.getString("name"));
                return null;
            });
        }

        @RequestFilter({"/cleared", "/cleared-async", "/cleared-files", "/cleared-parts"})
        MutableHttpRequest<?> clear(MutableHttpRequest<?> request) {
            return request.body(null);
        }

        @RequestFilter("/replaced")
        MutableHttpRequest<?> replace(MutableHttpRequest<?> request, FormData form) {
            return request.body(new SingleFieldForm("name", "Wilma".equals(form.getString("name")) ? "Fred" : "Wilma"));
        }

        @RequestFilter("/header")
        MutableHttpRequest<?> header(MutableHttpRequest<?> request) {
            return request.header("X-Filtered", "true");
        }

        @RequestFilter("/files")
        void files(HttpRequest<?> request, @Part("avatar") FileUpload avatar, @Part("name") String name) {
            request.setAttribute(SEEN, "filter:" + avatar.fileName() + " " + name);
            request.setAttribute(SEEN + "-file", avatar);
        }

        @RequestFilter("/multipart-data")
        CompletionStage<@Nullable HttpResponse<?>> multipartData(HttpRequest<?> request, FormData form) {
            return form.getFile("avatar").bytes(1024).thenApply(bytes -> {
                request.setAttribute(SEEN, "filter:" + form.getString("name") + " " + new String(bytes, StandardCharsets.UTF_8));
                return null;
            });
        }

        @RequestFilter("/filter-parts")
        CompletionStage<@Nullable HttpResponse<?>> parts(HttpRequest<?> request, FormParts parts) {
            StringBuilder result = new StringBuilder();
            return parts.forEach(part -> {
                if (part.isFile()) {
                    return part.bytes(1024).thenAccept(bytes -> result.append(part.fileName()).append('=').append(bytes.length).append(';'));
                }
                return part.text().thenAccept(value -> result.append(part.name()).append('=').append(value).append(';'));
            }).thenApply(done -> {
                request.setAttribute(SEEN, result.toString());
                return null;
            });
        }
    }

    @Controller(BASE)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FormController {

        @Post(uri = "/data", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        @SuppressWarnings("ReferenceEquality") // the same form
        String data(HttpRequest<?> request, FormData form, @Part("name") String name, String city) {
            String same = request.getAttribute(SEEN + "-form").orElse(null) == form ? "same" : "different";
            return seen(request) + "|" + name + " " + city + " " + same;
        }

        @Post(uri = "/field", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        String field(HttpRequest<?> request, FormData form) {
            return seen(request) + "|" + form.getString("name") + " " + form.getString("city");
        }

        @Post(uri = "/async-form", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        String asyncForm(HttpRequest<?> request, FormData form) {
            return seen(request) + "|" + form.getString("name");
        }

        @Post(uri = "/cleared", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        String cleared(FormData form, @Nullable @Part("name") String name) {
            return form.names() + " " + form.getString("city", "missing") + " " + name;
        }

        @Post(uri = "/cleared-async", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> clearedAsync(AsyncRequestBody body) {
            return body.form().thenApply(form -> form.names().toString());
        }

        @Post(uri = "/replaced", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        String replaced(FormData form, @Part("name") String name) {
            return form.getString("name") + " " + name + " " + form.names();
        }

        @Post(uri = "/header", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        String header(FormData form) {
            return form.getString("name");
        }

        @Post(uri = "/files", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        @SuppressWarnings("ReferenceEquality") // the same file
        CompletionStage<String> files(HttpRequest<?> request, @Part("avatar") FileUpload avatar, @Part("name") String name) {
            String same = request.getAttribute(SEEN + "-file").orElse(null) == avatar ? "same" : "different";
            return avatar.bytes(1024).thenApply(bytes -> seen(request) + "|" + name + " " + avatar.fileName() + " "
                + new String(bytes, StandardCharsets.UTF_8) + " " + same);
        }

        @Post(uri = "/multipart-data", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> multipartData(HttpRequest<?> request, FormData form) {
            // the filter consumed the content of the file: the controller reads the same form
            return CompletableFuture.completedFuture(seen(request) + "|" + form.getString("name") + " "
                + seen(request).substring(seen(request).lastIndexOf(' ') + 1));
        }

        @Post(uri = "/cleared-files", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String clearedFiles(@Nullable @Part("avatar") FileUpload avatar, @Nullable @Part("name") String name) {
            return (avatar == null ? "null" : avatar.fileName()) + " " + name;
        }

        @Post(uri = "/cleared-parts", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> clearedParts(FormParts parts) {
            StringBuilder result = new StringBuilder("parts:");
            return parts.forEach(part -> {
                result.append(part.name()).append(';');
                return CompletableFuture.completedFuture(null);
            }).thenApply(done -> result.toString());
        }

        @Post(uri = "/filter-parts", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String filterParts(HttpRequest<?> request) {
            return seen(request);
        }

        @Post(uri = "/stream", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> stream(@Part("name") String name, @Part("archive") FormPart archive) {
            AtomicLong size = new AtomicLong();
            // the content is read as it arrives, never buffered whole
            return archive.transferTo(new java.io.OutputStream() {
                @Override
                public void write(int b) {
                    size.incrementAndGet();
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    size.addAndGet(len);
                }
            })
                .thenApply(done -> name + " " + archive.fileName() + " " + size.get());
        }
    }

    /**
     * A form with one text field, set as the body by a filter.
     *
     * @param fieldName The name of the field
     * @param value     The value of the field
     */
    private record SingleFieldForm(String fieldName, String value) implements FormData {

        @Override
        public Set<String> names() {
            return Set.of(fieldName);
        }

        @Override
        public boolean contains(String name) {
            return fieldName.equals(name);
        }

        @Override
        public List<String> getValues(String name) {
            return contains(name) ? List.of(value) : List.of();
        }

        @Override
        public <T> T get(String name, Class<T> type) {
            return find(name, type).orElseThrow();
        }

        @Override
        public <T> java.util.Optional<T> find(String name, Class<T> type) {
            return contains(name) && type == String.class ? java.util.Optional.of(type.cast(value)) : java.util.Optional.empty();
        }

        @Override
        public List<FileUpload> getFiles(String name) {
            return List.of();
        }

        @Override
        public CompletionStage<Void> closeAsync() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
        }
    }
}
