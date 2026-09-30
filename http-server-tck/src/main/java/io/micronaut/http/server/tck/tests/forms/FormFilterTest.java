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
import io.micronaut.core.type.Argument;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.CloseableByteBody;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private static final String CONSUMPTION = "/consumption/";
    private static final String MULTIPART_BODY = "/multipart-body/";

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
    void aFilterReadsTheFormDataArgumentAndTheControllerReadsTheFormData() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("filter:Fred|Fred", call(server, urlEncoded("/async-form-argument", "name=Fred")).body());
        }
    }

    @Test
    void aRouteCannotReadTheFormAFilterConsumed() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String kind : List.of("form-consumed", "parts-consumed")) {
                String read = kind.startsWith("form") ? "form()" : "parts()";
                for (String route : List.of("data", "part", "async")) {
                    assertConsumed(call(server, urlEncoded(CONSUMPTION + kind + "/" + route, "name=Fred")), read);
                }
                // a route that does not read the body answers
                assertEquals("none", call(server, urlEncoded(CONSUMPTION + kind + "/none", "name=Fred")).body());
            }
        }
    }

    @Test
    @Tag("multipart")
    void aRouteCannotReadTheMultipartFormAFilterConsumed() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String kind : List.of("form-consumed", "parts-consumed")) {
                String read = kind.startsWith("form") ? "form()" : "parts()";
                for (String route : List.of("data", "part", "file", "async")) {
                    assertConsumed(call(server, multipart(CONSUMPTION + kind + "/" + route, avatarForm())), read);
                }
                assertEquals("none", call(server, multipart(CONSUMPTION + kind + "/none", avatarForm())).body());
            }
        }
    }

    @Test
    void aFilterReadsACopyOfTheFormAndTheRouteReadsTheForm() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("filter:Fred|Fred", call(server, urlEncoded(CONSUMPTION + "form-copied/data", "name=Fred")).body());
            assertEquals("filter:Fred|Fred", call(server, urlEncoded(CONSUMPTION + "form-copied/part", "name=Fred")).body());
            assertEquals("filter:Fred|Fred", call(server, urlEncoded(CONSUMPTION + "form-copied/async", "name=Fred")).body());
            assertEquals("name;|Fred", call(server, urlEncoded(CONSUMPTION + "parts-copied/data", "name=Fred")).body());
            assertEquals("name;|Fred", call(server, urlEncoded(CONSUMPTION + "parts-copied/part", "name=Fred")).body());
            assertEquals("name;|Fred", call(server, urlEncoded(CONSUMPTION + "parts-copied/async", "name=Fred")).body());
        }
    }

    @Test
    @Tag("multipart")
    void aFilterReadsACopyOfTheMultipartFormAndTheRouteReadsTheForm() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String route : List.of("data", "part", "async")) {
                assertEquals("filter:Fred|Fred", call(server, multipart(CONSUMPTION + "form-copied/" + route, avatarForm())).body());
                // the filter streams through the parts of its copy, the route reads the same form
                assertEquals("name;avatar;|Fred", call(server, multipart(CONSUMPTION + "parts-copied/" + route, avatarForm())).body());
            }
            assertEquals("filter:Fred|picture", call(server, multipart(CONSUMPTION + "form-copied/file", avatarForm())).body());
            assertEquals("name;avatar;|picture", call(server, multipart(CONSUMPTION + "parts-copied/file", avatarForm())).body());
        }
    }

    @Test
    void aFilterReadsTheFormOfACopyAndTheRouteReadsTheBodyInAnyWay() throws IOException {
        try (ServerUnderTest server = server()) {
            String form = "name=Fred&city=Prague";
            for (String route : List.of("text", "bytes", "taken")) {
                assertEquals("filter:Fred|" + form, call(server, urlEncoded(CONSUMPTION + "form-copied/" + route, form)).body(), route);
            }
            for (String route : List.of("decoded", "argument")) {
                assertEquals("filter:Fred|Fred Prague", call(server, urlEncoded(CONSUMPTION + "form-copied/" + route, form)).body(), route);
            }
            assertEquals("filter:Fred|name=Fred;city=Prague;", call(server, urlEncoded(CONSUMPTION + "form-copied/parts", form)).body());
            // the bytes are left to the elements, which a form does not have
            assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, call(server, urlEncoded(CONSUMPTION + "form-copied/elements", form)).status());
            // the form of the route is the form the filter read: it is not decoded a second time
            assertEquals("filter:Fred|Fred same", call(server, urlEncoded(CONSUMPTION + "form-copied/same-data", form)).body());
            assertEquals("filter:Fred|Fred same", call(server, urlEncoded(CONSUMPTION + "form-copied/same-async", form)).body());
            assertEquals("filter:Fred|Fred same " + form, call(server, urlEncoded(CONSUMPTION + "form-copied/data-and-text", form)).body());
        }
    }

    @Test
    @Tag("multipart")
    void aFilterReadsTheFormOfACopyAndTheRouteReadsTheMultipartBodyInAnyWay() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String route : List.of("text", "bytes", "taken")) {
                Response response = call(server, multipart(CONSUMPTION + "form-copied/" + route, avatarForm()));
                assertEquals(HttpStatus.OK, response.status(), route + ": " + response.body());
                assertTrue(response.body().startsWith("filter:Fred|"), route + ": " + response.body());
                // the whole body, as it was sent
                assertTrue(response.body().contains("name=\"avatar\"") && response.body().contains("picture"), route + ": " + response.body());
            }
            assertEquals("filter:Fred|name=Fred;avatar=picture;", call(server, multipart(CONSUMPTION + "form-copied/parts", avatarForm())).body());
            assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, call(server, multipart(CONSUMPTION + "form-copied/elements", avatarForm())).status());
            // the form of the route is the form the filter read, with the files it stored once
            assertEquals("filter:Fred|Fred same", call(server, multipart(CONSUMPTION + "form-copied/same-data", avatarForm())).body());
            assertEquals("filter:Fred|Fred same", call(server, multipart(CONSUMPTION + "form-copied/same-async", avatarForm())).body());
            Response both = call(server, multipart(CONSUMPTION + "form-copied/data-and-text", avatarForm()));
            assertTrue(both.body().startsWith("filter:Fred|Fred same ") && both.body().contains("picture"), both.body());
            assertEquals("filter:Fred|picture same", call(server, multipart(CONSUMPTION + "form-copied/same-file", avatarForm())).body());
        }
    }

    @Test
    @Tag("multipart")
    void theFormOfACopyIsHeldToTheBufferLimit() throws IOException {
        byte[] large = new byte[16 * 1024];
        java.util.Arrays.fill(large, (byte) 'x');
        MultipartBody body = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.bin", MediaType.APPLICATION_OCTET_STREAM_TYPE, large)
            .build();
        try (ServerUnderTest server = server(Map.of("micronaut.server.max-request-buffer-size", 1024))) {
            // the body is kept for the route while the form is read from the copy
            assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, call(server, multipart(CONSUMPTION + "form-copied/text", body)).status());
            assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, call(server, multipart(CONSUMPTION + "form-copied/data", body)).status());
        }
    }

    @Test
    @Tag("multipart")
    void aCopyOfAMultipartFormIsHeldToTheBufferLimit() throws IOException {
        byte[] large = new byte[16 * 1024];
        java.util.Arrays.fill(large, (byte) 'x');
        MultipartBody body = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.bin", MediaType.APPLICATION_OCTET_STREAM_TYPE, large)
            .build();
        try (ServerUnderTest server = server(Map.of("micronaut.server.max-request-buffer-size", 1024))) {
            // the copy streams through the parts, but the body is kept for the route
            assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, call(server, multipart(CONSUMPTION + "parts-copied/data", body)).status());
        }
    }

    private static void assertConsumed(Response response, String read) {
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.status(), read + ": " + response.body());
        assertTrue(response.body().contains("The body of the request was already read with " + read), response.body());
        assertTrue(response.body().contains("read it with copy() in the filter to leave it for the route"), response.body());
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

    @Test
    @Tag("multipart")
    void aFilterStreamsAFormPartAndARouteThatDoesNotReadTheBodyAnswers() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = call(server, multipart(CONSUMPTION + "part-consumed/seen", avatarForm()));
            assertEquals(HttpStatus.OK, response.status(), response.body());
            assertEquals("filter:avatar.txt=picture", response.body());
            assertEquals("none", call(server, multipart(CONSUMPTION + "part-consumed/none", avatarForm())).body());
        }
    }

    @Test
    @Tag("multipart")
    void aRouteCannotReadTheFormAFilterFormPartConsumed() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String route : List.of("part", "file", "async")) {
                Response response = call(server, multipart(CONSUMPTION + "part-consumed/" + route, avatarForm()));
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.status(), route + ": " + response.body());
                assertTrue(response.body().contains("The body of the request was already read with the FormPart argument"), response.body());
            }
            // a route that reads the whole form refuses the part before the filter reads it, like
            // the FormParts of a filter
            Response response = call(server, multipart(CONSUMPTION + "part-consumed/data", avatarForm()));
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.status(), response.body());
            assertTrue(response.body().contains("The form of the request is read whole by"), response.body());
            assertTrue(response.body().contains("which streams its part"), response.body());
        }
    }

    @Test
    @Tag("multipart")
    void aFilterReadsAnOptionalFileUpload() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("filter:avatar.txt", call(server, multipart("/optional-file", avatarForm())).body());
            assertEquals("filter:empty", call(server, multipart("/optional-file", nameForm())).body());
            assertEquals("filter:avatar.txt", call(server, multipart("/nullable-file", avatarForm())).body());
            assertEquals("filter:null", call(server, multipart("/nullable-file", nameForm())).body());
        }
    }

    @Test
    @Tag("multipart")
    void aFilterReadsAnOptionalFormPart() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("filter:avatar.txt=picture", call(server, multipart("/optional-part", avatarForm())).body());
            assertEquals("filter:empty", call(server, multipart("/optional-part", nameForm())).body());
            assertEquals("filter:avatar.txt=picture", call(server, multipart("/nullable-part", avatarForm())).body());
            assertEquals("filter:null", call(server, multipart("/nullable-part", nameForm())).body());
        }
    }

    @Test
    void aFilterReadsAnOptionalTextField() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("filter:Prague", call(server, urlEncoded("/optional-text", "name=Fred&city=Prague")).body());
            assertEquals("filter:empty", call(server, urlEncoded("/optional-text", "name=Fred")).body());
            assertEquals("filter:Prague", call(server, urlEncoded("/nullable-text", "name=Fred&city=Prague")).body());
            assertEquals("filter:null", call(server, urlEncoded("/nullable-text", "name=Fred")).body());
        }
    }

    @Test
    void aFilterReadsAnOptionalCollectionOfATextField() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("filter:[one, two] [one, two] [one, two]", call(server, urlEncoded("/optional-list", "tags=one&name=Fred&tags=two")).body());
            assertEquals("filter:null empty empty", call(server, urlEncoded("/optional-list", "name=Fred")).body());
        }
    }

    @Test
    @Tag("multipart")
    void aFilterArgumentCannotReadAFormThatIsStreamed() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String route : List.of("streamed-file", "streamed-data")) {
                Response response = call(server, multipart(MULTIPART_BODY + route, avatarForm()));
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.status(), route + ": " + response.body());
                assertTrue(response.body().contains("The form of the request is streamed by"), response.body());
            }
            Response response = call(server, multipart(MULTIPART_BODY + "collected-parts", avatarForm()));
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.status(), response.body());
            assertTrue(response.body().contains("The form of the request is read whole by"), response.body());
        }
    }

    @Test
    @Tag("multipart")
    void aMultipartFormReplacedByAFilterIsTheFormTheFilterSet() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("Wilma Wilma [name] null [] empty empty", call(server, multipart(MULTIPART_BODY + "replaced", avatarForm())).body());
            assertEquals("Wilma", call(server, multipart(MULTIPART_BODY + "replaced-async", avatarForm())).body());
            assertEquals("true OptionalLong.empty Wilma", call(server, multipart(MULTIPART_BODY + "replaced-body", avatarForm())).body());
            Response text = call(server, multipart(MULTIPART_BODY + "replaced-text", avatarForm()));
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, text.status(), text.body());
            assertTrue(text.body().contains("a filter replaced the body with a decoded object"), text.body());
            // a text field asked for as a file
            assertEquals(HttpStatus.BAD_REQUEST, call(server, multipart(MULTIPART_BODY + "replaced-file", avatarForm())).status());
            // a body that is not a form
            assertEquals(HttpStatus.BAD_REQUEST, call(server, multipart(MULTIPART_BODY + "not-a-form", avatarForm())).status());
        }
    }

    @Test
    @Tag("multipart")
    void aClearedMultipartBodyIsReadWithAnAsyncBody() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("false OptionalLong[0] [] false parts: 0", call(server, multipart(MULTIPART_BODY + "cleared-async", avatarForm())).body());
        }
    }

    private static MultipartBody nameForm() {
        return MultipartBody.builder()
            .addPart("name", "Fred")
            .build();
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
            return body.copy().form().thenApply(form -> {
                request.setAttribute(SEEN, "filter:" + form.getString("name"));
                return null;
            });
        }

        @RequestFilter("/async-form-argument")
        void asyncFormArgument(HttpRequest<?> request, FormData form) {
            request.setAttribute(SEEN, "filter:" + form.getString("name"));
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

        @RequestFilter("/optional-file")
        void optionalFile(HttpRequest<?> request, @Part("avatar") Optional<FileUpload> avatar) {
            request.setAttribute(SEEN, "filter:" + avatar.map(FileUpload::fileName).orElse("empty"));
        }

        @RequestFilter("/nullable-file")
        void nullableFile(HttpRequest<?> request, @Nullable @Part("avatar") FileUpload avatar) {
            request.setAttribute(SEEN, "filter:" + (avatar == null ? "null" : avatar.fileName()));
        }

        @RequestFilter("/optional-part")
        CompletionStage<@Nullable HttpResponse<?>> optionalPart(HttpRequest<?> request, @Part("avatar") Optional<FormPart> avatar) {
            return readPart(request, avatar.orElse(null), "empty");
        }

        @RequestFilter("/nullable-part")
        CompletionStage<@Nullable HttpResponse<?>> nullablePart(HttpRequest<?> request, @Nullable @Part("avatar") FormPart avatar) {
            return readPart(request, avatar, "null");
        }

        private static CompletionStage<@Nullable HttpResponse<?>> readPart(HttpRequest<?> request, @Nullable FormPart part, String missing) {
            if (part == null) {
                request.setAttribute(SEEN, "filter:" + missing);
                return CompletableFuture.completedFuture(null);
            }
            return part.bytes(1024).thenApply(bytes -> {
                request.setAttribute(SEEN, "filter:" + part.fileName() + "=" + new String(bytes, StandardCharsets.UTF_8));
                return null;
            });
        }

        @RequestFilter("/optional-text")
        void optionalText(HttpRequest<?> request, @Part("city") Optional<String> city) {
            request.setAttribute(SEEN, "filter:" + city.orElse("empty"));
        }

        @RequestFilter("/optional-list")
        void optionalList(HttpRequest<?> request,
                          @Nullable @Part("tags") List<String> list,
                          @Part("tags") Optional<List<String>> optionalList,
                          @Part("tags") Optional<String[]> optionalArray) {
            request.setAttribute(SEEN, "filter:" + list + " " + optionalList.map(List::toString).orElse("empty")
                + " " + optionalArray.map(Arrays::toString).orElse("empty"));
        }

        @RequestFilter("/nullable-text")
        void nullableText(HttpRequest<?> request, @Nullable @Part("city") String city) {
            request.setAttribute(SEEN, "filter:" + city);
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

        @Post(uri = "/async-form-argument", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        String asyncFormArgument(HttpRequest<?> request, FormData form) {
            return seen(request) + "|" + form.getString("name");
        }

        @Post(uri = "/optional-list", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        String optionalList(HttpRequest<?> request) {
            return seen(request);
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

        @Post(uris = {"/optional-file", "/nullable-file", "/optional-part", "/nullable-part"}, consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String optionalFile(HttpRequest<?> request) {
            return seen(request);
        }

        @Post(uris = {"/optional-text", "/nullable-text"}, consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        String optionalText(HttpRequest<?> request) {
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
     * Filters that read the form with an {@link AsyncRequestBody}: {@code form()} and
     * {@code parts()} consume the body, the reads of a copy leave it for the route.
     */
    @ServerFilter(BASE + "/consumption")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ConsumptionFilters {

        @RequestFilter("/form-consumed/**")
        CompletionStage<@Nullable HttpResponse<?>> formConsumed(AsyncRequestBody body) {
            return body.form().thenApply(form -> null);
        }

        @RequestFilter("/parts-consumed/**")
        CompletionStage<@Nullable HttpResponse<?>> partsConsumed(HttpRequest<?> request, AsyncRequestBody body) {
            return names(request, body.parts());
        }

        @RequestFilter("/part-consumed/**")
        CompletionStage<@Nullable HttpResponse<?>> partConsumed(HttpRequest<?> request, @Part("avatar") FormPart avatar) {
            // streams its own part: the body is consumed
            return avatar.bytes(1024).thenApply(bytes -> {
                request.setAttribute(SEEN, "filter:" + avatar.fileName() + "=" + new String(bytes, StandardCharsets.UTF_8));
                return null;
            });
        }

        @RequestFilter("/form-copied/**")
        CompletionStage<@Nullable HttpResponse<?>> formCopied(HttpRequest<?> request, AsyncRequestBody body) {
            return body.copy().form().thenApply(form -> {
                request.setAttribute(SEEN, "filter:" + form.getString("name"));
                request.setAttribute(SEEN + "-form", form);
                return null;
            });
        }

        @RequestFilter("/parts-copied/**")
        CompletionStage<@Nullable HttpResponse<?>> partsCopied(HttpRequest<?> request, AsyncRequestBody body) {
            return names(request, body.copy().parts());
        }

        /**
         * Stream through the parts: the names, the text of the text fields, and the files unread.
         */
        private static CompletionStage<@Nullable HttpResponse<?>> names(HttpRequest<?> request, FormParts parts) {
            StringBuilder names = new StringBuilder();
            return parts.forEach(part -> {
                names.append(part.name()).append(';');
                if (part.isFile()) {
                    return CompletableFuture.completedFuture(null);
                }
                return part.text();
            }).thenApply(done -> {
                request.setAttribute(SEEN, names.toString());
                return null;
            });
        }
    }

    @Controller(BASE + "/consumption")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ConsumptionController {

        @Post(uri = "/{kind}/data", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        String data(@PathVariable String kind, HttpRequest<?> request, FormData form) {
            return seen(request) + "|" + form.getString("name");
        }

        @Post(uri = "/{kind}/part", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        String part(@PathVariable String kind, HttpRequest<?> request, @Part("name") String name) {
            return seen(request) + "|" + name;
        }

        @Post(uri = "/{kind}/file", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> file(@PathVariable String kind, HttpRequest<?> request, @Part("avatar") FileUpload avatar) {
            return avatar.bytes(1024).thenApply(bytes -> seen(request) + "|" + new String(bytes, StandardCharsets.UTF_8));
        }

        @Post(uri = "/{kind}/async", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> async(@PathVariable String kind, HttpRequest<?> request, AsyncRequestBody body) {
            return body.form().thenApply(form -> seen(request) + "|" + form.getString("name"));
        }

        @Post(uri = "/{kind}/text", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> text(@PathVariable String kind, HttpRequest<?> request, AsyncRequestBody body) {
            return body.text().thenApply(text -> seen(request) + "|" + text);
        }

        @Post(uri = "/{kind}/bytes", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> bytes(@PathVariable String kind, HttpRequest<?> request, AsyncRequestBody body) {
            return body.bytes(4096).thenApply(bytes -> seen(request) + "|" + new String(bytes, StandardCharsets.UTF_8));
        }

        @Post(uri = "/{kind}/taken", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> taken(@PathVariable String kind, HttpRequest<?> request, AsyncRequestBody body) {
            CloseableByteBody taken = body.takeBody();
            return taken.buffer().thenApply(buffered -> {
                try (buffered) {
                    return seen(request) + "|" + buffered.toString(StandardCharsets.UTF_8);
                }
            });
        }

        @Post(uri = "/{kind}/decoded", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> decoded(@PathVariable String kind, HttpRequest<?> request, AsyncRequestBody body) {
            return body.body(Argument.mapOf(String.class, String.class)).thenApply(map -> seen(request) + "|" + map.get("name") + " " + map.get("city"));
        }

        @Post(uri = "/{kind}/argument", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        String argument(@PathVariable String kind, HttpRequest<?> request, @Body Map<String, String> body) {
            return seen(request) + "|" + body.get("name") + " " + body.get("city");
        }

        @Post(uri = "/{kind}/elements", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> elements(@PathVariable String kind, HttpRequest<?> request, AsyncRequestBody body) {
            return body.elements(String.class).forEach(element -> CompletableFuture.completedFuture(null)).thenApply(done -> seen(request));
        }

        @Post(uri = "/{kind}/parts", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> parts(@PathVariable String kind, HttpRequest<?> request, AsyncRequestBody body) {
            StringBuilder parts = new StringBuilder();
            return body.parts().forEach(part -> part.text().thenAccept(text -> parts.append(part.name()).append('=').append(text).append(';')))
                .thenApply(done -> seen(request) + "|" + parts);
        }

        @Post(uri = "/{kind}/same-data", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        String sameData(@PathVariable String kind, HttpRequest<?> request, FormData form) {
            return seen(request) + "|" + form.getString("name") + " " + same(request, form);
        }

        @Post(uri = "/{kind}/same-async", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> sameAsync(@PathVariable String kind, HttpRequest<?> request, AsyncRequestBody body) {
            return body.form().thenApply(form -> seen(request) + "|" + form.getString("name") + " " + same(request, form));
        }

        @Post(uri = "/{kind}/data-and-text", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> dataAndText(@PathVariable String kind, HttpRequest<?> request, FormData form, AsyncRequestBody body) {
            return body.text().thenApply(text -> seen(request) + "|" + form.getString("name") + " " + same(request, form) + " " + text);
        }

        @Post(uri = "/{kind}/same-file", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        @SuppressWarnings("ReferenceEquality") // the same file
        CompletionStage<String> sameFile(@PathVariable String kind, HttpRequest<?> request, @Part("avatar") FileUpload avatar) {
            FormData form = request.getAttribute(SEEN + "-form", FormData.class).orElse(null);
            String same = form != null && form.findFile("avatar").orElse(null) == avatar ? "same" : "different";
            return avatar.bytes(1024).thenApply(bytes -> seen(request) + "|" + new String(bytes, StandardCharsets.UTF_8) + " " + same);
        }

        @SuppressWarnings("ReferenceEquality") // the same form
        private static String same(HttpRequest<?> request, FormData form) {
            return request.getAttribute(SEEN + "-form").orElse(null) == form ? "same" : "different";
        }

        @Post(uri = "/{kind}/none", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        String none(@PathVariable String kind) {
            return "none";
        }

        @Post(uri = "/{kind}/seen", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        String seenRoute(@PathVariable String kind, HttpRequest<?> request) {
            return seen(request);
        }

        @Error(exception = IllegalStateException.class)
        HttpResponse<String> illegalState(IllegalStateException error) {
            // the message of the failure, e.g. of a read of a body a filter consumed, still a 500
            return HttpResponse.<String>serverError(error.getMessage()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }

    /**
     * Filters of a multipart request: form arguments of a route that streams the form or reads it
     * whole, and a body replaced or cleared.
     */
    @ServerFilter(BASE + "/multipart-body")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MultipartBodyFilters {

        @RequestFilter("/streamed-file")
        void streamedFile(HttpRequest<?> request, @Part("avatar") FileUpload avatar) {
            request.setAttribute(SEEN, "not reached");
        }

        @RequestFilter("/streamed-data")
        void streamedData(HttpRequest<?> request, FormData form) {
            request.setAttribute(SEEN, "not reached");
        }

        @RequestFilter("/collected-parts")
        void collectedParts(HttpRequest<?> request, FormParts parts) {
            request.setAttribute(SEEN, "not reached");
        }

        @RequestFilter({"/replaced", "/replaced-async", "/replaced-body", "/replaced-text", "/replaced-file"})
        MutableHttpRequest<?> replace(MutableHttpRequest<?> request) {
            return request.body(new SingleFieldForm("name", "Wilma"));
        }

        @RequestFilter("/not-a-form")
        MutableHttpRequest<?> notAForm(MutableHttpRequest<?> request) {
            return request.body(new StringBuilder("not a form"));
        }

        @RequestFilter("/cleared-async")
        MutableHttpRequest<?> clear(MutableHttpRequest<?> request) {
            return request.body(null);
        }
    }

    @Controller(BASE + "/multipart-body")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MultipartBodyController {

        @Post(uri = "/streamed-file", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String streamedFile(FormParts parts) {
            return "not reached";
        }

        @Post(uri = "/streamed-data", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String streamedData(FormParts parts) {
            return "not reached";
        }

        @Post(uri = "/collected-parts", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String collectedParts(FormData form) {
            return "not reached";
        }

        @Post(uri = "/replaced", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String replaced(FormData form, @Part("name") String name, @Nullable FileUpload avatar, @Nullable List<FileUpload> docs,
                        Optional<FormPart> other, Optional<FileUpload> cover) {
            return form.getString("name") + " " + name + " " + form.names() + " " + avatar + " " + form.getFiles("docs")
                + " " + other.map(FormPart::name).orElse("empty") + " " + cover.map(FileUpload::name).orElse("empty")
                + (docs == null ? "" : " docs");
        }

        @Post(uri = "/replaced-async", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> replacedAsync(AsyncRequestBody body) {
            return body.form().thenApply(form -> form.getString("name"));
        }

        @Post(uri = "/replaced-body", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> replacedBody(AsyncRequestBody body) {
            return body.body(FormData.class)
                .thenApply(form -> body.hasBody() + " " + body.expectedBodySize() + " " + form.getString("name"));
        }

        @Post(uri = "/replaced-text", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> replacedText(AsyncRequestBody body) {
            return body.text();
        }

        @Post(uri = "/replaced-file", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String replacedFile(@Part("name") FileUpload name) {
            return "not reached";
        }

        @Post(uri = "/not-a-form", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String notAForm(FormData form) {
            return "not reached";
        }

        @Post(uri = "/cleared-async", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> clearedAsync(AsyncRequestBody body) {
            String size = body.hasBody() + " " + body.expectedBodySize();
            FormParts parts = body.copy().parts();
            return body.copy().form().thenCompose(form -> parts.part("name", part -> part.text()).thenCompose(found -> names(parts)
                .thenApply(names -> {
                    try (CloseableByteBody taken = body.takeBody()) {
                        return size + " " + form.names() + " " + found + " " + names + " " + taken.expectedLength().orElse(-1);
                    }
                })));
        }

        private static CompletionStage<String> names(FormParts parts) {
            StringBuilder result = new StringBuilder("parts:");
            return parts.forEach(part -> {
                result.append(part.name()).append(';');
                return CompletableFuture.completedFuture(null);
            }).thenCompose(done -> parts.closeAsync()).thenApply(done -> {
                parts.close();
                return result.toString();
            });
        }

        @Error(exception = IllegalStateException.class)
        HttpResponse<String> illegalState(IllegalStateException error) {
            return HttpResponse.<String>serverError(error.getMessage()).contentType(MediaType.TEXT_PLAIN_TYPE);
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
