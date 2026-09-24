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
import io.micronaut.core.annotation.Order;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.LifecycleHttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.server.annotation.PreMatching;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filters declared as functions change the request in place or continue with another request,
 * like filter methods: a pre-matching server filter changes the route that is matched, a request
 * filter changes the headers the route sees, and a request with another body is read by the body
 * binders of controllers and handler routes.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class FilterRequestChangesTest {
    public static final String SPEC_NAME = "FilterRequestChangesTest";
    private static final String TRACE = "request-changes-trace";
    private static final String METHOD_OVERRIDE = "X-Method-Override";

    @Test
    void aPreMatchingFilterThatChangesTheUriChangesTheMatchedHandlerRouteAndController() throws IOException {
        try (ServerUnderTest server = server()) {
            // the pre-matching filters run by order with the pre-matching filter bean, then the route
            // of the new URI is matched, and its query is bound
            assertOk(server, HttpRequest.GET("/rc/old/handler"), "handler fn5,bean10,fn20 name=query");
            assertOk(server, HttpRequest.GET("/rc/old/controller"), "controller fn5,bean10,fn20 name=query");
        }
    }

    @Test
    void aPreMatchingFilterThatChangesTheUriKeepsTheBodyOfTheRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            assertOk(server, text(HttpRequest.POST("/rc/moved/handler", "moved")), "POST handler moved");
            assertOk(server, text(HttpRequest.POST("/rc/moved/controller", "moved")), "POST controller moved");
            assertOk(server, text(HttpRequest.POST("/rc/moved/async", "moved")), "POST async moved");
            assertOk(server, text(HttpRequest.POST("/rc/moved/async-body", "moved")), "POST async-body moved");
        }
    }

    @Test
    void aPreMatchingFilterMethodThatChangesTheUriInPlaceKeepsTheBodyOfTheRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            // the same with a @PreMatching filter method of a bean: the route reads the body
            assertOk(server, text(HttpRequest.POST("/rc/bean-moved/handler", "bean")), "POST handler bean");
            assertOk(server, text(HttpRequest.POST("/rc/bean-moved/controller", "bean")), "POST controller bean");
            assertOk(server, text(HttpRequest.POST("/rc/bean-moved/async", "bean")), "POST async bean");
            assertOk(server, text(HttpRequest.POST("/rc/bean-moved/async-body", "bean")), "POST async-body bean");
        }
    }

    @Test
    void aPreMatchingFilterThatReturnsARequestWithAnotherMethodChangesTheMatchedRouteAndKeepsTheBody() throws IOException {
        try (ServerUnderTest server = server()) {
            assertOk(server, text(HttpRequest.POST("/rc/echo/handler", "put").header(METHOD_OVERRIDE, "PUT")), "PUT handler put");
            assertOk(server, text(HttpRequest.POST("/rc/echo/controller", "put").header(METHOD_OVERRIDE, "PUT")), "PUT controller put");
            assertOk(server, text(HttpRequest.POST("/rc/echo/async", "put").header(METHOD_OVERRIDE, "PUT")), "PUT async put");
            assertOk(server, text(HttpRequest.POST("/rc/echo/async-body", "put").header(METHOD_OVERRIDE, "PUT")), "PUT async-body put");
            // without the header, the request is not changed
            assertOk(server, text(HttpRequest.POST("/rc/echo/handler", "post")), "POST handler post");
        }
    }

    @Test
    void theResponseFiltersOfAPreMatchingFilterFilterTheResponseItAnsweredWithAndTheResponseOfTheRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/rc/blocked"), HttpResponseAssertion.builder()
                .status(HttpStatus.FORBIDDEN)
                .header("X-Pre-After", "1")
                .build());
            AssertionUtils.assertDoesNotThrow(server, text(HttpRequest.POST("/rc/echo/handler", "x")), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .header("X-Pre-After", "1")
                .build());
        }
    }

    @Test
    void theServerTheGroupAndTheRouteFiltersChangeTheHeadersTheHandlerSees() throws IOException {
        try (ServerUnderTest server = server()) {
            assertOk(server, HttpRequest.GET("/rc/headers/handler").header("X-Client", "client"), "server group group-route");
        }
    }

    @Test
    void aRequestWithAnotherBodyIsReadByTheBodyBindersOfHandlerRoutesAndControllers() throws IOException {
        try (ServerUnderTest server = server()) {
            // the filter reads the body, and continues with a request whose body is the body in upper case
            assertOk(server, text(HttpRequest.POST("/rc/replace/handler", "body")), "POST handler BODY");
            assertOk(server, text(HttpRequest.POST("/rc/replace/controller", "body")), "POST controller BODY");
            assertOk(server, text(HttpRequest.POST("/rc/replace/async", "text")), "POST async TEXT");
            assertOk(server, text(HttpRequest.POST("/rc/replace/async-body", "body")), "POST async-body BODY");
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/rc/replace/json", Map.of("name", "json")), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> assertEquals("JSON", response.getBody(String.class).orElseThrow()))
                .build());
        }
    }

    @Test
    void theTextOfTheBodyIsDecodedInTheCharsetOfTheRequestAFilterContinuedWith() throws IOException {
        try (ServerUnderTest server = server()) {
            // the client sends UTF-8 and declares no charset, the filter continues with a request
            // in ISO-8859-1: the handler decodes the bytes in ISO-8859-1
            String text = "caf\u00e9";
            String asLatin1 = new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
            assertOk(server, HttpRequest.POST("/rc/charset/async", text).contentType(MediaType.TEXT_PLAIN_TYPE), asLatin1);
        }
    }

    @Test
    void aPreMatchingFilterReplacesTheWholeRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            assertOk(server, text(HttpRequest.POST("/rc/whole", "original")), "PUT /rc/whole/target from=filter replaced=yes body=whole body");
        }
    }

    private static <T> MutableHttpRequest<T> text(MutableHttpRequest<T> request) {
        return request.contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    private static void assertOk(ServerUnderTest server, HttpRequest<?> request, String body) {
        AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
            .status(HttpStatus.OK)
            .assertResponse(response -> assertEquals(body, response.getBody(String.class).orElseThrow()))
            .build());
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static HttpResponse<?> trace(HttpRequest<?> request, String step) {
        String trace = request.getAttribute(TRACE, String.class).orElse(null);
        request.setAttribute(TRACE, trace == null ? step : trace + "," + step);
        return null;
    }

    private static String describe(String route, HttpRequest<?> request) {
        return route + " " + request.getAttribute(TRACE, String.class).orElse("none") + " name=" + request.getParameters().get("name");
    }

    private static HttpResponse<?> textResponse(String body) {
        return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    private static <B> HttpRequest<B> withMethod(HttpRequest<B> request, HttpMethod method) {
        return new HttpRequestWrapper<>(request) {
            @Override
            public HttpMethod getMethod() {
                return method;
            }

            @Override
            public String getMethodName() {
                return method.name();
            }
        };
    }

    private static <B> HttpRequest<B> withCharset(HttpRequest<B> request, Charset charset) {
        MediaType contentType = new MediaType(MediaType.TEXT_PLAIN, Map.of(MediaType.CHARSET_PARAMETER, charset.name()));
        return new HttpRequestWrapper<>(request) {
            @Override
            public Charset getCharacterEncoding() {
                return charset;
            }

            @Override
            public Optional<MediaType> getContentType() {
                return Optional.of(contentType);
            }
        };
    }

    /**
     * Continue with a request whose body is the given text, and whose method is the given one.
     */
    private static <B> HttpRequest<B> withBody(MutableHttpRequest<B> request, @Nullable HttpMethod method, String text) {
        ServerHttpRequest<?> server = (ServerHttpRequest<?>) request;
        CloseableByteBody body = server.byteBodyFactory().copyOf(text, StandardCharsets.UTF_8);
        if (request instanceof LifecycleHttpRequest<?> lifecycle) {
            // the request owns its body: released when the request ends
            lifecycle.addDisposalResource(body::close);
        }
        return new ReplacedBody<>(method == null ? request : withMethod(request, method), body);
    }

    /**
     * A request with another body: the bytes that the body binders of the route read.
     *
     * @param <B> The body type
     */
    static final class ReplacedBody<B> extends HttpRequestWrapper<B> implements ServerHttpRequest<B> {
        private final ByteBody body;

        ReplacedBody(HttpRequest<B> request, ByteBody body) {
            super(request);
            this.body = body;
        }

        @Override
        public ByteBody byteBody() {
            return body;
        }

        @Override
        public Optional<B> getBody() {
            // not decoded yet
            return Optional.empty();
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Filters implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.filter("/rc/**").preMatching().beforeReplacing(request -> {
                String path = request.getPath();
                if (path.startsWith("/rc/old/")) {
                    // in place, like a void filter method changing its mutable request
                    request.uri(URI.create("/rc/new/" + path.substring("/rc/old/".length()) + "?name=query"));
                } else if (path.startsWith("/rc/moved/")) {
                    request.uri(URI.create("/rc/echo/" + path.substring("/rc/moved/".length())));
                }
                String override = request.getHeaders().get(METHOD_OVERRIDE);
                return override == null ? null : withMethod(request, HttpMethod.parse(override));
            });
            routes.filter("/rc/**").preMatching().order(5).before(request -> trace(request, "fn5"));
            routes.filter("/rc/**").preMatching().order(20).before(request -> trace(request, "fn20"));
            routes.filter("/rc/blocked", "/rc/echo/**").preMatching()
                .beforeReplacing(request -> request.getPath().equals("/rc/blocked") ? HttpResponse.status(HttpStatus.FORBIDDEN) : null)
                .and()
                .after((request, response) -> response.header("X-Pre-After", response.getHeaders().contains("X-Pre-After") ? "2" : "1"));
            // a request filter returning the request it changed
            routes.filter("/rc/headers/**").beforeReplacing(request -> request.header("X-Server", "server"));
            routes.filter("/rc/replace/**").beforeReplacingAsync(request -> ((ServerHttpRequest<?>) request).byteBody()
                .split(ByteBody.SplitBackpressureMode.FASTEST)
                .buffer()
                .thenApply(bytes -> {
                    try (bytes) {
                        return withBody(request, null, bytes.toString(StandardCharsets.UTF_8).toUpperCase(Locale.ROOT));
                    }
                }));
            routes.filter("/rc/charset/**").preMatching().beforeReplacing(request -> withCharset(request, StandardCharsets.ISO_8859_1));
            routes.filter("/rc/whole").preMatching().beforeReplacing(request -> {
                request.uri(URI.create("/rc/whole/target?from=filter"));
                request.header("X-Replaced", "yes");
                return withBody(request, HttpMethod.PUT, "whole body");
            });
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/rc/new/handler", (request, pathVariables) -> textResponse(describe("handler", request)));
            for (String prefix : new String[]{"/rc/echo", "/rc/replace"}) {
                routes.POST(prefix + "/handler", Argument.STRING, (request, pathVariables, body) ->
                    textResponse(request.getMethodName() + " handler " + body)).consumesAll();
                routes.PUT(prefix + "/handler", Argument.STRING, (request, pathVariables, body) ->
                    textResponse(request.getMethodName() + " handler " + body)).consumesAll();
                routes.handleAsync(Set.of(HttpMethod.POST, HttpMethod.PUT), prefix + "/async", (request, pathVariables, body) ->
                    body.text().thenApply(text -> textResponse(request.getMethodName() + " async " + text))).consumesAll();
                routes.handleAsync(Set.of(HttpMethod.POST, HttpMethod.PUT), prefix + "/async-body", (request, pathVariables, body) ->
                    body.body(String.class).thenApply(text -> textResponse(request.getMethodName() + " async-body " + text))).consumesAll();
            }
            routes.asyncPOST("/rc/charset/async", (request, pathVariables, body) ->
                body.text().thenApply(FilterRequestChangesTest::textResponse)).consumesAll();
            routes.POST("/rc/replace/json", Argument.mapOf(String.class, String.class), (request, pathVariables, body) ->
                textResponse(body.get("NAME")));
            routes.PUT("/rc/whole/target", Argument.STRING, (request, pathVariables, body) -> textResponse(request.getMethodName()
                + " " + request.getPath()
                + " from=" + request.getParameters().get("from")
                + " replaced=" + request.getHeaders().get("X-Replaced")
                + " body=" + body)).consumesAll();
            routes.path("/rc/headers", group -> {
                group.before(request -> {
                    request.getHeaders().set("X-Client", "group");
                });
                group.GET("/handler", (request, pathVariables) -> textResponse(request.getHeaders().get("X-Server")
                    + " " + request.getHeaders().get("X-Client")
                    + " " + request.getHeaders().get("X-Route"))
                ).before(request -> {
                    request.getHeaders().add("X-Route", request.getHeaders().get("X-Client") + "-route");
                });
            });
        }
    }

    @Controller("/rc")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Produces(MediaType.TEXT_PLAIN)
    static class ChangedController {

        @Get("/new/controller")
        String changed(HttpRequest<?> request) {
            return describe("controller", request);
        }

        @Post("/echo/controller")
        @Consumes(MediaType.TEXT_PLAIN)
        String post(HttpRequest<?> request, @Body String body) {
            return request.getMethodName() + " controller " + body;
        }

        @Put("/echo/controller")
        @Consumes(MediaType.TEXT_PLAIN)
        String put(HttpRequest<?> request, @Body String body) {
            return request.getMethodName() + " controller " + body;
        }

        @Post("/replace/controller")
        @Consumes(MediaType.TEXT_PLAIN)
        String replaced(HttpRequest<?> request, @Body String body) {
            return request.getMethodName() + " controller " + body;
        }
    }

    @ServerFilter("/rc/**")
    @Order(10)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PreMatchingFilterBean {
        @RequestFilter
        @PreMatching
        void filterRequest(HttpRequest<?> request) {
            trace(request, "bean10");
        }

        @RequestFilter
        @PreMatching
        void moved(MutableHttpRequest<?> request) {
            String path = request.getPath();
            if (path.startsWith("/rc/bean-moved/")) {
                request.uri(URI.create("/rc/echo/" + path.substring("/rc/bean-moved/".length())));
            }
        }
    }
}
