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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormPart;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.json.JsonSyntaxException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The controllers of the documentation about request bodies, answered the same by handler
 * routes: asynchronous handlers that read the body with
 * {@link io.micronaut.http.body.AsyncRequestBody}, and, where it applies, synchronous handlers
 * that receive the decoded body. Each request is sent to the controller, under {@code /ctl}, and
 * to the handler routes, under {@code /fn} and {@code /fn-sync}, and the responses are compared.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRoutesDocsParityTest {
    public static final String SPEC_NAME = "HandlerRoutesDocsParityTest";
    private static final String ASYNC = "/fn";
    private static final String SYNC = "/fn-sync";

    // MessageController

    @Test
    void echo() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = same(server, prefix -> HttpRequest.POST(prefix + "/receive/echo", "My Text").contentType(MediaType.TEXT_PLAIN_TYPE), ASYNC, SYNC);
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("My Text", response.body());
        }
    }

    @Test
    void echoPublisher() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = same(server, prefix -> HttpRequest.POST(prefix + "/receive/echo-publisher", "My Text").contentType(MediaType.TEXT_PLAIN_TYPE), ASYNC);
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("My Text", response.body());
        }
    }

    // json/PersonController

    @Test
    void jsonSave() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = same(server, prefix -> HttpRequest.POST(prefix + "/json/people", "{\"firstName\":\"Fred\",\"lastName\":\"Flintstone\",\"age\":45}"), ASYNC, SYNC);
            assertEquals(HttpStatus.CREATED, response.status());
            assertTrue(response.body().contains("\"firstName\":\"Fred\""), response.body());
        }
    }

    @Test
    void jsonSaveReactive() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = same(server, prefix -> HttpRequest.POST(prefix + "/json/people/saveReactive", "{\"firstName\":\"Wilma\",\"lastName\":\"Flintstone\",\"age\":36}"), ASYNC);
            assertEquals(HttpStatus.CREATED, response.status());
            assertTrue(response.body().contains("\"firstName\":\"Wilma\""), response.body());
        }
    }

    @Test
    void jsonSaveFuture() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = same(server, prefix -> HttpRequest.POST(prefix + "/json/people/saveFuture", "{\"firstName\":\"Pebbles\",\"lastName\":\"Flintstone\",\"age\":0}"), ASYNC);
            assertEquals(HttpStatus.CREATED, response.status());
            assertTrue(response.body().contains("\"firstName\":\"Pebbles\""), response.body());
        }
    }

    @Test
    void jsonSaveWithArgs() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = same(server, prefix -> HttpRequest.POST(prefix + "/json/people/saveWithArgs", "{\"firstName\":\"Dino\",\"lastName\":\"Flintstone\",\"age\":3}"), ASYNC, SYNC);
            assertEquals(HttpStatus.CREATED, response.status());
            assertTrue(response.body().contains("\"firstName\":\"Dino\""), response.body());
            assertTrue(response.body().contains("\"age\":3"), response.body());
        }
    }

    @Test
    void jsonSyntaxErrorIsAnsweredByTheErrorRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = same(server, prefix -> HttpRequest.POST(prefix + "/json/people", "{\""), ASYNC, SYNC);
            assertEquals(HttpStatus.BAD_REQUEST, response.status());
            assertTrue(response.body().contains("\"message\":\"Invalid JSON: Unexpected end-of-input"), response.body());
        }
    }

    // form/PersonController

    @Test
    void formSave() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = same(server, prefix -> formRequest(prefix + "/form/people"), ASYNC, SYNC);
            assertEquals(HttpStatus.CREATED, response.status());
            assertEquals("{\"firstName\":\"Fred\",\"lastName\":\"Flintstone\",\"age\":45}", response.body());
        }
    }

    @Test
    void formSaveWithArgs() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = same(server, prefix -> formRequest(prefix + "/form/people/saveWithArgs"), ASYNC, SYNC);
            assertEquals(HttpStatus.CREATED, response.status());
            assertEquals("{\"firstName\":\"Fred\",\"lastName\":\"Flintstone\",\"age\":45}", response.body());
        }
    }

    @Test
    void formSaveWithArgsOptional() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = same(server, prefix -> formRequest(prefix + "/form/people/saveWithArgsOptional"), ASYNC, SYNC);
            assertEquals(HttpStatus.CREATED, response.status());
            Response withoutAge = same(server, prefix -> HttpRequest.POST(prefix + "/form/people/saveWithArgsOptional", "firstName=Fred&lastName=Flintstone")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), ASYNC, SYNC);
            assertEquals("{\"firstName\":\"Fred\",\"lastName\":\"Flintstone\",\"age\":0}", withoutAge.body());
        }
    }

    // http/server/stream/StreamController

    @Test
    void streamRead() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = same(server, prefix -> HttpRequest.POST(prefix + "/stream/read", "My body").contentType(MediaType.TEXT_PLAIN_TYPE), ASYNC, SYNC);
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("My body", response.body());
        }
    }

    // server/upload controllers

    @Test
    @Tag("multipart") // a runner whose client cannot send multipart bodies can exclude this tag
    void upload() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = same(server, prefix -> upload(prefix + "/upload", "file", "file.json", "{\"title\":\"Foo\"}".getBytes(StandardCharsets.UTF_8)), ASYNC);
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("Uploaded", response.body());
        }
    }

    @Test
    @Tag("multipart") // a runner whose client cannot send multipart bodies can exclude this tag
    void uploadOutputStream() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = same(server, prefix -> upload(prefix + "/upload/outputStream", "file", "file.json", "{\"title\":\"Foo\"}".getBytes(StandardCharsets.UTF_8)), ASYNC);
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("Uploaded", response.body());
        }
    }

    @Test
    @Tag("multipart") // a runner whose client cannot send multipart bodies can exclude this tag
    void completedUpload() throws IOException {
        try (ServerUnderTest server = server()) {
            Response response = same(server, prefix -> upload(prefix + "/upload/completed", "file", "file.json", "{\"title\":\"Foo\"}".getBytes(StandardCharsets.UTF_8)), ASYNC, SYNC);
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("Uploaded", response.body());
            Response empty = same(server, prefix -> upload(prefix + "/upload/completed", "file", "file.json", new byte[0]), ASYNC, SYNC);
            assertEquals("Uploaded", empty.body());
        }
    }

    @Test
    @Tag("multipart") // a runner whose client cannot send multipart bodies can exclude this tag
    void completedUploadThatIsNotAFileIsABadRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            // a part without a file name is a text field: the controller cannot bind it, and the
            // form of a handler has no file of that name. Both answer 400, with their own message
            for (byte[] content : List.of("{\"title\":\"Foo\"}".getBytes(StandardCharsets.UTF_8), new byte[0])) {
                MultipartBody.Builder body = MultipartBody.builder().addPart("file", "", MediaType.APPLICATION_JSON_TYPE, content);
                Response controller = call(server, multipart("/ctl/upload/completed", body));
                assertEquals(HttpStatus.BAD_REQUEST, controller.status());
                assertTrue(controller.body().contains("Field [file] was expected to be a file upload, but is missing a file name"), controller.body());
                for (String prefix : List.of(ASYNC, SYNC)) {
                    Response handler = call(server, multipart(prefix + "/upload/completed", body));
                    assertEquals(HttpStatus.BAD_REQUEST, handler.status());
                    assertTrue(handler.body().contains("Required file [file] not uploaded"), handler.body());
                }
            }
        }
    }

    @Test
    @Tag("multipart") // a runner whose client cannot send multipart bodies can exclude this tag
    void completedUploadWithoutThePartIsABadRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            MultipartBody.Builder body = MultipartBody.builder().addPart("filex", "", MediaType.APPLICATION_JSON_TYPE, new byte[0]);
            Response controller = call(server, multipart("/ctl/upload/completed", body));
            assertEquals(HttpStatus.BAD_REQUEST, controller.status());
            assertTrue(controller.body().contains("Required argument [CompletedFileUpload file] not specified"), controller.body());
            for (String prefix : List.of(ASYNC, SYNC)) {
                Response handler = call(server, multipart(prefix + "/upload/completed", body));
                assertEquals(HttpStatus.BAD_REQUEST, handler.status());
                assertTrue(handler.body().contains("Required file [file] not uploaded"), handler.body());
            }
        }
    }

    @Test
    @Tag("multipart") // a runner whose client cannot send multipart bodies can exclude this tag
    void bytesUpload() throws IOException {
        try (ServerUnderTest server = server()) {
            MultipartBody.Builder body = MultipartBody.builder()
                .addPart("file", "file.json", MediaType.TEXT_PLAIN_TYPE, "some data".getBytes(StandardCharsets.UTF_8))
                .addPart("fileName", "bar");
            Response response = same(server, prefix -> multipart(prefix + "/upload/bytes", body), ASYNC, SYNC);
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("Uploaded", response.body());
        }
    }

    @Test
    @Tag("multipart") // a runner whose client cannot send multipart bodies can exclude this tag
    void wholeBodyUpload() throws IOException {
        try (ServerUnderTest server = server()) {
            MultipartBody.Builder body = MultipartBody.builder()
                .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, "{\"title\":\"Foo\"}".getBytes(StandardCharsets.UTF_8))
                .addPart("title", "Foo");
            Response response = same(server, prefix -> multipart(prefix + "/upload/whole-body", body), ASYNC);
            assertEquals(HttpStatus.OK, response.status());
            assertEquals("Uploaded", response.body());
        }
    }

    private static HttpRequest<?> formRequest(String path) {
        return HttpRequest.POST(path, "firstName=Fred&lastName=Flintstone&age=45").contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE);
    }

    private static HttpRequest<?> upload(String path, String name, String fileName, byte[] content) {
        return multipart(path, MultipartBody.builder().addPart(name, fileName, MediaType.APPLICATION_JSON_TYPE, content));
    }

    private static HttpRequest<?> multipart(String path, MultipartBody.Builder body) {
        return HttpRequest.POST(path, body.build()).contentType(MediaType.MULTIPART_FORM_DATA_TYPE).accept(MediaType.TEXT_PLAIN_TYPE);
    }

    /**
     * Send the request to the controller, under {@code /ctl}, and to the handler routes under the
     * given prefixes, and check that they answer the same.
     *
     * @return The response of the controller
     */
    private static Response same(ServerUnderTest server, Function<String, HttpRequest<?>> request, String... prefixes) {
        Response controller = call(server, request.apply("/ctl"));
        for (String prefix : prefixes) {
            HttpRequest<?> handlerRequest = request.apply(prefix);
            Response handler = call(server, handlerRequest);
            assertEquals(controller.status(), handler.status(), () -> handlerRequest.getPath() + ": " + handler.body());
            assertEquals(controller.body().replace("/ctl/", prefix + "/"), handler.body(), handlerRequest.getPath());
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
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static Path newFile(String name) {
        try {
            // a new file in a new directory: uploads are written to files that do not exist yet
            return Files.createTempDirectory("docs-parity").resolve(name.isEmpty() ? "upload" : name);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String readText(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static HttpResponse<String> write(String fileName, byte[] content) {
        try {
            File tempFile = File.createTempFile(fileName, "temp");
            Files.write(tempFile.toPath(), content);
            return HttpResponse.ok("Uploaded");
        } catch (IOException e) {
            return HttpResponse.badRequest("Upload Failed");
        }
    }

    record Response(HttpStatus status, String body) {
    }

    /**
     * The person of the JSON examples.
     */
    @Introspected
    @ReflectiveAccess
    public static class JsonPerson {
        private String firstName;
        private String lastName;
        private int age;

        public JsonPerson() {
        }

        public JsonPerson(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    /**
     * The fields the {@code saveWithArgs} example binds one by one: a record for a handler.
     *
     * @param firstName The first name
     * @param lastName  The last name
     * @param age       The age, if given
     */
    @Introspected
    @ReflectiveAccess
    record SaveArgs(String firstName, String lastName, @Nullable Integer age) {
        JsonPerson person() {
            JsonPerson person = new JsonPerson(firstName, lastName);
            if (age != null) {
                person.setAge(age);
            }
            return person;
        }
    }

    // the controllers of the documentation, under /ctl

    @Controller("/ctl/receive")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MessageController {
        @Post(value = "/echo", consumes = MediaType.TEXT_PLAIN)
        String echo(@Size(max = 1024) @Body String text) {
            return text;
        }

        @Post(value = "/echo-publisher", consumes = MediaType.TEXT_PLAIN)
        @SingleResult
        Publisher<HttpResponse<String>> echoFlow(@Body Publisher<String> text) {
            return Flux.from(text)
                .collect(StringBuffer::new, StringBuffer::append)
                .map(buffer -> HttpResponse.ok(buffer.toString()));
        }
    }

    @Controller("/ctl/json/people")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class JsonPersonController {
        final Map<String, JsonPerson> inMemoryDatastore = new ConcurrentHashMap<>();

        @Post("/saveReactive")
        @SingleResult
        Publisher<HttpResponse<JsonPerson>> save(@Body Publisher<JsonPerson> person) {
            return Mono.from(person).map(p -> {
                inMemoryDatastore.put(p.getFirstName(), p);
                return HttpResponse.created(p);
            });
        }

        @Post("/saveWithArgs")
        HttpResponse<JsonPerson> save(String firstName, String lastName, Optional<Integer> age) {
            JsonPerson p = new JsonPerson(firstName, lastName);
            age.ifPresent(p::setAge);
            inMemoryDatastore.put(p.getFirstName(), p);
            return HttpResponse.created(p);
        }

        @Post("/saveFuture")
        CompletableFuture<HttpResponse<JsonPerson>> save(@Body CompletableFuture<JsonPerson> person) {
            return person.thenApply(p -> {
                inMemoryDatastore.put(p.getFirstName(), p);
                return HttpResponse.created(p);
            });
        }

        @Post
        HttpResponse<JsonPerson> save(@Body JsonPerson person) {
            inMemoryDatastore.put(person.getFirstName(), person);
            return HttpResponse.created(person);
        }

        @Error
        HttpResponse<JsonError> jsonError(HttpRequest<?> request, JsonSyntaxException e) {
            return invalidJson(request, e);
        }

        static HttpResponse<JsonError> invalidJson(HttpRequest<?> request, Throwable e) {
            JsonError error = new JsonError("Invalid JSON: " + e.getMessage())
                .link(Link.SELF, Link.of(request.getUri()));
            return HttpResponse.<JsonError>status(HttpStatus.BAD_REQUEST, "Fix Your JSON")
                .body(error);
        }
    }

    @Controller("/ctl/form/people")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FormPersonController {
        final Map<String, JsonPerson> inMemoryDatastore = new ConcurrentHashMap<>();

        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Post
        HttpResponse<JsonPerson> save(@Body JsonPerson person) {
            inMemoryDatastore.put(person.getFirstName(), person);
            return HttpResponse.created(person);
        }

        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Post("/saveWithArgs")
        HttpResponse<JsonPerson> save(String firstName, String lastName, @Nullable Integer age) {
            JsonPerson p = new JsonPerson(firstName, lastName);
            if (age != null) {
                p.setAge(age);
            }
            inMemoryDatastore.put(p.getFirstName(), p);
            return HttpResponse.created(p);
        }

        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Post("/saveWithArgsOptional")
        HttpResponse<JsonPerson> saveOptional(String firstName, String lastName, Optional<Integer> age) {
            JsonPerson p = new JsonPerson(firstName, lastName);
            age.ifPresent(p::setAge);
            inMemoryDatastore.put(p.getFirstName(), p);
            return HttpResponse.created(p);
        }
    }

    @Controller("/ctl/stream")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class StreamController {
        @Post(value = "/read", processes = MediaType.TEXT_PLAIN)
        @ExecuteOn(TaskExecutors.IO)
        String read(@Body InputStream inputStream) {
            return readText(inputStream);
        }
    }

    @Controller("/ctl/upload")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class UploadController {
        @Post(value = "/", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        @SingleResult
        Publisher<HttpResponse<String>> upload(StreamingFileUpload file) {
            File tempFile;
            try {
                tempFile = File.createTempFile(file.getFilename(), "temp");
            } catch (IOException e) {
                return Mono.error(e);
            }
            Publisher<?> uploadPublisher = file.transferTo(tempFile);
            return Mono.from(uploadPublisher)
                .<HttpResponse<String>>thenReturn(HttpResponse.ok("Uploaded"))
                .onErrorReturn(HttpResponse.<String>status(HttpStatus.CONFLICT).body("Upload Failed"));
        }

        @Post(value = "/outputStream", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        @SingleResult
        Mono<HttpResponse<String>> uploadOutputStream(StreamingFileUpload file) {
            OutputStream outputStream = new ByteArrayOutputStream();
            Publisher<?> uploadPublisher = file.transferTo(outputStream);
            return Mono.from(uploadPublisher)
                .<HttpResponse<String>>thenReturn(HttpResponse.ok("Uploaded"))
                .onErrorReturn(HttpResponse.<String>status(HttpStatus.CONFLICT).body("Upload Failed"));
        }

        @Post(value = "/completed", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        @ExecuteOn(TaskExecutors.BLOCKING)
        HttpResponse<String> uploadCompleted(CompletedFileUpload file) throws IOException {
            try {
                return write(file.getFilename(), file.getBytes());
            } finally {
                file.close();
            }
        }

        @Post(value = "/bytes", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> uploadBytes(byte[] file, String fileName) {
            return write(fileName, file);
        }

        @Post(value = "/whole-body", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        @SingleResult
        Publisher<String> uploadWholeBody(@Body io.micronaut.http.server.multipart.MultipartBody body) {
            return Mono.create(emitter -> Flux.from(body).subscribe(new Subscriber<>() {
                private Subscription s;

                @Override
                public void onSubscribe(Subscription s) {
                    this.s = s;
                    s.request(1);
                }

                @Override
                public void onNext(CompletedPart completedPart) {
                    try {
                        completedPart.close();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    s.request(1);
                }

                @Override
                public void onError(Throwable t) {
                    emitter.error(t);
                }

                @Override
                public void onComplete() {
                    emitter.success("Uploaded");
                }
            }));
        }
    }

    // the same, as handler routes: asynchronous under /fn, synchronous under /fn-sync

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {
        private final Map<String, JsonPerson> inMemoryDatastore = new ConcurrentHashMap<>();

        @Singleton
        HttpRoutes docsRoutes() {
            return routes -> {
                messageRoutes(routes);
                jsonRoutes(routes);
                formRoutes(routes);
                streamRoutes(routes);
                uploadRoutes(routes);
            };
        }

        private void messageRoutes(HttpRouteBuilder routes) {
            // @Size(max = 1024) @Body String: the handler reads at most 1024 bytes
            routes.asyncPOST(ASYNC + "/receive/echo", (request, pathVariables, body) -> body.text(1024).thenApply(HttpResponse::ok))
                .consumes(MediaType.TEXT_PLAIN_TYPE);
            routes.POST(SYNC + "/receive/echo", Argument.STRING, (request, pathVariables, text) -> HttpResponse.ok(text))
                .consumes(MediaType.TEXT_PLAIN_TYPE);
            // @Body Publisher<String>, collected: the text of the body
            routes.asyncPOST(ASYNC + "/receive/echo-publisher", (request, pathVariables, body) -> body.text().thenApply(HttpResponse::ok))
                .consumes(MediaType.TEXT_PLAIN_TYPE);
        }

        private void jsonRoutes(HttpRouteBuilder routes) {
            // @Body Person, @Body Publisher<Person> and @Body CompletableFuture<Person>
            for (String path : List.of("", "/saveReactive", "/saveFuture")) {
                routes.asyncPOST(ASYNC + "/json/people" + path, (request, pathVariables, body) -> body.body(JsonPerson.class).thenApply(this::created));
            }
            routes.POST(SYNC + "/json/people", Argument.of(JsonPerson.class), (request, pathVariables, person) -> created(person));
            // the fields of the body as arguments: a record
            routes.asyncPOST(ASYNC + "/json/people/saveWithArgs", (request, pathVariables, body) ->
                body.body(SaveArgs.class).thenApply(args -> created(args.person())));
            routes.POST(SYNC + "/json/people/saveWithArgs", Argument.of(SaveArgs.class), (request, pathVariables, args) -> created(args.person()));
            // the @Error route of the controller: a global error route
            routes.error(JsonSyntaxException.class, JsonPersonController::invalidJson);
        }

        private void formRoutes(HttpRouteBuilder routes) {
            MediaType form = MediaType.APPLICATION_FORM_URLENCODED_TYPE;
            // a form decoded to a type
            routes.asyncPOST(ASYNC + "/form/people", (request, pathVariables, body) -> body.body(JsonPerson.class).thenApply(this::created))
                .consumes(form);
            routes.POST(SYNC + "/form/people", Argument.of(JsonPerson.class), (request, pathVariables, person) -> created(person))
                .consumes(form);
            // the fields of the form
            for (String path : List.of("/saveWithArgs", "/saveWithArgsOptional")) {
                routes.asyncPOST(ASYNC + "/form/people" + path, (request, pathVariables, body) -> body.form().thenApply(fields ->
                    created(new SaveArgs(fields.getString("firstName"), fields.getString("lastName"), fields.find("age", Integer.class).orElse(null)).person())))
                    .consumes(form);
                routes.POST(SYNC + "/form/people" + path, (request, pathVariables, fields) ->
                    created(new SaveArgs(fields.getString("firstName"), fields.getString("lastName"), fields.find("age", Integer.class).orElse(null)).person()))
                    .consumes(form);
            }
        }

        private void streamRoutes(HttpRouteBuilder routes) {
            // @ExecuteOn @Body InputStream: a synchronous route on an executor
            routes.POST(SYNC + "/stream/read", Argument.of(InputStream.class), (request, pathVariables, in) -> HttpResponse.ok(readText(in)))
                .consumes(MediaType.TEXT_PLAIN_TYPE)
                .produces(MediaType.TEXT_PLAIN_TYPE)
                .executeOn(TaskExecutors.IO);
            // an asynchronous handler does not block: it reads the text
            routes.asyncPOST(ASYNC + "/stream/read", (request, pathVariables, body) -> body.text().thenApply(HttpResponse::ok))
                .consumes(MediaType.TEXT_PLAIN_TYPE)
                .produces(MediaType.TEXT_PLAIN_TYPE);
        }

        private void uploadRoutes(HttpRouteBuilder routes) {
            MediaType multipart = MediaType.MULTIPART_FORM_DATA_TYPE;
            MediaType text = MediaType.TEXT_PLAIN_TYPE;
            // StreamingFileUpload.transferTo(File)
            routes.asyncPOST(ASYNC + "/upload", (request, pathVariables, body) -> body.parts()
                    .part("file", part -> part.file().transferTo(newFile(part.file().fileName())))
                    .thenApply(found -> found ? HttpResponse.ok("Uploaded") : HttpResponse.<String>status(HttpStatus.CONFLICT).body("Upload Failed"))
                    .exceptionally(error -> HttpResponse.<String>status(HttpStatus.CONFLICT).body("Upload Failed")))
                .consumes(multipart).produces(text);
            // StreamingFileUpload.transferTo(OutputStream)
            routes.asyncPOST(ASYNC + "/upload/outputStream", (request, pathVariables, body) -> body.parts()
                    .part("file", part -> part.file().transferTo(new ByteArrayOutputStream()))
                    .thenApply(found -> found ? HttpResponse.ok("Uploaded") : HttpResponse.<String>status(HttpStatus.CONFLICT).body("Upload Failed"))
                    .exceptionally(error -> HttpResponse.<String>status(HttpStatus.CONFLICT).body("Upload Failed")))
                .consumes(multipart).produces(text);
            // CompletedFileUpload: the whole form, then the bytes of the file
            routes.asyncPOST(ASYNC + "/upload/completed", (request, pathVariables, body) -> body.form().thenCompose(form -> {
                    FileUpload file = form.getFile("file");
                    return file.bytes(Integer.MAX_VALUE).thenApply(bytes -> write(file.fileName(), bytes));
                }))
                .consumes(multipart).produces(text);
            routes.POST(SYNC + "/upload/completed", (request, pathVariables, form) -> {
                    FileUpload file = form.getFile("file");
                    return write(file.fileName(), file.readAllBytes());
                })
                .consumes(multipart).produces(text)
                .executeOn(TaskExecutors.BLOCKING);
            // byte[] file, String fileName
            routes.asyncPOST(ASYNC + "/upload/bytes", (request, pathVariables, body) -> body.form().thenCompose(form ->
                    form.getFile("file").bytes(Integer.MAX_VALUE).thenApply(bytes -> write(form.getString("fileName"), bytes))))
                .consumes(multipart).produces(text);
            routes.POST(SYNC + "/upload/bytes", (request, pathVariables, form) -> write(form.getString("fileName"), form.getFile("file").readAllBytes()))
                .consumes(multipart).produces(text);
            // @Body MultipartBody, part by part
            routes.asyncPOST(ASYNC + "/upload/whole-body", (request, pathVariables, body) -> body.parts()
                    .forEach(FormPart::closeAsync)
                    .thenApply(done -> HttpResponse.ok("Uploaded")))
                .consumes(multipart).produces(text);
        }

        private HttpResponse<JsonPerson> created(JsonPerson person) {
            inMemoryDatastore.put(Objects.requireNonNull(person.getFirstName()), person);
            return HttpResponse.created(person);
        }
    }
}
