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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
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
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
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
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A filter that changes the URI of the request in place, a filter method with a
 * {@link MutableHttpRequest} parameter or a filter declared as a function, continues with the
 * mutable request it was given: the route still sees the request of the connection, its remote
 * and server addresses, its HTTP version and whether it is secure.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class FilterInPlaceUriChangeTest {
    public static final String SPEC_NAME = "FilterInPlaceUriChangeTest";
    private static final String REQUERY = "X-Requery";

    @Test
    void aPreMatchingFilterMethodThatChangesTheUriKeepsTheConnectionOfTheRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            String direct = get(server, HttpRequest.GET("/ipc/target/info"));
            assertEquals(direct.replace("from=controller", "from=handler"), get(server, HttpRequest.GET("/ipc/target/handler")));
            assertEquals(direct, get(server, HttpRequest.GET("/ipc/pre/info")));
            assertEquals(direct.replace("from=controller", "from=handler"), get(server, HttpRequest.GET("/ipc/pre/handler")));
        }
    }

    @Test
    void anAsynchronousPreMatchingFilterMethodThatChangesTheUriWhenItCompletesChangesTheMatchedRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            String direct = get(server, HttpRequest.GET("/ipc/target/info"));
            assertEquals(direct, get(server, HttpRequest.GET("/ipc/async/info")));
            assertEquals(direct.replace("from=controller", "from=handler"), get(server, HttpRequest.GET("/ipc/async/handler")));
        }
    }

    @Test
    void aFilterMethodThatChangesTheUriAfterTheRouteMatchKeepsTheConnectionOfTheRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            String direct = get(server, HttpRequest.GET("/ipc/target/info"));
            assertEquals(direct.replace("changed=null", "changed=method"), get(server, HttpRequest.GET("/ipc/target/info").header(REQUERY, "method")));
        }
    }

    @Test
    void aFilterMethodThatContinuesWithTheMutableViewKeepsTheConnectionOfTheRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            String direct = get(server, HttpRequest.GET("/ipc/target/info"));
            // the request returned by HttpRequest.mutate(), with another URI
            assertEquals(direct.replace("changed=null", "changed=mutate"), get(server, HttpRequest.GET("/ipc/target/info").header(REQUERY, "mutate")));
        }
    }

    @Test
    void aFilterFunctionThatChangesTheUriKeepsTheConnectionOfTheRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            String direct = get(server, HttpRequest.GET("/ipc/target/info"));
            String handler = direct.replace("from=controller", "from=handler");
            // a pre-matching server filter function
            assertEquals(direct, get(server, HttpRequest.GET("/ipc/fn/info")));
            assertEquals(handler, get(server, HttpRequest.GET("/ipc/fn/handler")));
            // a filter of the route
            assertEquals(handler.replace("changed=null", "changed=route"), get(server, HttpRequest.GET("/ipc/target/handler").header(REQUERY, "route")));
        }
    }

    @Test
    @Tag("multipart")
    void theUploadsOfARequestAFilterChangedTheUriOfInPlaceAreBound() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String path : new String[]{"/ipc/target", "/ipc/pre", "/ipc/fn"}) {
                assertEquals("completed a.txt=first", post(server, HttpRequest.POST(path + "/completed", upload("first"))), path);
                assertEquals("streaming a.txt=second", post(server, HttpRequest.POST(path + "/streaming", upload("second"))), path);
                assertEquals("parts a.txt=third", post(server, HttpRequest.POST(path + "/parts", upload("third"))), path);
            }
            // changed after the route match
            assertEquals("completed a.txt=fourth", post(server, HttpRequest.POST("/ipc/target/completed", upload("fourth")).header(REQUERY, "method")));
            assertEquals("streaming a.txt=fifth", post(server, HttpRequest.POST("/ipc/target/streaming", upload("fifth")).header(REQUERY, "method")));
            assertEquals("parts a.txt=sixth", post(server, HttpRequest.POST("/ipc/target/parts", upload("sixth")).header(REQUERY, "method")));
        }
    }

    @Test
    void theUrlEncodedFormOfARequestAFilterChangedTheUriOfIsReadIntoTheBody() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String path : new String[]{"/ipc/target", "/ipc/pre", "/ipc/fn"}) {
                assertForms(server, path, null);
            }
            // changed after the route match, in place or continuing with the mutable view
            assertForms(server, "/ipc/target", "method");
            assertForms(server, "/ipc/target", "mutate");
        }
    }

    private static void assertForms(ServerUnderTest server, String path, @Nullable String requery) {
        String message = path + " " + requery;
        assertEquals("pojo Fred 42", form(server, path + "/form-pojo", requery), message);
        assertEquals("map Fred 42", form(server, path + "/form-map", requery), message);
        assertEquals("field Fred 42", form(server, path + "/form-field", requery), message);
        assertEquals("handler pojo Fred 42", form(server, path + "/form-handler-pojo", requery), message);
        assertEquals("handler map Fred 42", form(server, path + "/form-handler-map", requery), message);
    }

    private static String form(ServerUnderTest server, String path, @Nullable String requery) {
        MutableHttpRequest<String> request = HttpRequest.POST(path, "name=Fred&age=42")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE);
        if (requery != null) {
            request.header(REQUERY, requery);
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
    static String describe(String from, HttpRequest<?> request) {
        InetSocketAddress remote = request.getRemoteAddress();
        InetSocketAddress local = request.getServerAddress();
        return "from=" + from
            + " remote=" + remote.getAddress().getHostAddress()
            + " server=" + local.getAddress().getHostAddress() + ":" + local.getPort()
            + " name=" + request.getServerName()
            + " version=" + request.getHttpVersion()
            + " secure=" + request.isSecure()
            + " ssl=" + request.getSslSession().isPresent()
            + " certificate=" + request.getCertificate().isPresent()
            + " changed=" + request.getParameters().get("changed");
    }

    private static String moved(String path, String from) {
        return "/ipc/target/" + path.substring(from.length());
    }

    @ServerFilter("/ipc/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class InPlaceFilters {

        @RequestFilter
        @PreMatching
        void preMatching(MutableHttpRequest<?> request) {
            String path = request.getPath();
            if (path.startsWith("/ipc/pre/")) {
                request.uri(URI.create(moved(path, "/ipc/pre/")));
            }
        }

        @RequestFilter
        @PreMatching
        Publisher<HttpResponse<?>> asyncPreMatching(MutableHttpRequest<?> request) {
            String path = request.getPath();
            if (!path.startsWith("/ipc/async/")) {
                return Mono.empty();
            }
            // the URI is changed once the filter looked something up
            return Mono.delay(Duration.ofMillis(10))
                .then(Mono.fromRunnable(() -> request.uri(URI.create(moved(path, "/ipc/async/")))));
        }

        @RequestFilter
        void matched(MutableHttpRequest<?> request) {
            if ("method".equals(request.getHeaders().get(REQUERY))) {
                request.uri(URI.create(request.getPath() + "?changed=method"));
            }
        }

        @RequestFilter
        @Nullable
        HttpRequest<?> mutated(HttpRequest<?> request) {
            if ("mutate".equals(request.getHeaders().get(REQUERY))) {
                return request.mutate().uri(URI.create(request.getPath() + "?changed=mutate"));
            }
            return null;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.filter("/ipc/fn/**").preMatching().before(request -> {
                request.uri(URI.create(moved(request.getPath(), "/ipc/fn/")));
            });
            routes.POST("/ipc/target/form-handler-pojo", Argument.of(Person.class), (request, pathVariables, person) ->
                HttpResponse.ok("handler pojo " + person.name() + " " + person.age()).contentType(MediaType.TEXT_PLAIN_TYPE)
            ).consumes(MediaType.APPLICATION_FORM_URLENCODED_TYPE);
            routes.POST("/ipc/target/form-handler-map", Argument.mapOf(String.class, String.class), (request, pathVariables, form) ->
                HttpResponse.ok("handler map " + form.get("name") + " " + form.get("age")).contentType(MediaType.TEXT_PLAIN_TYPE)
            ).consumes(MediaType.APPLICATION_FORM_URLENCODED_TYPE);
            routes.GET("/ipc/target/handler", (request, pathVariables) ->
                HttpResponse.ok(describe("handler", request)).contentType(MediaType.TEXT_PLAIN_TYPE)
            ).before(request -> {
                if ("route".equals(request.getHeaders().get(REQUERY))) {
                    request.uri(URI.create(request.getPath() + "?changed=route"));
                }
            });
        }
    }

    @Introspected
    @ReflectiveAccess
    record Person(String name, int age) {
    }

    @Controller("/ipc/target")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Produces(MediaType.TEXT_PLAIN)
    static class TargetController {

        @Get("/info")
        String info(HttpRequest<?> request) {
            return describe("controller", request);
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
