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

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.HttpRoutes;
import io.micronaut.web.router.RouteSource;
import io.micronaut.web.router.RouteTable;
import io.micronaut.web.router.RouteTableFactory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Routes to handler functions, declared by {@link HttpRoutes} beans or published at runtime by a
 * {@link RouteSource}, run like controller routes: filters and error routes apply, bodies are
 * decoded and encoded, {@code GET} routes answer {@code HEAD}, and a wrong method is answered
 * with 405.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRoutesTest {
    public static final String SPEC_NAME = "HandlerRoutesTest";
    private static final String TRACE = "handler-routes-trace";
    private static final String FILTER_THREAD = "handler-routes-filter-thread";

    @Test
    void handlerRouteIsHandledAndFiltered() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/hello/Fred"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Hello Fred")
                .headers(Map.of("X-Fn-Filter", "true"))
                .build());
        }
    }

    @Test
    void headRequestIsHandledByTheGetRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.HEAD("/fn/hello/Fred"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .build());
        }
    }

    @Test
    void bodyIsDecodedAndTheResultEncoded() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/items", Map.of("name", "apple")), HttpResponseAssertion.builder()
                .status(HttpStatus.CREATED)
                .body("{\"saved\":\"apple\"}")
                .build());
        }
    }

    @Test
    void asyncHandlerCompletesTheResponse() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/async"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("async")
                .build());
        }
    }

    @Test
    void errorRouteHandlesTheHandlerException() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/fn/fail"), HttpResponseAssertion.builder()
                .status(HttpStatus.CONFLICT)
                .body("handled checked failure")
                .build());
        }
    }

    @Test
    void wrongMethodIsNotAllowed() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.DELETE("/fn/hello/Fred"), HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .build());
        }
    }

    @Test
    void routeRequestFilterAnswersInsteadOfTheRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/fn/guarded"), HttpResponseAssertion.builder()
                .status(HttpStatus.UNAUTHORIZED)
                .headers(Map.of("X-Fn-Filter", "true"))
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.HEAD("/fn/guarded"), HttpResponseAssertion.builder()
                .status(HttpStatus.UNAUTHORIZED)
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/guarded").header("X-Token", "secret"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("guarded")
                .build());
        }
    }

    @Test
    void routeFiltersRunClosestToTheRouteInTheOrderDeclared() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/trace"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("global,before1,before2")
                .headers(Map.of("X-Trace", "after1,after2,global"))
                .build());
        }
    }

    @Test
    void asyncRouteFiltersCompleteTheChainLater() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/fn/async-guarded"), HttpResponseAssertion.builder()
                .status(HttpStatus.FORBIDDEN)
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/async-guarded").header("X-Token", "secret"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("async guarded")
                .headers(Map.of("X-Async-After", "true"))
                .build());
        }
    }

    @Test
    void routeFilterRunsOnItsExecutor() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/filter-executor"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("handler-filter-thread")
                .build());
        }
    }

    @Test
    void urlEncodedFormIsReadIntoFormData() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms/7", "name=Fred&age=42&tag=a&tag=b")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("7 Fred 43 [a, b] no-file")
                .build());
        }
    }

    @Test
    void multipartFormIsReadIntoFormData() throws IOException {
        try (ServerUnderTest server = server()) {
            MultipartBody body = MultipartBody.builder()
                .addPart("name", "Fred")
                .addPart("age", "42")
                .addPart("tag", "a")
                .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, "picture".getBytes(StandardCharsets.UTF_8))
                .build();
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms/8", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("8 Fred 43 [a] avatar.txt=picture")
                .build());
        }
    }

    @Test
    void missingOrInvalidFormFieldIsABadRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.POST("/fn/forms/7", "age=42")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.POST("/fn/forms/7", "name=Fred&age=old")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .build());
        }
    }

    @Test
    void runtimeRoutesUseHandlers() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/fn-dynamic/x"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());

            server.getApplicationContext().getBean(DynamicHandlerRoutes.class).enable();

            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn-dynamic/x"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("dynamic /fn-dynamic/x")
                .headers(Map.of("X-Fn-Filter", "true", "X-Table-Route", "true"))
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    /**
     * Completes on an executor of the application, like a service call would: the filter chain
     * then continues on that thread.
     */
    private static <T> CompletableFuture<T> completeLater(ExecutorService executor, Supplier<T> value) {
        return CompletableFuture.supplyAsync(value, executor);
    }

    private static byte[] bytes(CompletedFileUpload upload) {
        try {
            return upload.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static HttpResponse<?> append(HttpRequest<?> request, String step) {
        request.setAttribute(TRACE, request.getAttribute(TRACE, String.class).orElse("") + "," + step);
        return null;
    }

    static final class CheckedFailure extends Exception {
        CheckedFailure(String message) {
            super(message);
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {
        @Singleton
        @Named("fn")
        HttpRoutes fnRoutes(@Named("handler-filter") ExecutorService executor) {
            return routes -> {
                routes.GET("/fn/hello/{name}", (request, pathVariables) ->
                    HttpResponse.ok("Hello " + pathVariables.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE));
                routes.POST("/fn/items", Argument.mapOf(String.class, String.class), (request, pathVariables, item) ->
                    HttpResponse.created(Map.of("saved", item.get("name"))));
                routes.handleAsync(HttpMethod.GET, "/fn/async", (request, pathVariables) ->
                    completeLater(executor, () -> HttpResponse.ok("async").contentType(MediaType.TEXT_PLAIN_TYPE)));
                routes.GET("/fn/guarded", (request, pathVariables) -> HttpResponse.ok("guarded").contentType(MediaType.TEXT_PLAIN_TYPE))
                    .before(request -> "secret".equals(request.getHeaders().get("X-Token")) ? null : HttpResponse.unauthorized());
                routes.GET("/fn/trace", (request, pathVariables) -> HttpResponse.ok(request.getAttribute(TRACE, String.class).orElse("")).contentType(MediaType.TEXT_PLAIN_TYPE))
                    .before(request -> append(request, "before1"))
                    .before(request -> append(request, "before2"))
                    .after((request, response) -> response.getHeaders().set("X-Trace", "after1"))
                    .after((request, response) -> response.getHeaders().set("X-Trace", response.getHeaders().get("X-Trace") + ",after2"));
                routes.GET("/fn/async-guarded", (request, pathVariables) -> HttpResponse.ok("async guarded").contentType(MediaType.TEXT_PLAIN_TYPE))
                    .beforeAsync(request -> completeLater(executor, () ->
                        "secret".equals(request.getHeaders().get("X-Token")) ? null : HttpResponse.status(HttpStatus.FORBIDDEN)))
                    .afterAsync((request, response) -> completeLater(executor, () -> response.header("X-Async-After", "true")));
                routes.GET("/fn/filter-executor", (request, pathVariables) ->
                        HttpResponse.ok(request.getAttribute(FILTER_THREAD, String.class).orElse("")).contentType(MediaType.TEXT_PLAIN_TYPE))
                    .before("handler-filter", request -> {
                        request.setAttribute(FILTER_THREAD, Thread.currentThread().getName());
                        return null;
                    });
                routes.POST("/fn/forms/{id}", (request, pathVariables, form) -> {
                    String file = form.findFile("avatar")
                        .map(upload -> upload.getFilename() + "=" + new String(bytes(upload), StandardCharsets.UTF_8))
                        .orElse("no-file");
                    return HttpResponse.ok(pathVariables.getLong("id") + " " + form.getString("name") + " " + (form.getInt("age") + 1)
                        + " " + form.getValues("tag") + " " + file).contentType(MediaType.TEXT_PLAIN_TYPE);
                });
                routes.GET("/fn/fail", (request, pathVariables) -> {
                    throw new CheckedFailure("checked failure");
                });
            };
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Executors {
        @Singleton
        @Named("handler-filter")
        @Bean(preDestroy = "shutdown")
        ExecutorService handlerFilterExecutor() {
            return java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "handler-filter-thread"));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class DynamicHandlerRoutes implements RouteSource {
        private final RouteTableFactory tables;
        private volatile RouteTable current = RouteTable.empty();

        DynamicHandlerRoutes(RouteTableFactory tables) {
            this.tables = tables;
        }

        void enable() {
            current = tables.build(routes -> routes.GET("/fn-dynamic/{+path}", (request, pathVariables) ->
                HttpResponse.ok("dynamic " + request.getPath()).contentType(MediaType.TEXT_PLAIN_TYPE))
                .after((request, response) -> response.header("X-Table-Route", "true")));
        }

        @Override
        public RouteTable snapshot() {
            return current;
        }
    }

    @ServerFilter({"/fn/**", "/fn-dynamic/**"})
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FnFilter {
        @RequestFilter
        void filterRequest(HttpRequest<?> request) {
            if (request.getPath().equals("/fn/trace")) {
                request.setAttribute(TRACE, "global");
            }
        }

        @ResponseFilter
        void filter(MutableHttpResponse<?> response) {
            response.header("X-Fn-Filter", "true");
            String trace = response.getHeaders().get("X-Trace");
            if (trace != null) {
                response.getHeaders().set("X-Trace", trace + ",global");
            }
        }
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Errors {
        @Error(global = true, exception = CheckedFailure.class)
        HttpResponse<String> checkedFailure(CheckedFailure failure) {
            return HttpResponse.<String>status(HttpStatus.CONFLICT).body("handled " + failure.getMessage()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }
}
