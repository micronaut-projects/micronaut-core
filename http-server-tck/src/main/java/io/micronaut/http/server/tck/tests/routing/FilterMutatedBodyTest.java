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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A filter that continues with a mutated request whose body it set, even to {@code null}, is bound
 * with that body, not with the bytes of the request: a filter can clear or redact the body. A
 * request whose body the filter did not touch is bound with the bytes of the request.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class FilterMutatedBodyTest {
    public static final String SPEC_NAME = "FilterMutatedBodyTest";
    private static final String BODY = "X-Body";
    private static final String BAD_REQUEST = "400";

    @Test
    void aFilterMethodThatContinuesWithTheMutatedRequest() throws IOException {
        assertBodies("method");
    }

    @Test
    void aFilterFunctionThatContinuesWithTheMutableRequest() throws IOException {
        assertBodies("function");
    }

    @Test
    void anAsynchronousHandlerReadsTheBodyAFilterSet() throws IOException {
        try (ServerUnderTest server = server()) {
            // cleared: no body, read once
            assertEquals("|read-once", post(server, "/mb/async/text-body", "function-clear"));
            assertEquals("missing|read-once", post(server, "/mb/async/body-text", "function-clear"));
            assertEquals("false", post(server, "/mb/async/has-body", "function-clear"));
            // replaced with an object: converted by body(Type), not readable as bytes
            assertEquals("replacement|decoded", post(server, "/mb/async/body-text", "function-replace"));
            assertEquals("decoded|replacement", post(server, "/mb/async/text-body", "function-replace"));
            assertEquals("true", post(server, "/mb/async/has-body", "function-replace"));
            // untouched: the bytes of the request
            for (String body : new String[]{"function-untouched", null}) {
                assertEquals("original|read-once", post(server, "/mb/async/text-body", body));
                assertEquals("original|read-once", post(server, "/mb/async/body-text", body));
                assertEquals("true", post(server, "/mb/async/has-body", body));
            }
        }
    }

    private static void assertBodies(String filter) throws IOException {
        try (ServerUnderTest server = server()) {
            for (String route : new String[]{"/mb/required", "/mb/handler"}) {
                String message = filter + " " + route;
                assertEquals(BAD_REQUEST, post(server, route, filter + "-clear"), message);
                assertEquals("replacement", post(server, route, filter + "-replace"), message);
                assertEquals("original", post(server, route, filter + "-untouched"), message);
                assertEquals("original", post(server, route, null), message);
            }
            assertEquals("null", post(server, "/mb/nullable", filter + "-clear"), filter);
            assertEquals("replacement", post(server, "/mb/nullable", filter + "-replace"), filter);
            assertEquals("original", post(server, "/mb/nullable", filter + "-untouched"), filter);
        }
    }

    private static String post(ServerUnderTest server, String path, @Nullable String body) {
        MutableHttpRequest<String> request = HttpRequest.POST(path, "original").contentType(MediaType.TEXT_PLAIN_TYPE);
        if (body != null) {
            request.header(BODY, body);
        }
        try {
            HttpResponse<String> response = server.exchange(request, String.class);
            assertEquals(200, response.code());
            return response.body();
        } catch (HttpClientResponseException e) {
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatus(), e.getMessage());
            return BAD_REQUEST;
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    @ServerFilter("/mb/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BodyFilter {

        @RequestFilter
        @Nullable
        HttpRequest<?> filter(HttpRequest<?> request) {
            String body = request.getHeaders().get(BODY);
            if (body == null) {
                return null;
            }
            return switch (body) {
                case "method-clear" -> request.mutate().body(null);
                case "method-replace" -> request.mutate().body("replacement");
                case "method-untouched" -> request.mutate();
                default -> null;
            };
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.filter("/mb/**").before(request -> {
                String body = request.getHeaders().get(BODY);
                if (body == null) {
                    return null;
                }
                return switch (body) {
                    case "function-clear" -> request.body(null);
                    case "function-replace" -> request.body("replacement");
                    case "function-untouched" -> request;
                    default -> null;
                };
            });
            routes.asyncPOST("/mb/async/text-body", (request, pathVariables) -> read(request::text)
                .thenCompose(text -> read(() -> request.body(String.class)).thenApply(body -> textResponse(text + "|" + body)))).consumesAll();
            routes.asyncPOST("/mb/async/body-text", (request, pathVariables) -> read(() -> request.body(String.class))
                .thenCompose(body -> read(request::text).thenApply(text -> textResponse(body + "|" + text)))).consumesAll();
            routes.asyncPOST("/mb/async/has-body", (request, pathVariables) -> {
                String hasBody = String.valueOf(request.hasBody());
                return request.discardBody().thenApply(ignored -> textResponse(hasBody));
            }).consumesAll();
            routes.POST("/mb/handler", Argument.STRING, (request, pathVariables, body) ->
                HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE)
            ).consumes(MediaType.TEXT_PLAIN_TYPE);
        }
    }

    private static HttpResponse<?> textResponse(String body) {
        return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    /**
     * Read the body, and describe how the read failed.
     */
    private static CompletionStage<String> read(Supplier<CompletionStage<String>> reader) {
        CompletionStage<String> stage;
        try {
            stage = reader.get();
        } catch (RuntimeException e) {
            stage = CompletableFuture.failedFuture(e);
        }
        return stage.handle((value, error) -> error == null ? value : describe(error));
    }

    private static String describe(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        String message = String.valueOf(cause.getMessage());
        if (cause instanceof IllegalStateException && message.contains("can be read once")) {
            return "read-once";
        }
        if (cause instanceof IllegalStateException && message.contains("decoded object")) {
            return "decoded";
        }
        if (cause instanceof UnsatisfiedRouteException) {
            return "missing";
        }
        return cause.toString();
    }

    @Controller("/mb")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.TEXT_PLAIN)
    static class BodyController {

        @Post("/required")
        String required(@Body String body) {
            return body;
        }

        @Post("/nullable")
        String nullable(@Nullable @Body String body) {
            return String.valueOf(body);
        }
    }
}
