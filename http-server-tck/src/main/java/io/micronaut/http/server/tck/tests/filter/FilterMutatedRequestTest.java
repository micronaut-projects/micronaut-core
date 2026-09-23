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
package io.micronaut.http.server.tck.tests.filter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.annotation.PreMatching;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A filter that continues with the mutable view of the request, {@link HttpRequest#mutate()},
 * e.g. with another URI: the route still sees the connection of the request, its remote and server
 * addresses, its HTTP version and whether it is secure, and its body, the uploads and the
 * url-encoded form, is bound.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class FilterMutatedRequestTest {
    public static final String SPEC_NAME = "FilterMutatedRequestTest";
    private static final String MUTATE = "X-Mutate";

    @Test
    void aFilterThatContinuesWithTheMutableViewKeepsTheConnectionOfTheRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            String direct = get(server, HttpRequest.GET("/fmr/target/info"));
            assertEquals(direct.replace("changed=null", "changed=mutate"), get(server, HttpRequest.GET("/fmr/target/info").header(MUTATE, "true")));
        }
    }

    @Test
    void aPreMatchingFilterThatContinuesWithTheMutableViewKeepsTheConnectionOfTheRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            String direct = get(server, HttpRequest.GET("/fmr/target/info"));
            assertEquals(direct, get(server, HttpRequest.GET("/fmr/pre/info")));
        }
    }

    @Test
    @Tag("multipart")
    void theUploadsOfARequestAFilterContinuedWithTheMutableViewOfAreBound() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String path : new String[]{"/fmr/target", "/fmr/pre"}) {
                assertEquals("completed a.txt=first", post(server, HttpRequest.POST(path + "/completed", upload("first"))), path);
                assertEquals("streaming a.txt=second", post(server, HttpRequest.POST(path + "/streaming", upload("second"))), path);
                assertEquals("parts a.txt=third", post(server, HttpRequest.POST(path + "/parts", upload("third"))), path);
                assertEquals("part Fred a.txt=seventh", post(server, HttpRequest.POST(path + "/part", uploadWithName("seventh"))), path);
            }
            // continued with after the route match
            assertEquals("completed a.txt=fourth", post(server, HttpRequest.POST("/fmr/target/completed", upload("fourth")).header(MUTATE, "true")));
            assertEquals("streaming a.txt=fifth", post(server, HttpRequest.POST("/fmr/target/streaming", upload("fifth")).header(MUTATE, "true")));
            assertEquals("parts a.txt=sixth", post(server, HttpRequest.POST("/fmr/target/parts", upload("sixth")).header(MUTATE, "true")));
            assertEquals("part Fred a.txt=eighth", post(server, HttpRequest.POST("/fmr/target/part", uploadWithName("eighth")).header(MUTATE, "true")));
        }
    }

    @Test
    void theUrlEncodedFormOfARequestAFilterContinuedWithTheMutableViewOfIsReadIntoTheBody() throws IOException {
        try (ServerUnderTest server = server()) {
            assertForms(server, "/fmr/target", false);
            assertForms(server, "/fmr/pre", false);
            // continued with after the route match
            assertForms(server, "/fmr/target", true);
        }
    }

    private static void assertForms(ServerUnderTest server, String path, boolean mutate) {
        String expected = String.join("\n",
            "pojo Fred 42",
            "map Fred 42",
            "field Fred 42",
            "json Fred 42",
            "json field Fred 42",
            "json string {\"name\":\"Fred\",\"age\":42}",
            "json stream {\"name\":\"Fred\",\"age\":42}",
            "json future Fred 42",
            "json publisher Fred 42"
        );
        String actual = String.join("\n",
            form(server, path + "/form-pojo", mutate),
            form(server, path + "/form-map", mutate),
            form(server, path + "/form-field", mutate),
            json(server, path + "/json-pojo", mutate),
            json(server, path + "/json-field", mutate),
            json(server, path + "/json-string", mutate),
            json(server, path + "/json-stream", mutate),
            json(server, path + "/json-future", mutate),
            json(server, path + "/json-publisher", mutate)
        );
        assertEquals(expected, actual, path + " " + mutate);
    }

    private static String form(ServerUnderTest server, String path, boolean mutate) {
        MutableHttpRequest<String> request = HttpRequest.POST(path, "name=Fred&age=42")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE);
        if (mutate) {
            request.header(MUTATE, "true");
        }
        try {
            return get(server, request);
        } catch (HttpClientResponseException e) {
            return e.getStatus() + " " + e.getResponse().getBody(String.class).orElse("");
        }
    }

    private static String json(ServerUnderTest server, String path, boolean mutate) {
        MutableHttpRequest<String> request = HttpRequest.POST(path, "{\"name\":\"Fred\",\"age\":42}")
            .contentType(MediaType.APPLICATION_JSON_TYPE);
        if (mutate) {
            request.header(MUTATE, "true");
        }
        try {
            return get(server, request);
        } catch (HttpClientResponseException e) {
            return e.getStatus() + " " + e.getResponse().getBody(String.class).orElse("");
        }
    }

    private static MultipartBody upload(String content) {
        return MultipartBody.builder()
            .addPart("file", "a.txt", MediaType.TEXT_PLAIN_TYPE, content.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    private static MultipartBody uploadWithName(String content) {
        return MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("file", "a.txt", MediaType.TEXT_PLAIN_TYPE, content.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    private static String post(ServerUnderTest server, MutableHttpRequest<?> request) {
        return get(server, request.contentType(MediaType.MULTIPART_FORM_DATA_TYPE));
    }

    private static String get(ServerUnderTest server, HttpRequest<?> request) {
        HttpResponse<String> response = server.exchange(request, String.class);
        assertEquals(200, response.code());
        return response.body();
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    /**
     * The connection of the request as the route sees it.
     */
    static String describe(HttpRequest<?> request) {
        InetSocketAddress remote = request.getRemoteAddress();
        InetSocketAddress local = request.getServerAddress();
        return "remote=" + remote.getAddress().getHostAddress()
            + " server=" + local.getAddress().getHostAddress() + ":" + local.getPort()
            + " name=" + request.getServerName()
            + " version=" + request.getHttpVersion()
            + " secure=" + request.isSecure()
            + " ssl=" + request.getSslSession().isPresent()
            + " certificate=" + request.getCertificate().isPresent()
            + " changed=" + request.getParameters().get("changed");
    }

    @ServerFilter("/fmr/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MutatingFilters {

        @RequestFilter
        @PreMatching
        @Nullable
        HttpRequest<?> preMatching(HttpRequest<?> request) {
            String path = request.getPath();
            if (path.startsWith("/fmr/pre/")) {
                return request.mutate().uri(URI.create("/fmr/target/" + path.substring("/fmr/pre/".length())));
            }
            return null;
        }

        @RequestFilter
        @Nullable
        HttpRequest<?> mutated(HttpRequest<?> request) {
            if (request.getHeaders().contains(MUTATE)) {
                return request.mutate().uri(URI.create(request.getPath() + "?changed=mutate"));
            }
            return null;
        }
    }

    @Introspected
    @ReflectiveAccess
    record Person(String name, int age) {
    }

    @Controller("/fmr/target")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Produces(MediaType.TEXT_PLAIN)
    static class TargetController {

        @Get("/info")
        String info(HttpRequest<?> request) {
            return describe(request);
        }

        @Post(value = "/form-pojo", consumes = MediaType.APPLICATION_FORM_URLENCODED)
        String formPojo(@Body Person person) {
            return "pojo " + person.name() + " " + person.age();
        }

        @Post(value = "/form-map", consumes = MediaType.APPLICATION_FORM_URLENCODED)
        String formMap(@Body Map<String, String> form) {
            return "map " + form.get("name") + " " + form.get("age");
        }

        @Post(value = "/form-field", consumes = MediaType.APPLICATION_FORM_URLENCODED)
        String formField(@Body("name") String name, @Body("age") int age) {
            return "field " + name + " " + age;
        }

        @Post(value = "/json-pojo", consumes = MediaType.APPLICATION_JSON)
        String jsonPojo(@Body Person person) {
            return "json " + person.name() + " " + person.age();
        }

        @Post(value = "/json-field", consumes = MediaType.APPLICATION_JSON)
        String jsonField(@Body("name") String name, @Body("age") int age) {
            return "json field " + name + " " + age;
        }

        @Post(value = "/json-string", consumes = MediaType.APPLICATION_JSON)
        String jsonString(@Body String body) {
            return "json string " + body;
        }

        @Post(value = "/json-stream", consumes = MediaType.APPLICATION_JSON)
        @ExecuteOn(TaskExecutors.BLOCKING)
        String jsonStream(@Body InputStream body) throws IOException {
            try (body) {
                return "json stream " + new String(body.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        @Post(value = "/json-future", consumes = MediaType.APPLICATION_JSON)
        CompletableFuture<String> jsonFuture(@Body CompletableFuture<Person> person) {
            return person.thenApply(p -> "json future " + p.name() + " " + p.age());
        }

        @Post(value = "/json-publisher", consumes = MediaType.APPLICATION_JSON)
        Mono<String> jsonPublisher(@Body Mono<Person> person) {
            return person.map(p -> "json publisher " + p.name() + " " + p.age());
        }

        @Post(value = "/part", consumes = MediaType.MULTIPART_FORM_DATA)
        String part(@Part("name") String name, CompletedFileUpload file) throws IOException {
            return "part " + name + " " + file.getFilename() + "=" + new String(file.getBytes(), StandardCharsets.UTF_8);
        }

        @Post(value = "/completed", consumes = MediaType.MULTIPART_FORM_DATA)
        String completed(CompletedFileUpload file) throws IOException {
            return "completed " + file.getFilename() + "=" + new String(file.getBytes(), StandardCharsets.UTF_8);
        }

        @Post(value = "/streaming", consumes = MediaType.MULTIPART_FORM_DATA)
        @ExecuteOn(TaskExecutors.BLOCKING)
        String streaming(StreamingFileUpload file) throws IOException {
            try (InputStream stream = file.asInputStream()) {
                return "streaming " + file.getFilename() + "=" + new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        @Post(value = "/parts", consumes = MediaType.MULTIPART_FORM_DATA)
        Publisher<String> parts(Publisher<CompletedFileUpload> file) {
            return Flux.from(file).map(upload -> {
                try {
                    return "parts " + upload.getFilename() + "=" + new String(upload.getBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
