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
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RouteDeclaration;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The declared type of the body of the responses of a handler route,
 * {@link io.micronaut.web.router.builder.HttpRouteSpec#responseType}: the message body writer is
 * selected for the declared type, with its type arguments, like for the return type of a
 * controller method, not for the runtime class of the body.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteResponseTypeTest {
    public static final String SPEC_NAME = "HandlerRouteResponseTypeTest";

    private static final String FOO_LIST = "application/x-foo-list";
    private static final Argument<List<Foo>> FOOS = Argument.listOf(Foo.class);

    @Test
    void theWriterOfTheDeclaredGenericTypeWritesTheBody() throws IOException {
        assertFooList("/typed/foos");
    }

    @Test
    void theBodyIsWrittenLikeTheBodyOfAControllerMethodReturningTheType() throws IOException {
        assertFooList("/typed-controller/foos");
        assertFooList("/typed-controller/response");
    }

    @Test
    void withoutADeclaredTypeTheRuntimeClassSelectsTheWriter() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/untyped/foos").accept(FOO_LIST), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("untyped list of 2")
                .build());
        }
    }

    @Test
    void theResponseTypeIsNegotiated() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String path : new String[]{"/typed/foos", "/typed-controller/foos"}) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(path).accept(MediaType.APPLICATION_JSON_TYPE), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("[{\"name\":\"a\"},{\"name\":\"b\"}]")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .build());
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(path).accept(FOO_LIST), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("Foo list: a,b")
                    .header(HttpHeaders.CONTENT_TYPE, FOO_LIST)
                    .build());
            }
        }
    }

    @Test
    void theStatusAndTheHeadersOfTheResponseApply() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/typed/foos", "{\"name\":\"c\"}")
                .contentType(MediaType.APPLICATION_JSON_TYPE).accept(FOO_LIST), HttpResponseAssertion.builder()
                .status(HttpStatus.CREATED)
                .header("X-Count", "3")
                .body("Foo list: a,b,c")
                .build());
        }
    }

    @Test
    void everyKindOfHandlerHasTheDeclaredType() throws IOException {
        assertFooList("/typed/async");
        assertFooList("/typed/declared");
        assertFooList("/typed/methods");
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/typed/form", "name=x&name=y")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept(FOO_LIST), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Foo list: x,y")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.PUT("/typed/methods", "").accept(FOO_LIST), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Foo list: a,b")
                .build());
        }
    }

    @Test
    void errorAndStatusRoutesHaveTheDeclaredType() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/typed/fail").accept(FOO_LIST), HttpResponseAssertion.builder()
                .status(HttpStatus.CONFLICT)
                .body("Foo list: failed")
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.GET("/typed/pay").accept(FOO_LIST), HttpResponseAssertion.builder()
                .status(HttpStatus.PAYMENT_REQUIRED)
                .body("Foo list: pay")
                .build());
        }
    }

    @Test
    void aResponseWithoutABodyAndNoResponseAreAnsweredLikeThoseOfAController() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String path : new String[]{"/typed/empty", "/typed-controller/empty"}) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(path).accept(FOO_LIST), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(response -> assertTrue(response.getBody().isEmpty(), path))
                    .build());
            }
            for (String path : new String[]{"/typed/null", "/typed-controller/null"}) {
                AssertionUtils.assertThrows(server, HttpRequest.GET(path).accept(FOO_LIST), HttpResponseAssertion.builder()
                    .status(HttpStatus.NOT_FOUND)
                    .build());
            }
        }
    }

    @Test
    void aBodyThatIsNotOfTheDeclaredTypeIsWrittenAsItsRuntimeClass() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/typed/other"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("not a list")
                .build());
        }
    }

    private static void assertFooList(String path) throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(path).accept(FOO_LIST), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Foo list: a,b")
                .header(HttpHeaders.CONTENT_TYPE, FOO_LIST)
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static List<Foo> foos(String... names) {
        // the runtime class is ArrayList, the declared type List<Foo>
        List<Foo> foos = new ArrayList<>();
        for (String name : names) {
            foos.add(new Foo(name));
        }
        return foos;
    }

    public record Foo(String name) {
    }

    static final class FooFailure extends RuntimeException {
        FooFailure() {
            super("failed");
        }
    }

    /**
     * Writes a {@code List<Foo>} only if the type says so: the type arguments of the type it is
     * given tell the declared type from the runtime class.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Produces(FOO_LIST)
    static class FooListWriter implements MessageBodyWriter<List<Foo>> {
        @Override
        public boolean isWriteable(Argument<List<Foo>> type, @Nullable MediaType mediaType) {
            return mediaType != null && mediaType.getName().equals(FOO_LIST);
        }

        @Override
        public void writeTo(Argument<List<Foo>> type, MediaType mediaType, List<Foo> object, MutableHeaders outgoingHeaders,
                            OutputStream outputStream) throws CodecException {
            Argument<?>[] typeParameters = type.getTypeParameters();
            String text = typeParameters.length == 1 && typeParameters[0].getType() == Foo.class
                ? "Foo list: " + object.stream().map(Foo::name).collect(Collectors.joining(","))
                : "untyped list of " + object.size();
            outgoingHeaders.set(HttpHeaders.CONTENT_TYPE, mediaType.toString());
            try {
                outputStream.write(text.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new CodecException("Cannot write the list", e);
            }
        }
    }

    @Controller("/typed-controller")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FooController {
        @Get("/foos")
        @Produces({FOO_LIST, MediaType.APPLICATION_JSON})
        List<Foo> foos() {
            return HandlerRouteResponseTypeTest.foos("a", "b");
        }

        @Get("/response")
        @Produces({FOO_LIST, MediaType.APPLICATION_JSON})
        HttpResponse<List<Foo>> response() {
            return HttpResponse.ok(HandlerRouteResponseTypeTest.foos("a", "b"));
        }

        @Get("/empty")
        @Produces({FOO_LIST, MediaType.APPLICATION_JSON})
        HttpResponse<List<Foo>> empty() {
            return HttpResponse.ok();
        }

        @Get("/null")
        @Produces({FOO_LIST, MediaType.APPLICATION_JSON})
        @Nullable HttpResponse<List<Foo>> nothing() {
            return null;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TypedRoutes implements HttpRoutes {
        private final Executor executor;

        TypedRoutes(@Named(TaskExecutors.IO) ExecutorService executor) {
            this.executor = executor;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            MediaType fooList = MediaType.of(FOO_LIST);
            routes.GET("/typed/foos", (request, pathVariables) -> HttpResponse.ok(foos("a", "b")))
                .produces(fooList, MediaType.APPLICATION_JSON_TYPE)
                .responseType(FOOS);
            routes.GET("/untyped/foos", (request, pathVariables) -> HttpResponse.ok(foos("a", "b")))
                .produces(fooList, MediaType.APPLICATION_JSON_TYPE);
            routes.POST("/typed/foos", Argument.of(Foo.class), (request, pathVariables, foo) -> {
                List<Foo> foos = foos("a", "b");
                foos.add(foo);
                return HttpResponse.created(foos).header("X-Count", String.valueOf(foos.size()));
            }).produces(fooList).responseType(FOOS);
            routes.asyncGET("/typed/async", (request, pathVariables) ->
                CompletableFuture.supplyAsync(() -> HttpResponse.ok(foos("a", "b")), executor))
                .produces(fooList).responseType(FOOS);
            routes.handle(RouteDeclaration.of(HttpMethod.GET, "/typed/declared"),
                    (request, pathVariables) -> HttpResponse.ok(foos("a", "b")))
                .produces(fooList).responseType(FOOS);
            routes.handle(Set.of(HttpMethod.GET, HttpMethod.PUT), "/typed/methods", (request, pathVariables) -> HttpResponse.ok(foos("a", "b")))
                .consumesAll().produces(fooList).responseType(FOOS);
            routes.POST("/typed/form", (request, pathVariables, form) ->
                    HttpResponse.ok(form.get("name", Argument.listOf(String.class)).stream().map(Foo::new).collect(Collectors.toCollection(ArrayList::new))))
                .produces(fooList).responseType(FOOS);
            routes.GET("/typed/empty", (request, pathVariables) -> HttpResponse.ok())
                .produces(fooList, MediaType.APPLICATION_JSON_TYPE).responseType(FOOS);
            routes.GET("/typed/null", (request, pathVariables) -> null)
                .produces(fooList, MediaType.APPLICATION_JSON_TYPE).responseType(FOOS);
            routes.GET("/typed/other", (request, pathVariables) -> HttpResponse.ok("not a list").contentType(MediaType.TEXT_PLAIN_TYPE))
                .produces(fooList, MediaType.TEXT_PLAIN_TYPE).responseType(FOOS);
            routes.GET("/typed/fail", (request, pathVariables) -> {
                throw new FooFailure();
            }).produces(fooList);
            routes.error(FooFailure.class, (request, error) -> HttpResponse.status(HttpStatus.CONFLICT).body(foos(error.getMessage())))
                .produces(fooList).responseType(FOOS);
            routes.GET("/typed/pay", (request, pathVariables) -> HttpResponse.status(HttpStatus.PAYMENT_REQUIRED)).produces(fooList);
            routes.status(HttpStatus.PAYMENT_REQUIRED, request -> HttpResponse.status(HttpStatus.PAYMENT_REQUIRED).body(foos("pay")))
                .produces(fooList).responseType(FOOS);
        }
    }
}
